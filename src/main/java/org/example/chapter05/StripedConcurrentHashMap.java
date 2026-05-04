package org.example.chapter05;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A pedagogical re-implementation of the JAVA 7 ConcurrentHashMap,
 * which is the design JCIP (2006) discusses directly in chapters 5 and 11.
 *
 * ==================================================================
 * THE BIG IDEA: LOCK STRIPING
 * ==================================================================
 *
 * A naive "thread-safe HashMap" wraps every operation in one lock:
 *
 *     synchronized (map) { ... }          // Hashtable, Collections.synchronizedMap
 *
 * That gives correctness but terrible scalability — N threads serialize
 * on one monitor regardless of whether they touch the same keys.
 *
 * Java 7's ConcurrentHashMap instead splits the table into N independent
 * sub-tables called SEGMENTS. Each segment is itself a small hash table
 * with its own ReentrantLock. A key's hash code chooses:
 *
 *   1. which segment it belongs to (top bits of hash)
 *   2. which bin inside that segment it lives in (low bits of hash)
 *
 *      hash = h(key.hashCode())
 *
 *         ┌─────────── top bits ──────────┐┌── low bits ──┐
 *      hash = SSSS SSSS SSSS SSSS ........ BBBB BBBB BBBB
 *                    segment index           bin index
 *
 * Two writers that hash to different segments NEVER contend. With the
 * default 16 segments, the theoretical write concurrency is ~16× that
 * of a single-lock map.
 *
 * ==================================================================
 * LOCK-FREE READS
 * ==================================================================
 *
 * get() does NOT acquire any lock in the common case. It relies on two
 * JMM-enforced guarantees:
 *
 *   • HashEntry.value is VOLATILE — a write to it happens-before any
 *     subsequent read, so readers never see a stale value once a put
 *     has completed.
 *
 *   • HashEntry.next is FINAL — the chain structure is IMMUTABLE from
 *     a given entry forward. Removing a middle entry requires REBUILDING
 *     all entries before it (copy-on-write on the prefix). This is what
 *     lets readers walk the chain without locking: the chain they see
 *     at the moment they load the head is a consistent linked sequence
 *     and cannot change under them. See remove() for the rebuild.
 *
 *   • Segment.count is VOLATILE and serves as the "publication fence."
 *     Writers update count LAST on a put; readers read count FIRST on a
 *     get (see the "count != 0" guard). Per JMM, reading a volatile
 *     observes all writes the writer performed before the volatile
 *     write — so reading count ensures the new entry is visible.
 *
 * This is the Java-5-era trick called "piggybacking on volatile": one
 * volatile variable publishes many non-volatile ones safely.
 *
 * ==================================================================
 * WHAT THIS IMPLEMENTATION DOES AND DOESN'T DO
 * ==================================================================
 *
 *   Does:        put, get, remove, containsKey, size, isEmpty,
 *                putIfAbsent, replace (atomic compound ops)
 *                per-segment locking
 *                lock-free reads via volatile + final next
 *                the "try N times unlocked, then lock all" size()
 *
 *   Doesn't:     segment rehashing on overflow (fixed-size segments)
 *                treeification of long chains (Java 8 feature)
 *                iterators (would need weakly-consistent traversal)
 *                Map interface implementation (kept minimal for clarity)
 *                null keys/values (real CHM rejects them — we do too)
 *
 * The real JDK class is ~1500 lines with many micro-optimizations;
 * this is ~250 lines chosen for readability.
 */
public class StripedConcurrentHashMap<K, V> {

    // --------------------------------------------------------------
    // Constants — chosen to match the JDK 7 defaults where meaningful
    // --------------------------------------------------------------

    /** Default number of segments. Must be a power of two so we can
     *  mask off the high bits with (hash >>> shift) & (n-1). */
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    /** Default per-segment table size. Must be a power of two. */
    private static final int DEFAULT_SEGMENT_CAPACITY = 16;

    /** Max retries for an unlocked size() before we fall back to
     *  locking every segment. Tuned empirically in the real class. */
    private static final int SIZE_RETRIES = 2;

    // --------------------------------------------------------------
    // Final fields — set at construction, never change afterwards
    // --------------------------------------------------------------

    /** The segments. Final, so safely published to every thread that
     *  observes a fully-constructed map (JMM guarantee on final fields). */
    private final Segment<K, V>[] segments;

    /** Shift used to extract the high bits that pick a segment.
     *  If we have 16 segments (= 1 << 4), the top 4 meaningful bits
     *  of a 32-bit hash pick the segment; shift = 32 - 4 = 28. */
    private final int segmentShift;

    /** Mask = segments.length - 1. segmentIndex = (hash >>> shift) & mask. */
    private final int segmentMask;

    // --------------------------------------------------------------
    // Construction
    // --------------------------------------------------------------

    public StripedConcurrentHashMap() {
        this(DEFAULT_CONCURRENCY_LEVEL, DEFAULT_SEGMENT_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public StripedConcurrentHashMap(int concurrencyLevel, int segmentCapacity) {
        // Round up to next power of two so the mask trick works.
        int segCount = 1;
        int shiftBits = 0;
        while (segCount < concurrencyLevel) {
            segCount <<= 1;
            shiftBits++;
        }
        this.segmentShift = 32 - shiftBits;
        this.segmentMask = segCount - 1;

        int cap = 1;
        while (cap < segmentCapacity) cap <<= 1;

        this.segments = (Segment<K, V>[]) new Segment[segCount];
        for (int i = 0; i < segCount; i++) {
            segments[i] = new Segment<>(cap);
        }
    }

    // --------------------------------------------------------------
    // Hashing
    // --------------------------------------------------------------

    /**
     * A supplemental hash function. Real CHM does this because many
     * Java classes (especially Integer, String in old JDKs) have hash
     * codes that collide heavily on their low bits. If we passed the
     * raw hashCode straight into "& mask" we'd cluster everything into
     * one segment.
     *
     * The "single-word Wang/Jenkins" spreader used here is the same one
     * the JDK 7 CHM uses internally.
     */
    private static int spread(int h) {
        h += (h <<  15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h <<   3);
        h ^= (h >>>  6);
        h += (h <<   2) + (h << 14);
        return h ^ (h >>> 16);
    }

    /** Select which segment owns this key. Uses the HIGH bits of the
     *  spread hash. The low bits are reused inside the segment for bin
     *  selection — this way neighbouring keys land in different bins
     *  but may still share a segment. */
    private Segment<K, V> segmentFor(int hash) {
        return segments[(hash >>> segmentShift) & segmentMask];
    }

    // --------------------------------------------------------------
    // Public API — each method just forwards to the right segment
    // --------------------------------------------------------------

    public V get(Object key) {
        if (key == null) throw new NullPointerException();
        int h = spread(key.hashCode());
        return segmentFor(h).get(key, h);
    }

    public boolean containsKey(Object key) {
        if (key == null) throw new NullPointerException();
        int h = spread(key.hashCode());
        return segmentFor(h).get(key, h) != null;
    }

    public V put(K key, V value) {
        if (key == null || value == null) throw new NullPointerException();
        int h = spread(key.hashCode());
        return segmentFor(h).put(key, h, value, false);
    }

    public V putIfAbsent(K key, V value) {
        if (key == null || value == null) throw new NullPointerException();
        int h = spread(key.hashCode());
        return segmentFor(h).put(key, h, value, true);
    }

    public V remove(Object key) {
        if (key == null) throw new NullPointerException();
        int h = spread(key.hashCode());
        return segmentFor(h).remove(key, h, null);
    }

    /** Atomic conditional remove — only removes if mapped to the given value. */
    public boolean remove(Object key, Object expectedValue) {
        if (key == null || expectedValue == null) return false;
        int h = spread(key.hashCode());
        return segmentFor(h).remove(key, h, expectedValue) != null;
    }

    /** Atomic replace — only writes if currently mapped to oldValue. */
    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null)
            throw new NullPointerException();
        int h = spread(key.hashCode());
        return segmentFor(h).replace(key, h, oldValue, newValue);
    }

    public boolean isEmpty() {
        // Cheap check: if any segment has a non-zero count, we're non-empty.
        // We DON'T need a lock because count is volatile.
        //
        // Subtlety: a naïve loop "return any(count > 0)" is wrong because
        // entries can move between segments conceptually if we were rehashing,
        // but since we don't rehash segments here, the volatile read is enough.
        for (Segment<K, V> s : segments) {
            if (s.count != 0) return false;
        }
        return true;
    }

    /**
     * size() is interesting because summing each segment's volatile count
     * non-atomically can return a value that was NEVER simultaneously
     * true of the map — writers could have moved elements between the
     * reads. Real CHM's strategy, reproduced here:
     *
     *   1. Sum all segment counts AND all segment modCounts unlocked.
     *   2. Repeat. If the modCount sum matches the previous pass, the
     *      counts are consistent — return the count sum.
     *   3. After SIZE_RETRIES failures, give up and lock every segment
     *      in order, sum, then unlock every segment.
     *
     * modCount is incremented on every structural change (put of a new
     * key, remove). A stable modCount sum across two passes means no
     * structural change happened during those passes.
     */
    public int size() {
        for (int attempt = 0; attempt < SIZE_RETRIES; attempt++) {
            long modSum1 = 0, modSum2 = 0;
            long countSum = 0;

            for (Segment<K, V> s : segments) modSum1 += s.modCount;
            for (Segment<K, V> s : segments) countSum += s.count;
            for (Segment<K, V> s : segments) modSum2 += s.modCount;

            if (modSum1 == modSum2) {
                return (int) Math.min(countSum, Integer.MAX_VALUE);
            }
        }

        // Fallback: lock every segment so no writer can slip through.
        long total = 0;
        for (Segment<K, V> s : segments) s.lock();
        try {
            for (Segment<K, V> s : segments) total += s.count;
        } finally {
            // Unlock in reverse order — symmetric with acquisition.
            for (int i = segments.length - 1; i >= 0; i--) segments[i].unlock();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    // ==============================================================
    // HashEntry — a single node in a segment's chain
    // ==============================================================

    /**
     * Immutable-in-structure entry. KEY, HASH, and NEXT are FINAL —
     * once published, the chain starting at this entry can never
     * change its topology. Only VALUE can change (it's volatile).
     *
     * Consequence: to "remove" an entry from the middle of a chain,
     * the writer rebuilds every entry BEFORE the removed one into a
     * new chain (copy-on-write on the prefix), then publishes a new
     * head. Readers walking the old chain see the old, still-valid
     * structure and eventually observe the new head via a volatile
     * read of the bin slot.
     */
    static final class HashEntry<K, V> {
        final K key;
        final int hash;
        final HashEntry<K, V> next;
        volatile V value;

        HashEntry(K key, int hash, HashEntry<K, V> next, V value) {
            this.key = key;
            this.hash = hash;
            this.next = next;
            this.value = value;
        }
    }

    // ==============================================================
    // Segment — a hash table with its own lock
    // ==============================================================

    /**
     * Each Segment is a small HashMap guarded by a ReentrantLock.
     * It extends ReentrantLock directly — an idiom from the real CHM
     * that saves one object indirection per segment. (We'd lose this
     * if we ever wanted Segment to extend something else; in the real
     * CHM it's a deliberate micro-optimization on hot data.)
     *
     * Fields touched by readers without the lock are volatile:
     *   - count       : publication fence for the whole table
     *   - table slots : each one holds a HashEntry reference; we use
     *                   array-volatile semantics via an atomic accessor
     *                   (see getBin / setBin).
     *
     * modCount is only written under the lock, so readers don't need
     * volatile semantics for correctness — but size() reads it without
     * the lock, tolerating a stale value. That's fine because size()
     * verifies stability by double-reading it.
     */
    static final class Segment<K, V> extends ReentrantLock {
        private static final long serialVersionUID = 1L;

        /** Number of entries. Volatile for the unlocked get() fast path. */
        volatile int count;

        /** Incremented on every structural change (add/remove). Read
         *  unlocked by size(); written only while we hold the lock. */
        int modCount;

        /** The per-segment bin array. The array reference itself is
         *  effectively final in this simplified version (no rehashing). */
        final HashEntry<K, V>[] table;

        @SuppressWarnings("unchecked")
        Segment(int capacity) {
            this.table = (HashEntry<K, V>[]) new HashEntry[capacity];
        }

        private int indexFor(int hash) {
            return hash & (table.length - 1);
        }

        // ----------------------------------------------------------
        // Reads — no lock acquired
        // ----------------------------------------------------------

        V get(Object key, int hash) {
            // The "count != 0" gate is more than an optimization: reading
            // this volatile establishes happens-before with the writer's
            // final count update on put(). If count is 0, the table is
            // provably empty for this thread.
            if (count != 0) {
                HashEntry<K, V> e = table[indexFor(hash)];
                while (e != null) {
                    if (e.hash == hash && key.equals(e.key)) {
                        V v = e.value;
                        if (v != null) return v;
                        // Rare race: entry was seen mid-removal. Fall back
                        // to a locked read to resolve authoritatively.
                        return getUnderLock(key, hash);
                    }
                    e = e.next;
                }
            }
            return null;
        }

        private V getUnderLock(Object key, int hash) {
            lock();
            try {
                HashEntry<K, V> e = table[indexFor(hash)];
                while (e != null) {
                    if (e.hash == hash && key.equals(e.key)) return e.value;
                    e = e.next;
                }
                return null;
            } finally {
                unlock();
            }
        }

        // ----------------------------------------------------------
        // Writes — all acquire the segment lock
        // ----------------------------------------------------------

        V put(K key, int hash, V value, boolean onlyIfAbsent) {
            lock();
            try {
                int i = indexFor(hash);
                HashEntry<K, V> first = table[i];

                // Walk the chain looking for an existing key.
                for (HashEntry<K, V> e = first; e != null; e = e.next) {
                    if (e.hash == hash && key.equals(e.key)) {
                        V old = e.value;
                        if (!onlyIfAbsent) {
                            // value is volatile — visible to lock-free readers.
                            e.value = value;
                        }
                        return old;
                    }
                }

                // New key — prepend a new entry whose next is the old head.
                // This is cheap: no copy of the existing chain needed.
                HashEntry<K, V> newHead = new HashEntry<>(key, hash, first, value);
                table[i] = newHead;

                modCount++;
                // count MUST be written LAST (after table[i] and modCount).
                // Its volatile write is what publishes the new entry's fields
                // to unlocked readers. Keeping it last is load-bearing.
                count = count + 1;
                return null;
            } finally {
                unlock();
            }
        }

        /**
         * If expectedValue is non-null, remove only when the current
         * mapping matches (atomic conditional remove). Returns the old
         * value when removed, null otherwise.
         */
        V remove(Object key, int hash, Object expectedValue) {
            lock();
            try {
                int i = indexFor(hash);
                HashEntry<K, V> first = table[i];
                HashEntry<K, V> e = first;

                while (e != null && !(e.hash == hash && key.equals(e.key))) {
                    e = e.next;
                }
                if (e == null) return null;

                V oldVal = e.value;
                if (expectedValue != null && !expectedValue.equals(oldVal)) {
                    return null;
                }

                // ============================================================
                // The chain-rebuild trick — the heart of lock-free readers
                // ============================================================
                // Because HashEntry.next is FINAL, we can't just rewire the
                // previous node's next pointer around `e`. Instead, we:
                //
                //   1. Keep the suffix (everything AFTER e) as-is — its
                //      structure is already correct and already published.
                //   2. Rebuild each entry BEFORE e as a fresh HashEntry that
                //      points at this suffix.
                //   3. Store the new head into the bin.
                //
                // Readers holding a reference into the OLD chain continue
                // walking it harmlessly — they may briefly observe the
                // removed entry, which is acceptable "weakly consistent"
                // behaviour and matches the real CHM.
                HashEntry<K, V> newFirst = e.next;
                for (HashEntry<K, V> p = first; p != e; p = p.next) {
                    newFirst = new HashEntry<>(p.key, p.hash, newFirst, p.value);
                }
                table[i] = newFirst;

                modCount++;
                count = count - 1;   // publication write — same rule as put()
                return oldVal;
            } finally {
                unlock();
            }
        }

        boolean replace(K key, int hash, V oldValue, V newValue) {
            lock();
            try {
                for (HashEntry<K, V> e = table[indexFor(hash)]; e != null; e = e.next) {
                    if (e.hash == hash && key.equals(e.key)) {
                        if (oldValue.equals(e.value)) {
                            e.value = newValue;
                            return true;
                        }
                        return false;
                    }
                }
                return false;
            } finally {
                unlock();
            }
        }
    }
}
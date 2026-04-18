package org.example.chapter04;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Listing 4.16 - Implementing put-if-absent Using Composition.
 *
 * The preferred way to add a new atomic operation to an existing
 * class: wrap it in a new class that implements List itself, and use
 * this wrapper's OWN intrinsic lock to guard every method that
 * touches the underlying list.
 *
 * Why composition wins over extension and client-side locking:
 *   - The synchronization policy lives entirely in ImprovedList,
 *     independent of the underlying list's implementation.
 *   - Works whether the underlying list is thread-safe or not.
 *   - Doesn't break if the underlying class changes its locking.
 *
 * Assumption: once a List is passed to ImprovedList, callers must
 * NOT touch the underlying list directly — ImprovedList holds the
 * only outstanding reference, effectively confining it.
 */
public class ImprovedList<T> implements List<T> {

    private final List<T> list;

    public ImprovedList(List<T> list) {
        this.list = list;
    }

    public synchronized boolean putIfAbsent(T x) {
        boolean absent = !list.contains(x);
        if (absent) {
            list.add(x);
        }
        return absent;
    }

    // All List methods delegate under the same lock
    @Override public synchronized int size() { return list.size(); }
    @Override public synchronized boolean isEmpty() { return list.isEmpty(); }
    @Override public synchronized boolean contains(Object o) { return list.contains(o); }
    @Override public synchronized Iterator<T> iterator() { return list.iterator(); }
    @Override public synchronized Object[] toArray() { return list.toArray(); }
    @Override public synchronized <U> U[] toArray(U[] a) { return list.toArray(a); }
    @Override public synchronized boolean add(T t) { return list.add(t); }
    @Override public synchronized boolean remove(Object o) { return list.remove(o); }
    @Override public synchronized boolean containsAll(Collection<?> c) { return list.containsAll(c); }
    @Override public synchronized boolean addAll(Collection<? extends T> c) { return list.addAll(c); }
    @Override public synchronized boolean addAll(int index, Collection<? extends T> c) { return list.addAll(index, c); }
    @Override public synchronized boolean removeAll(Collection<?> c) { return list.removeAll(c); }
    @Override public synchronized boolean retainAll(Collection<?> c) { return list.retainAll(c); }
    @Override public synchronized void clear() { list.clear(); }
    @Override public synchronized T get(int index) { return list.get(index); }
    @Override public synchronized T set(int index, T element) { return list.set(index, element); }
    @Override public synchronized void add(int index, T element) { list.add(index, element); }
    @Override public synchronized T remove(int index) { return list.remove(index); }
    @Override public synchronized int indexOf(Object o) { return list.indexOf(o); }
    @Override public synchronized int lastIndexOf(Object o) { return list.lastIndexOf(o); }
    @Override public synchronized ListIterator<T> listIterator() { return list.listIterator(); }
    @Override public synchronized ListIterator<T> listIterator(int index) { return list.listIterator(index); }
    @Override public synchronized List<T> subList(int fromIndex, int toIndex) { return list.subList(fromIndex, toIndex); }
}

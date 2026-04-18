package org.example.chapter04;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Listing 4.7 - Delegating Thread Safety to a ConcurrentHashMap.
 *
 * No explicit synchronization. Thread safety is delegated entirely to:
 *   - ConcurrentHashMap for the map structure.
 *   - Immutable {@link Point} values (freely shareable).
 *
 * getLocations() returns an unmodifiable LIVE view — updates made
 * after the call are visible to callers. For a static snapshot,
 * use {@link #getLocationsAsStaticSnapshot()} (Listing 4.8).
 *
 * This delegation works because the tracker imposes no invariants
 * across multiple state variables — each vehicle's location is
 * independent of every other's.
 */
public class DelegatingVehicleTracker {

    private final ConcurrentMap<String, Point> locations;
    private final Map<String, Point> unmodifiableMap;

    public DelegatingVehicleTracker(Map<String, Point> points) {
        this.locations = new ConcurrentHashMap<>(points);
        this.unmodifiableMap = Collections.unmodifiableMap(locations);
    }

    /** Returns an unmodifiable LIVE view — reflects subsequent updates. */
    public Map<String, Point> getLocations() {
        return unmodifiableMap;
    }

    /** Listing 4.8 - Returns a static snapshot of locations. */
    public Map<String, Point> getLocationsAsStaticSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(locations));
    }

    public Point getLocation(String id) {
        return locations.get(id);
    }

    public void setLocation(String id, int x, int y) {
        if (locations.replace(id, new Point(x, y)) == null) {
            throw new IllegalArgumentException("invalid vehicle name: " + id);
        }
    }
}

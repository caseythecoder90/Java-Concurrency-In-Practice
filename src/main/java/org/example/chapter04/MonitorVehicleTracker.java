package org.example.chapter04;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Listing 4.4 - Monitor-based Vehicle Tracker Implementation.
 *
 * The Java monitor pattern applied to a vehicle tracker. All state is
 * guarded by `this`; the internal map and its MutablePoint values
 * never escape because:
 *   - getLocations() returns a deep copy of the map AND its values.
 *   - getLocation() returns a copy of the MutablePoint (or null).
 *   - setLocation() mutates the stored point under the lock.
 *
 * Trade-offs:
 *   - deepCopy runs under the lock — potentially long operation.
 *   - Returned snapshot does not update as vehicles move (could be
 *     considered a feature: internal consistency).
 *
 * See {@link DelegatingVehicleTracker} for a delegating variant and
 * {@link PublishingVehicleTracker} for a state-publishing variant.
 */
public class MonitorVehicleTracker {

    private final Map<String, MutablePoint> locations;

    public MonitorVehicleTracker(Map<String, MutablePoint> locations) {
        this.locations = deepCopy(locations);
    }

    public synchronized Map<String, MutablePoint> getLocations() {
        return deepCopy(locations);
    }

    public synchronized MutablePoint getLocation(String id) {
        MutablePoint loc = locations.get(id);
        return loc == null ? null : new MutablePoint(loc);
    }

    public synchronized void setLocation(String id, int x, int y) {
        MutablePoint loc = locations.get(id);
        if (loc == null) {
            throw new IllegalArgumentException("No such ID: " + id);
        }
        loc.x = x;
        loc.y = y;
    }

    private static Map<String, MutablePoint> deepCopy(Map<String, MutablePoint> m) {
        Map<String, MutablePoint> result = new HashMap<>();
        for (String id : m.keySet()) {
            result.put(id, new MutablePoint(m.get(id)));
        }
        return Collections.unmodifiableMap(result);
    }
}

package org.example.chapter04;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listing 4.12 - Vehicle Tracker that Safely Publishes Underlying State.
 *
 * Uses thread-safe mutable {@link SafePoint}s as the map values.
 * Clients receive the SafePoint references directly (not copies) and
 * may mutate them via SafePoint.set(...).
 *
 * This works ONLY because PublishingVehicleTracker imposes no
 * additional constraints on vehicle locations. If it needed to veto
 * updates, enforce per-vehicle invariants, or coordinate updates
 * across vehicles, this design would not be appropriate — the
 * tracker would have to interpose logic instead of publishing the
 * points.
 *
 * Contrast with {@link MonitorVehicleTracker} (defensive copies) and
 * {@link DelegatingVehicleTracker} (immutable points).
 */
public class PublishingVehicleTracker {

    private final Map<String, SafePoint> locations;
    private final Map<String, SafePoint> unmodifiableMap;

    public PublishingVehicleTracker(Map<String, SafePoint> locations) {
        this.locations = new ConcurrentHashMap<>(locations);
        this.unmodifiableMap = Collections.unmodifiableMap(this.locations);
    }

    public Map<String, SafePoint> getLocations() {
        return unmodifiableMap;
    }

    public SafePoint getLocation(String id) {
        return locations.get(id);
    }

    public void setLocation(String id, int x, int y) {
        if (!locations.containsKey(id)) {
            throw new IllegalArgumentException("invalid vehicle name: " + id);
        }
        locations.get(id).set(x, y);
    }
}

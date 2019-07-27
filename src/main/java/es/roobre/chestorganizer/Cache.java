package es.roobre.chestorganizer;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches the best receivers for a given organizer and material
 * TODO: This cache is only invalidated if a returned location is no longer valid, not if it is no longer the best. This is not optimal.
 */
class Cache {
    private static long MAX_AGE = 30 * 60 * 1000; // 30 minutes
    private Map<Location, Map<Material, CacheEntry<Location>>> holder;

    Cache() {
        this.holder = new HashMap<>();
    }

    synchronized Location get(Location organizerLocation, Material material) {
        Map<Material, CacheEntry<Location>> materialMap = this.holder.get(organizerLocation);
        if (materialMap != null) {
            CacheEntry<Location> item = materialMap.get(material);
            if (item != null && item.time + MAX_AGE > System.currentTimeMillis()) {
                // Still valid
                return item.item;
            } else {
                this.remove(organizerLocation, material);
                return null;
            }
        }

        return null;
    }

    synchronized Location put(Location organizerLocation, Material material, Location receiverLocation) {
        Map<Material, CacheEntry<Location>> materialMap = this.holder.computeIfAbsent(organizerLocation, k -> new HashMap<>());
        CacheEntry<Location> putLoc = materialMap.put(material, new CacheEntry<>(receiverLocation));
        if (putLoc == null) {
            return null;
        }

        return putLoc.item;
    }

    synchronized void remove(Location organizerLocation, Material material) {
        Map<Material, CacheEntry<Location>> materialMap = this.holder.get(organizerLocation);
        if (materialMap != null) {
            materialMap.remove(material);
        }
    }
}

class CacheEntry<T> {
    final T item;
    long time;

    CacheEntry(T item) {
        this.item = item;
        this.time = System.currentTimeMillis();
    }
}
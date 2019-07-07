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
    private Map<Location, Map<Material, CacheItem<Location>>> holder;

    Cache() {
        this.holder = new HashMap<>();
    }

    synchronized Location get(Location organizerLocation, Material material) {
        Map<Material, CacheItem<Location>> materialMap = this.holder.get(organizerLocation);
        if (materialMap != null) {
            CacheItem<Location> item = materialMap.get(material);
            if (item.time + MAX_AGE > System.currentTimeMillis()) {
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
        Map<Material, CacheItem<Location>> materialMap = this.holder.computeIfAbsent(organizerLocation, k -> new HashMap<>());
        return materialMap.put(material, new CacheItem<>(receiverLocation)).item;
    }

    synchronized void remove(Location organizerLocation, Material material) {
        Map<Material, CacheItem<Location>> materialMap = this.holder.get(organizerLocation);
        if (materialMap != null) {
            materialMap.remove(material);
        }
    }
}

class CacheItem<T> {
    T item;
    long time;

    CacheItem(T item) {
        this.item = item;
        this.time = System.currentTimeMillis();
    }
}
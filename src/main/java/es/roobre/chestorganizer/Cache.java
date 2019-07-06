package es.roobre.chestorganizer;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches the best receivers for a given organizer and material
 * TODO: This cache is only invalidated if a returned location is no longer valid, not if it is no longer the best. This is not optimal.
 */
public class Cache {
    private Map<Location, Map<Material, Location>> holder;

    public Cache() {
        this.holder = new HashMap<>();
    }

    public synchronized Location get(Location organizerLocation, Material material) {
        Map<Material, Location> materialMap = this.holder.get(organizerLocation);
        if (materialMap != null) {
            return materialMap.get(material);
        }

        return null;
    }

    public synchronized Location put(Location organizerLocation, Material material, Location receiverLocation) {
        Map<Material, Location> materialMap = this.holder.computeIfAbsent(organizerLocation, k -> new HashMap<>());
        return materialMap.put(material, receiverLocation);
    }

    public synchronized void remove(Location organizerLocation, Material material) {
        Map<Material, Location> materialMap = this.holder.get(organizerLocation);
        if (materialMap != null) {
            materialMap.remove(material);
        }
    }
}

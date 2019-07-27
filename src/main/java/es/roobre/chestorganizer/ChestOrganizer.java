package es.roobre.chestorganizer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ChestOrganizer extends JavaPlugin implements Listener {
    private Logger log = getLogger();
    private Cache cache = new Cache();

    // TODO: move these configs out to a config file
    private static final int RANGE_HORIZONTAL = 8;
    private static final int RANGE_VERTICAL = 1;

    private static final Set<Material> ACTIVATOR_MATERIALS = Stream.of(Material.REDSTONE_BLOCK).collect(Collectors.toSet());
    private static final Set<Material> ACTIVATOR_CONTAINERS = Stream.of(Material.CHEST).collect(Collectors.toSet());

    @Override
    public void onEnable() {
        log.info("ChestOrganizer enabled, registering listeners...");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent click) {
        // Get currently open inventory
        InventoryHolder holder = click.getInventory().getHolder();
        if (!(isOrganizer(holder))) {
            return;
        }

        // Get clicked inventory (which can be a player's, if they shift-clicked an item
        Inventory clickedInventory = click.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        InventoryHolder clickedHolder = clickedInventory.getHolder();
        if (clickedHolder == null) {
            return;
        }

        InventoryAction action = click.getAction();

        if (
                !(clickedHolder.equals(holder) && (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME)) && // We are placing stuff, so the interesting (being placed) items are on hand (cursor)
                        !(clickedHolder instanceof Player && (action == InventoryAction.MOVE_TO_OTHER_INVENTORY)) && // We are moving (shift-clicking) items, so interesting ones are on slot
                        !(!click.getClick().isLeftClick() && click.getClick().isRightClick()) // Middle click on inventory
        ) {
            return;
        }

        organize((Container) holder);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent drag) {
        // Get currently open inventory
        InventoryHolder holder = drag.getInventory().getHolder();
        if (!(isOrganizer(holder))) {
            return;
        }

        organize((Container) holder);
    }

    /**
     * If supplied chest is an organizer, move the given stack of items to the closest suitable receiver, if any
     *
     * @param container Original target of the items
     */
    private void organize(Container container) {
        getServer().getScheduler().runTask(this, () -> this.asyncOrganize(container));
    }

    private synchronized void asyncOrganize(Container container) {
        List<ItemStack> removeList = new ArrayList<>();

        Stream.of(container.getInventory().getStorageContents())
                .filter(i -> i != null && i.getType() != Material.AIR && i.getAmount() > 0)
                .forEach(items -> {
                    Container targetChest = findSuitable(container.getLocation(), items.getType());
                    if (targetChest == null) {
                        return;
                    }

                    // Add items and get those which could not be added
                    ItemStack notAdded = targetChest.getInventory().addItem(items.clone()).get(0);

                    // Create a stack of items to be removed, set its amount to the actually added items
                    ItemStack toRemove = items.clone();
                    toRemove.setAmount(toRemove.getAmount() - (notAdded == null ? 0 : notAdded.getAmount()));
                    if (toRemove.getAmount() > 0) {
                        removeList.add(toRemove);
                    }

                    log.info("Moved " + (toRemove.getAmount()) + " " + items.getType() + " to " + targetChest.getLocation());
                });

        for (ItemStack toRemove : removeList) {
            // Remove added items and check if we could not remove any
            int notRemoved = Util.removeItems(container.getInventory(), toRemove);

            if (notRemoved != 0) {
                log.warning("Player cheated: Could not remove " + notRemoved + " " + toRemove.getType() + " from " + container);
            }
        }
    }

    /**
     * Returns the closest holder to location which already contains at least one item of the given material
     *
     * @param chestLocation Central location to search from
     * @param material      Material to look for
     * @return A suitable holder, null if none
     */
    private Container findSuitable(Location chestLocation, Material material) {
        Location cachedLoc = this.cache.get(chestLocation, material);
        if (cachedLoc != null) {
            Container cachedReceiver = isSuitableReceiver(cachedLoc.getBlock().getState(), material);
            if (cachedReceiver != null) {
                return cachedReceiver;
            } else {
                this.cache.remove(chestLocation, material);
            }
        }

        Container bestCandidate = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int deltaX = -RANGE_HORIZONTAL; deltaX <= RANGE_HORIZONTAL; deltaX++) {
            for (int deltaZ = -RANGE_HORIZONTAL; deltaZ <= RANGE_HORIZONTAL; deltaZ++) {
                Location targetLoc = new Location(chestLocation.getWorld(), 0, 0, 0).add(chestLocation).add(deltaX, 0, deltaZ);
                if (chestLocation.equals(targetLoc)) {
                    continue;
                }

                Container candidate = isSuitableReceiver(targetLoc.getBlock().getState(), material);
                if (candidate != null) {
                    double distance = chestLocation.distance(targetLoc);
                    if (distance < bestDistance) {
                        bestCandidate = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }

        if (bestCandidate != null) {
            this.cache.put(chestLocation, material, bestCandidate.getLocation());
        }

        return bestCandidate;
    }

    /**
     * Checks if the given location contains an unlocked chest with at least one instance of the given item
     *
     * @param block Entity to check
     * @param mat   Item to look for
     * @return A suitable chest (as InventoryHolder), or null if it wasn't suitable
     */
    private Container isSuitableReceiver(BlockState block, Material mat) {
        // TODO: add array of accepted types of containers instead (just like we check it for the source)
        if (block instanceof Container) {
            Container container = (Container) block;

            if (!container.isLocked() && container.getInventory().contains(mat)) {
                // isSimilar
                return container;
            }
        }

        return null;
    }

    /**
     * Checks if the target holder is flagged as an organizer chest (i.e. is a container sitting on a redstone block)
     *
     * @param holder The holder to check
     * @return Whether the chest is an organizer or not
     */
    private boolean isOrganizer(InventoryHolder holder) {
        // check if the holder is a container
        if (holder instanceof Container) {
            Container container = (Container) holder;

            return ACTIVATOR_MATERIALS.contains(container.getLocation().subtract(0, 1, 0).getBlock().getBlockData().getMaterial()) && ACTIVATOR_CONTAINERS.contains(container.getBlock().getType());
        }

        return false;
    }
}

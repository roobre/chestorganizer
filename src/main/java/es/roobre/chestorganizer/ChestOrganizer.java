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

        // Should not happen but just to make the linter happy
        if (clickedInventory == null) {
            return;
        }

        InventoryHolder clickedHolder = clickedInventory.getHolder();

        ItemStack playerStack;
        InventoryAction action = click.getAction();

        if (clickedHolder.equals(holder) && (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME)) {
            // We are placing stuff, so the interesting (being placed) items are on hand (cursor)
            playerStack = click.getCursor();
        } else if (clickedHolder instanceof Player && (action == InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
            // We are moving (shift-clicking) items, so interesting ones are on slot
            playerStack = click.getCurrentItem();
        } else {
            return;
        }

        if (playerStack == null) {
            return;
        }

        ItemStack depositedStack = playerStack.clone();

        if (action == InventoryAction.PLACE_ONE) {
            depositedStack.setAmount(1);
        }

        try {
            organize((Container) holder, depositedStack);
        } catch (NoReceiverException e) {
            click.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent drag) {
        // Get currently open inventory
        InventoryHolder holder = drag.getInventory().getHolder();
        if (!(isOrganizer(holder))) {
            return;
        }

        // TODO: Drag events are broken, as a workaround, cancel the event
        drag.setCancelled(true);
        return;

        // There is probably a more efficient way to do this, but drag events are quite rare, so honestly i wont bother
//        for (ItemStack items : drag.getNewItems().values()) {
//            organize((Chest) holder, items);
//        }
    }

    /**
     * If supplied chest is an organizer, move the given stack of items to the closest suitable receiver, if any
     *
     * @param container Original target of the items
     * @param items     Items to move
     */
    private void organize(Container container, ItemStack items) throws NoReceiverException {
        if (!isOrganizer(container)) {
            return;
        }

        Container targetChest = findSuitable(container.getLocation(), items.getType());
        if (targetChest == null) {
            throw new NoReceiverException();
        }

        getServer().getScheduler().runTask(
                this,
                () -> {
                    ItemStack notAdded = targetChest.getInventory().addItem(items.clone()).get(0);
                    int notRemoved = Util.removeItems(container.getInventory(), items.getType(), items.getAmount() - (notAdded == null ? 0 : notAdded.getAmount()));

                    log.info("Moved " + items.getAmount() + " " + items.getType() + " from " + container.getBlock().getLocation() + " to " + targetChest.getBlock().getLocation());

                    if (notRemoved != 0) {
                        log.warning("Player cheated: Could not remove " + notRemoved + " " + items.getType() + " from " + container);
                    }
                });
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

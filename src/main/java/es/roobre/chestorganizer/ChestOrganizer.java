package es.roobre.chestorganizer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.Comparator;

public final class ChestOrganizer extends JavaPlugin implements Listener {
    private static final int RANGE_HORIZONTAL = 5;
    private static final int RANGE_VERTICAL = 1;

    private static final HashSet<Material> ACTIVATOR_MATERIALS = new HashSet<>(Arrays.asList(Material.REDSTONE_BLOCK));

    @Override
    public void onEnable() {
        getLogger().info("ChestOrganizer enabled, registering listeners...");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent click) {
        // Get currently open inventory
        InventoryHolder holder = click.getInventory().getHolder();
        if (!(holder instanceof Chest)) {
            return;
        }

        // Get clicked inventory (which can be a player's, if they shift-clicked an item
        Inventory clickedInventory = click.getClickedInventory();
        if (clickedInventory == null) return; // Should not happen but just to make the linter happy
        InventoryHolder clickedHolder = clickedInventory.getHolder();

        ItemStack playerStack;
        InventoryAction action = click.getAction();

        if (clickedHolder instanceof Chest && (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME)) {
            // We are placing stuff, so the interesting (being placed) items are on hand (cursor)
            playerStack = click.getCursor();
        } else if (!(clickedHolder instanceof Chest) && (action == InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
            // We are moving (shift-clicking) items, so interesting ones are on slot
            playerStack = click.getCurrentItem();
        } else {
            return;
        }

        if (playerStack == null) return;
        ItemStack depositedStack = playerStack.clone();

        if (action == InventoryAction.PLACE_ONE) depositedStack.setAmount(1);

        organize((Chest) holder, depositedStack);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent drag) {
        // Get currently open inventory
        InventoryHolder holder = drag.getInventory().getHolder();
        if (!(holder instanceof Chest)) {
            return;
        }

        // There is probably a more efficient way to do this, but drag events are quite rare, so honestly i wont bother
        for (ItemStack items : drag.getNewItems().values()) {
            organize((Chest) holder, items);
        }
    }

    /**
     * If supplied chest is an organizer, move the given stack of items to the closest suitable receiver, if any
     *
     * @param container Original target of the items
     * @param items     Items to move
     */
    private void organize(Container container, ItemStack items) {
        if (!isOrganizer(container.getBlock())) {
            return;
        }

        Container targetChest = findSuitable(container.getLocation(), items.getType());
        if (targetChest == null) {
            return;
        }

        getServer().getScheduler().runTask(
                this,
                () -> {
                    ItemStack notAdded = targetChest.getInventory().addItem(items).get(0);
                    int notRemoved = removeItems(container.getInventory(), items.getType(), items.getAmount() - (notAdded == null ? 0 : notAdded.getAmount()));
                    getLogger().info("Moved " + items.getAmount() + " " + items.getType() + " from " + container.getBlock().getLocation() + " to " + targetChest.getBlock().getLocation());
                    if (notRemoved != 0) {
                        getLogger().warning("Player cheated: Could not remove " + notRemoved + " " + items.getType() + " from " + container);
                    }
                });
    }

    /**
     * Returns a the closest holder to location which already contains at least one item of the given material
     *
     * @param chestLocation Central location to search from
     * @param material      Material to look for
     * @return A suitable holder, null if none
     */
    private static Container findSuitable(Location chestLocation, Material material) {
        Container bestCandidate = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int deltaX = -RANGE_HORIZONTAL; deltaX <= RANGE_HORIZONTAL; deltaX++) {
            for (int deltaZ = -RANGE_HORIZONTAL; deltaZ <= RANGE_HORIZONTAL; deltaZ++) {
                Location targetLoc = new Location(chestLocation.getWorld(), 0, 0, 0).add(chestLocation).add(deltaX, 0, deltaZ);
                if (chestLocation.equals(targetLoc)) continue;

                Container candidate = isSuitable(targetLoc.getBlock().getState(), material);
                if (candidate != null) {
                    double distance = chestLocation.distance(targetLoc);
                    if (distance < bestDistance) {
                        bestCandidate = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }

        return bestCandidate;
    }

    /**
     * Removes the specified number of items matching a given type from an inventory.
     * Stacks of items will be removed until the specified amount has been reached, or until there are no more items to remove.
     * Smaller stacks of items are removed first, in an attempt to keep the inventory as tidy as possible.
     *
     * @param src    Inventory to remove items from
     * @param type   Material to target
     * @param amount Maximum amount of items to remove
     * @return The number of items that could not be removed, if amount was higher than the total number of items in the inventory
     */
    private int removeItems(Inventory src, Material type, int amount) {
        List<ItemStack> stackList = new ArrayList<>(src.all(type).values());
        stackList.sort(Comparator.comparingInt(ItemStack::getAmount));
        for (ItemStack stack : stackList) {
            int toRemove = Math.min(amount, stack.getAmount());
            stack.setAmount(stack.getAmount() - toRemove);
            amount -= toRemove;

            if (amount == 0) break; // We are done removing
        }

        return amount;
    }

    /**
     * Checks if the given location contains an unlocked chest with at least one instance of the given item
     *
     * @param block Entity to check
     * @param mat   Item to look for
     * @return A suitable chest (as InventoryHolder), or null if it wasn't suitable
     */
    private static Container isSuitable(BlockState block, Material mat) {
        if (!(block instanceof Container)) {
            return null;
        }

        Container container = (Container) block;

        if (container.isLocked()) {
            return null;
        }

        if (!container.getInventory().contains(mat)) {
            return null;
        }

        // isSimilar
        return container;
    }

    /**
     * Checks if the target block is flagged as an organizer chest (i.e. is a container sitting on a redstone block)
     *
     * @param block The block to check
     * @return Wether the chest is an organizer or not
     */
    private static boolean isOrganizer(Block block) {
        return ACTIVATOR_MATERIALS.contains(block.getLocation().subtract(0, 1, 0).getBlock().getBlockData().getMaterial());
    }
}

package es.roobre.chestorganizer;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Util {
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
    protected static int removeItems(Inventory src, Material type, int amount) {
        List<ItemStack> stackList = src.all(type).values().stream()
                .sorted(Comparator.comparingInt(ItemStack::getAmount))
                .collect(Collectors.toList());

        for (ItemStack stack : stackList) {
            int toRemove = Math.min(amount, stack.getAmount());
            stack.setAmount(stack.getAmount() - toRemove);
            amount -= toRemove;

            if (amount == 0) {
                break; // We are done removing
            }
        }

        return amount;
    }

    protected static int removeItems(Inventory src, ItemStack items) {
        return removeItems(src, items.getType(), items.getAmount());
    }
}

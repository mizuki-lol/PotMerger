package dev.potionmerger;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Listens to crafting table events.
 *
 * Flow:
 *  1. PrepareItemCraftEvent – whenever the player arranges items in the grid,
 *     we check if it's a valid merge recipe and inject the result into the
 *     output slot. This gives live preview.
 *  2. CraftItemEvent – when the player actually takes the result, we consume
 *     all potion inputs manually (Bukkit won't do it for us since we bypassed
 *     the recipe system).
 */
public final class CraftingListener implements Listener {

    @SuppressWarnings("unused")
    private final JavaPlugin plugin;

    public CraftingListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Step 1 – preview
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        ItemStack result = PotionMerger.tryMerge(matrix);
        if (result != null) {
            inv.setResult(result);
        }
    }

    // -------------------------------------------------------------------------
    // Step 2 – actually craft
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        // Only handle our custom merge; if tryMerge returns null this is a
        // vanilla recipe, leave it alone.
        ItemStack mergeResult = PotionMerger.tryMerge(matrix);
        if (mergeResult == null) return;

        // Cancel the vanilla event so Bukkit doesn't mess with ingredients
        event.setCancelled(true);

        // Give the result to the player
        HumanEntity player = event.getWhoClicked();
        giveOrDrop(player, mergeResult);

        // Consume one of each potion in the matrix
        for (int i = 0; i < matrix.length; i++) {
            ItemStack slot = matrix[i];
            if (slot != null && !slot.getType().isAir()) {
                if (slot.getAmount() > 1) {
                    slot.setAmount(slot.getAmount() - 1);
                } else {
                    matrix[i] = null;
                }
            }
        }
        inv.setMatrix(matrix);
        inv.setResult(null);

        // Refresh the preview for the remaining items (if any)
        ItemStack nextResult = PotionMerger.tryMerge(matrix);
        inv.setResult(nextResult); // null is fine — clears the slot
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private void giveOrDrop(HumanEntity player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(drop ->
                player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }
}

package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.api.UnlockResult;
import com.otectus.runictome.impl.AbsorptionPolicy;
import com.otectus.runictome.impl.ItemDelivery;
import com.otectus.runictome.impl.TomeCraftingMatcher;
import com.otectus.runictome.recipe.TomeAbsorbRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Files the books from a {@link com.otectus.runictome.recipe.TomeAbsorbRecipe} craft into the
 * crafting player's library.
 *
 * <p>This is separate from {@link AbsorptionHandler}, which looks at what a craft <em>produced</em>.
 * This one looks at what a craft <em>consumed</em>, and is gated by its own config option.
 *
 * <p>{@code ItemCraftedEvent} fires from {@code ResultSlot.checkTakeAchievements} before the
 * ingredient-shrink loop in {@code ResultSlot.onTake}, so the grid is still fully populated here and
 * vanilla will remove exactly one item from each occupied slot once this returns. Shift-clicking the
 * output crafts in a loop, but the tome is consumed on the first pass, so the recipe stops matching
 * and the event fires exactly once.
 */
@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class CraftingAbsorptionHandler {

    private CraftingAbsorptionHandler() {}

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack output = event.getCrafting();
        if (output.isEmpty() || !TomeCraftingMatcher.isRunicTome(output)) return;
        // getInventory() is typed Container: a mod may fire this from an auto-crafter with an
        // arbitrary container, and getRecipeFor needs a real grid.
        if (!(event.getInventory() instanceof CraftingContainer grid)) return;

        // The event does not name the recipe. getRecipeFor returns the first match in an
        // arbitrarily-ordered map, so confirm ours actually won this grid before consuming anything
        // -- absorbing another recipe's ingredients would destroy them.
        Level level = sp.level();
        if (!(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .orElse(null) instanceof TomeAbsorbRecipe)) {
            return;
        }

        // Re-run the same matcher the recipe used, over the same grid, so the two can never disagree
        // about which slots counted.
        TomeCraftingMatcher.scan(grid).ifPresent(match -> absorb(sp, match));
    }

    private static void absorb(ServerPlayer sp, TomeCraftingMatcher.Match match) {
        boolean refunded = false;
        for (TomeCraftingMatcher.Entry entry : match.books()) {
            ItemStack retained = entry.stack().copy();
            retained.setCount(1);
            // The extraction marker is this mod's bookkeeping, not part of the book. Storing it
            // would leave a marked stack sitting in the library for no reason.
            AbsorptionPolicy.clearExtracted(retained);

            UnlockResult result = RunicTomeAPI.unlockBook(sp, entry.key(), retained);
            if (RunicTomeConfig.COMMON.verboseLogging.get()) {
                RunicTome.LOGGER.info("Crafted {} into {}'s tome -> {}",
                        entry.key(), sp.getName().getString(), result);
            }

            // Vanilla is about to take exactly one item from this slot. Reuse the shared consumption
            // contract so crafting agrees with pickup, sweep and smelt: a book that was not stored
            // (FAILED), or a duplicate under absorbWholeStack=false, must not cost the player
            // anything. Refunding here works precisely because the shrink has not happened yet.
            if (AbsorptionPolicy.consumptionFor(result, 1) > 0) continue;

            ItemStack refund = entry.stack().copy();
            refund.setCount(1);
            // Name it first: Inventory.add mutates the stack it is given and leaves it empty on a
            // full transfer, at which point getHoverName() would report "Air".
            Component refundName = refund.getHoverName();
            deliver(sp, refund);
            refunded = true;
            sp.displayClientMessage(Component.translatable(
                    result == UnlockResult.FAILED ? "runictome.craft.failed" : "runictome.craft.already_had",
                    refundName), false);
        }
        if (refunded) {
            sp.getInventory().setChanged();
            sp.inventoryMenu.broadcastChanges();
            if (sp.containerMenu != null && sp.containerMenu != sp.inventoryMenu) {
                sp.containerMenu.broadcastChanges();
            }
        }
    }

    /** Inventory first, then the ground — the same never-destroy path extraction uses. */
    private static void deliver(ServerPlayer sp, ItemStack stack) {
        ItemDelivery.deliver(sp, stack);
    }
}

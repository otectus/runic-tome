package com.otectus.runictome.recipe;

import com.otectus.runictome.impl.TomeCraftingMatcher;
import com.otectus.runictome.item.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Runic Tome plus one or more books in any crafting grid yields the Runic Tome, with the books
 * filed into the crafting player's library.
 *
 * <p>This has to be a {@link CustomRecipe} rather than a JSON shapeless recipe because "is this a
 * book" is decided at runtime by the adapter chain, which depends on config, item tags, datapacks
 * and which mods are installed. No fixed {@code Ingredient} can express it.
 *
 * <p><b>This class performs no side effects.</b> {@link #assemble} is re-run for the grid preview
 * every time a slot changes and gets no {@code Player} to write to; unlocking here would file books
 * the instant they were placed. The library write lives in
 * {@link com.otectus.runictome.event.CraftingAbsorptionHandler}, which runs on
 * {@code PlayerEvent.ItemCraftedEvent} — fired from {@code ResultSlot.checkTakeAchievements} with
 * the grid still populated, immediately before vanilla shrinks the ingredients.
 *
 * <p>The input tome is consumed by that shrink and replaced by the output. Because
 * {@link com.otectus.runictome.item.RunicTomeItem} is stateless — no NBT, all data in a player
 * capability — tome-in/tome-out is a clean no-op. Deliberately no {@code getRemainingItems}
 * override: handing the tome back as a container remainder while also producing one would duplicate
 * it.
 */
public class TomeAbsorbRecipe extends CustomRecipe {

    public TomeAbsorbRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        // The logical client answers on shape alone. It has no authority -- the grid preview is
        // computed server-side and pushed -- but it does consult its own RecipeManager inside the
        // predicted ResultSlot.onTake, where a disagreement would corrupt the displayed stacks.
        // See TomeCraftingMatcher.hasAbsorbShape for the full reasoning.
        if (level != null && level.isClientSide) {
            return TomeCraftingMatcher.hasAbsorbShape(container);
        }
        return TomeCraftingMatcher.scan(container).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        // Reads the tome slot directly rather than re-running the scan, so it agrees with whichever
        // branch of matches() accepted this grid. Hands back the player's own tome so a custom name
        // or another mod's NBT survives the craft.
        int tomeSlot = TomeCraftingMatcher.tomeSlot(container);
        if (tomeSlot < 0) return ItemStack.EMPTY;
        ItemStack output = container.getItem(tomeSlot).copy();
        output.setCount(1);
        return output;
    }

    /** Works in the 2x2 player grid as well as the 3x3 table. */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    /**
     * Vanilla special recipes return {@link ItemStack#EMPTY} here; naming the real output is more
     * informative for anything that inspects recipes, and is safe because {@code isSpecial()} keeps
     * this recipe out of the recipe book and out of recipe unlocking.
     */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return new ItemStack(ModItems.RUNIC_TOME.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TOME_ABSORB.get();
    }
}

package com.otectus.runictome;

import com.otectus.runictome.recipe.TomeAbsorbRecipe;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the recipe class itself. The Runic Tome has no registry entry in a unit JVM, so a positive
 * match is not reachable here — {@link TomeCraftingMatcherTest} drives that through the injected
 * core. What is worth pinning at this level is the recipe's declared contract: the grid sizes it
 * accepts, that it stays out of the recipe book, and that it is inert without a tome.
 */
class TomeAbsorbRecipeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TomeAbsorbRecipe recipe() {
        return new TomeAbsorbRecipe(
                new ResourceLocation("runictome", "tome_absorb"), CraftingBookCategory.MISC);
    }

    @Test
    void worksInTheTwoByTwoInventoryGrid() {
        // The 2x2 player grid is an explicit requirement, not just the crafting table.
        assertTrue(recipe().canCraftInDimensions(2, 2));
        assertTrue(recipe().canCraftInDimensions(3, 3));
        assertTrue(recipe().canCraftInDimensions(1, 2), "a tome plus one book needs only two slots");
    }

    @Test
    void doesNotFitInASingleSlot() {
        assertFalse(recipe().canCraftInDimensions(1, 1));
    }

    @Test
    void isSpecialSoItStaysOutOfTheRecipeBook() {
        // Load-bearing: the ingredient set is dynamic and cannot be expressed as fixed Ingredients,
        // so the recipe must be exempt from the recipe book and from doLimitedCrafting.
        assertTrue(recipe().isSpecial());
    }

    @Test
    void aGridWithoutATomeNeitherMatchesNorAssembles() {
        CraftingContainer grid = new TestGrid(3, 3);
        grid.setItem(0, new ItemStack(Items.PAPER));
        assertFalse(recipe().matches(grid, null));
        assertTrue(recipe().assemble(grid, null).isEmpty());
    }

    @Test
    void matchingDoesNotConsumeTheGrid() {
        CraftingContainer grid = new TestGrid(3, 3);
        ItemStack books = new ItemStack(Items.PAPER, 4);
        grid.setItem(0, books);
        recipe().matches(grid, null);
        recipe().assemble(grid, null);
        assertTrue(books.getCount() == 4, "preview evaluation must never consume an ingredient");
    }

    /** Minimal {@link CraftingContainer}; the real ones need a live menu. */
    private static final class TestGrid implements CraftingContainer {
        private final int width;
        private final int height;
        private final NonNullList<ItemStack> items;

        TestGrid(int width, int height) {
            this.width = width;
            this.height = height;
            this.items = NonNullList.withSize(width * height, ItemStack.EMPTY);
        }

        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public List<ItemStack> getItems() { return items; }
        @Override public int getContainerSize() { return items.size(); }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return items.set(slot, ItemStack.EMPTY); }
        @Override public ItemStack removeItem(int slot, int amount) {
            return net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
        }
        @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { items.clear(); }
        @Override public void fillStackedContents(StackedContents contents) {
            for (ItemStack stack : items) contents.accountSimpleStack(stack);
        }
    }
}

package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.integration.VanillaBookAdapter;
import com.otectus.runictome.item.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Decides whether a crafting grid holds "one Runic Tome plus books, and nothing else".
 *
 * <p>Both the recipe ({@link com.otectus.runictome.recipe.TomeAbsorbRecipe}) and the handler that
 * performs the unlock ({@link com.otectus.runictome.event.CraftingAbsorptionHandler}) call this, so
 * they can never disagree about which slots were accepted or under which key.
 *
 * <p>The core {@link #scan(List, Predicate, Function)} takes its tome test and book resolver as
 * parameters. That keeps it pure and unit-testable without a running game — the item registry has no
 * {@code runictome:runic_tome} in a test JVM — and follows the same split that made
 * {@link com.otectus.runictome.integration.HeuristicBookAdapter#classify} testable.
 */
public final class TomeCraftingMatcher {

    private TomeCraftingMatcher() {}

    /** One accepted book ingredient: which slot it came from, its key, and the live stack. */
    public record Entry(int slot, BookKey key, ItemStack stack) {}

    /** An accepted grid. */
    public record Match(int tomeSlot, List<Entry> books) {}

    /**
     * Production entry point. Applies the config gate, then resolves books through the vanilla
     * family and the adapter chain.
     */
    public static Optional<Match> scan(List<ItemStack> slots) {
        if (!RunicTomeConfig.absorbViaCrafting()) return Optional.empty();
        return scan(slots, TomeCraftingMatcher::isRunicTome, TomeCraftingMatcher::resolveBook);
    }

    /** Convenience for a crafting grid. {@code CraftingContainer} is a {@link Container}. */
    public static Optional<Match> scan(Container container) {
        if (container == null) return Optional.empty();
        int size = container.getContainerSize();
        List<ItemStack> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(container.getItem(i));
        }
        return scan(slots);
    }

    /**
     * Pure core. Never touches the world, the config or the registries beyond what the supplied
     * functions do.
     *
     * <p>Rules, in order:
     * <ol>
     *   <li>Exactly one Runic Tome. Zero or two or more is a rejection — and this check runs first,
     *       so the overwhelmingly common no-tome grid returns before a single book is resolved.
     *       That matters: {@code matches} is evaluated against every loaded crafting recipe every
     *       time a grid slot changes.</li>
     *   <li>Every other non-empty slot must resolve to a book. One unrecognized ingredient rejects
     *       the whole grid rather than being silently consumed alongside the books.</li>
     *   <li>At least one book.</li>
     * </ol>
     */
    public static Optional<Match> scan(List<ItemStack> slots,
                                       Predicate<ItemStack> isTome,
                                       Function<ItemStack, Optional<BookKey>> bookResolver) {
        if (slots == null || slots.isEmpty()) return Optional.empty();

        int tomeSlot = -1;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack == null || stack.isEmpty()) continue;
            if (isTome.test(stack)) {
                if (tomeSlot >= 0) return Optional.empty();
                tomeSlot = i;
            }
        }
        if (tomeSlot < 0) return Optional.empty();

        List<Entry> books = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (i == tomeSlot) continue;
            ItemStack stack = slots.get(i);
            if (stack == null || stack.isEmpty()) continue;
            Optional<BookKey> key = bookResolver.apply(stack);
            if (key.isEmpty()) return Optional.empty();
            books.add(new Entry(i, key.get(), stack));
        }
        if (books.isEmpty()) return Optional.empty();
        return Optional.of(new Match(tomeSlot, List.copyOf(books)));
    }

    /**
     * Index of the single Runic Tome in a grid, or {@code -1} when there is not exactly one.
     * Allocation-free and free of {@code identify} calls.
     */
    public static int tomeSlot(Container container) {
        if (container == null) return -1;
        int found = -1;
        for (int i = 0, n = container.getContainerSize(); i < n; i++) {
            if (!isRunicTome(container.getItem(i))) continue;
            if (found >= 0) return -1;
            found = i;
        }
        return found;
    }

    /**
     * Shape-only test: exactly one Runic Tome plus at least one other item, without asking whether
     * those items are books.
     *
     * <p>Used as the logical client's answer in {@code matches}. The client has no authority here —
     * {@code CraftingMenu.slotChangedCraftingGrid} is server-only, so the client's result slot holds
     * only what the server sent — but it does run its own {@code RecipeManager} inside the predicted
     * {@code ResultSlot.onTake}. If the client disagreed there,
     * {@code RecipeManager.getRemainingItemsFor} would fall through to returning the grid's own
     * stacks <em>by reference</em>, and {@code onTake} would then {@code grow} each slot by its own
     * count — visibly doubling the player's books until the next server sync corrected it.
     *
     * <p>Disagreement is realistic because {@link com.otectus.runictome.api.RunicTomeAPI#identify}
     * reads the COMMON config ({@code bookKeywords}, {@code extraBookItemIds}, ...) and Forge does
     * not sync COMMON configs. Answering on shape alone is a deliberate superset: it cannot approve
     * a craft the server refused, and it keeps the client out of that fallback.
     */
    public static boolean hasAbsorbShape(Container container) {
        int tome = tomeSlot(container);
        if (tome < 0) return false;
        for (int i = 0, n = container.getContainerSize(); i < n; i++) {
            if (i == tome) continue;
            if (!container.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    /**
     * Guarded so an early-load or test-time call cannot throw: {@code RegistryObject.get()} fails
     * hard before registration completes.
     */
    public static boolean isRunicTome(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && ModItems.RUNIC_TOME.isPresent() && stack.is(ModItems.RUNIC_TOME.get());
    }

    /**
     * Resolves one grid ingredient to a book key.
     *
     * <p>The vanilla family is tried first. It is a closed five-item set and the only resolver that
     * mints a per-stack unique key; letting a {@code #runictome:guide_books} tag claim
     * {@code minecraft:written_book} ahead of it would collapse every written book a player owns
     * into a single library entry.
     *
     * <p>The adapter chain is then consulted through
     * {@link AdapterRegistry#identifyForExplicitAction}, which honours every exclusion except the
     * extraction marker — crafting a book back in is a deliberate act, unlike an ambient sweep.
     */
    public static Optional<BookKey> resolveBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        // Hoisted out of identifyForExplicitAction so it covers the vanilla family too: a pack must
        // be able to exclude minecraft:enchanted_book on its own via #runictome:absorb_blocklist,
        // and VanillaBookAdapter.keyFor is deliberately policy-free.
        if (AbsorptionPolicy.isExcludedForExplicitAction(stack)) return Optional.empty();
        if (RunicTomeConfig.craftingAcceptsVanillaBooks()) {
            Optional<BookKey> vanilla = VanillaBookAdapter.keyFor(stack);
            if (vanilla.isPresent()) return vanilla;
        }
        return AdapterRegistry.get().identifyForExplicitAction(stack);
    }
}

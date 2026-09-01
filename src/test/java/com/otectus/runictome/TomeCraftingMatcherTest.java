package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.impl.TomeCraftingMatcher;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the parameterized core of {@link TomeCraftingMatcher}. The real Runic Tome is not
 * registered in a unit JVM and {@code RunicTomeAPI.identify} would hit the un-installed delegate, so
 * the tome predicate and the book resolver are injected — the same reason
 * {@link com.otectus.runictome.integration.HeuristicBookAdapter#classify} was extracted.
 */
class TomeCraftingMatcherTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Stands in for the Runic Tome, which has no registry entry here. */
    private static final Predicate<ItemStack> IS_TOME = stack -> stack.is(Items.NETHER_STAR);

    private static final BookKey KEY = new BookKey(
            new ResourceLocation("runictome", "test"), new ResourceLocation("examplemod", "field_guide"));

    /** Recognizes paper as a book and refuses everything else. */
    private static final Function<ItemStack, Optional<BookKey>> RESOLVER =
            stack -> stack.is(Items.PAPER) ? Optional.of(KEY) : Optional.empty();

    private static List<ItemStack> grid(ItemStack... stacks) {
        List<ItemStack> slots = new ArrayList<>(List.of(stacks));
        while (slots.size() < 9) slots.add(ItemStack.EMPTY);
        return slots;
    }

    private static ItemStack tome() {
        return new ItemStack(Items.NETHER_STAR);
    }

    private static ItemStack book() {
        return new ItemStack(Items.PAPER);
    }

    private static Optional<TomeCraftingMatcher.Match> scan(List<ItemStack> slots) {
        return TomeCraftingMatcher.scan(slots, IS_TOME, RESOLVER);
    }

    @Test
    void emptyGridIsRejected() {
        assertTrue(scan(grid()).isEmpty());
    }

    @Test
    void gridWithoutATomeIsRejectedWithoutResolvingAnyBook() {
        // The performance contract, pinned: matches() runs against every loaded crafting recipe on
        // every grid change, so the no-tome case must not reach the adapter chain.
        AtomicInteger calls = new AtomicInteger();
        Function<ItemStack, Optional<BookKey>> counting = stack -> {
            calls.incrementAndGet();
            return Optional.of(KEY);
        };
        var result = TomeCraftingMatcher.scan(grid(book(), book(), book()), IS_TOME, counting);
        assertTrue(result.isEmpty());
        assertEquals(0, calls.get(), "no tome present, so no ingredient should have been resolved");
    }

    @Test
    void twoTomesAreRejected() {
        assertTrue(scan(grid(tome(), book(), tome())).isEmpty(),
                "ambiguous which tome is the library and which is an ingredient");
    }

    @Test
    void tomeAloneIsRejected() {
        assertTrue(scan(grid(tome())).isEmpty());
    }

    @Test
    void tomePlusOneBookMatches() {
        var match = scan(grid(tome(), book()));
        assertTrue(match.isPresent());
        assertEquals(0, match.get().tomeSlot());
        assertEquals(1, match.get().books().size());
        assertEquals(KEY, match.get().books().get(0).key());
    }

    @Test
    void matchesInATwoSlotGrid() {
        // The 2x2 player grid must work, not just the crafting table.
        var match = TomeCraftingMatcher.scan(List.of(tome(), book()), IS_TOME, RESOLVER);
        assertTrue(match.isPresent());
        assertEquals(1, match.get().books().size());
    }

    @Test
    void oneUnrecognizedItemVetoesTheWholeGrid() {
        var match = scan(grid(tome(), book(), new ItemStack(Items.STICK)));
        assertTrue(match.isEmpty(), "an unrecognized ingredient must fail the recipe, not be consumed");
    }

    @Test
    void slotIndicesSurviveEmptyGaps() {
        List<ItemStack> slots = grid();
        slots.set(0, tome());
        slots.set(3, book());
        slots.set(7, book());
        var match = scan(slots);
        assertTrue(match.isPresent());
        assertEquals(0, match.get().tomeSlot());
        assertEquals(List.of(3, 7), match.get().books().stream().map(TomeCraftingMatcher.Entry::slot).toList());
    }

    @Test
    void theTomeIsNeverTreatedAsABook() {
        // Simulates a datapack that tagged the tome into #runictome:guide_books: even a resolver
        // that claims everything must not see the tome slot offered to it.
        var match = TomeCraftingMatcher.scan(grid(tome(), book()), IS_TOME, stack -> Optional.of(KEY));
        assertTrue(match.isPresent());
        assertEquals(1, match.get().books().size());
        assertEquals(1, match.get().books().get(0).slot());
    }

    @Test
    void eightBooksAroundOneTomeAllMatch() {
        List<ItemStack> slots = grid();
        slots.set(4, tome());
        for (int i = 0; i < 9; i++) {
            if (i != 4) slots.set(i, book());
        }
        var match = scan(slots);
        assertTrue(match.isPresent());
        assertEquals(8, match.get().books().size());
    }

    @Test
    void scanDoesNotMutateTheGrid() {
        ItemStack stacked = new ItemStack(Items.PAPER, 5);
        List<ItemStack> slots = grid(tome(), stacked);
        assertTrue(scan(slots).isPresent());
        assertEquals(5, stacked.getCount(), "matching is a preview and must never consume");
        assertEquals(1, slots.get(0).getCount());
    }

    @Test
    void entriesReferenceTheLiveStack() {
        // The handler reads Entry.stack() to build the retained copy and any refund, so it must be
        // the grid's own stack rather than a snapshot.
        ItemStack live = book();
        var match = scan(grid(tome(), live));
        assertTrue(match.isPresent());
        assertTrue(live == match.get().books().get(0).stack());
    }

    @Test
    void nullAndEmptySlotListsAreRejected() {
        assertTrue(TomeCraftingMatcher.scan((List<ItemStack>) null, IS_TOME, RESOLVER).isEmpty());
        assertTrue(TomeCraftingMatcher.scan(List.of(), IS_TOME, RESOLVER).isEmpty());
    }

    @Test
    void theUnregisteredTomeIsNeverMistakenForAnItem() {
        // isRunicTome must not throw when the RegistryObject is unbound, which is exactly the
        // situation during early mod loading and in this test JVM.
        assertFalse(TomeCraftingMatcher.isRunicTome(new ItemStack(Items.PAPER)));
        assertFalse(TomeCraftingMatcher.isRunicTome(ItemStack.EMPTY));
    }
}

package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.capability.RunicTomeData;
import com.otectus.runictome.impl.AbsorptionPolicy;
import com.otectus.runictome.impl.BookExtraction;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for RT-09, RT-12, RT-13 and RT-14: recovering a library that absorbed something
 * it should not have, and the collection/stack contracts that recovery depends on.
 */
class LibraryRecoveryTest {

    private static final ResourceLocation HEURISTIC = new ResourceLocation("runictome", "heuristic");
    private static final ResourceLocation PATCHOULI = new ResourceLocation("runictome", "patchouli");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // Before as well as after: these tests mutate process-wide static exclusion sets, so a class
    // that ran earlier in the same JVM must not be able to change what they see.
    @BeforeEach
    @AfterEach
    void restoreDefaults() {
        AbsorptionPolicy.rebuild(RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_ITEMS,
                RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_MODS);
    }

    @Test
    void purgeRemovesEntriesThatBecameGloballyExcluded() {
        RunicTomeData data = new RunicTomeData();
        BookKey nowExcluded = new BookKey(HEURISTIC, new ResourceLocation("minecraft", "written_book"));
        BookKey stillFine = new BookKey(HEURISTIC, new ResourceLocation("minecraft", "book"));
        data.unlockBook(nowExcluded, new ItemStack(Items.WRITTEN_BOOK));
        data.unlockBook(stillFine, new ItemStack(Items.BOOK));
        data.toggleFavorite(nowExcluded);

        // The pack operator discovers written_book is functional and hard-excludes it.
        AbsorptionPolicy.rebuild(List.of("minecraft:written_book"), List.of());

        var found = AbsorptionPolicy.findExcluded(data);
        assertEquals(1, found.size());
        assertEquals(nowExcluded, found.get(0).key());
        assertEquals(AbsorptionPolicy.Reason.GLOBAL_ITEM, found.get(0).reason());
        assertEquals(1, AbsorptionPolicy.purgeExcluded(data));
        assertFalse(data.hasBook(nowExcluded));
        assertTrue(data.hasBook(stillFine));
        assertFalse(data.isFavorite(nowExcluded), "purging must drop the favorite with the entry");
        assertTrue(data.getBookStack(nowExcluded).isEmpty(), "purging must drop the retained stack");
    }

    @Test
    void purgeIsIdempotentAndReportsZeroWhenNothingMatches() {
        RunicTomeData data = new RunicTomeData();
        data.unlockBook(new BookKey(HEURISTIC, new ResourceLocation("minecraft", "book")),
                new ItemStack(Items.BOOK));
        AbsorptionPolicy.rebuild(List.of("minecraft:written_book"), List.of());

        assertEquals(0, AbsorptionPolicy.purgeExcluded(data));
        assertEquals(0, AbsorptionPolicy.purgeExcluded(data));
        assertEquals(1, data.getBooks().size());
    }

    @Test
    void purgeMatchesANonItemIdentityEntryThroughItsRetainedStack() {
        // A runictome:patchouli bookId is a *book* id, not an item id, so the only reliable signal
        // is the retained source stack. RT-14.
        RunicTomeData data = new RunicTomeData();
        BookKey patchouliEntry = new BookKey(PATCHOULI, new ResourceLocation("somemod", "some_book"));
        data.unlockBook(patchouliEntry, new ItemStack(Items.WRITTEN_BOOK));

        AbsorptionPolicy.rebuild(List.of("minecraft:written_book"), List.of());

        var found = AbsorptionPolicy.findExcluded(data);
        assertEquals(1, found.size());
        assertEquals(patchouliEntry, found.get(0).key());
        assertEquals(AbsorptionPolicy.Reason.GLOBAL_ITEM, found.get(0).reason());
    }

    @Test
    void purgeMatchesANamespaceExclusionThroughTheRetainedStack() {
        RunicTomeData data = new RunicTomeData();
        BookKey entry = new BookKey(PATCHOULI, new ResourceLocation("somemod", "some_book"));
        data.unlockBook(entry, new ItemStack(Items.BOOK));

        AbsorptionPolicy.rebuild(List.of(), List.of("minecraft"));

        var found = AbsorptionPolicy.findExcluded(data);
        assertEquals(1, found.size());
        assertEquals(entry, found.get(0).key());
        assertEquals(AbsorptionPolicy.Reason.GLOBAL_NAMESPACE, found.get(0).reason());
    }

    @Test
    void purgeRemovesExactlyTheSetThatWasShownToTheOperator() {
        // The command lists candidates, then removes that same list. Re-scanning at confirm time
        // would let the removed set drift from the set the operator agreed to.
        RunicTomeData data = new RunicTomeData();
        BookKey a = new BookKey(HEURISTIC, new ResourceLocation("minecraft", "written_book"));
        BookKey b = new BookKey(HEURISTIC, new ResourceLocation("minecraft", "book"));
        data.unlockBook(a, new ItemStack(Items.WRITTEN_BOOK));
        data.unlockBook(b, new ItemStack(Items.BOOK));

        AbsorptionPolicy.rebuild(List.of("minecraft:written_book"), List.of());
        var shown = AbsorptionPolicy.findExcluded(data);

        // The exclusion list widens between the dry run and the confirmation.
        AbsorptionPolicy.rebuild(List.of("minecraft:written_book", "minecraft:book"), List.of());

        assertEquals(1, AbsorptionPolicy.purge(data, shown));
        assertFalse(data.hasBook(a));
        assertTrue(data.hasBook(b), "an entry the operator was never shown must not be removed");
    }

    @Test
    void extractedAndVirtualMarkersAreNotPurgeReasons() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = new BookKey(HEURISTIC, new ResourceLocation("minecraft", "book"));
        ItemStack retained = new ItemStack(Items.BOOK);
        AbsorptionPolicy.markExtracted(retained);
        data.unlockBook(key, retained);

        AbsorptionPolicy.rebuild(List.of(), List.of());

        assertTrue(AbsorptionPolicy.findExcluded(data).isEmpty(),
                "a stray EXTRACTED/VIRTUAL marker on retained data must not delete the entry");
    }

    @Test
    void getBooksIsASnapshotSoCallersCanMutateWhileIterating() {
        RunicTomeData data = new RunicTomeData();
        for (int i = 0; i < 5; i++) {
            data.unlockBook(new BookKey(HEURISTIC, new ResourceLocation("mod", "book" + i)));
        }
        // The purge path, the public API and the tome UI all iterate this while the server may be
        // absorbing or removing books. A live unmodifiableCollection view would throw here. RT-13.
        for (BookKey key : data.getBooks()) {
            data.lockBook(key);
        }
        assertTrue(data.getBooks().isEmpty());
    }

    @Test
    void returnedCollectionsAreNotWritable() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = new BookKey(HEURISTIC, new ResourceLocation("mod", "book"));
        data.unlockBook(key);
        data.toggleFavorite(key);
        assertThrows(UnsupportedOperationException.class, () -> data.getBooks().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.getFavorites().clear());
    }

    @Test
    void clearResetsEveryField() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = new BookKey(HEURISTIC, new ResourceLocation("mod", "book"));
        data.unlockBook(key, new ItemStack(Items.BOOK));
        data.toggleFavorite(key);
        data.setReceivedTome(true);
        data.setStashedTomes(2);

        data.clear();

        assertTrue(data.getBooks().isEmpty());
        assertTrue(data.getFavorites().isEmpty());
        assertTrue(data.getBookStack(key).isEmpty());
        assertFalse(data.hasReceivedTome());
        assertEquals(0, data.getStashedTomes());
    }

    @Test
    void materializeYieldsNothingForAnUnregisteredBookId() {
        // RT-02/RT-12: getValue() would have returned minecraft:air here, so the old code built an
        // ItemStack(AIR). It happened to be empty, but the same lookup elsewhere was not so lucky.
        RunicTomeData data = new RunicTomeData();
        BookKey orphan = new BookKey(HEURISTIC, new ResourceLocation("removedmod", "removed_guide"));
        data.unlockBook(orphan);

        ItemStack materialized = BookExtraction.materialize(data, orphan);

        assertTrue(materialized.isEmpty());
        assertSame(ItemStack.EMPTY, materialized);
    }

    @Test
    void materializePrefersTheRetainedStackOverTheRegistryFallback() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = new BookKey(HEURISTIC, new ResourceLocation("minecraft", "book"));
        ItemStack source = new ItemStack(Items.WRITTEN_BOOK, 3);
        source.getOrCreateTag().putString("example:chapter", "two");
        data.unlockBook(key, source);

        ItemStack materialized = BookExtraction.materialize(data, key);

        assertEquals(Items.WRITTEN_BOOK, materialized.getItem(),
                "the retained stack wins over the item registered under the book id");
        assertEquals(1, materialized.getCount());
        assertEquals("two", materialized.getTag().getString("example:chapter"));
    }
}

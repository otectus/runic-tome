package com.otectus.runictome.impl;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.capability.RunicTomeData;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Copying differs from extraction in exactly one way that matters: the library entry survives. Every
 * test here pins some consequence of that.
 *
 * <p>The cost helpers are exercised against a bare {@link Inventory}, which is constructible without
 * a player — its constructor only stores the reference, and neither {@code items} nor
 * {@code removeItem} touches it.
 */
class BookCopyTest {

    private static final BookKey KEY = new BookKey(
            new ResourceLocation("runictome", "test"),
            new ResourceLocation("minecraft", "written_book"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RunicTomeData libraryWithOneBook() {
        RunicTomeData data = new RunicTomeData();
        ItemStack source = new ItemStack(Items.WRITTEN_BOOK, 3);
        source.getOrCreateTag().putString("title", "Field Notes");
        data.unlockBook(KEY, source);
        return data;
    }

    @Test
    void copyingLeavesTheLibraryEntryAndItsRetainedStackUntouched() {
        // The invariant the whole feature rests on. RunicTomeData.getBookStack answers with a
        // defensive copy, so materialize can never hand a live reference into player inventory --
        // if that ever regressed, marking the copy would silently mark the stored book too.
        RunicTomeData data = libraryWithOneBook();

        ItemStack copy = BookCopy.prepareCopy(data, KEY);

        assertTrue(data.hasBook(KEY), "copying must not remove the entry the way extraction does");
        ItemStack retained = data.getBookStack(KEY);
        assertFalse(AbsorptionPolicy.isExtracted(retained),
                "marking the copy must not mark the stack still held by the tome");
        assertEquals("Field Notes", retained.getTag().getString("title"));
        assertEquals(1, retained.getCount(), "the retained stack is always normalized to one");

        assertEquals(1, copy.getCount());
        assertEquals("Field Notes", copy.getTag().getString("title"),
                "a copy carries the original's data, not just its item type");
        assertTrue(AbsorptionPolicy.isExtracted(copy),
                "an unmarked copy would be eaten by the next inventory sweep as a known duplicate");
    }

    @Test
    void copyingIsRepeatable() {
        RunicTomeData data = libraryWithOneBook();

        ItemStack first = BookCopy.prepareCopy(data, KEY);
        ItemStack second = BookCopy.prepareCopy(data, KEY);

        assertTrue(ItemStack.isSameItemSameTags(first, second),
                "furnishing a shelf means clicking copy repeatedly; every click must give the same book");
        assertTrue(data.hasBook(KEY));
    }

    @Test
    void anEntryWithNoMaterializableItemYieldsNothing() {
        RunicTomeData data = new RunicTomeData();
        // A Patchouli-style key: its bookId is a book id, not an item id, and nothing is retained.
        BookKey unresolvable = new BookKey(new ResourceLocation("runictome", "patchouli"),
                new ResourceLocation("examplemod", "no_such_thing"));
        data.unlockBook(unresolvable);

        assertTrue(BookCopy.prepareCopy(data, unresolvable).isEmpty(),
                "an empty result is what makes the caller report no_item rather than charging books");
    }

    @Test
    void plainBooksAreCountedAcrossSlotsAndNamedOnesAreNot() {
        Inventory inv = new Inventory(null);
        inv.items.set(0, new ItemStack(Items.BOOK, 5));
        inv.items.set(4, new ItemStack(Items.BOOK, 2));
        ItemStack named = new ItemStack(Items.BOOK, 9);
        named.getOrCreateTag().putString("keepsake", "yes");
        inv.items.set(7, named);

        assertEquals(7, BookCopy.countPlainBooks(inv),
                "a player's NBT-bearing book must not be spendable as a copy cost");
    }

    @Test
    void chargingSpansSlotsAndReturnsExactlyWhatItTook() {
        Inventory inv = new Inventory(null);
        inv.items.set(0, new ItemStack(Items.BOOK, 2));
        inv.items.set(3, new ItemStack(Items.BOOK, 6));

        List<ItemStack> taken = BookCopy.takePlainBooks(inv, 5);

        int totalTaken = taken.stream().mapToInt(ItemStack::getCount).sum();
        assertEquals(5, totalTaken, "the refund path hands these straight back, so it must be exact");
        assertEquals(3, BookCopy.countPlainBooks(inv));
    }

    @Test
    void chargingNothingTakesNothing() {
        Inventory inv = new Inventory(null);
        inv.items.set(0, new ItemStack(Items.BOOK, 4));

        assertTrue(BookCopy.takePlainBooks(inv, 0).isEmpty(),
                "bookCopyCost = 0 and creative mode both route through here");
        assertEquals(4, BookCopy.countPlainBooks(inv));
    }
}

package com.otectus.runictome;

import com.otectus.runictome.impl.AbsorptionPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the extraction exemption and the operator escape hatch that clears it (audit §13.9).
 *
 * <p>The marker is deliberately permanent in normal play — that is what stops an extracted book from
 * being re-absorbed the moment it touches an inventory. But "permanent" previously meant "not
 * recoverable at all": a book extracted by mistake could never re-enter the library without editing
 * the save. {@code /runictome unmark} is the only thing that clears it, and these tests pin both
 * that it works and that it stays narrow.
 */
class ExtractionMarkerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    @AfterEach
    void restoreDefaults() {
        AbsorptionPolicy.rebuild(RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_ITEMS,
                RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_MODS);
    }

    @Test
    void clearingTheMarkerMakesTheBookAbsorbableAgain() {
        ItemStack extracted = new ItemStack(Items.WRITTEN_BOOK);
        AbsorptionPolicy.markExtracted(extracted);
        assertTrue(AbsorptionPolicy.isExcluded(extracted));

        assertTrue(AbsorptionPolicy.clearExtracted(extracted));

        assertFalse(AbsorptionPolicy.isExtracted(extracted));
        assertFalse(AbsorptionPolicy.isExcluded(extracted),
                "with the marker gone the normal absorption rules apply again");
    }

    @Test
    void clearingReportsWhetherThereWasAnythingToClear() {
        // The command distinguishes "cleared it" from "this item was never marked", so the operator
        // is not told they fixed something they did not.
        ItemStack plain = new ItemStack(Items.WRITTEN_BOOK);
        assertFalse(AbsorptionPolicy.clearExtracted(plain));

        AbsorptionPolicy.markExtracted(plain);
        assertTrue(AbsorptionPolicy.clearExtracted(plain));
        assertFalse(AbsorptionPolicy.clearExtracted(plain), "clearing twice is a no-op");
    }

    @Test
    void clearingLeavesNoEmptyTagBehind() {
        // An empty compound is not equivalent to no compound: it defeats vanilla stack merging and
        // trips the heuristic's "does this carry meaningful NBT" check. A cleared book must be
        // indistinguishable from one that was never extracted.
        ItemStack extracted = new ItemStack(Items.WRITTEN_BOOK);
        AbsorptionPolicy.markExtracted(extracted);
        AbsorptionPolicy.clearExtracted(extracted);

        assertNull(extracted.getTag(), "the stack must be left with no NBT at all");
        assertTrue(ItemStack.isSameItemSameTags(extracted, new ItemStack(Items.WRITTEN_BOOK)),
                "a cleared book must stack with a fresh one");
    }

    @Test
    void clearingPreservesEveryOtherTag() {
        // Books carry their own data — page contents, author, mod-specific state. Clearing the
        // exemption must remove exactly one key and nothing else.
        ItemStack extracted = new ItemStack(Items.WRITTEN_BOOK);
        extracted.getOrCreateTag().putString("author", "Otectus");
        extracted.getOrCreateTag().putInt("generation", 2);
        AbsorptionPolicy.markExtracted(extracted);

        assertTrue(AbsorptionPolicy.clearExtracted(extracted));

        assertFalse(AbsorptionPolicy.isExtracted(extracted));
        assertTrue(extracted.hasTag());
        assertTrue("Otectus".equals(extracted.getTag().getString("author")),
                "unrelated NBT must survive");
        assertTrue(extracted.getTag().getInt("generation") == 2, "unrelated NBT must survive");
    }

    @Test
    void clearingIsSafeOnAnEmptyStack() {
        // Guards the RT-03 class of bug: never mutate a stack derived from the shared EMPTY
        // singleton. Writing to it would corrupt ItemStack.EMPTY process-wide.
        assertFalse(AbsorptionPolicy.clearExtracted(ItemStack.EMPTY));
        assertNull(ItemStack.EMPTY.getTag(),
                "ItemStack.EMPTY must still carry no NBT after the call");
    }

    @Test
    void clearingIsSafeOnNull() {
        assertFalse(AbsorptionPolicy.clearExtracted(null));
    }

    @Test
    void theMarkerStillSurvivesCopyingAndStillBlocksAbsorption() {
        // Pins the behaviour the exemption exists for: nothing short of the explicit command clears
        // it, so moving the book through chests, trades or stacking keeps it exempt.
        ItemStack extracted = new ItemStack(Items.WRITTEN_BOOK);
        AbsorptionPolicy.markExtracted(extracted);

        assertTrue(AbsorptionPolicy.isExtracted(extracted.copy()));
        ItemStack roundTripped = ItemStack.of(extracted.save(new net.minecraft.nbt.CompoundTag()));
        assertTrue(AbsorptionPolicy.isExtracted(roundTripped),
                "the exemption must survive a save/load round trip");
    }
}

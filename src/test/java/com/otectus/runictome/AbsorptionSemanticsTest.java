package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.UnlockResult;
import com.otectus.runictome.capability.RunicTomeData;
import com.otectus.runictome.impl.AbsorptionPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the absorption contract that the item-consumption decision depends on (RT-01, and the
 * fail-closed guarantee behind {@link UnlockResult}).
 *
 * <p><b>Documented, intentional behaviour:</b> absorption is deduplicating. Only one copy of a book
 * is ever retained. By default the whole source stack is consumed — absorbing a stack of N identical
 * guide books yields exactly one extractable copy and destroys the other N−1. These tests exist so
 * that policy cannot change by accident; changing it is a deliberate product decision, not a bug fix.
 *
 * <p>Since 0.6.0 the <em>consumption</em> half of that contract is configurable via
 * {@code absorbWholeStack}, which defaults to the behaviour pinned here. What each mode consumes is
 * covered by {@code AbsorptionConsumptionTest}; the storage contract below is unaffected by it —
 * one retained copy either way.
 */
class AbsorptionSemanticsTest {

    private static final ResourceLocation HEURISTIC = new ResourceLocation("runictome", "heuristic");

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
    void onlyOneCopyIsEverRetainedRegardlessOfSourceStackSize() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = new BookKey(HEURISTIC, new ResourceLocation("mod", "field_guide"));

        assertTrue(data.unlockBook(key, new ItemStack(Items.WRITTEN_BOOK, 16)));

        assertEquals(1, data.getBookStack(key).getCount(),
                "the retained copy is normalized to one item; the rest of the stack is consumed");
        assertEquals(16, new ItemStack(Items.WRITTEN_BOOK, 16).getCount(),
                "sanity: the source stack itself is untouched by unlockBook");
    }

    @Test
    void absorbingTheSameBookTwiceReportsAlreadyHadAndStillConsumes() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = new BookKey(HEURISTIC, new ResourceLocation("mod", "field_guide"));

        assertTrue(data.unlockBook(key, new ItemStack(Items.WRITTEN_BOOK)));
        assertFalse(data.unlockBook(key, new ItemStack(Items.WRITTEN_BOOK)),
                "a duplicate is not newly unlocked");

        // isStored() is the safety gate: it authorizes consumption but no longer decides how much.
        // Under the default absorbWholeStack the duplicate is destroyed, which is the deduplicating
        // premise of the mod; AbsorptionConsumptionTest pins both modes.
        assertTrue(UnlockResult.ALREADY_HAD.isStored());
        assertTrue(UnlockResult.ADDED.isStored());
        assertEquals(1, AbsorptionPolicy.consumptionFor(UnlockResult.ALREADY_HAD, 1, true));
    }

    @Test
    void failedNeverAuthorizesConsumingThePhysicalItem() {
        // The single most important invariant: no storage, no consumption.
        assertFalse(UnlockResult.FAILED.isStored());
    }

    @Test
    void reacquiringDoesNotOverwriteAnAlreadyRetainedStack() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = new BookKey(HEURISTIC, new ResourceLocation("mod", "field_guide"));
        ItemStack first = new ItemStack(Items.WRITTEN_BOOK);
        first.getOrCreateTag().putString("variant", "first");
        ItemStack second = new ItemStack(Items.WRITTEN_BOOK);
        second.getOrCreateTag().putString("variant", "second");

        data.unlockBook(key, first);
        data.unlockBook(key, second);

        assertEquals("first", data.getBookStack(key).getTag().getString("variant"),
                "the first absorbed variant is kept; a later variant does not replace it");
    }

    @Test
    void anExtractedCopyIsExemptFromReabsorptionUntilExplicitlyUnmarked() {
        // The exemption marker is persistent NBT on the item, so an extracted book is never absorbed
        // again in the course of normal play. Since 0.6.0 an operator can clear it deliberately with
        // /runictome unmark — the only mechanism that does, covered by ExtractionMarkerTest.
        ItemStack extracted = new ItemStack(Items.WRITTEN_BOOK);
        AbsorptionPolicy.markExtracted(extracted);

        assertTrue(AbsorptionPolicy.isExtracted(extracted));
        assertEquals(AbsorptionPolicy.Reason.EXTRACTED,
                AbsorptionPolicy.evaluate(extracted).reason());

        // Surviving a copy is what makes it permanent across chests, trades and stacking.
        assertTrue(AbsorptionPolicy.isExtracted(extracted.copy()));
    }

    @Test
    void aVirtualSimulationStackIsNeverAbsorbed() {
        ItemStack virtual = new ItemStack(Items.WRITTEN_BOOK);
        virtual.getOrCreateTag().putBoolean(com.otectus.runictome.impl.UseSimulator.VIRTUAL_TAG, true);

        AbsorptionPolicy.Decision decision = AbsorptionPolicy.evaluate(virtual);

        assertTrue(decision.excluded());
        assertEquals(AbsorptionPolicy.Reason.VIRTUAL, decision.reason());
    }
}

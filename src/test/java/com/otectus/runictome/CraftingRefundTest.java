package com.otectus.runictome;

import com.otectus.runictome.api.UnlockResult;
import com.otectus.runictome.impl.AbsorptionPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the decision {@code CraftingAbsorptionHandler} makes after each unlock.
 *
 * <p>{@code ResultSlot.onTake} removes exactly one item from every occupied grid slot once
 * {@code ItemCraftedEvent} returns, unconditionally. The handler therefore asks the shared contract
 * "should one item be consumed?" — {@code consumptionFor(result, 1)} — and refunds when the answer
 * is no. Sibling to {@link AbsorptionConsumptionTest}, so the crafting path provably cannot drift
 * from pickup, smelt and the inventory sweep.
 */
class CraftingRefundTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Mirrors the handler: vanilla is about to take one item — may it? */
    private static boolean refunds(UnlockResult result, boolean wholeStack) {
        return AbsorptionPolicy.consumptionFor(result, 1, wholeStack) <= 0;
    }

    @Test
    void aStoredBookIsConsumedInEitherStackMode() {
        assertFalse(refunds(UnlockResult.ADDED, true));
        assertFalse(refunds(UnlockResult.ADDED, false));
    }

    @Test
    void aFailedStoreAlwaysRefunds() {
        // The invariant every absorption site holds: no storage, no consumption. Vanilla will still
        // shrink the slot, so without the refund this craft would destroy the book outright.
        assertTrue(refunds(UnlockResult.FAILED, true));
        assertTrue(refunds(UnlockResult.FAILED, false));
    }

    @Test
    void aNullResultRefunds() {
        // A defensive caller must never be read as "safe to destroy".
        assertTrue(refunds(null, true));
        assertTrue(refunds(null, false));
    }

    @Test
    void aDuplicateFollowsTheAbsorbWholeStackContract() {
        // absorbWholeStack=true is the deduplicating default: the tome keeps one copy and the
        // duplicate is destroyed, exactly as on pickup.
        assertFalse(refunds(UnlockResult.ALREADY_HAD, true));
        // absorbWholeStack=false promises extras stay physical, so the craft hands it back.
        assertTrue(refunds(UnlockResult.ALREADY_HAD, false));
    }

    @Test
    void aRefundIsAlwaysExactlyOneItem() {
        // The handler refunds a single item because vanilla takes a single item. Larger slots are
        // untouched by this path: a slot of five loses one per craft, as any recipe would.
        for (int available : new int[] {1, 2, 5, 64}) {
            assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.FAILED, available, true));
            assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.FAILED, available, false));
        }
    }
}

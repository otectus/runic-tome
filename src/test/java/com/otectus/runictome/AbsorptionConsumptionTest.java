package com.otectus.runictome;

import com.otectus.runictome.api.UnlockResult;
import com.otectus.runictome.impl.AbsorptionPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@link AbsorptionPolicy#consumptionFor}, the single decision shared by every absorption site
 * (pickup, craft, smelt, inventory sweep).
 *
 * <p>Before 0.6.0 each site made this decision itself as "consume everything if
 * {@code result.isStored()}", which is why absorbing a stack of eight manuals destroyed seven of
 * them (RT-01). The behaviour is unchanged by default; these tests exist so the two modes cannot
 * drift apart or be changed by accident.
 */
class AbsorptionConsumptionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------------------------------------------------------------- invariants in both modes

    @Test
    void failedNeverConsumesAnythingInEitherMode() {
        // The single most important invariant: no storage, no consumption. A book that could not be
        // stored (capability not attached yet) must leave its physical item completely alone.
        assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.FAILED, 64, true));
        assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.FAILED, 64, false));
        assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.FAILED, 1, true));
        assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.FAILED, 1, false));
    }

    @Test
    void neverConsumesMoreThanTheStackHolds() {
        for (boolean wholeStack : new boolean[]{true, false}) {
            for (int available : new int[]{0, 1, 2, 64}) {
                int consumed = AbsorptionPolicy.consumptionFor(UnlockResult.ADDED, available, wholeStack);
                assertEquals(Math.min(consumed, available), consumed,
                        "consumption must never exceed the stack, wholeStack=" + wholeStack
                                + " available=" + available);
            }
        }
    }

    @Test
    void anEmptyOrNegativeStackConsumesNothing() {
        assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.ADDED, 0, true));
        assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.ADDED, -1, true));
        assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.ADDED, 0, false));
    }

    @Test
    void aNullResultConsumesNothing() {
        // Defensive: a third-party caller passing null must not be read as "safe to destroy".
        assertEquals(0, AbsorptionPolicy.consumptionFor(null, 64, true));
        assertEquals(0, AbsorptionPolicy.consumptionFor(null, 64, false));
    }

    // ---------------------------------------------------------------- wholeStack = true (default)

    @Test
    void wholeStackModeConsumesTheEntireStackOnFirstAbsorption() {
        assertEquals(8, AbsorptionPolicy.consumptionFor(UnlockResult.ADDED, 8, true),
                "the documented pre-0.6.0 behaviour: one copy is retained and the rest destroyed");
    }

    @Test
    void wholeStackModeAlsoConsumesADuplicateOutright() {
        assertEquals(8, AbsorptionPolicy.consumptionFor(UnlockResult.ALREADY_HAD, 8, true),
                "picking up a book already owned destroys it — the deduplicating premise");
    }

    // ---------------------------------------------------------------- wholeStack = false

    @Test
    void perItemModeConsumesExactlyOneForANewBook() {
        assertEquals(1, AbsorptionPolicy.consumptionFor(UnlockResult.ADDED, 8, false),
                "absorb one copy, leave the other seven physical");
        assertEquals(1, AbsorptionPolicy.consumptionFor(UnlockResult.ADDED, 1, false));
    }

    @Test
    void perItemModeNeverConsumesADuplicate() {
        // This is the load-bearing case. If ALREADY_HAD consumed here, the periodic inventory sweep
        // would re-identify the remaining stack every interval and eat it one item at a time —
        // silently reproducing whole-stack absorption over a few seconds, which is precisely the
        // behaviour this mode exists to switch off.
        assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.ALREADY_HAD, 7, false));
        assertEquals(0, AbsorptionPolicy.consumptionFor(UnlockResult.ALREADY_HAD, 1, false));
    }

    @Test
    void perItemModeIsStableUnderRepeatedSweeps() {
        // Simulates the sweep running repeatedly over the leftovers of a stack of 8: the first pass
        // takes one copy, and every later pass reports ALREADY_HAD and must take nothing.
        int remaining = 8;
        remaining -= AbsorptionPolicy.consumptionFor(UnlockResult.ADDED, remaining, false);
        assertEquals(7, remaining);

        for (int sweep = 0; sweep < 100; sweep++) {
            remaining -= AbsorptionPolicy.consumptionFor(UnlockResult.ALREADY_HAD, remaining, false);
        }
        assertEquals(7, remaining, "the leftovers must survive an unbounded number of sweeps");
    }

    @Test
    void configuredOverloadDefaultsToWholeStackWhenTheSpecIsNotLoaded() {
        // Unit tests and early-startup callers run without a loaded config; the documented default
        // (and the behaviour of every release before 0.6.0) must be what they see.
        assertEquals(8, AbsorptionPolicy.consumptionFor(UnlockResult.ADDED, 8));
    }
}

package com.otectus.runictome;

import com.otectus.runictome.impl.RequestLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the per-player request limiter behind the favorite toggle (RT-10).
 *
 * <p>The limiter holds process-wide static state, so like the other such classes in this suite it
 * resets both before <em>and</em> after each test: Gradle runs every test class in one JVM.
 */
class RequestLimitsTest {

    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player-a".getBytes());
    private static final UUID OTHER = UUID.nameUUIDFromBytes("player-b".getBytes());

    @BeforeEach
    @AfterEach
    void resetBuckets() {
        RequestLimits.reset();
    }

    @Test
    void aFreshPlayerStartsWithAFullAllowance() {
        // Logging in and immediately clicking must not be throttled.
        for (int i = 0; i < 20; i++) {
            assertTrue(RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20),
                    "request #" + i + " within the first second's allowance was rejected");
        }
    }

    @Test
    void requestsBeyondTheAllowanceAreRejected() {
        for (int i = 0; i < 20; i++) {
            RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20);
        }
        assertFalse(RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20),
                "the 21st request in the same tick exceeds an allowance of 20/second");
    }

    @Test
    void theAllowanceRefillsOverTime() {
        for (int i = 0; i < 20; i++) {
            RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20);
        }
        assertFalse(RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20));

        // One second later (20 ticks) the bucket is full again.
        for (int i = 0; i < 20; i++) {
            assertTrue(RequestLimits.allowFavoriteToggle(PLAYER, 20L, 20),
                    "request #" + i + " after a full second of refill was rejected");
        }
    }

    @Test
    void refillIsProportionalToElapsedTicks() {
        for (int i = 0; i < 20; i++) {
            RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20);
        }
        // A quarter second at 20/second is worth exactly five requests, and not a sixth.
        int granted = 0;
        for (int i = 0; i < 10; i++) {
            if (RequestLimits.allowFavoriteToggle(PLAYER, 5L, 20)) granted++;
        }
        assertEquals(5, granted, "5 ticks at 20/second must refill exactly 5 requests");
    }

    @Test
    void sustainedTrafficSettlesAtTheConfiguredRate() {
        // A client toggling every single tick for 10 seconds may be served at most the allowance
        // times ten, plus the one full bucket it started with.
        int granted = 0;
        for (long tick = 0; tick < 200; tick++) {
            if (RequestLimits.allowFavoriteToggle(PLAYER, tick, 20)) granted++;
        }
        assertTrue(granted <= 20 * 10 + 20, "sustained rate exceeded the allowance: " + granted);
        assertTrue(granted >= 20 * 10, "the limiter must not throttle below the allowance: " + granted);
    }

    @Test
    void bucketsArePerPlayer() {
        for (int i = 0; i < 20; i++) {
            RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20);
        }
        assertFalse(RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20));
        assertTrue(RequestLimits.allowFavoriteToggle(OTHER, 0L, 20),
                "one player exhausting their allowance must not throttle anyone else");
    }

    @Test
    void anAllowanceOfZeroDisablesTheLimit() {
        // Documented escape hatch for operators who would rather not have the limit at all.
        for (int i = 0; i < 1000; i++) {
            assertTrue(RequestLimits.allowFavoriteToggle(PLAYER, 0L, 0));
        }
    }

    @Test
    void aTickCounterGoingBackwardsDoesNotDrainTheBucket() {
        // Rejoining a single-player world restarts the server tick counter. A naive elapsed-time
        // calculation would go negative and subtract tokens, throttling the player for no reason.
        assertTrue(RequestLimits.allowFavoriteToggle(PLAYER, 10_000L, 20));
        for (int i = 0; i < 19; i++) {
            assertTrue(RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20),
                    "request #" + i + " after the tick counter reset was rejected");
        }
    }

    @Test
    void forgettingAPlayerReleasesTheirBucket() {
        // The logout hook calls this; without it the map grows with every unique player.
        for (int i = 0; i < 20; i++) {
            RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20);
        }
        assertFalse(RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20));

        RequestLimits.forget(PLAYER);
        assertTrue(RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20),
                "a rejoining player starts fresh");
    }

    @Test
    void raisingTheAllowanceOnReloadTakesEffectImmediately() {
        for (int i = 0; i < 20; i++) {
            RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20);
        }
        assertFalse(RequestLimits.allowFavoriteToggle(PLAYER, 0L, 20));

        // The config is live-reloadable, so an existing bucket must adopt the new capacity rather
        // than staying clamped to the old one until the player relogs.
        assertTrue(RequestLimits.allowFavoriteToggle(PLAYER, 1L, 200),
                "a raised allowance must apply to a bucket that already exists");
    }
}

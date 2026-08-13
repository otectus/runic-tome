package com.otectus.runictome.event;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the inventory-sweep schedule (RT-22).
 *
 * <p>The sweep used to run every online player on the same tick, so a full server paid the entire
 * cost in one tick of every interval and nothing on the rest. Staggering must not change <em>how
 * often</em> a player is swept — only which tick it lands on. Both halves of that are pinned here,
 * because a stagger that quietly skipped players would look like a performance win.
 */
class SweepScheduleTest {

    @Test
    void everyPlayerIsSweptExactlyOncePerInterval() {
        int interval = 20;
        for (int entityId = 0; entityId < 200; entityId++) {
            int due = 0;
            for (int tick = 0; tick < interval; tick++) {
                if (ServerTickHandler.isDueThisTick(tick, entityId, interval)) due++;
            }
            assertEquals(1, due,
                    "entity " + entityId + " must be swept exactly once per interval, was " + due);
        }
    }

    @Test
    void theLoadIsSpreadEvenlyAcrossTheInterval() {
        int interval = 20;
        int players = 200;
        Map<Integer, Integer> perTick = new HashMap<>();
        for (int tick = 0; tick < interval; tick++) {
            int due = 0;
            for (int entityId = 0; entityId < players; entityId++) {
                if (ServerTickHandler.isDueThisTick(tick, entityId, interval)) due++;
            }
            perTick.put(tick, due);
        }
        // With 200 sequentially-numbered players over a 20-tick interval, each tick sweeps 10.
        // The old behaviour was 200 on one tick and 0 on the other 19.
        for (Map.Entry<Integer, Integer> e : perTick.entrySet()) {
            assertEquals(players / interval, e.getValue(),
                    "tick " + e.getKey() + " carried an uneven share of the sweep");
        }
    }

    @Test
    void anIntervalOfOneSweepsEveryPlayerEveryTick() {
        // The documented "restore legacy per-tick behavior" setting must stay exactly that.
        for (int tick = 0; tick < 50; tick++) {
            for (int entityId = 0; entityId < 20; entityId++) {
                assertTrue(ServerTickHandler.isDueThisTick(tick, entityId, 1),
                        "interval=1 must sweep every player every tick");
            }
        }
    }

    @Test
    void aNonPositiveIntervalNeverSkipsAPlayer() {
        // Defensive: the caller clamps this, but treating a bad interval as "never sweep" would
        // silently disable absorption rather than fail loudly.
        assertTrue(ServerTickHandler.isDueThisTick(5, 3, 0));
        assertTrue(ServerTickHandler.isDueThisTick(5, 3, -20));
    }

    @Test
    void aNegativeEntityIdStillLandsOnExactlyOneTick() {
        // Entity ids are non-negative in practice, but '%' on a negative value yields a negative
        // remainder that never equals zero — which would silently stop sweeping that player.
        int interval = 20;
        for (int entityId = -50; entityId < 0; entityId++) {
            int due = 0;
            for (int tick = 0; tick < interval; tick++) {
                if (ServerTickHandler.isDueThisTick(tick, entityId, interval)) due++;
            }
            assertEquals(1, due, "entity " + entityId + " must still be swept once per interval");
        }
    }

    @Test
    void theScheduleIsStableAsTheTickCounterGrows() {
        // A player's slot must not drift over a long-running server, or two players could converge
        // onto the same tick and undo the staggering.
        int interval = 20;
        int entityId = 7;
        int firstDue = -1;
        for (int tick = 0; tick < interval; tick++) {
            if (ServerTickHandler.isDueThisTick(tick, entityId, interval)) firstDue = tick;
        }
        assertTrue(firstDue >= 0);

        // One million ticks later the player is still due on the same offset within the interval.
        int laterBase = 1_000_000 - (1_000_000 % interval);
        assertTrue(ServerTickHandler.isDueThisTick(laterBase + firstDue, entityId, interval));
    }
}

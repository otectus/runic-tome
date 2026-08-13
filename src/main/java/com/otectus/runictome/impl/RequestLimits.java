package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTome;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player rate limiting for client-to-server requests.
 *
 * <p>Nothing stops a client from sending a packet every tick, so any handler doing per-request work
 * is a lever a modified client can pull. The favorite toggle is the one that matters (RT-10): it is
 * cheap to send and used to be answered with a full library re-serialization. That amplification is
 * gone, but a bounded request rate is still the difference between "a spamming client costs the
 * server what it costs itself" and "…costs it more".
 *
 * <p>The bucket is a token bucket in exact integer arithmetic. Tokens are counted in twentieths of a
 * token (one server tick's worth at 20 tps) so that a fractional per-tick refill rate never needs a
 * {@code double}, which would accumulate rounding error over a long-running server.
 */
@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class RequestLimits {

    /** Server ticks per second — the unit tokens are scaled by. */
    static final int TICKS_PER_SECOND = 20;

    private static final Map<UUID, Bucket> FAVORITE_BUCKETS = new ConcurrentHashMap<>();

    private RequestLimits() {}

    /**
     * Consumes one favorite-toggle token for {@code player}.
     *
     * @param nowTicks the current server tick count.
     * @param perSecond the configured allowance; {@code <= 0} disables the limit.
     * @return true if the request is within the allowance and may proceed.
     */
    public static boolean allowFavoriteToggle(ServerPlayer player, long nowTicks, int perSecond) {
        return allowFavoriteToggle(player.getUUID(), nowTicks, perSecond);
    }

    /** Keyed by id rather than player, so the limiter can be exercised without a running server. */
    public static boolean allowFavoriteToggle(UUID playerId, long nowTicks, int perSecond) {
        if (perSecond <= 0) return true;
        return FAVORITE_BUCKETS
                .computeIfAbsent(playerId, id -> new Bucket())
                .tryAcquire(nowTicks, perSecond);
    }

    /** Full-bucket size for an allowance, in scaled tokens. */
    private static long capacity(int perSecond) {
        return (long) perSecond * TICKS_PER_SECOND;
    }

    /** Drops a player's buckets. Without this the map would grow with every unique player seen. */
    public static void forget(UUID playerId) {
        FAVORITE_BUCKETS.remove(playerId);
    }

    /** Test hook: drops every bucket so one test cannot see another's accumulated state. */
    public static void reset() {
        FAVORITE_BUCKETS.clear();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    private static final class Bucket {
        /** Tokens in twentieths; one request costs {@link #TICKS_PER_SECOND}. */
        private long tokens;
        private long lastTick;
        private boolean started;

        synchronized boolean tryAcquire(long nowTicks, int perSecond) {
            long capacity = capacity(perSecond);
            if (!started) {
                // First request: start from a full bucket sized to the current allowance, so a
                // player is never throttled for having just logged in.
                tokens = capacity;
                started = true;
            } else {
                // Clamp elapsed at zero: the tick counter restarts when a single-player world is
                // reloaded, and a negative elapsed would otherwise drain the bucket.
                long elapsed = Math.max(0L, nowTicks - lastTick);
                tokens = Math.min(capacity, tokens + elapsed * perSecond);
            }
            lastTick = nowTicks;
            if (tokens < TICKS_PER_SECOND) return false;
            tokens -= TICKS_PER_SECOND;
            return true;
        }
    }
}

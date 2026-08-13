package com.otectus.runictome.network;

import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.impl.RequestLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server request to flip a book's favorite flag. The server is authoritative: it toggles
 * only if the player has the book unlocked, then answers with that book's state.
 *
 * <p>The answer is a {@link SyncFavoritePacket}, not a full capability sync (RT-10). Re-serializing
 * the entire library — every key plus every retained {@code ItemStack} and its NBT — for a one-bit
 * change made this packet an amplification lever: cheap to send, expensive to answer, and unbounded
 * in how often it could be sent. Every path below now costs one small, fixed-size response.
 */
public record ToggleFavoritePacket(BookKey key) {

    public static void encode(ToggleFavoritePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.key.systemId());
        buf.writeResourceLocation(msg.key.bookId());
    }

    public static ToggleFavoritePacket decode(FriendlyByteBuf buf) {
        ResourceLocation system = buf.readResourceLocation();
        ResourceLocation book = buf.readResourceLocation();
        return new ToggleFavoritePacket(new BookKey(system, book));
    }

    public static void handle(ToggleFavoritePacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Registered via consumerMainThread: already on the server thread.
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        player.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent(data -> {
            if (!allowed(player)) {
                // Over the allowance: do not toggle, but still answer with the authoritative state.
                // The client flipped optimistically, so staying silent would leave its UI wrong
                // until the next full sync. Correcting costs the same tiny packet as accepting.
                respond(player, msg.key, data);
                return;
            }
            data.toggleFavorite(msg.key);
            respond(player, msg.key, data);
        });
    }

    private static boolean allowed(ServerPlayer player) {
        // getTickCount() is monotonic per server run; RequestLimits tolerates it restarting.
        long now = player.server == null ? 0L : player.server.getTickCount();
        return RequestLimits.allowFavoriteToggle(
                player, now, RunicTomeConfig.COMMON.maxFavoriteTogglesPerSecond.get());
    }

    /**
     * Reports the authoritative flag. Read back from the capability rather than trusting
     * {@code toggleFavorite}'s return value, so a book the player does not own — which cannot be
     * favorited — is reported as not favorited instead of echoing the client's assumption.
     */
    private static void respond(ServerPlayer player, BookKey key, IRunicTomeData data) {
        RunicTomeNetwork.sendTo(player, new SyncFavoritePacket(key, data.isFavorite(key)));
    }
}

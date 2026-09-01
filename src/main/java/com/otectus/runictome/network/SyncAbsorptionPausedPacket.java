package com.otectus.runictome.network;

import com.otectus.runictome.client.ClientDataCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client authoritative state of the absorption pause flag.
 *
 * <p>Narrow by design, for the reason {@link SyncFavoritePacket} documents: answering a one-bit
 * change with a full {@code RunicTomeData.serializeNBT()} — every book key and every retained
 * {@code ItemStack} — turns a cheap request into an expensive response.
 *
 * <p>Sent for rejected requests too, because the client flips optimistically for instant feedback.
 */
public record SyncAbsorptionPausedPacket(boolean paused) {

    public static void encode(SyncAbsorptionPausedPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.paused);
    }

    public static SyncAbsorptionPausedPacket decode(FriendlyByteBuf buf) {
        return new SyncAbsorptionPausedPacket(buf.readBoolean());
    }

    public static void handle(SyncAbsorptionPausedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Registered via consumerMainThread, so this already runs on the client thread and is
        // marked handled by Forge — same contract as the other PLAY_TO_CLIENT packets.
        ClientDataCache.acceptAbsorptionPaused(msg.paused);
    }
}

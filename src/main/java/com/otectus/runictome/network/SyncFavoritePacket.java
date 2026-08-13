package com.otectus.runictome.network;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.client.ClientDataCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client authoritative state of a single favorite flag.
 *
 * <p>Replaces the full-library re-serialization that a favorite toggle used to trigger (RT-10). The
 * old path ran {@code RunicTomeData.serializeNBT()} — every book key <em>and</em> every retained
 * {@code ItemStack} with its NBT — for a one-bit change, which a client could repeat at will. This
 * packet is three fields wide regardless of library size, so a toggle request can no longer be
 * amplified into a large response.
 *
 * <p>It is also sent when a request is <em>rejected</em> (rate limited, or the book is not owned),
 * because the client flips its own state optimistically for instant feedback. Answering every
 * request with the authoritative value is what keeps that optimism from drifting out of sync,
 * without a full re-sync.
 */
public record SyncFavoritePacket(BookKey key, boolean favorite) {

    public static void encode(SyncFavoritePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.key.systemId());
        buf.writeResourceLocation(msg.key.bookId());
        buf.writeBoolean(msg.favorite);
    }

    public static SyncFavoritePacket decode(FriendlyByteBuf buf) {
        ResourceLocation system = buf.readResourceLocation();
        ResourceLocation book = buf.readResourceLocation();
        boolean favorite = buf.readBoolean();
        return new SyncFavoritePacket(new BookKey(system, book), favorite);
    }

    public static void handle(SyncFavoritePacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Registered via consumerMainThread, so this already runs on the client thread and is
        // marked handled by Forge — same contract as the other PLAY_TO_CLIENT packets.
        ClientDataCache.acceptFavorite(msg.key, msg.favorite);
    }
}

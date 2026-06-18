package com.otectus.runictome.network;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.event.CapabilityEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server request to flip a book's favorite flag. The server is authoritative:
 * it toggles only if the player has the book unlocked, then re-syncs full state.
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
            data.toggleFavorite(msg.key);
            CapabilityEvents.syncTo(player);
        });
    }
}

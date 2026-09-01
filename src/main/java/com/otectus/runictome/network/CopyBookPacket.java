package com.otectus.runictome.network;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.impl.BookCopy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server request to duplicate one unlocked entry into a physical book, leaving the entry
 * in place. The server is authoritative about the cost and about whether copying is allowed at all.
 */
public record CopyBookPacket(BookKey key) {

    public static void encode(CopyBookPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.key.systemId());
        buf.writeResourceLocation(msg.key.bookId());
    }

    public static CopyBookPacket decode(FriendlyByteBuf buf) {
        ResourceLocation system = buf.readResourceLocation();
        ResourceLocation book = buf.readResourceLocation();
        return new CopyBookPacket(new BookKey(system, book));
    }

    public static void handle(CopyBookPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Registered via consumerMainThread: already on the server thread.
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            BookCopy.copy(player, msg.key);
        }
    }
}

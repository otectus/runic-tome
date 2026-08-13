package com.otectus.runictome.network;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.impl.BookExtraction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-to-server request to turn one unlocked entry back into a physical book. */
public record ExtractBookPacket(BookKey key) {

    public static void encode(ExtractBookPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.key.systemId());
        buf.writeResourceLocation(msg.key.bookId());
    }

    public static ExtractBookPacket decode(FriendlyByteBuf buf) {
        ResourceLocation system = buf.readResourceLocation();
        ResourceLocation book = buf.readResourceLocation();
        return new ExtractBookPacket(new BookKey(system, book));
    }

    public static void handle(ExtractBookPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            BookExtraction.extract(player, msg.key);
        }
    }
}

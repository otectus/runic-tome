package com.otectus.runictome.network;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.client.ClientDataCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UnlockBookPacket(BookKey key) {

    public static void encode(UnlockBookPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.key.systemId());
        buf.writeResourceLocation(msg.key.bookId());
    }

    public static UnlockBookPacket decode(FriendlyByteBuf buf) {
        ResourceLocation system = buf.readResourceLocation();
        ResourceLocation book = buf.readResourceLocation();
        return new UnlockBookPacket(new BookKey(system, book));
    }

    public static void handle(UnlockBookPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        ClientDataCache.acceptUnlock(msg.key);
    }
}

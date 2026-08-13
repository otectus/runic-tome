package com.otectus.runictome.network;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.client.ClientDataCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UnlockBookPacket(BookKey key, ItemStack stack) {

    public static void encode(UnlockBookPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.key.systemId());
        buf.writeResourceLocation(msg.key.bookId());
        buf.writeItem(msg.stack);
    }

    public static UnlockBookPacket decode(FriendlyByteBuf buf) {
        ResourceLocation system = buf.readResourceLocation();
        ResourceLocation book = buf.readResourceLocation();
        return new UnlockBookPacket(new BookKey(system, book), buf.readItem());
    }

    public static void handle(UnlockBookPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Registered via consumerMainThread, so this already runs on the client thread
        // (safe for the shared cache and the toast) and is marked handled by Forge.
        ClientDataCache.acceptUnlock(msg.key, msg.stack);
    }
}

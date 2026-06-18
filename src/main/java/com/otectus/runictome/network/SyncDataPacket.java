package com.otectus.runictome.network;

import com.otectus.runictome.client.ClientDataCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncDataPacket(CompoundTag payload) {

    public static void encode(SyncDataPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.payload);
    }

    public static SyncDataPacket decode(FriendlyByteBuf buf) {
        return new SyncDataPacket(buf.readNbt());
    }

    public static void handle(SyncDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Registered via consumerMainThread, so this already runs on the client thread
        // and the packet is marked handled by Forge — just apply the update.
        ClientDataCache.acceptSync(msg.payload);
    }
}

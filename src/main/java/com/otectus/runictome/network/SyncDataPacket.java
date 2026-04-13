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
        ctx.get().setPacketHandled(true);
        ClientDataCache.acceptSync(msg.payload);
    }
}

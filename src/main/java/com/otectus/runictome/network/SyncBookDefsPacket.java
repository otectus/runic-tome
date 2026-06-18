package com.otectus.runictome.network;

import com.otectus.runictome.integration.datapack.BookDef;
import com.otectus.runictome.integration.datapack.DatapackBookManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server-to-client sync of datapack-defined book definitions, so clients on a dedicated server
 * register the same single-item adapters and can display/open those books from the tome.
 */
public record SyncBookDefsPacket(List<BookDef> defs) {

    public static void encode(SyncBookDefsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.defs.size());
        for (BookDef def : msg.defs) {
            buf.writeResourceLocation(def.systemId());
            buf.writeResourceLocation(def.itemId());
            String name = def.name() == null ? "" : def.name();
            buf.writeUtf(name);
        }
    }

    public static SyncBookDefsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<BookDef> defs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation systemId = buf.readResourceLocation();
            ResourceLocation itemId = buf.readResourceLocation();
            String name = buf.readUtf();
            defs.add(new BookDef(systemId, itemId, name.isEmpty() ? null : name));
        }
        return new SyncBookDefsPacket(defs);
    }

    public static void handle(SyncBookDefsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // consumerMainThread: already on the client thread.
        DatapackBookManager.applyDefs(msg.defs);
    }
}

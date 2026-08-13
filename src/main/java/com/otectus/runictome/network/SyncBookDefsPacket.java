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

    /**
     * Hard ceiling on definitions in one packet. Far above any realistic pack (a large modpack
     * defines tens), low enough that a hostile or corrupted count cannot be used to make the client
     * pre-allocate an enormous list before a single element has been read.
     */
    public static final int MAX_DEFS = 4096;

    /** Ceiling on the optional display name, so a hostile packet cannot ship megabyte strings. */
    public static final int MAX_NAME_LENGTH = 256;

    public static void encode(SyncBookDefsPacket msg, FriendlyByteBuf buf) {
        int count = Math.min(msg.defs.size(), MAX_DEFS);
        if (count < msg.defs.size()) {
            com.otectus.runictome.RunicTome.LOGGER.warn(
                    "Runic Tome: {} datapack book definitions exceed the {} sync limit; {} will not be sent",
                    msg.defs.size(), MAX_DEFS, msg.defs.size() - count);
        }
        buf.writeVarInt(count);
        int written = 0;
        for (BookDef def : msg.defs) {
            if (written++ >= count) break;
            buf.writeResourceLocation(def.systemId());
            buf.writeResourceLocation(def.itemId());
            String name = def.name() == null ? "" : def.name();
            if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);
            buf.writeUtf(name, MAX_NAME_LENGTH);
        }
    }

    public static SyncBookDefsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_DEFS) {
            throw new IllegalArgumentException(
                    "SyncBookDefsPacket declared " + size + " definitions (limit " + MAX_DEFS + ")");
        }
        // Do not pre-size from the declared count beyond the ceiling above; grow as elements
        // actually decode so a truncated payload fails on read instead of on allocation.
        List<BookDef> defs = new ArrayList<>(Math.min(size, 64));
        for (int i = 0; i < size; i++) {
            ResourceLocation systemId = buf.readResourceLocation();
            ResourceLocation itemId = buf.readResourceLocation();
            String name = buf.readUtf(MAX_NAME_LENGTH);
            defs.add(new BookDef(systemId, itemId, name.isEmpty() ? null : name));
        }
        return new SyncBookDefsPacket(defs);
    }

    public static void handle(SyncBookDefsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // consumerMainThread: already on the client thread.
        DatapackBookManager.applyDefs(msg.defs);
    }
}

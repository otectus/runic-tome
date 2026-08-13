package com.otectus.runictome.network;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Client-to-server request to open a book from the tome. Lets adapters run their
 * server-side open logic (see {@link GuideSystemAdapter#openServer}) in addition to
 * the client-side {@link GuideSystemAdapter#open}, so books that open their GUI from
 * the server (e.g. Minecraft Comes Alive) re-open correctly.
 */
public record OpenBookPacket(BookKey key) {

    public static void encode(OpenBookPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.key.systemId());
        buf.writeResourceLocation(msg.key.bookId());
    }

    public static OpenBookPacket decode(FriendlyByteBuf buf) {
        ResourceLocation system = buf.readResourceLocation();
        ResourceLocation book = buf.readResourceLocation();
        return new OpenBookPacket(new BookKey(system, book));
    }

    public static void handle(OpenBookPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            // Only open a book the player has actually unlocked.
            if (!RunicTomeAPI.isBookUnlocked(player, msg.key)) return;
            ItemStack sourceStack = player.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                    .map(data -> data.getBookStack(msg.key)).orElse(ItemStack.EMPTY);
            Optional<GuideSystemAdapter> adapter = RunicTomeAPI.adapterFor(msg.key.systemId());
            adapter.ifPresent(a -> a.openServer(msg.key, player, sourceStack));
        });
    }
}

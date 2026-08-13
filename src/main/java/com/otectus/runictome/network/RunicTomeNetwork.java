package com.otectus.runictome.network;

import com.otectus.runictome.RunicTome;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RunicTomeNetwork {

    /**
     * Bumped 4 -> 5 in 0.6.0 by {@link SyncFavoritePacket}, which added a message id. Both sides
     * must agree on the id-to-class mapping, so a 0.5.x peer is correctly refused rather than
     * allowed to connect and misread ids 5 and 6.
     */
    private static final String PROTOCOL_VERSION = "5";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(RunicTome.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int id = 0;

    private RunicTomeNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(SyncDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDataPacket::encode)
                .decoder(SyncDataPacket::decode)
                .consumerMainThread(SyncDataPacket::handle)
                .add();

        CHANNEL.messageBuilder(UnlockBookPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnlockBookPacket::encode)
                .decoder(UnlockBookPacket::decode)
                .consumerMainThread(UnlockBookPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenBookPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenBookPacket::encode)
                .decoder(OpenBookPacket::decode)
                .consumerMainThread(OpenBookPacket::handle)
                .add();

        CHANNEL.messageBuilder(ToggleFavoritePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleFavoritePacket::encode)
                .decoder(ToggleFavoritePacket::decode)
                .consumerMainThread(ToggleFavoritePacket::handle)
                .add();

        CHANNEL.messageBuilder(ExtractBookPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ExtractBookPacket::encode)
                .decoder(ExtractBookPacket::decode)
                .consumerMainThread(ExtractBookPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncBookDefsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncBookDefsPacket::encode)
                .decoder(SyncBookDefsPacket::decode)
                .consumerMainThread(SyncBookDefsPacket::handle)
                .add();

        // Appended last so the ids of every pre-existing packet are unchanged; the protocol bump
        // is what keeps a mismatched peer out.
        CHANNEL.messageBuilder(SyncFavoritePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncFavoritePacket::encode)
                .decoder(SyncFavoritePacket::decode)
                .consumerMainThread(SyncFavoritePacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}

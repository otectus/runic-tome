package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.capability.RunicTomeDataProvider;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.SyncDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class CapabilityEvents {

    private CapabilityEvents() {}

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(RunicTomeCapabilities.PLAYER_DATA).isPresent()) {
                event.addCapability(RunicTomeDataProvider.IDENTIFIER, new RunicTomeDataProvider());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        Player oldPlayer = event.getOriginal();
        oldPlayer.reviveCaps();
        oldPlayer.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent(oldData ->
                newPlayer.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent(newData ->
                        newData.copyFrom(oldData)));
        oldPlayer.invalidateCaps();
        syncTo(newPlayer);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncTo(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncTo(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncTo(sp);
        }
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer sp) {
            syncTo(sp);
        }
    }

    public static void syncTo(ServerPlayer player) {
        player.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent((IRunicTomeData data) ->
                RunicTomeNetwork.sendTo(player, new SyncDataPacket(data.serializeNBT())));
    }
}

package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.impl.TomeGrant;
import com.otectus.runictome.capability.RunicTomeDataProvider;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.SyncDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class CapabilityEvents {

    private CapabilityEvents() {}

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof Player) {
            // Check what this event has already gathered rather than calling getCapability() on the
            // entity: the provider map is still being built while this event runs, so querying the
            // object mid-gather depends on Forge internals.
            if (!event.getCapabilities().containsKey(RunicTomeDataProvider.IDENTIFIER)) {
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

        // Drain any tomes stashed during death (see SoulboundHandler) back to the new player.
        // Non-death clones never populate the stash so this branch is a no-op for dimension
        // changes / end-portal returns.
        //
        // With Curios installed the stash is usually empty even on death: the curio slot declares
        // ALWAYS_KEEP, so an equipped tome never enters LivingDropsEvent and SoulboundHandler never
        // sees it. This path still covers tomes carried in the inventory.
        newPlayer.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent(newData -> {
            int stashed = newData.getStashedTomes();
            if (stashed > 0) {
                // stacksTo=1, so grant one at a time rather than a single count-N stack.
                for (int i = 0; i < stashed; i++) {
                    TomeGrant.give(newPlayer);
                }
                newData.setStashedTomes(0);
            }
        });

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

    public static void syncTo(ServerPlayer player) {
        player.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent((IRunicTomeData data) ->
                RunicTomeNetwork.sendTo(player, new SyncDataPacket(data.serializeNBT())));
    }
}

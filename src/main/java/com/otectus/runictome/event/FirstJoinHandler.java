package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.impl.TomeGrant;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class FirstJoinHandler {

    private FirstJoinHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (RunicTomeConfig.COMMON.grantTomeOnFirstJoin.get()) {
            sp.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent(data -> {
                if (!data.hasReceivedTome()) {
                    TomeGrant.give(sp);
                    data.setReceivedTome(true);
                    CapabilityEvents.syncTo(sp);
                }
            });
        }
        // Run an immediate sweep so books already in the player's inventory are
        // absorbed at login rather than waiting up to one sweep interval.
        // Deferred by one tick to let capabilities finish attaching.
        sp.server.execute(() -> ServerTickHandler.sweep(sp));
    }
}

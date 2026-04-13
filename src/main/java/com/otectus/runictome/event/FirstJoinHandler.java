package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class FirstJoinHandler {

    private FirstJoinHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        sp.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent(data -> {
            if (!data.hasReceivedTome()) {
                ItemStack tome = new ItemStack(ModItems.RUNIC_TOME.get());
                if (!sp.getInventory().add(tome)) {
                    sp.drop(tome, false);
                }
                data.setReceivedTome(true);
                CapabilityEvents.syncTo(sp);
            }
        });
        // Run an immediate sweep so books already in the player's inventory are
        // absorbed at login rather than waiting up to one sweep interval.
        // Deferred by one tick to let capabilities finish attaching.
        sp.server.execute(() -> ServerTickHandler.sweep(sp));
    }
}

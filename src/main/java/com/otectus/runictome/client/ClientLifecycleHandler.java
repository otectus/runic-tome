package com.otectus.runictome.client;

import com.otectus.runictome.RunicTome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Resets client-only state when the player leaves a world or server.
 *
 * <p>Without this the static {@link ClientDataCache} survives a disconnect, so after quitting to the
 * menu and joining a different world the tome UI can list — and act on — the previous world's
 * library during the window between joining and the login {@code SyncDataPacket} arriving.
 */
@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID, value = Dist.CLIENT)
public final class ClientLifecycleHandler {

    private ClientLifecycleHandler() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDataCache.clear();
    }
}

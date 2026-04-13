package com.otectus.runictome.integration.patchouli;

import com.otectus.runictome.RunicTome;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class PatchouliReloadHandler {

    private PatchouliReloadHandler() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (!PatchouliGuideAdapter.isAvailable()) return;
        PatchouliGuideAdapter.invalidateCustomItemMap();
        // Eagerly rebuild so the first absorption doesn't pay the map-build
        // cost inside a hot sweep loop.
        PatchouliGuideAdapter.prewarmCustomItemMap();
    }
}

package com.otectus.runictome.integration.patchouli;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.RunicTomeAPI;
import net.minecraftforge.fml.ModList;

public final class PatchouliIntegration {

    private PatchouliIntegration() {}

    public static void setup() {
        if (!ModList.get().isLoaded("patchouli")) {
            RunicTome.LOGGER.debug("Patchouli not loaded — skipping PatchouliGuideAdapter registration");
            return;
        }
        PatchouliGuideAdapter.tryInit();
        if (PatchouliGuideAdapter.isAvailable()) {
            RunicTomeAPI.registerAdapter(new PatchouliGuideAdapter());
            RunicTome.LOGGER.info("Registered PatchouliGuideAdapter");
        } else {
            RunicTome.LOGGER.warn("Patchouli present but API reflection failed");
        }
    }
}

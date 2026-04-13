package com.otectus.runictome.client;

import com.otectus.runictome.client.screen.RunicTomeScreen;
import net.minecraft.client.Minecraft;

/**
 * Entry points called from common code — kept as a thin indirection so the
 * item class does not reference client-only types at classload time.
 */
public final class ClientHooks {

    private ClientHooks() {}

    public static void openRunicTomeScreen() {
        Minecraft.getInstance().setScreen(new RunicTomeScreen());
    }
}

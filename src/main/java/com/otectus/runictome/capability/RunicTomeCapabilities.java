package com.otectus.runictome.capability;

import com.otectus.runictome.api.IRunicTomeData;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

import java.util.Optional;

public final class RunicTomeCapabilities {

    public static final Capability<IRunicTomeData> PLAYER_DATA =
            CapabilityManager.get(new CapabilityToken<>() {});

    private RunicTomeCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IRunicTomeData.class);
    }

    public static Optional<IRunicTomeData> get(Player player) {
        return player.getCapability(PLAYER_DATA).resolve();
    }
}

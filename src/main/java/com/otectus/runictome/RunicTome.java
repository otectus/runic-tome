package com.otectus.runictome;

import com.mojang.logging.LogUtils;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.impl.AdapterRegistry;
import com.otectus.runictome.impl.ImcHandler;
import com.otectus.runictome.integration.ModIntegrations;
import com.otectus.runictome.item.ModItems;
import com.otectus.runictome.network.RunicTomeNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RunicTome.MOD_ID)
public class RunicTome {

    public static final String MOD_ID = "runictome";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RunicTome() {
        RunicTomeAPI._installDelegate(AdapterRegistry.get());

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::processImc);
        modBus.addListener(RunicTomeCapabilities::register);

        ModItems.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RunicTomeConfig.COMMON_SPEC);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RunicTomeNetwork.register();
            ModIntegrations.setupAll();
        });
        LOGGER.info("Runic Tome common setup complete");
    }

    private void processImc(final InterModProcessEvent event) {
        ImcHandler.processImc(event);
    }
}

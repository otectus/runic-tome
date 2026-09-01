package com.otectus.runictome;

import com.mojang.logging.LogUtils;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.impl.AdapterRegistry;
import com.otectus.runictome.impl.AbsorptionPolicy;
import com.otectus.runictome.impl.ImcHandler;
import com.otectus.runictome.integration.ModIntegrations;
import com.otectus.runictome.item.ModItems;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.recipe.ModRecipes;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
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
        modBus.addListener(this::onConfigLoadOrReload);
        modBus.addListener(RunicTomeCapabilities::register);

        ModItems.register(modBus);
        ModRecipes.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RunicTomeConfig.COMMON_SPEC);
        // Per-player display preferences. Registered on both sides: a CLIENT spec is a no-op on a
        // dedicated server, and registering it unconditionally keeps the class free of side-dependent
        // branching.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, RunicTomeConfig.CLIENT_SPEC);

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

    private void onConfigLoadOrReload(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != RunicTomeConfig.COMMON_SPEC) return;
        // ModConfigEvent fires on Forge's config-watcher thread. The exclusion policy and the
        // config-derived adapters are each individually safe to swap, but they are two separate
        // publishes: an absorption landing between them would evaluate new exclusion lists against
        // the old heuristic snapshot. Running both as one server-thread task closes that window and
        // also keeps the item-registry reads inside applyConfigAdapters() on the server thread.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !server.isSameThread()) {
            server.execute(this::rebuildFromConfig);
        } else {
            rebuildFromConfig();
        }
    }

    private void rebuildFromConfig() {
        AbsorptionPolicy.rebuildFromConfig();
        // Also rebuild the adapters that snapshot config values (extraBookItemIds,
        // absorbUnknownBooks, bookKeywords, bookBlocklist, bookBlocklistMods) so they honour a
        // reload like the exclusion lists do. Skipped before common setup: the item registry may
        // not be populated yet and setupAll() performs the first build itself.
        if (ModIntegrations.isReady()) {
            try {
                ModIntegrations.applyConfigAdapters();
            } catch (Throwable t) {
                LOGGER.error("Runic Tome: failed to rebuild config-derived adapters on config reload", t);
            }
        }
    }
}

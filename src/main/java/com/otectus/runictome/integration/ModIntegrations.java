package com.otectus.runictome.integration;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.integration.patchouli.PatchouliIntegration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Central point for registering built-in adapters. Third-party mods should
 * register via {@link RunicTomeAPI#registerAdapter} or IMC rather than adding
 * entries here.
 */
public final class ModIntegrations {

    private ModIntegrations() {}

    public static void setupAll() {
        PatchouliIntegration.setup();
        registerTinkers();
        registerConfigBooks();

        var adapters = RunicTomeAPI.allAdapters();
        String ids = adapters.stream()
                .map(a -> a.systemId().toString())
                .collect(Collectors.joining(", "));
        RunicTome.LOGGER.info("Runic Tome: registered {} guide-book adapter(s): [{}]", adapters.size(), ids);
    }

    private static void registerTinkers() {
        if (!ModList.get().isLoaded("tconstruct")) return;
        registerTinkersBook("materials_and_you", "Materials and You");
        registerTinkersBook("puny_smelting", "Puny Smelting");
        registerTinkersBook("mighty_smelting", "Mighty Smelting");
        registerTinkersBook("fantastic_foundry", "Fantastic Foundry");
        registerTinkersBook("encyclopedia", "Encyclopedia of Tinkering");
        registerTinkersBook("tinkers_gadgetry", "Tinkers' Gadgetry");
    }

    private static void registerTinkersBook(String path, String displayName) {
        ResourceLocation itemId = new ResourceLocation("tconstruct", path);
        if (ForgeRegistries.ITEMS.getValue(itemId) == null) {
            RunicTome.LOGGER.debug("Tinkers book item missing, skipping: {}", itemId);
            return;
        }
        var adapter = new ItemBasedAdapter(
                new ResourceLocation(RunicTome.MOD_ID, "tinkers/" + path),
                itemId,
                Component.literal(displayName));
        RunicTomeAPI.registerAdapter(adapter);
        RunicTome.LOGGER.info("Registered Tinkers adapter for {}", itemId);
    }

    private static void registerConfigBooks() {
        List<? extends String> entries = RunicTomeConfig.COMMON.extraBookItemIds.get();
        if (entries == null || entries.isEmpty()) return;
        for (String raw : entries) {
            ResourceLocation itemId = ResourceLocation.tryParse(raw);
            if (itemId == null) {
                RunicTome.LOGGER.warn("extraBookItemIds: invalid ResourceLocation '{}', skipping", raw);
                continue;
            }
            var item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null) {
                RunicTome.LOGGER.warn("extraBookItemIds: item '{}' is not registered, skipping", itemId);
                continue;
            }
            String path = itemId.getNamespace() + "/" + itemId.getPath();
            GuideSystemAdapter adapter = new ItemBasedAdapter(
                    new ResourceLocation(RunicTome.MOD_ID, "config/" + path),
                    itemId,
                    item.getDescription().copy());
            RunicTomeAPI.registerAdapter(adapter);
            RunicTome.LOGGER.info("Registered config-defined book adapter for {}", itemId);
        }
    }
}

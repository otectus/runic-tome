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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
        registerBetterAnimalsPlus();
        registerMca();
        registerImmersiveEngineering();
        registerModonomicon();
        registerConfigBooks();
        registerHeuristic();

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

    private static void registerBetterAnimalsPlus() {
        if (!ModList.get().isLoaded("betteranimalsplus")) return;
        registerSimpleBook("betteranimalsplus", "animal_dictionary", "betteranimalsplus");
    }

    /**
     * MCA exposes many lore books ({@code mca:book_*}) plus a few others. Scan the registry
     * rather than hard-coding the list, which varies across MCA versions.
     */
    private static void registerMca() {
        if (!ModList.get().isLoaded("mca")) return;
        int count = 0;
        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            if (!"mca".equals(id.getNamespace())) continue;
            String p = id.getPath();
            if (p.contains("book") || p.contains("family_tree") || p.contains("dictionary")
                    || p.contains("registry")) {
                if (registerSimpleBook(id.getNamespace(), p, "mca")) count++;
            }
        }
        RunicTome.LOGGER.info("Registered {} MCA book adapter(s)", count);
    }

    private static void registerImmersiveEngineering() {
        if (!ModList.get().isLoaded("immersiveengineering")) return;
        // Single-item manual that opens its GUI via use() — ItemBasedAdapter + UseSimulator suffices.
        registerSimpleBook("immersiveengineering", "manual", "immersiveengineering");
    }

    /**
     * Modonomicon registers a distinct book item per book; scan its namespace for book-like items
     * (mirrors {@link #registerMca}) rather than hard-coding a list that varies per pack.
     */
    private static void registerModonomicon() {
        if (!ModList.get().isLoaded("modonomicon")) return;
        int count = 0;
        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            if (!"modonomicon".equals(id.getNamespace())) continue;
            String p = id.getPath();
            if (p.contains("book") || p.equals("modonomicon")) {
                if (registerSimpleBook(id.getNamespace(), p, "modonomicon")) count++;
            }
        }
        RunicTome.LOGGER.info("Registered {} Modonomicon book adapter(s)", count);
    }

    private static boolean registerSimpleBook(String namespace, String path, String systemPrefix) {
        ResourceLocation itemId = new ResourceLocation(namespace, path);
        var item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            RunicTome.LOGGER.debug("{} book item missing, skipping: {}", systemPrefix, itemId);
            return false;
        }
        var adapter = new ItemBasedAdapter(
                new ResourceLocation(RunicTome.MOD_ID, systemPrefix + "/" + path),
                itemId,
                item.getDescription().copy());
        RunicTomeAPI.registerAdapter(adapter);
        RunicTome.LOGGER.info("Registered {} adapter for {}", systemPrefix, itemId);
        return true;
    }

    private static void registerHeuristic() {
        if (!RunicTomeConfig.COMMON.absorbUnknownBooks.get()) {
            RunicTome.LOGGER.info("Runic Tome: keyword catch-all disabled (absorbUnknownBooks=false)");
            return;
        }
        List<String> keywords = RunicTomeConfig.COMMON.bookKeywords.get().stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        Set<ResourceLocation> blocklist = new HashSet<>();
        for (String raw : RunicTomeConfig.COMMON.bookBlocklist.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(raw);
            if (rl != null) blocklist.add(rl);
        }
        Set<String> blockedNamespaces = new HashSet<>(RunicTomeConfig.COMMON.bookBlocklistMods.get());
        var adapter = new HeuristicBookAdapter(
                new ResourceLocation(RunicTome.MOD_ID, "heuristic"), keywords, blocklist, blockedNamespaces);
        RunicTomeAPI.registerAdapter(adapter);
        RunicTome.LOGGER.info("Runic Tome: keyword catch-all active ({} keyword(s), {} item(s) + {} mod(s) blocked)",
                keywords.size(), blocklist.size(), blockedNamespaces.size());
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

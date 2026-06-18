package com.otectus.runictome.integration.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.impl.AdapterRegistry;
import com.otectus.runictome.integration.ItemBasedAdapter;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.SyncBookDefsPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads packmaker-defined guide books from {@code data/<namespace>/runictome/books/*.json}, e.g.
 * <pre>{ "item": "immersiveengineering:manual", "name": "Engineer's Manual" }</pre>
 * Each definition becomes a single-item {@link ItemBasedAdapter}. Definitions are applied
 * server-side on datapack load and synced to clients (so client-side book opening and the tome UI
 * see them on dedicated servers too).
 */
@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class DatapackBookManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "runictome/books";

    /** Last applied definitions, sent to players on datapack sync. */
    private static volatile List<BookDef> currentDefs = List.of();

    public DatapackBookManager() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager mgr, ProfilerFiller profiler) {
        List<BookDef> defs = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), "book");
                ResourceLocation itemId = new ResourceLocation(GsonHelper.getAsString(obj, "item"));
                String name = GsonHelper.getAsString(obj, "name", null);
                ResourceLocation systemId = obj.has("system")
                        ? new ResourceLocation(GsonHelper.getAsString(obj, "system"))
                        : new ResourceLocation(RunicTome.MOD_ID,
                                AdapterRegistry.DATAPACK_PREFIX + file.getNamespace() + "/" + file.getPath());
                defs.add(new BookDef(systemId, itemId, name));
            } catch (Exception e) {
                RunicTome.LOGGER.warn("Runic Tome: skipping invalid datapack book '{}': {}", file, e.getMessage());
            }
        }
        currentDefs = List.copyOf(defs);
        applyDefs(currentDefs);
        RunicTome.LOGGER.info("Runic Tome: loaded {} datapack book definition(s)", defs.size());
    }

    /** Builds adapters from definitions and swaps them into the registry (used on both sides). */
    public static void applyDefs(List<BookDef> defs) {
        List<GuideSystemAdapter> adapters = new ArrayList<>();
        for (BookDef def : defs) {
            var item = ForgeRegistries.ITEMS.getValue(def.itemId());
            if (item == null) {
                RunicTome.LOGGER.debug("Runic Tome: datapack book item '{}' not registered, skipping", def.itemId());
                continue;
            }
            Component name = def.name() != null && !def.name().isBlank()
                    ? Component.literal(def.name())
                    : item.getDescription().copy();
            adapters.add(new ItemBasedAdapter(def.systemId(), def.itemId(), name));
        }
        AdapterRegistry.get().setDatapackAdapters(adapters);
    }

    public static List<BookDef> currentDefs() {
        return currentDefs;
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new DatapackBookManager());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncBookDefsPacket packet = new SyncBookDefsPacket(currentDefs);
        if (event.getPlayer() != null) {
            RunicTomeNetwork.sendTo(event.getPlayer(), packet);
        } else {
            for (ServerPlayer sp : event.getPlayerList().getPlayers()) {
                RunicTomeNetwork.sendTo(sp, packet);
            }
        }
    }
}

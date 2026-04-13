package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.UnlockBookPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class AbsorptionHandler {

    private static final Set<ResourceLocation> UNRECOGNIZED_LOGGED = ConcurrentHashMap.newKeySet();

    private AbsorptionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.isCanceled()) return;
        if (!RunicTomeConfig.COMMON.absorbOnPickup.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack stack = event.getItem().getItem();
        Optional<BookKey> maybe = RunicTomeAPI.identify(stack);
        if (maybe.isEmpty()) {
            logUnrecognizedBookLike(stack);
            return;
        }
        BookKey key = maybe.get();
        boolean newlyUnlocked = RunicTomeAPI.unlockBook(sp, key);
        if (RunicTomeConfig.COMMON.verboseLogging.get()) {
            RunicTome.LOGGER.info("Absorbed {} for {} (newly={})", key, sp.getName().getString(), newlyUnlocked);
        }
        event.getItem().discard();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!RunicTomeConfig.COMMON.absorbOnCraft.get()) return;
        handleCreation(event.getEntity(), event.getCrafting());
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!RunicTomeConfig.COMMON.absorbOnCraft.get()) return;
        handleCreation(event.getEntity(), event.getSmelting());
    }

    private static void handleCreation(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer sp)) return;
        Optional<BookKey> maybe = RunicTomeAPI.identify(stack);
        if (maybe.isEmpty()) return;
        BookKey key = maybe.get();
        sp.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent(data -> {
            if (data.unlockBook(key)) {
                RunicTomeNetwork.sendTo(sp, new UnlockBookPacket(key));
                CapabilityEvents.syncTo(sp);
            }
        });
        stack.setCount(0);
    }

    private static void logUnrecognizedBookLike(ItemStack stack) {
        if (stack.isEmpty()) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;
        if (!UNRECOGNIZED_LOGGED.add(id)) return;
        String path = id.getPath();
        if (path.contains("book") || path.contains("manual") || path.contains("guide")
                || path.contains("lexicon") || path.contains("tome")) {
            RunicTome.LOGGER.info(
                    "Runic Tome: unrecognized book-like item '{}' — add to config.extraBookItemIds to absorb it",
                    id);
        }
    }
}

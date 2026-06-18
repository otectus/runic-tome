package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.api.UnlockResult;
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

    /** Cap so the once-per-item de-dup set can't grow without bound on a long-running server. */
    private static final int MAX_UNRECOGNIZED_LOGGED = 512;
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
        UnlockResult result = RunicTomeAPI.unlockBook(sp, key);
        if (RunicTomeConfig.COMMON.verboseLogging.get()) {
            RunicTome.LOGGER.info("Absorb {} for {} -> {}", key, sp.getName().getString(), result);
        }
        // Only consume the item once the book is reliably stored; on FAILED (e.g. capability
        // not yet attached) leave it on the ground so it is never silently destroyed.
        if (result.isStored()) {
            event.getItem().discard();
            event.setCanceled(true);
        }
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
        UnlockResult result = RunicTomeAPI.unlockBook(sp, maybe.get());
        // Only remove the crafted/smelted output once the book is reliably stored.
        if (result.isStored()) {
            stack.setCount(0);
        }
    }

    private static void logUnrecognizedBookLike(ItemStack stack) {
        // When the keyword catch-all is on it already absorbs book-like items, so anything
        // reaching here is intentionally excluded (blocklisted, a block item, etc.) — don't
        // suggest adding it to config.
        if (RunicTomeConfig.COMMON.absorbUnknownBooks.get()) return;
        if (stack.isEmpty()) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;
        if (UNRECOGNIZED_LOGGED.contains(id)) return;
        if (UNRECOGNIZED_LOGGED.size() >= MAX_UNRECOGNIZED_LOGGED) return;
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

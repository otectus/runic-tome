package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.api.UnlockResult;
import com.otectus.runictome.impl.AbsorptionPolicy;
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
    private static final java.util.concurrent.atomic.AtomicInteger UNRECOGNIZED_SLOTS =
            new java.util.concurrent.atomic.AtomicInteger();

    private AbsorptionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.isCanceled()) return;
        if (!RunicTomeConfig.COMMON.absorbOnPickup.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (RunicTomeAPI.isAbsorptionPaused(sp)) return;
        ItemStack stack = event.getItem().getItem();
        Optional<BookKey> maybe = RunicTomeAPI.identify(stack);
        if (maybe.isEmpty()) {
            logUnrecognizedBookLike(stack);
            return;
        }
        BookKey key = maybe.get();
        UnlockResult result = RunicTomeAPI.unlockBook(sp, key, stack);
        if (RunicTomeConfig.COMMON.verboseLogging.get()) {
            RunicTome.LOGGER.info("Absorb {} for {} -> {}", key, sp.getName().getString(), result);
        }
        // Only consume once the book is reliably stored; on FAILED (e.g. capability not yet
        // attached) leave the item on the ground so it is never silently destroyed.
        int consumed = AbsorptionPolicy.consumptionFor(result, stack.getCount());
        if (consumed <= 0) return;
        if (consumed >= stack.getCount()) {
            event.getItem().discard();
            event.setCanceled(true);
            return;
        }
        // Partial absorption: take our copy and let vanilla hand the remainder to the player. The
        // event is deliberately left uncanceled so the shrunken stack is still picked up.
        //
        // Mutating in place is what makes this work: ItemEntity.playerTouch captures its stack
        // reference before firing this event and then adds that same object to the inventory, so the
        // shrink is already visible to the rest of the pickup. Do NOT hand the entity a copy via
        // setItem() to force a client sync — vanilla would keep adding the original while the entity
        // held the copy, corrupting the pickup statistics. (setItem with this same reference is a
        // no-op anyway: the data watcher compares by identity and never marks itself dirty.)
        stack.shrink(consumed);
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
        if (RunicTomeAPI.isAbsorptionPaused(sp)) return;
        Optional<BookKey> maybe = RunicTomeAPI.identify(stack);
        if (maybe.isEmpty()) return;
        UnlockResult result = RunicTomeAPI.unlockBook(sp, maybe.get(), stack);
        // Only remove the crafted/smelted output once the book is reliably stored. Shrinking rather
        // than zeroing lets absorbWholeStack=false leave the remainder of a multi-output recipe.
        int consumed = AbsorptionPolicy.consumptionFor(result, stack.getCount());
        if (consumed > 0) {
            stack.shrink(consumed);
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
        // Reserve a slot before inserting, so concurrent pickups cannot push the set past the cap.
        if (UNRECOGNIZED_SLOTS.getAndIncrement() >= MAX_UNRECOGNIZED_LOGGED) {
            UNRECOGNIZED_SLOTS.decrementAndGet();
            return;
        }
        if (!UNRECOGNIZED_LOGGED.add(id)) {
            UNRECOGNIZED_SLOTS.decrementAndGet();
            return;
        }
        String path = id.getPath();
        if (path.contains("book") || path.contains("manual") || path.contains("guide")
                || path.contains("lexicon") || path.contains("tome")) {
            RunicTome.LOGGER.info(
                    "Runic Tome: unrecognized book-like item '{}' — add to config.extraBookItemIds to absorb it",
                    id);
        }
    }
}

package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.event.CapabilityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;


/** Server-authoritative materialization of one virtual library entry. */
public final class BookExtraction {

    private BookExtraction() {}

    public enum Result {
        EXTRACTED,
        DISABLED,
        NOT_UNLOCKED,
        NO_PHYSICAL_ITEM,
        DELIVERY_FAILED
    }

    public static Result extract(ServerPlayer player, BookKey key) {
        if (!RunicTomeConfig.COMMON.allowBookExtraction.get()) {
            player.displayClientMessage(Component.translatable("runictome.extract.disabled"), false);
            return Result.DISABLED;
        }

        IRunicTomeData data = player.getCapability(RunicTomeCapabilities.PLAYER_DATA).orElse(null);
        if (data == null || !data.hasBook(key)) {
            player.displayClientMessage(Component.translatable("runictome.extract.not_unlocked"), false);
            return Result.NOT_UNLOCKED;
        }

        ItemStack extracted = materialize(data, key);
        if (extracted.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("runictome.extract.no_item", key.bookId().toString()), false);
            return Result.NO_PHYSICAL_ITEM;
        }

        extracted.setCount(1);
        AbsorptionPolicy.markExtracted(extracted);
        Component extractedName = extracted.getHoverName();

        // Do not remove the virtual entry unless the item actually reached the player.
        if (!ItemDelivery.deliver(player, extracted)) {
            player.displayClientMessage(Component.translatable("runictome.extract.delivery_failed"), false);
            return Result.DELIVERY_FAILED;
        }

        if (!data.lockBook(key)) {
            RunicTome.LOGGER.warn("Extraction delivered {} to {} but its library entry disappeared concurrently",
                    key, player.getGameProfile().getName());
            return Result.NOT_UNLOCKED;
        }

        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
        CapabilityEvents.syncTo(player);
        player.displayClientMessage(
                Component.translatable("runictome.extract.success", extractedName), false);
        return Result.EXTRACTED;
    }

    /**
     * Rebuilds the physical item without mutating retained data. Current saves use the exact
     * absorbed stack; legacy key-only saves fall back to the adapter icon, then the book id.
     */
    public static ItemStack materialize(IRunicTomeData data, BookKey key) {
        ItemStack retained = data.getBookStack(key);
        if (!retained.isEmpty()) {
            retained.setCount(1);
            return retained;
        }

        try {
            ItemStack adapterStack = RunicTomeAPI.adapterFor(key.systemId())
                    .map(adapter -> adapter.displayIcon(key))
                    .orElse(ItemStack.EMPTY);
            if (adapterStack != null && !adapterStack.isEmpty()) {
                ItemStack copy = adapterStack.copy();
                copy.setCount(1);
                return copy;
            }
        } catch (Throwable t) {
            RunicTome.LOGGER.warn("Adapter failed to materialize legacy book {}", key, t);
        }

        // Last resort: treat the book id as an item registry id. This is only meaningful for
        // item-identity systems (heuristic, tagged, config, datapack, ItemBasedAdapter). For a
        // system whose bookId is not an item id — Patchouli's is a book id — a coincidental match
        // would hand back an unrelated item, so the fallback is logged rather than silent.
        // ItemRefs, not getValue(): the latter returns minecraft:air for an unregistered id.
        Item item = ItemRefs.resolve(key.bookId());
        if (item == null) return ItemStack.EMPTY;
        RunicTome.LOGGER.warn(
                "Extraction for {} has no retained stack and no adapter icon; falling back to the item "
                        + "registered under its book id ({})", key, key.bookId());
        return new ItemStack(item);
    }
}

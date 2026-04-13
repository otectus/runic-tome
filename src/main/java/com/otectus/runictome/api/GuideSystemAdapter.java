package com.otectus.runictome.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GuideSystemAdapter {

    ResourceLocation systemId();

    Optional<BookKey> identify(ItemStack stack);

    /** Called on the logical client. Implementations should open the foreign book UI. */
    void open(BookKey key, Player clientPlayer);

    default boolean supportsBulkEnumeration() {
        return false;
    }

    default Collection<BookKey> enumerateAll() {
        return List.of();
    }

    default Component displayName(BookKey key) {
        return Component.literal(key.bookId().toString());
    }

    default ItemStack displayIcon(BookKey key) {
        return ItemStack.EMPTY;
    }
}

package com.otectus.runictome.integration;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.impl.ItemRefs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

/**
 * Adapter for simple guide books represented by a single registered item.
 * Identification is by item registry key; opening uses
 * {@link com.otectus.runictome.impl.UseSimulator} on the client.
 */
public class ItemBasedAdapter implements GuideSystemAdapter {

    private final ResourceLocation systemId;
    private final ResourceLocation itemId;
    private final Component displayName;

    public ItemBasedAdapter(ResourceLocation systemId, ResourceLocation itemId, Component displayName) {
        this.systemId = systemId;
        this.itemId = itemId;
        this.displayName = displayName;
    }

    @Override
    public ResourceLocation systemId() {
        return systemId;
    }

    @Override
    public Optional<BookKey> identify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (!itemId.equals(id)) return Optional.empty();
        return Optional.of(new BookKey(systemId, itemId));
    }

    @Override
    public void open(BookKey key, Player clientPlayer) {
        open(key, clientPlayer, ItemStack.EMPTY);
    }

    @Override
    public void open(BookKey key, Player clientPlayer, ItemStack sourceStack) {
        if (!clientPlayer.level().isClientSide) return;
        ItemStack opener = ItemRefs.openerFor(itemId, sourceStack);
        if (opener.isEmpty()) {
            clientPlayer.displayClientMessage(
                    Component.translatable("runictome.unknown_item", itemId.toString()), false);
            return;
        }
        com.otectus.runictome.impl.UseSimulator.simulateClientUse(opener, clientPlayer);
    }

    @Override
    public void openServer(BookKey key, net.minecraft.server.level.ServerPlayer serverPlayer,
                           ItemStack sourceStack) {
        ItemStack opener = ItemRefs.openerFor(itemId, sourceStack);
        if (opener.isEmpty()) return;
        com.otectus.runictome.impl.UseSimulator.simulateServerUse(opener, serverPlayer);
    }

    @Override
    public boolean supportsBulkEnumeration() {
        return true;
    }

    @Override
    public java.util.Collection<BookKey> enumerateAll() {
        return List.of(new BookKey(systemId, itemId));
    }

    @Override
    public Component displayName(BookKey key) {
        return displayName;
    }

    @Override
    public ItemStack displayIcon(BookKey key) {
        return ItemRefs.stackOf(itemId);
    }
}

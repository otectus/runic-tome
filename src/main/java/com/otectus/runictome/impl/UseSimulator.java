package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTome;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only helper: temporarily swaps the selected hotbar slot for a
 * synthetic "virtual" stack, calls its {@code use} method so the foreign mod
 * opens its UI, then restores the original stack no matter what happens.
 * The synthetic stack is tagged so safety filters can drop it if a mod tries
 * to consume it.
 */
public final class UseSimulator {

    public static final String VIRTUAL_TAG = "runictome:virtual";

    private UseSimulator() {}

    public static void simulateClientUse(ItemStack fakeStack, Player player) {
        if (!player.level().isClientSide) return;
        if (Minecraft.getInstance().player != player) return;

        CompoundTag tag = fakeStack.getOrCreateTag();
        tag.putBoolean(VIRTUAL_TAG, true);

        Inventory inv = player.getInventory();
        int slot = inv.selected;
        ItemStack original = inv.items.get(slot);
        inv.items.set(slot, fakeStack);
        try {
            InteractionResultHolder<ItemStack> result =
                    fakeStack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            if (RunicTome.LOGGER.isDebugEnabled()) {
                RunicTome.LOGGER.debug("UseSimulator: {} -> {}", fakeStack.getItem(), result.getResult());
            }
        } catch (Throwable t) {
            RunicTome.LOGGER.warn("UseSimulator: foreign use() threw for {}", fakeStack.getItem(), t);
        } finally {
            inv.items.set(slot, original);
        }
    }

    public static boolean isVirtual(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(VIRTUAL_TAG);
    }
}

package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTome;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
        // Never proceed with an empty stack: ItemStack.copy() on an empty stack returns the shared
        // ItemStack.EMPTY singleton, so setCount/getOrCreateTag below would write the virtual marker
        // into global state visible to every other mod. See prepare().
        if (isUnusable(fakeStack)) {
            RunicTome.LOGGER.warn("UseSimulator: refusing to simulate use of an empty stack");
            player.displayClientMessage(
                    Component.translatable("runictome.open_no_item"), false);
            return;
        }

        fakeStack = prepare(fakeStack);

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
            // Foreign use() blew up — log with a stack trace and tell the player instead of
            // silently doing nothing, so a broken integration is visible rather than mysterious.
            RunicTome.LOGGER.warn("UseSimulator: foreign use() threw for {}", fakeStack.getItem(), t);
            player.displayClientMessage(
                    Component.translatable("runictome.open_failed", fakeStack.getHoverName()), false);
        } finally {
            restore(inv, slot, fakeStack, original);
        }
    }

    /**
     * Server-side counterpart to {@link #simulateClientUse}: swaps the player's
     * selected slot for a synthetic stack, calls its {@code use} so a foreign mod
     * runs its server-side open logic (e.g. sending its own GUI-open packet), then
     * restores the original stack no matter what happens.
     */
    public static void simulateServerUse(ItemStack fakeStack, net.minecraft.server.level.ServerPlayer player) {
        if (player.level().isClientSide) return;
        if (isUnusable(fakeStack)) {
            RunicTome.LOGGER.warn("UseSimulator(server): refusing to simulate use of an empty stack");
            return;
        }

        fakeStack = prepare(fakeStack);

        Inventory inv = player.getInventory();
        int slot = inv.selected;
        ItemStack original = inv.items.get(slot);
        inv.items.set(slot, fakeStack);
        try {
            InteractionResultHolder<ItemStack> result =
                    fakeStack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            if (RunicTome.LOGGER.isDebugEnabled()) {
                RunicTome.LOGGER.debug("UseSimulator(server): {} -> {}", fakeStack.getItem(), result.getResult());
            }
        } catch (Throwable t) {
            RunicTome.LOGGER.warn("UseSimulator(server): foreign use() threw for {}", fakeStack.getItem(), t);
        } finally {
            restore(inv, slot, fakeStack, original);
        }
    }

    /**
     * A stack is unusable as a simulation subject when it is null or empty. This check is the guard
     * that keeps {@link ItemStack#EMPTY} out of {@link #prepare}: {@code ItemStack.copy()} returns
     * the {@code EMPTY} singleton itself for an empty stack, and {@code getOrCreateTag()} on that
     * singleton installs a tag that every other mod then observes through
     * {@code ItemStack.EMPTY.getTag()}.
     */
    public static boolean isUnusable(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    /** Defensive one-item copy carrying the virtual marker. Never called with an empty stack. */
    private static ItemStack prepare(ItemStack source) {
        ItemStack fake = source.copy();
        fake.setCount(1);
        CompoundTag tag = fake.getOrCreateTag();
        tag.putBoolean(VIRTUAL_TAG, true);
        return fake;
    }

    /**
     * Puts the player's original stack back, unconditionally.
     *
     * <p>This is deliberately blunt. Whatever foreign {@code use()} left in the simulated slot is
     * discarded, which makes the simulation net-zero by construction: the player cannot gain an item
     * from opening a book. Keeping a foreign replacement and re-homing the original elsewhere would
     * be friendlier to "transform on use" items — but it would also mint a free item on every open
     * for any book whose {@code use()} does {@code player.setItemInHand(hand, new ItemStack(x))},
     * and this method runs on both logical sides on every open from the tome UI. Never trade a
     * duplication exploit for politeness; the discarded stack only ever exists because we put a
     * synthetic item in that slot a moment ago.
     *
     * <p>The replacement is logged so a mod that behaves this way is diagnosable rather than
     * mysterious.
     */
    private static void restore(Inventory inv, int slot, ItemStack fake, ItemStack original) {
        ItemStack current = inv.items.get(slot);
        if (current != fake && !current.isEmpty() && !isVirtual(current)) {
            RunicTome.LOGGER.warn(
                    "UseSimulator: foreign use() replaced the simulated slot with {}; discarding it and "
                            + "restoring the player's own stack", current);
        }
        inv.items.set(slot, original);
    }

    public static boolean isVirtual(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(VIRTUAL_TAG);
    }
}

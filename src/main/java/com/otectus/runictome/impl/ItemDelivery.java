package com.otectus.runictome.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The never-destroy hand-off used wherever this mod gives an item back to a player: inventory first,
 * the player's own drop path second, and an honest failure if neither worked.
 *
 * <p>Consolidated from three copies of the same four lines (extraction, the crafting refund, and the
 * inventory half of {@link TomeGrant}). {@code TomeGrant} keeps its own version because it tries the
 * Curios slot first and answers with a three-valued placement; everything else wants this.
 */
public final class ItemDelivery {

    private ItemDelivery() {}

    /**
     * Places {@code stack} in the player's inventory, or drops it at their feet if it will not fit.
     *
     * <p>{@link net.minecraft.world.entity.player.Inventory#add} mutates the supplied stack and
     * leaves it empty on a full transfer, so callers that need the item's name for a message must
     * read it <em>before</em> calling this. The remainder — whatever {@code add} could not take — is
     * what reaches the drop path, so a partial transfer never loses the rest.
     *
     * @return true if the stack reached the inventory or the ground. A false return means the item
     *         is still held by the caller and must not be treated as delivered.
     */
    public static boolean deliver(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (player.getInventory().add(stack)) return true;
        // add() returning false can still have moved part of a stack; only the remainder is left.
        return stack.isEmpty() || player.drop(stack, false) != null;
    }
}

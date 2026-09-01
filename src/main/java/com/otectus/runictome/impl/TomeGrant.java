package com.otectus.runictome.impl;

import com.otectus.runictome.integration.curios.CuriosSupport;
import com.otectus.runictome.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The single way a Runic Tome is handed to a player.
 *
 * <p>Three sites used to repeat the same {@code getInventory().add(stack)} / else
 * {@code drop(stack, false)} idiom — the first-join grant, the respawn re-issue of stashed tomes,
 * and {@code /runictome give}. Collapsing them here means the Curios preference applies at all three
 * without being remembered three times.
 *
 * <p>Order is curio slot, then inventory, then the ground. The tome is a permanent, never-stacking,
 * never-consumed item, so the wearable slot is where it belongs when one is available; the ground is
 * the last resort that guarantees it is never silently destroyed.
 */
public final class TomeGrant {

    private TomeGrant() {}

    /** Where a granted tome ended up, for callers that want to log or message differently. */
    public enum Placement {
        CURIO_SLOT,
        INVENTORY,
        DROPPED
    }

    /** Grants one new Runic Tome. */
    public static Placement give(ServerPlayer player) {
        return deliver(player, new ItemStack(ModItems.RUNIC_TOME.get()));
    }

    /**
     * Delivers an existing tome stack. The stack is consumed by whichever path accepts it —
     * {@code Inventory.add} mutates it, so callers must not reuse it afterwards.
     */
    public static Placement deliver(ServerPlayer player, ItemStack tome) {
        if (CuriosSupport.tryEquip(player, tome)) {
            return Placement.CURIO_SLOT;
        }
        if (player.getInventory().add(tome)) {
            return Placement.INVENTORY;
        }
        player.drop(tome, false);
        return Placement.DROPPED;
    }
}

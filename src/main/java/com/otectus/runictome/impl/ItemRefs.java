package com.otectus.runictome.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

/**
 * Safe registry lookups for item ids.
 *
 * <p><b>Why this exists.</b> {@code ForgeRegistries.ITEMS} is a defaulted registry: for an id that
 * is not registered, {@link net.minecraftforge.registries.IForgeRegistry#getValue(ResourceLocation)}
 * returns the registry's default value — {@code minecraft:air} — and <em>never</em> {@code null}.
 * Every {@code getValue(id) == null} guard is therefore dead code, which previously caused unknown
 * config/datapack book items to register live adapters named "Air", suppressed the diagnostics
 * packmakers rely on, and fed empty stacks into the use simulator (which then mutated the shared
 * {@link ItemStack#EMPTY} singleton).
 *
 * <p>Resolution here goes through {@code containsKey} first, so an unregistered id resolves to
 * {@code null} as the calling code always intended. An id that is genuinely {@code minecraft:air}
 * still resolves normally, because it <em>is</em> registered.
 */
public final class ItemRefs {

    private ItemRefs() {}

    /** @return the registered item for {@code id}, or {@code null} if no item is registered under it. */
    @Nullable
    public static Item resolve(@Nullable ResourceLocation id) {
        if (id == null) return null;
        if (!ForgeRegistries.ITEMS.containsKey(id)) return null;
        return ForgeRegistries.ITEMS.getValue(id);
    }

    /** @return true if an item is registered under {@code id}. */
    public static boolean exists(@Nullable ResourceLocation id) {
        return id != null && ForgeRegistries.ITEMS.containsKey(id);
    }

    /**
     * One-item stack for {@code id}, or {@link ItemStack#EMPTY} when the id is not registered.
     * Never returns a stack of {@code minecraft:air} for an unknown id.
     */
    public static ItemStack stackOf(@Nullable ResourceLocation id) {
        Item item = resolve(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Picks the stack an adapter should replay through the use simulator: the retained source stack
     * when one survived, otherwise a fresh stack for {@code id}. Returns {@link ItemStack#EMPTY}
     * when neither is usable, so callers can fail closed instead of simulating with an empty stack.
     */
    public static ItemStack openerFor(@Nullable ResourceLocation id, @Nullable ItemStack sourceStack) {
        if (sourceStack != null && !sourceStack.isEmpty()) return sourceStack;
        return stackOf(id);
    }
}

package com.otectus.runictome.api;

import net.minecraft.world.item.ItemStack;

/**
 * Optional richer payload for the {@link ImcMethods#REGISTER_BOOK} IMC message. A plain
 * {@link BookKey} payload is still accepted (icon-less, message-only); sending an {@code ImcBook}
 * additionally supplies an item used both as the list icon and as the stack whose {@code use()} is
 * replayed to open the book (via the {@code UseSimulator}). Pass {@link ItemStack#EMPTY} for
 * {@code openWith} to keep the message-only behavior while still providing an icon is not needed.
 */
public record ImcBook(BookKey key, ItemStack openWith) {
}

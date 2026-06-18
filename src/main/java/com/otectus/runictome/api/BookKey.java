package com.otectus.runictome.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record BookKey(ResourceLocation systemId, ResourceLocation bookId) {

    public BookKey {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(bookId, "bookId");
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("system", systemId.toString());
        tag.putString("book", bookId.toString());
        return tag;
    }

    /**
     * Reconstructs a key from NBT, tolerating corruption: malformed or missing
     * ResourceLocation strings yield an empty Optional rather than throwing, so a
     * single bad entry can never break capability/data loading.
     */
    public static Optional<BookKey> fromNbt(CompoundTag tag) {
        String systemStr = tag.getString("system");
        String bookStr = tag.getString("book");
        // Reject blanks explicitly: tryParse("") yields "minecraft:" (empty path is valid),
        // which would otherwise turn a missing field into a bogus key.
        if (systemStr.isEmpty() || bookStr.isEmpty()) return Optional.empty();
        ResourceLocation system = ResourceLocation.tryParse(systemStr);
        ResourceLocation book = ResourceLocation.tryParse(bookStr);
        if (system == null || book == null) return Optional.empty();
        return Optional.of(new BookKey(system, book));
    }

    @Override
    public String toString() {
        return systemId + "::" + bookId;
    }
}

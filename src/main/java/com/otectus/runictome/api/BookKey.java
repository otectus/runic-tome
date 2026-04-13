package com.otectus.runictome.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

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

    public static BookKey fromNbt(CompoundTag tag) {
        return new BookKey(
                new ResourceLocation(tag.getString("system")),
                new ResourceLocation(tag.getString("book"))
        );
    }

    @Override
    public String toString() {
        return systemId + "::" + bookId;
    }
}

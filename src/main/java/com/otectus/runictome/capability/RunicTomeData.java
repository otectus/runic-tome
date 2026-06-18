package com.otectus.runictome.capability;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.IRunicTomeData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

public class RunicTomeData implements IRunicTomeData {

    private final LinkedHashSet<BookKey> books = new LinkedHashSet<>();
    private final LinkedHashSet<BookKey> favorites = new LinkedHashSet<>();
    private boolean receivedTome = false;
    private int stashedTomes = 0;

    @Override
    public boolean hasBook(BookKey key) {
        return books.contains(key);
    }

    @Override
    public boolean unlockBook(BookKey key) {
        return books.add(key);
    }

    @Override
    public boolean lockBook(BookKey key) {
        favorites.remove(key); // a locked book can't stay favorited
        return books.remove(key);
    }

    @Override
    public Collection<BookKey> getBooks() {
        return Collections.unmodifiableCollection(books);
    }

    @Override
    public boolean isFavorite(BookKey key) {
        return favorites.contains(key);
    }

    @Override
    public boolean toggleFavorite(BookKey key) {
        if (!books.contains(key)) return false; // only unlocked books can be favorited
        if (favorites.remove(key)) return false;
        favorites.add(key);
        return true;
    }

    @Override
    public Collection<BookKey> getFavorites() {
        return Collections.unmodifiableCollection(favorites);
    }

    @Override
    public boolean hasReceivedTome() {
        return receivedTome;
    }

    @Override
    public void setReceivedTome(boolean value) {
        this.receivedTome = value;
    }

    @Override
    public int getStashedTomes() {
        return stashedTomes;
    }

    @Override
    public void setStashedTomes(int count) {
        this.stashedTomes = Math.max(0, count);
    }

    @Override
    public void copyFrom(IRunicTomeData other) {
        this.books.clear();
        this.books.addAll(other.getBooks());
        this.favorites.clear();
        this.favorites.addAll(other.getFavorites());
        this.receivedTome = other.hasReceivedTome();
        this.stashedTomes = other.getStashedTomes();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (BookKey key : books) {
            list.add(key.toNbt());
        }
        tag.put("books", list);
        ListTag favs = new ListTag();
        for (BookKey key : favorites) {
            favs.add(key.toNbt());
        }
        tag.put("favorites", favs);
        tag.putBoolean("receivedTome", receivedTome);
        tag.putInt("stashedTomes", stashedTomes);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        books.clear();
        ListTag list = tag.getList("books", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            // Skip malformed entries instead of letting one bad key abort the whole load;
            // the LinkedHashSet handles duplicates automatically.
            BookKey.fromNbt(list.getCompound(i)).ifPresent(books::add);
        }
        favorites.clear();
        ListTag favs = tag.getList("favorites", Tag.TAG_COMPOUND);
        for (int i = 0; i < favs.size(); i++) {
            // Only honor favorites that are still unlocked, in case data drifted.
            BookKey.fromNbt(favs.getCompound(i)).filter(books::contains).ifPresent(favorites::add);
        }
        this.receivedTome = tag.getBoolean("receivedTome");
        this.stashedTomes = tag.getInt("stashedTomes");
    }
}

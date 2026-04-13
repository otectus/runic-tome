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
        return books.remove(key);
    }

    @Override
    public Collection<BookKey> getBooks() {
        return Collections.unmodifiableCollection(books);
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
        tag.putBoolean("receivedTome", receivedTome);
        tag.putInt("stashedTomes", stashedTomes);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        books.clear();
        ListTag list = tag.getList("books", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            books.add(BookKey.fromNbt(list.getCompound(i)));
        }
        this.receivedTome = tag.getBoolean("receivedTome");
        this.stashedTomes = tag.getInt("stashedTomes");
    }
}

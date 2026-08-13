package com.otectus.runictome.capability;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.IRunicTomeData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class RunicTomeData implements IRunicTomeData {

    private final LinkedHashSet<BookKey> books = new LinkedHashSet<>();
    private final java.util.LinkedHashMap<BookKey, ItemStack> bookStacks = new java.util.LinkedHashMap<>();
    private final LinkedHashSet<BookKey> favorites = new LinkedHashSet<>();
    private boolean receivedTome = false;
    private int stashedTomes = 0;

    @Override
    public boolean hasBook(BookKey key) {
        return books.contains(key);
    }

    @Override
    public boolean unlockBook(BookKey key) {
        return unlockBook(key, ItemStack.EMPTY);
    }

    @Override
    public boolean unlockBook(BookKey key, ItemStack sourceStack) {
        boolean added = books.add(key);
        if ((added || !bookStacks.containsKey(key)) && sourceStack != null && !sourceStack.isEmpty()) {
            ItemStack retained = sourceStack.copy();
            retained.setCount(1);
            bookStacks.put(key, retained);
        }
        return added;
    }

    @Override
    public ItemStack getBookStack(BookKey key) {
        ItemStack stack = bookStacks.get(key);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    public boolean lockBook(BookKey key) {
        favorites.remove(key); // a locked book can't stay favorited
        bookStacks.remove(key);
        return books.remove(key);
    }

    /** Book count without allocating a snapshot — used by the per-frame UI header. */
    public int bookCount() {
        return books.size();
    }

    /** Resets every field to the state of a freshly-constructed instance. */
    public void clear() {
        books.clear();
        bookStacks.clear();
        favorites.clear();
        receivedTome = false;
        stashedTomes = 0;
    }

    /**
     * An immutable snapshot, not a live view. Callers (the public API, the tome UI, commands, the
     * policy purge) frequently iterate this while the server absorbs or removes a book; a
     * {@code unmodifiableCollection} wrapper would still throw {@link java.util.ConcurrentModificationException}
     * and would silently change under the caller.
     */
    @Override
    public Collection<BookKey> getBooks() {
        return List.copyOf(books);
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

    /** An immutable snapshot; see {@link #getBooks()}. */
    @Override
    public Collection<BookKey> getFavorites() {
        return List.copyOf(favorites);
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
        this.bookStacks.clear();
        for (BookKey key : other.getBooks()) {
            unlockBook(key, other.getBookStack(key));
        }
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
            CompoundTag entry = key.toNbt();
            ItemStack stack = bookStacks.get(key);
            if (stack != null && !stack.isEmpty()) {
                entry.put("stack", stack.save(new CompoundTag()));
            }
            list.add(entry);
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
        bookStacks.clear();
        ListTag list = tag.getList("books", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            // Skip malformed entries instead of letting one bad key abort the whole load;
            // the LinkedHashSet handles duplicates automatically.
            CompoundTag entry = list.getCompound(i);
            BookKey.fromNbt(entry).ifPresent(key -> {
                ItemStack stack = entry.contains("stack", Tag.TAG_COMPOUND)
                        ? ItemStack.of(entry.getCompound("stack")) : ItemStack.EMPTY;
                unlockBook(key, stack);
            });
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

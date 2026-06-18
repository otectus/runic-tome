package com.otectus.runictome.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.Collection;

public interface IRunicTomeData extends INBTSerializable<CompoundTag> {

    boolean hasBook(BookKey key);

    /** @return true if the book was newly unlocked; false if already present. */
    boolean unlockBook(BookKey key);

    /** @return true if the book was present and removed. */
    boolean lockBook(BookKey key);

    Collection<BookKey> getBooks();

    /** @return true if the book is marked as a favorite. */
    boolean isFavorite(BookKey key);

    /**
     * Flips the favorite state of an unlocked book.
     * @return the new favorite state (true = now favorite), or false if the book isn't unlocked.
     */
    boolean toggleFavorite(BookKey key);

    Collection<BookKey> getFavorites();

    boolean hasReceivedTome();

    void setReceivedTome(boolean value);

    /** Number of Runic Tomes stashed on the player's last death, pending restoration on respawn. */
    int getStashedTomes();

    void setStashedTomes(int count);

    /** Copy state from another instance — used on PlayerEvent.Clone. */
    void copyFrom(IRunicTomeData other);
}

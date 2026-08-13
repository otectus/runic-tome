package com.otectus.runictome.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.Collection;

public interface IRunicTomeData extends INBTSerializable<CompoundTag> {

    boolean hasBook(BookKey key);

    /** @return true if the book was newly unlocked; false if already present. */
    boolean unlockBook(BookKey key);

    /** Unlocks a book while retaining the identifying stack and its opening NBT. */
    boolean unlockBook(BookKey key, ItemStack sourceStack);

    /** A defensive copy of the retained stack, or {@link ItemStack#EMPTY}. */
    ItemStack getBookStack(BookKey key);

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

    /**
     * Sets the favorite state of an unlocked book to an absolute value, rather than flipping it.
     *
     * <p>Needed wherever a state is <em>received</em> rather than requested — applying an
     * authoritative value from the server must be idempotent, or a retransmitted or reordered update
     * would invert the flag instead of converging on it.
     *
     * <p>A {@code default} method, not a new abstract one, so existing implementors of this public
     * interface keep compiling and the API version does not change.
     *
     * @return the resulting favorite state; false if the book is not unlocked.
     */
    default boolean setFavorite(BookKey key, boolean favorite) {
        if (isFavorite(key) == favorite) {
            // Already correct. Report the real state: a book that is not unlocked cannot be a
            // favorite, and must not report that it became one.
            return favorite && hasBook(key);
        }
        return toggleFavorite(key);
    }

    Collection<BookKey> getFavorites();

    boolean hasReceivedTome();

    void setReceivedTome(boolean value);

    /** Number of Runic Tomes stashed on the player's last death, pending restoration on respawn. */
    int getStashedTomes();

    void setStashedTomes(int count);

    /** Copy state from another instance — used on PlayerEvent.Clone. */
    void copyFrom(IRunicTomeData other);
}

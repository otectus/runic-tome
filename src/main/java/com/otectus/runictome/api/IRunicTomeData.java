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

    boolean hasReceivedTome();

    void setReceivedTome(boolean value);

    /** Copy state from another instance — used on PlayerEvent.Clone. */
    void copyFrom(IRunicTomeData other);
}

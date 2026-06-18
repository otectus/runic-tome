package com.otectus.runictome.api;

/**
 * Outcome of attempting to unlock a book into a player's tome. Callers use this to
 * decide whether the source item is safe to consume: {@link #ADDED} and
 * {@link #ALREADY_HAD} mean the book is reliably stored (consume the item), while
 * {@link #FAILED} means it is not (leave the item so it is never silently destroyed).
 */
public enum UnlockResult {
    /** The book was newly added to the player's tome. */
    ADDED,
    /** The player already had the book; nothing changed but it is safely stored. */
    ALREADY_HAD,
    /** The book could not be stored (capability absent or an error occurred). */
    FAILED;

    /** Whether the source item can be safely consumed (i.e. the book is now stored). */
    public boolean isStored() {
        return this != FAILED;
    }
}

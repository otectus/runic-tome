package com.otectus.runictome.client;

import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Optional;

public final class ClientDataCache {

    private static final RunicTomeData DATA = new RunicTomeData();

    private ClientDataCache() {}

    public static void acceptSync(CompoundTag tag) {
        if (tag != null) {
            DATA.deserializeNBT(tag);
        }
    }

    /**
     * Drops every cached book. Called when the client leaves a world so the next world does not
     * briefly show the previous one's library in the window between joining and the login sync.
     */
    public static void clear() {
        DATA.clear();
    }

    /**
     * Applies the server's authoritative favorite state for one book. Absolute rather than a flip,
     * so it converges no matter how it interleaves with the optimistic local toggle in
     * {@link #toggleFavoriteOptimistic}.
     */
    public static void acceptFavorite(BookKey key, boolean favorite) {
        DATA.setFavorite(key, favorite);
    }

    public static void acceptUnlock(BookKey key, ItemStack stack) {
        boolean added = DATA.unlockBook(key, stack);
        if (!added) return;
        if (!RunicTomeConfig.showUnlockToast()) return;
        Minecraft mc = Minecraft.getInstance();
        Optional<GuideSystemAdapter> adapter = RunicTomeAPI.adapterFor(key.systemId());
        Component body = adapter.map(a -> a.displayName(key))
                .orElseGet(() -> Component.literal(key.bookId().toString()));
        mc.getToasts().addToast(SystemToast.multiline(
                mc,
                SystemToast.SystemToastIds.NARRATOR_TOGGLE,
                Component.translatable("runictome.toast.unlocked"),
                body));
    }

    /** An immutable snapshot; {@link com.otectus.runictome.capability.RunicTomeData#getBooks()} already copies. */
    public static Collection<BookKey> getBooks() {
        return DATA.getBooks();
    }

    /**
     * Unlocked-book count without building a snapshot. The tome screen draws this every frame, so
     * it must not allocate a copy of the whole library per frame.
     */
    public static int size() {
        return DATA.bookCount();
    }

    public static boolean hasBook(BookKey key) {
        return DATA.hasBook(key);
    }

    public static ItemStack getBookStack(BookKey key) {
        return DATA.getBookStack(key);
    }

    public static boolean isFavorite(BookKey key) {
        return DATA.isFavorite(key);
    }

    /**
     * Optimistically flips the favorite state client-side for instant UI feedback. The server
     * toggles authoritatively and re-syncs; both flip the same membership so they converge.
     * @return the new favorite state.
     */
    public static boolean toggleFavoriteOptimistic(BookKey key) {
        return DATA.toggleFavorite(key);
    }

    public static boolean isAbsorptionPaused() {
        return DATA.isAbsorptionPaused();
    }

    /**
     * Applies the server's authoritative pause state. Absolute rather than a flip, for the reason
     * given on {@link #acceptFavorite}: it must converge however it interleaves with the optimistic
     * local set below.
     */
    public static void acceptAbsorptionPaused(boolean paused) {
        DATA.setAbsorptionPaused(paused);
    }

    /**
     * Optimistically applies a pause state client-side for instant UI feedback. The server answers
     * with the authoritative value, which wins.
     */
    public static void setAbsorptionPausedOptimistic(boolean paused) {
        DATA.setAbsorptionPaused(paused);
    }
}

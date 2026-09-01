package com.otectus.runictome.api;

import com.otectus.runictome.capability.RunicTomeCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * Public, stable entry point for third-party mods to integrate with Runic Tome.
 * Implementation is installed by the core mod at setup time.
 */
public final class RunicTomeAPI {

    private RunicTomeAPI() {}

    /**
     * API version, bumped on breaking changes so integrators can detect compatibility.
     *
     * <p>3 -&gt; 4 in 0.9.0: {@link #isAbsorptionPaused} plus the {@code isAbsorptionPaused} /
     * {@code setAbsorptionPaused} pair on {@link IRunicTomeData}. Additive only — the new interface
     * methods are {@code default}, and {@link Delegate} is unchanged, so an integrator compiled
     * against 3 still links.
     */
    public static final int API_VERSION = 4;

    public interface Delegate {
        void registerAdapter(GuideSystemAdapter adapter);
        Optional<GuideSystemAdapter> adapterFor(ResourceLocation systemId);
        Optional<BookKey> identify(ItemStack stack);
        Collection<GuideSystemAdapter> allAdapters();
        boolean isBookUnlocked(Player player, BookKey key);
        UnlockResult unlockBook(ServerPlayer player, BookKey key);
        UnlockResult unlockBook(ServerPlayer player, BookKey key, ItemStack sourceStack);
        boolean lockBook(ServerPlayer player, BookKey key);
        Collection<BookKey> getUnlockedBooks(Player player);
    }

    private static volatile Delegate DELEGATE = new Delegate() {
        @Override public void registerAdapter(GuideSystemAdapter adapter) { warn(); }
        @Override public Optional<GuideSystemAdapter> adapterFor(ResourceLocation systemId) { warn(); return Optional.empty(); }
        @Override public Optional<BookKey> identify(ItemStack stack) { warn(); return Optional.empty(); }
        @Override public Collection<GuideSystemAdapter> allAdapters() { warn(); return java.util.List.of(); }
        @Override public boolean isBookUnlocked(Player player, BookKey key) { warn(); return false; }
        @Override public UnlockResult unlockBook(ServerPlayer player, BookKey key) { warn(); return UnlockResult.FAILED; }
        @Override public UnlockResult unlockBook(ServerPlayer player, BookKey key, ItemStack sourceStack) { warn(); return UnlockResult.FAILED; }
        @Override public boolean lockBook(ServerPlayer player, BookKey key) { warn(); return false; }
        @Override public Collection<BookKey> getUnlockedBooks(Player player) { warn(); return java.util.List.of(); }
        private void warn() {
            System.err.println("[RunicTomeAPI] called before runictome initialized its delegate");
        }
    };

    /** Internal — called by the core mod during setup. */
    public static void _installDelegate(Delegate delegate) {
        DELEGATE = delegate;
    }

    public static void registerAdapter(GuideSystemAdapter adapter) {
        DELEGATE.registerAdapter(adapter);
    }

    public static Optional<GuideSystemAdapter> adapterFor(ResourceLocation systemId) {
        return DELEGATE.adapterFor(systemId);
    }

    public static Optional<BookKey> identify(ItemStack stack) {
        return DELEGATE.identify(stack);
    }

    public static Collection<GuideSystemAdapter> allAdapters() {
        return DELEGATE.allAdapters();
    }

    public static boolean isBookUnlocked(Player player, BookKey key) {
        return DELEGATE.isBookUnlocked(player, key);
    }

    public static UnlockResult unlockBook(ServerPlayer player, BookKey key) {
        return DELEGATE.unlockBook(player, key);
    }

    public static UnlockResult unlockBook(ServerPlayer player, BookKey key, ItemStack sourceStack) {
        return DELEGATE.unlockBook(player, key, sourceStack);
    }

    public static boolean lockBook(ServerPlayer player, BookKey key) {
        return DELEGATE.lockBook(player, key);
    }

    public static Collection<BookKey> getUnlockedBooks(Player player) {
        return DELEGATE.getUnlockedBooks(player);
    }

    /**
     * Whether {@code player} has switched off ambient absorption.
     *
     * <p>Read straight from the capability rather than through {@link Delegate}: the flag is player
     * state, not adapter behaviour, and routing it through the delegate interface would have made
     * this a breaking API change for anyone implementing {@code Delegate}.
     *
     * <p>Callers should treat this as covering ambient absorption only — pickup, craft and smelt
     * output, and the periodic sweep. Deliberate absorption ignores it; see
     * {@link IRunicTomeData#isAbsorptionPaused()}.
     */
    public static boolean isAbsorptionPaused(Player player) {
        if (player == null) return false;
        return RunicTomeCapabilities.get(player).map(IRunicTomeData::isAbsorptionPaused).orElse(false);
    }

    /** Convenience for tests / simple callers. */
    public static <T> T withDelegate(Function<Delegate, T> fn) {
        return fn.apply(DELEGATE);
    }
}

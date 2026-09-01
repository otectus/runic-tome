package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.api.UnlockResult;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.event.CapabilityEvents;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.UnlockBookPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public final class AdapterRegistry implements RunicTomeAPI.Delegate {

    private static final AdapterRegistry INSTANCE = new AdapterRegistry();

    private final LinkedHashMap<ResourceLocation, GuideSystemAdapter> adapters = new LinkedHashMap<>();

    /**
     * Immutable, priority-sorted snapshot rebuilt on every registration. Reads
     * ({@link #identify}/{@link #allAdapters}) go through this so they never iterate a
     * map being mutated on another thread.
     */
    private volatile List<GuideSystemAdapter> snapshot = List.of();

    private AdapterRegistry() {}

    public static AdapterRegistry get() {
        return INSTANCE;
    }

    /** systemId path prefix marking adapters owned by the datapack loader (reload-replaceable). */
    public static final String DATAPACK_PREFIX = "datapack/";

    /** systemId path prefix marking adapters built from the common config (reload-replaceable). */
    public static final String CONFIG_PREFIX = "config/";

    /** systemId of the keyword catch-all, rebuilt alongside the config-derived adapters. */
    public static final String HEURISTIC_PATH = "heuristic";

    /** Adapters whose {@code identify} threw, so the failure is logged once rather than per stack. */
    private final java.util.Set<ResourceLocation> failedIdentify =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * First-registration sequence per systemId. Precedence within a priority tier is registration
     * order, and a reload-driven replacement must not change it: removing and re-inserting a
     * config-derived adapter would otherwise move it behind every adapter registered after common
     * setup (IMC, datapack, third-party), silently flipping which adapter claims a contested item
     * and re-absorbing it under a different BookKey.
     */
    private final java.util.Map<ResourceLocation, Integer> firstSeen = new java.util.HashMap<>();
    private int registrationCounter = 0;

    @Override
    public synchronized void registerAdapter(GuideSystemAdapter adapter) {
        put(adapter);
        rebuildSnapshot();
    }

    /**
     * Removes a single adapter by systemId.
     *
     * @return true if an adapter was registered under that id.
     */
    public synchronized boolean unregisterAdapter(ResourceLocation systemId) {
        boolean removed = adapters.remove(systemId) != null;
        if (removed) {
            failedIdentify.remove(systemId);
            rebuildSnapshot();
        }
        return removed;
    }

    /**
     * Replaces the entire datapack-defined adapter set (those whose systemId is in this mod's
     * namespace and whose path starts with {@link #DATAPACK_PREFIX}) in one pass, so a datapack
     * reload doesn't leave stale entries.
     */
    public synchronized void setDatapackAdapters(Collection<GuideSystemAdapter> datapackAdapters) {
        replaceOwned(id -> id.getPath().startsWith(DATAPACK_PREFIX), datapackAdapters);
    }

    /**
     * Replaces the entire config-derived adapter set — the {@code extraBookItemIds} adapters and the
     * keyword catch-all — in one pass. Used on every common-config load/reload so edits to
     * {@code extraBookItemIds}, {@code absorbUnknownBooks}, {@code bookKeywords},
     * {@code bookBlocklist} and {@code bookBlocklistMods} take effect without a restart. Passing a
     * collection without a heuristic adapter therefore also removes a previously-registered one.
     */
    public synchronized void setConfigAdapters(Collection<GuideSystemAdapter> configAdapters) {
        replaceOwned(
                id -> id.getPath().startsWith(CONFIG_PREFIX) || HEURISTIC_PATH.equals(id.getPath()),
                configAdapters);
    }

    /**
     * Swaps out the adapters this mod owns and that match {@code ownedPath}, leaving every other
     * adapter alone.
     *
     * <p>The namespace check matters: a third-party or datapack-declared systemId such as
     * {@code somemod:config/guides} or {@code somemod:heuristic} is legal, and a namespace-blind
     * path match would delete it on the next config reload with no way to get it back (IMC runs once
     * per launch).
     *
     * <p>The new map is built completely before it is published, so an adapter whose
     * {@code systemId()} throws cannot leave the registry half-mutated.
     */
    private void replaceOwned(java.util.function.Predicate<ResourceLocation> ownedPath,
                              Collection<GuideSystemAdapter> replacements) {
        LinkedHashMap<ResourceLocation, GuideSystemAdapter> rebuilt = new LinkedHashMap<>();
        for (java.util.Map.Entry<ResourceLocation, GuideSystemAdapter> e : adapters.entrySet()) {
            ResourceLocation id = e.getKey();
            boolean owned = RunicTome.MOD_ID.equals(id.getNamespace()) && ownedPath.test(id);
            if (!owned) rebuilt.put(id, e.getValue());
        }
        for (GuideSystemAdapter a : replacements) {
            rebuilt.put(a.systemId(), a);
        }
        adapters.clear();
        adapters.putAll(rebuilt);
        for (GuideSystemAdapter a : replacements) {
            failedIdentify.remove(a.systemId());
        }
        rebuildSnapshot();
    }

    private void put(GuideSystemAdapter adapter) {
        ResourceLocation id = adapter.systemId();
        adapters.put(id, adapter);
        firstSeen.putIfAbsent(id, registrationCounter++);
        failedIdentify.remove(id);
    }

    private void rebuildSnapshot() {
        // Descending priority, then first-registration order within a tier. Using the recorded
        // sequence rather than current map order keeps precedence stable across reloads.
        for (ResourceLocation id : adapters.keySet()) {
            firstSeen.putIfAbsent(id, registrationCounter++);
        }
        List<GuideSystemAdapter> rebuilt = new ArrayList<>(adapters.values());
        rebuilt.sort(Comparator.comparingInt(GuideSystemAdapter::priority).reversed()
                .thenComparingInt(a -> firstSeen.getOrDefault(a.systemId(), Integer.MAX_VALUE)));
        snapshot = List.copyOf(rebuilt);
    }

    @Override
    public Optional<GuideSystemAdapter> adapterFor(ResourceLocation systemId) {
        synchronized (this) {
            return Optional.ofNullable(adapters.get(systemId));
        }
    }

    @Override
    public Optional<BookKey> identify(ItemStack stack) {
        return identify(stack, false);
    }

    /**
     * {@link #identify} for a deliberate player action, which honours every exclusion except the
     * extraction marker. Used by the crafting path so a book the owner pulled out of the tome can be
     * filed back in; ambient absorption must keep using {@link #identify}.
     */
    public Optional<BookKey> identifyForExplicitAction(ItemStack stack) {
        return identify(stack, true);
    }

    private Optional<BookKey> identify(ItemStack stack, boolean allowExtracted) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        try {
            boolean excluded = allowExtracted
                    ? AbsorptionPolicy.isExcludedForExplicitAction(stack)
                    : AbsorptionPolicy.isExcluded(stack);
            if (excluded) return Optional.empty();
        } catch (Throwable t) {
            // Fail closed: if the safety gate itself cannot be evaluated, never claim the item.
            RunicTome.LOGGER.error("Runic Tome: absorption policy threw; refusing to absorb", t);
            return Optional.empty();
        }
        for (GuideSystemAdapter a : snapshot) {
            Optional<BookKey> result;
            try {
                result = a.identify(stack);
            } catch (Throwable t) {
                // A third-party / IMC / datapack adapter must never be able to abort an item pickup,
                // an inventory sweep, or a craft. Isolate it, log once, and keep probing.
                if (failedIdentify.add(a.systemId())) {
                    RunicTome.LOGGER.error(
                            "Runic Tome: adapter {} threw from identify() and will be skipped for this stack "
                                    + "(further failures from this adapter are not logged)",
                            a.systemId(), t);
                }
                continue;
            }
            if (result != null && result.isPresent()) return result;
        }
        return Optional.empty();
    }

    @Override
    public Collection<GuideSystemAdapter> allAdapters() {
        return snapshot;
    }

    @Override
    public boolean isBookUnlocked(Player player, BookKey key) {
        return player.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                .map(data -> data.hasBook(key))
                .orElse(false);
    }

    @Override
    public UnlockResult unlockBook(ServerPlayer player, BookKey key) {
        return unlockBook(player, key, ItemStack.EMPTY);
    }

    @Override
    public UnlockResult unlockBook(ServerPlayer player, BookKey key, ItemStack sourceStack) {
        IRunicTomeData data = player.getCapability(RunicTomeCapabilities.PLAYER_DATA).orElse(null);
        if (data == null) {
            // Capability not attached (yet): report failure so the caller keeps the item.
            return UnlockResult.FAILED;
        }
        boolean lackedStoredStack = data.getBookStack(key).isEmpty();
        if (!data.unlockBook(key, sourceStack)) {
            // A legacy save may know the key but not have retained its original stack. Reacquiring
            // the book backfills that data and updates the client without showing a second unlock.
            if (lackedStoredStack && !data.getBookStack(key).isEmpty()) {
                RunicTomeNetwork.sendTo(player, new UnlockBookPacket(key, data.getBookStack(key)));
            }
            return UnlockResult.ALREADY_HAD;
        }
        // Newly unlocked: an incremental packet is enough to update the client and toast.
        // A full SyncDataPacket is only needed on login/respawn/dimension change.
        RunicTomeNetwork.sendTo(player, new UnlockBookPacket(key, data.getBookStack(key)));
        return UnlockResult.ADDED;
    }

    @Override
    public boolean lockBook(ServerPlayer player, BookKey key) {
        boolean removed = player.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                .map(data -> data.lockBook(key))
                .orElse(false);
        if (removed) {
            CapabilityEvents.syncTo(player);
        }
        return removed;
    }

    @Override
    public Collection<BookKey> getUnlockedBooks(Player player) {
        return player.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                .map(IRunicTomeData::getBooks)
                .orElse(List.of());
    }
}

package com.otectus.runictome.impl;

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

    @Override
    public synchronized void registerAdapter(GuideSystemAdapter adapter) {
        adapters.put(adapter.systemId(), adapter);
        rebuildSnapshot();
    }

    /**
     * Replaces the entire datapack-defined adapter set (those whose systemId path starts with
     * {@link #DATAPACK_PREFIX}) in one pass, so a datapack reload doesn't leave stale entries.
     */
    public synchronized void setDatapackAdapters(Collection<GuideSystemAdapter> datapackAdapters) {
        adapters.values().removeIf(a -> a.systemId().getPath().startsWith(DATAPACK_PREFIX));
        for (GuideSystemAdapter a : datapackAdapters) {
            adapters.put(a.systemId(), a);
        }
        rebuildSnapshot();
    }

    private void rebuildSnapshot() {
        // Stable sort by descending priority preserves registration order within a tier.
        List<GuideSystemAdapter> rebuilt = new ArrayList<>(adapters.values());
        rebuilt.sort(Comparator.comparingInt(GuideSystemAdapter::priority).reversed());
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
        if (stack == null || stack.isEmpty()) return Optional.empty();
        for (GuideSystemAdapter a : snapshot) {
            Optional<BookKey> result = a.identify(stack);
            if (result.isPresent()) return result;
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
        IRunicTomeData data = player.getCapability(RunicTomeCapabilities.PLAYER_DATA).orElse(null);
        if (data == null) {
            // Capability not attached (yet): report failure so the caller keeps the item.
            return UnlockResult.FAILED;
        }
        if (!data.unlockBook(key)) {
            return UnlockResult.ALREADY_HAD;
        }
        // Newly unlocked: an incremental packet is enough to update the client and toast.
        // A full SyncDataPacket is only needed on login/respawn/dimension change.
        RunicTomeNetwork.sendTo(player, new UnlockBookPacket(key));
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

package com.otectus.runictome.impl;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.event.CapabilityEvents;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.UnlockBookPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public final class AdapterRegistry implements RunicTomeAPI.Delegate {

    private static final AdapterRegistry INSTANCE = new AdapterRegistry();

    private final LinkedHashMap<ResourceLocation, GuideSystemAdapter> adapters = new LinkedHashMap<>();

    private AdapterRegistry() {}

    public static AdapterRegistry get() {
        return INSTANCE;
    }

    @Override
    public synchronized void registerAdapter(GuideSystemAdapter adapter) {
        adapters.put(adapter.systemId(), adapter);
    }

    @Override
    public Optional<GuideSystemAdapter> adapterFor(ResourceLocation systemId) {
        return Optional.ofNullable(adapters.get(systemId));
    }

    @Override
    public Optional<BookKey> identify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        for (GuideSystemAdapter a : adapters.values()) {
            Optional<BookKey> result = a.identify(stack);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    @Override
    public Collection<GuideSystemAdapter> allAdapters() {
        return Collections.unmodifiableCollection(adapters.values());
    }

    @Override
    public boolean isBookUnlocked(Player player, BookKey key) {
        return player.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                .map(data -> data.hasBook(key))
                .orElse(false);
    }

    @Override
    public boolean unlockBook(ServerPlayer player, BookKey key) {
        boolean added = player.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                .map(data -> data.unlockBook(key))
                .orElse(false);
        if (added) {
            RunicTomeNetwork.sendTo(player, new UnlockBookPacket(key));
            CapabilityEvents.syncTo(player);
        }
        return added;
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

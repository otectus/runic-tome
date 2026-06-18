package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.impl.AdapterRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterRegistryTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Minimal adapter that never matches anything — used only to probe ordering. */
    private record FakeAdapter(ResourceLocation systemId, int prio) implements GuideSystemAdapter {
        @Override public int priority() { return prio; }
        @Override public Optional<BookKey> identify(ItemStack stack) { return Optional.empty(); }
        @Override public void open(BookKey key, Player clientPlayer) { }
    }

    @Test
    void higherPriorityAdapterIsProbedFirst() {
        AdapterRegistry reg = AdapterRegistry.get();
        ResourceLocation low = new ResourceLocation("runictome_test", "low_prio");
        ResourceLocation high = new ResourceLocation("runictome_test", "high_prio");

        // Register low first; priority (not registration order) must determine precedence.
        reg.registerAdapter(new FakeAdapter(low, 0));
        reg.registerAdapter(new FakeAdapter(high, 500));

        List<ResourceLocation> order = reg.allAdapters().stream()
                .map(GuideSystemAdapter::systemId)
                .filter(id -> id.getNamespace().equals("runictome_test"))
                .toList();

        assertTrue(order.indexOf(high) < order.indexOf(low),
                "Higher-priority adapter should appear before lower-priority one regardless of registration order");
    }
}

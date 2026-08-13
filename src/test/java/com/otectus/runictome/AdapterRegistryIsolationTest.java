package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.impl.AdapterRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for RT-05 (a throwing adapter must not break identification for everyone else)
 * and RT-06 (the config-derived adapter set must be replaceable wholesale on a config reload).
 */
class AdapterRegistryIsolationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Every systemId this class registers, so the process-wide {@link AdapterRegistry} singleton is
     * left exactly as it was found. Gradle runs all test classes in one JVM.
     */
    private static final List<ResourceLocation> REGISTERED = List.of(
            new ResourceLocation("runictome", "config/exploding"),
            new ResourceLocation("runictome", "config/working"),
            new ResourceLocation("runictome", "config/first"),
            new ResourceLocation("runictome", "config/second"),
            new ResourceLocation("runictome", AdapterRegistry.HEURISTIC_PATH),
            new ResourceLocation("runictome", "tinkers/materials_and_you"),
            new ResourceLocation("runictome", "datapack/pack/manual"),
            new ResourceLocation("somemod", "config/guides"),
            new ResourceLocation("somemod", AdapterRegistry.HEURISTIC_PATH),
            new ResourceLocation("runictome", "config/contested"),
            new ResourceLocation("somemod", "contested_imc"));

    @BeforeEach
    @AfterEach
    void clearOwnAdapters() {
        AdapterRegistry reg = AdapterRegistry.get();
        for (ResourceLocation id : REGISTERED) {
            reg.unregisterAdapter(id);
        }
    }

    /** Always throws from identify — stands in for a broken third-party/IMC/datapack adapter. */
    private record ExplodingAdapter(ResourceLocation systemId, int prio) implements GuideSystemAdapter {
        @Override public int priority() { return prio; }
        @Override public Optional<BookKey> identify(ItemStack stack) {
            throw new IllegalStateException("adapter is broken");
        }
        @Override public void open(BookKey key, Player clientPlayer) { }
    }

    /** Matches every non-empty stack. */
    private record AlwaysMatchAdapter(ResourceLocation systemId, int prio) implements GuideSystemAdapter {
        @Override public int priority() { return prio; }
        @Override public Optional<BookKey> identify(ItemStack stack) {
            return Optional.of(new BookKey(systemId, new ResourceLocation("test", "matched")));
        }
        @Override public void open(BookKey key, Player clientPlayer) { }
    }

    @Test
    void athrowingAdapterDoesNotPreventALowerPriorityAdapterFromMatching() {
        AdapterRegistry reg = AdapterRegistry.get();
        reg.setConfigAdapters(List.of(
                new ExplodingAdapter(new ResourceLocation("runictome", "config/exploding"), 900),
                new AlwaysMatchAdapter(new ResourceLocation("runictome", "config/working"), 800)));

        Optional<BookKey> result = reg.identify(new ItemStack(Items.PAPER));

        assertTrue(result.isPresent(),
                "a broken high-priority adapter must be skipped, not abort the whole identify pass");
        assertEquals(new ResourceLocation("runictome", "config/working"), result.get().systemId());
    }

    @Test
    void athrowingAdapterAloneJustYieldsNoMatch() {
        AdapterRegistry reg = AdapterRegistry.get();
        reg.setConfigAdapters(List.of(
                new ExplodingAdapter(new ResourceLocation("runictome", "config/exploding"), 900)));

        // Must fail closed (no match, item untouched) rather than propagating into the caller —
        // the callers are the item-pickup event, the inventory sweep, and the craft/smelt handlers.
        assertTrue(reg.identify(new ItemStack(Items.PAPER)).isEmpty());
    }

    @Test
    void setConfigAdaptersReplacesThePreviousSetWholesale() {
        AdapterRegistry reg = AdapterRegistry.get();
        ResourceLocation first = new ResourceLocation("runictome", "config/first");
        ResourceLocation second = new ResourceLocation("runictome", "config/second");

        reg.setConfigAdapters(List.of(new AlwaysMatchAdapter(first, 100)));
        assertTrue(reg.adapterFor(first).isPresent());

        reg.setConfigAdapters(List.of(new AlwaysMatchAdapter(second, 100)));
        assertFalse(reg.adapterFor(first).isPresent(),
                "an extraBookItemIds entry removed from the config must lose its adapter on reload");
        assertTrue(reg.adapterFor(second).isPresent());
    }

    @Test
    void setConfigAdaptersRemovesTheHeuristicWhenItIsNoLongerBuilt() {
        AdapterRegistry reg = AdapterRegistry.get();
        ResourceLocation heuristic =
                new ResourceLocation("runictome", AdapterRegistry.HEURISTIC_PATH);

        reg.setConfigAdapters(List.of(new AlwaysMatchAdapter(heuristic, 0)));
        assertTrue(reg.adapterFor(heuristic).isPresent());

        // Simulates absorbUnknownBooks flipping to false and the config being reloaded.
        reg.setConfigAdapters(List.of());
        assertFalse(reg.adapterFor(heuristic).isPresent());
    }

    /** Never matches, so registering it cannot leak into other tests through the shared singleton. */
    private record InertAdapter(ResourceLocation systemId) implements GuideSystemAdapter {
        @Override public Optional<BookKey> identify(ItemStack stack) { return Optional.empty(); }
        @Override public void open(BookKey key, Player clientPlayer) { }
    }

    @Test
    void aConfigReloadCannotDeleteAForeignNamespacesAdapters() {
        // Third-party and datapack systemIds are unrestricted: somemod:config/guides and
        // somemod:heuristic are legal. IMC runs once per launch, so deleting one on a config reload
        // would lose it for the rest of the session with no way to get it back.
        AdapterRegistry reg = AdapterRegistry.get();
        ResourceLocation foreignConfig = new ResourceLocation("somemod", "config/guides");
        ResourceLocation foreignHeuristic = new ResourceLocation("somemod", AdapterRegistry.HEURISTIC_PATH);
        reg.registerAdapter(new InertAdapter(foreignConfig));
        reg.registerAdapter(new InertAdapter(foreignHeuristic));

        reg.setConfigAdapters(List.of());

        assertTrue(reg.adapterFor(foreignConfig).isPresent());
        assertTrue(reg.adapterFor(foreignHeuristic).isPresent());
    }

    @Test
    void aConfigReloadDoesNotFlipPrecedenceWithinAPriorityTier() {
        // Equal priorities are broken by registration order. If a reload re-inserted the config
        // adapter at the tail it would drop behind an IMC adapter registered after common setup,
        // and a contested item would start identifying as a different BookKey — absorbing it a
        // second time as a duplicate library entry.
        AdapterRegistry reg = AdapterRegistry.get();
        ResourceLocation configAdapter = new ResourceLocation("runictome", "config/contested");
        ResourceLocation imcAdapter = new ResourceLocation("somemod", "contested_imc");

        reg.setConfigAdapters(List.of(new AlwaysMatchAdapter(configAdapter, 100)));
        reg.registerAdapter(new AlwaysMatchAdapter(imcAdapter, 100));

        assertEquals(configAdapter, reg.identify(new ItemStack(Items.PAPER)).orElseThrow().systemId());

        // Config file touched -> adapters rebuilt.
        reg.setConfigAdapters(List.of(new AlwaysMatchAdapter(configAdapter, 100)));

        assertEquals(configAdapter, reg.identify(new ItemStack(Items.PAPER)).orElseThrow().systemId(),
                "precedence within a priority tier must survive a config reload");
    }

    @Test
    void unregisterAdapterRemovesExactlyOneAdapter() {
        AdapterRegistry reg = AdapterRegistry.get();
        ResourceLocation id = new ResourceLocation("runictome", "config/first");
        reg.registerAdapter(new InertAdapter(id));
        assertTrue(reg.unregisterAdapter(id));
        assertFalse(reg.adapterFor(id).isPresent());
        assertFalse(reg.unregisterAdapter(id), "removing an absent adapter reports false");
    }

    @Test
    void setConfigAdaptersLeavesUnrelatedAdaptersAlone() {
        AdapterRegistry reg = AdapterRegistry.get();
        ResourceLocation builtIn = new ResourceLocation("runictome", "tinkers/materials_and_you");
        ResourceLocation datapack = new ResourceLocation("runictome", "datapack/pack/manual");
        reg.registerAdapter(new InertAdapter(builtIn));
        reg.registerAdapter(new InertAdapter(datapack));

        reg.setConfigAdapters(List.of());

        assertTrue(reg.adapterFor(builtIn).isPresent(),
                "a config reload must not drop built-in, IMC or third-party adapters");
        assertTrue(reg.adapterFor(datapack).isPresent(),
                "a config reload must not drop datapack-defined adapters");
    }
}

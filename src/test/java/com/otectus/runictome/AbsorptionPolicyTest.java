package com.otectus.runictome;

import com.otectus.runictome.impl.AbsorptionPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbsorptionPolicyTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreDefaults() {
        AbsorptionPolicy.rebuild(RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_ITEMS,
                RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_MODS);
    }

    @Test
    void theRunicTomeIsGloballyExcludedFromAbsorption() {
        // The tome must never be absorbable by any adapter at any priority: absorbing the library
        // itself would destroy it, and a craft producing one would have its output eaten. The
        // heuristic already skips this mod's namespace, but the tagged and extraBookItemIds
        // adapters do not, so the guarantee lives in the always-unioned default exclusion list.
        AbsorptionPolicy.rebuild(List.of(), List.of());
        assertTrue(AbsorptionPolicy.isExcludedId(new ResourceLocation("runictome", "runic_tome")),
                "the built-in defaults must survive a config that omits them");
    }

    @Test
    void explicitActionIgnoresTheExtractionMarkerOnly() {
        // Crafting a book into the tome is deliberate, so an extraction marker -- which exists to
        // stop the ambient sweep -- must not block it. Every other exclusion still applies.
        AbsorptionPolicy.rebuild(List.of("minecraft:book"), List.of());

        ItemStack extracted = new ItemStack(Items.PAPER);
        AbsorptionPolicy.markExtracted(extracted);
        assertTrue(AbsorptionPolicy.isExcluded(extracted), "the sweep must still refuse it");
        assertFalse(AbsorptionPolicy.isExcludedForExplicitAction(extracted),
                "a deliberate craft may re-file an extracted book");

        ItemStack blocklisted = new ItemStack(Items.BOOK);
        AbsorptionPolicy.markExtracted(blocklisted);
        assertTrue(AbsorptionPolicy.isExcludedForExplicitAction(blocklisted),
                "a globally excluded item stays excluded however deliberately it is offered");
    }

    @Test
    void itemExclusionOverridesAdapters() {
        AbsorptionPolicy.rebuild(List.of("minecraft:book"), List.of());
        var decision = AbsorptionPolicy.evaluate(new ItemStack(Items.BOOK));
        assertTrue(decision.excluded());
        assertEquals(AbsorptionPolicy.Reason.GLOBAL_ITEM, decision.reason());
    }

    @Test
    void namespaceExclusionMatchesEveryItemInNamespace() {
        AbsorptionPolicy.rebuild(List.of(), List.of("minecraft"));
        assertTrue(AbsorptionPolicy.isExcluded(new ItemStack(Items.PAPER)));
        assertTrue(AbsorptionPolicy.isExcludedId(new ResourceLocation("minecraft", "written_book")));
    }

    @Test
    void unrelatedItemIsAllowed() {
        AbsorptionPolicy.rebuild(List.of("minecraft:book"), List.of("examplemod"));
        var decision = AbsorptionPolicy.evaluate(new ItemStack(Items.PAPER));
        assertFalse(decision.excluded());
        assertTrue(decision.allowed());
    }

    @Test
    void extractedPhysicalCopyIsNeverReabsorbed() {
        ItemStack stack = new ItemStack(Items.BOOK);
        AbsorptionPolicy.markExtracted(stack);

        var decision = AbsorptionPolicy.evaluate(stack);
        assertTrue(decision.excluded());
        assertEquals(AbsorptionPolicy.Reason.EXTRACTED, decision.reason());
    }

    @Test
    void midnightApocalypseFunctionalBooksAreBuiltInExclusions() {
        AbsorptionPolicy.rebuild(List.of(), List.of());

        for (String raw : RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_ITEMS) {
            assertTrue(AbsorptionPolicy.isExcludedId(new ResourceLocation(raw)), raw);
        }
    }
}

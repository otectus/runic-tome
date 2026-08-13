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

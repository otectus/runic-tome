package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.integration.HeuristicBookAdapter;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the extracted {@link HeuristicBookAdapter#classify} so modded ids can be fabricated without
 * registering items (the unit env only has vanilla items) or loading tag bindings.
 */
class HeuristicBookAdapterTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static HeuristicBookAdapter adapter() {
        return new HeuristicBookAdapter(
                new ResourceLocation("runictome", "heuristic"),
                List.of("book", "tome", "guide", "codex"),
                Set.of(new ResourceLocation("examplemod", "blocked_tome")),
                Set.of("scriptor"));
    }

    /** A non-empty, non-keyword stack so stack-based functional checks are exercisable. */
    private static ItemStack plainStack() {
        return new ItemStack(Items.PAPER);
    }

    @Test
    void blocklistedNamespaceIsRejected() {
        Optional<BookKey> result = adapter().classify(
                new ResourceLocation("scriptor", "ancient_tome"), plainStack());
        assertTrue(result.isEmpty(), "Scriptor namespace should be blocked");
    }

    @Test
    void unknownGuideBookIsClassified() {
        ResourceLocation id = new ResourceLocation("examplemod", "field_guide");
        Optional<BookKey> result = adapter().classify(id, plainStack());
        assertTrue(result.isPresent(), "A keyword item from an unblocked namespace should classify");
        assertEquals(new ResourceLocation("runictome", "heuristic"), result.get().systemId());
        assertEquals(id, result.get().bookId());
    }

    @Test
    void functionalKeywordItemIsRejected() {
        // Path contains both a keyword ("tome") and a functional marker ("spell").
        Optional<BookKey> result = adapter().classify(
                new ResourceLocation("examplemod", "spell_tome"), plainStack());
        assertTrue(result.isEmpty(), "looksFunctional should reject a spell tome before the keyword loop");
    }

    @Test
    void itemBlocklistIsRejected() {
        Optional<BookKey> result = adapter().classify(
                new ResourceLocation("examplemod", "blocked_tome"), plainStack());
        assertTrue(result.isEmpty(), "An item-blocklisted id should be rejected");
    }

    @Test
    void enchantedKeywordItemIsRejected() {
        ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
        enchanted.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 1);
        Optional<BookKey> result = adapter().classify(
                new ResourceLocation("examplemod", "guide"), enchanted);
        assertTrue(result.isEmpty(), "An enchanted (functional) stack should be rejected even with a keyword id");
    }
}

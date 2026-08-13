package com.otectus.runictome;

import com.otectus.runictome.impl.ItemRefs;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for RT-02.
 *
 * <p>{@code ForgeRegistries.ITEMS} is a <em>defaulted</em> registry, so {@code getValue} on an
 * unregistered id returns {@code minecraft:air} instead of {@code null}. Nine {@code item == null}
 * guards across the mod were therefore dead code. {@link ItemRefs} restores the intended semantics;
 * {@link #forgeRegistryReturnsAirNotNullForUnknownIds()} pins the platform behaviour those guards
 * were written against so the regression cannot silently return.
 */
class ItemRefsTest {

    private static final ResourceLocation MISSING =
            new ResourceLocation("nonexistentmod", "nonexistent_book");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void forgeRegistryReturnsAirNotNullForUnknownIds() {
        assertNotNull(ForgeRegistries.ITEMS.getValue(MISSING),
                "Forge's item registry is defaulted; getValue must never be null-checked");
        assertSame(Items.AIR, ForgeRegistries.ITEMS.getValue(MISSING));
        assertFalse(ForgeRegistries.ITEMS.containsKey(MISSING));
    }

    @Test
    void resolveReturnsNullForUnregisteredId() {
        assertNull(ItemRefs.resolve(MISSING));
        assertNull(ItemRefs.resolve(null));
        assertFalse(ItemRefs.exists(MISSING));
    }

    @Test
    void resolveReturnsTheItemForARegisteredId() {
        assertSame(Items.BOOK, ItemRefs.resolve(new ResourceLocation("minecraft", "book")));
        assertTrue(ItemRefs.exists(new ResourceLocation("minecraft", "book")));
    }

    @Test
    void airIsStillResolvableWhenExplicitlyRequested() {
        // minecraft:air *is* registered, so asking for it by name must still work — the fix must
        // not turn a legitimate air lookup into "unregistered".
        assertSame(Items.AIR, ItemRefs.resolve(new ResourceLocation("minecraft", "air")));
        assertTrue(ItemRefs.exists(new ResourceLocation("minecraft", "air")));
    }

    @Test
    void stackOfNeverProducesAnAirStackForAnUnknownId() {
        ItemStack stack = ItemRefs.stackOf(MISSING);
        assertTrue(stack.isEmpty());
        assertSame(ItemStack.EMPTY, stack);

        ItemStack real = ItemRefs.stackOf(new ResourceLocation("minecraft", "book"));
        assertEquals(Items.BOOK, real.getItem());
        assertEquals(1, real.getCount());
    }

    @Test
    void openerForPrefersTheRetainedStackAndFailsClosedOtherwise() {
        ItemStack retained = new ItemStack(Items.WRITTEN_BOOK);
        retained.getOrCreateTag().putString("example:book_id", "chapter_two");

        assertSame(retained, ItemRefs.openerFor(MISSING, retained),
                "a surviving retained stack must be replayed even when the id is unknown");
        assertTrue(ItemRefs.openerFor(MISSING, ItemStack.EMPTY).isEmpty(),
                "no retained stack and no registered item must yield EMPTY so callers can fail closed");
        assertTrue(ItemRefs.openerFor(MISSING, null).isEmpty());
        assertEquals(Items.BOOK,
                ItemRefs.openerFor(new ResourceLocation("minecraft", "book"), ItemStack.EMPTY).getItem());
    }
}

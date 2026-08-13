package com.otectus.runictome;

import com.otectus.runictome.impl.ItemRefs;
import com.otectus.runictome.impl.UseSimulator;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for RT-03: the use simulator must never touch {@link ItemStack#EMPTY}.
 *
 * <p>The original code did {@code stack.copy().setCount(1).getOrCreateTag().putBoolean(VIRTUAL, true)}
 * on whatever it was handed. Because {@code ItemStack.copy()} returns the {@code EMPTY}
 * <em>singleton</em> for an empty stack, an empty opener installed the virtual marker into global,
 * process-wide state: every mod calling {@code ItemStack.EMPTY.getTag()} then saw
 * {@code {"runictome:virtual":1b}} instead of {@code null}.
 *
 * <p>These tests pin both halves: the JVM-level hazard that makes the guard necessary, and the guard
 * itself.
 */
class UseSimulatorSafetyTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void theSharedSingletonStartsClean() {
        // If this fails, something earlier in the JVM polluted ItemStack.EMPTY — the assertions
        // below would then blame the wrong code.
        assertNull(ItemStack.EMPTY.getTag(),
                "ItemStack.EMPTY was already polluted before this test class ran");
    }

    @Test
    void copyingAnEmptyStackReturnsTheSharedSingleton() {
        // This is the hazard the guard exists for. If a future Minecraft/Forge version changes it,
        // this test fails and the rationale in UseSimulator should be revisited rather than trusted.
        assertSame(ItemStack.EMPTY, ItemStack.EMPTY.copy());
        assertSame(ItemStack.EMPTY, new ItemStack(Items.AIR).copy());
    }

    @Test
    void emptyStacksAreRejectedBeforeAnyMutation() {
        assertTrue(UseSimulator.isUnusable(null));
        assertTrue(UseSimulator.isUnusable(ItemStack.EMPTY));
        assertTrue(UseSimulator.isUnusable(new ItemStack(Items.AIR)),
                "an air stack is empty and must be rejected like EMPTY itself");
        assertFalse(UseSimulator.isUnusable(new ItemStack(Items.BOOK)));
    }

    @Test
    void adapterOpenerResolutionCannotProduceASimulatableEmptyStack() {
        // The path that used to reach the simulator with EMPTY: an adapter whose bookId resolves to
        // no registered item and whose retained stack did not survive (a legacy key-only entry).
        ItemStack opener = ItemRefs.openerFor(
                new ResourceLocation("removedmod", "removed_guide"), ItemStack.EMPTY);
        assertTrue(opener.isEmpty());
        assertTrue(UseSimulator.isUnusable(opener),
                "adapters must hand the simulator an empty stack only through the rejected path");
    }

    @Test
    void isVirtualIsSafeOnEmptyStacksAndTheGlobalSingletonStaysClean() {
        assertFalse(UseSimulator.isVirtual(ItemStack.EMPTY));
        assertFalse(UseSimulator.isVirtual(new ItemStack(Items.AIR)));
        assertFalse(UseSimulator.isVirtual(null));
        assertNull(ItemStack.EMPTY.getTag(),
                "ItemStack.EMPTY must never acquire NBT — it is shared with every other mod");
    }

    @Test
    void virtualMarkerIsDetectedOnARealStack() {
        ItemStack real = new ItemStack(Items.BOOK);
        real.getOrCreateTag().putBoolean(UseSimulator.VIRTUAL_TAG, true);
        assertTrue(UseSimulator.isVirtual(real));
        assertNull(ItemStack.EMPTY.getTag());
    }
}

package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.capability.RunicTomeData;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The absorption pause flag is player state, so it has to survive everything player state survives:
 * saving, death, and a dimension change. Each of those goes through a different method on
 * {@link RunicTomeData}, and missing any one of them makes the setting silently forget itself.
 */
class AbsorptionPauseTest {

    private static final BookKey KEY = new BookKey(
            new ResourceLocation("runictome", "test"),
            new ResourceLocation("minecraft", "written_book"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void absorptionIsOnByDefault() {
        assertFalse(new RunicTomeData().isAbsorptionPaused(),
                "a new player must have the tome working, not paused");
    }

    @Test
    void theFlagSurvivesAnNbtRoundTrip() {
        RunicTomeData data = new RunicTomeData();
        data.setAbsorptionPaused(true);
        data.unlockBook(KEY, new ItemStack(Items.WRITTEN_BOOK));

        RunicTomeData loaded = new RunicTomeData();
        loaded.deserializeNBT(data.serializeNBT());

        assertTrue(loaded.isAbsorptionPaused(), "the setting must persist across a save and reload");
        assertTrue(loaded.hasBook(KEY), "and must not disturb the rest of the payload");
    }

    @Test
    void aSaveWrittenBeforeThisFieldExistedLoadsUnpaused() {
        // Forge does not migrate NBT, so an upgraded world simply has no such key. getBoolean
        // answers false for an absent key, which is the intended default -- assert it rather than
        // assume it, since a change to how the flag is read would break every existing save.
        RunicTomeData legacy = new RunicTomeData();
        legacy.setAbsorptionPaused(true);
        legacy.unlockBook(KEY, new ItemStack(Items.WRITTEN_BOOK));
        CompoundTag tag = legacy.serializeNBT();
        tag.remove("absorptionPaused");

        RunicTomeData loaded = new RunicTomeData();
        loaded.deserializeNBT(tag);

        assertFalse(loaded.isAbsorptionPaused());
        assertTrue(loaded.hasBook(KEY));
    }

    @Test
    void theFlagIsCarriedAcrossDeathAndDimensionChange() {
        // PlayerEvent.Clone routes through copyFrom for both, so this one method covers both cases.
        RunicTomeData before = new RunicTomeData();
        before.setAbsorptionPaused(true);

        RunicTomeData after = new RunicTomeData();
        after.copyFrom(before);

        assertTrue(after.isAbsorptionPaused(),
                "dying must not silently re-enable absorption on a player who turned it off");
    }

    @Test
    void clearResetsTheFlag() {
        // The client cache calls clear() on leaving a world; a stale paused flag would otherwise
        // show the wrong button state on the next world joined.
        RunicTomeData data = new RunicTomeData();
        data.setAbsorptionPaused(true);

        data.clear();

        assertFalse(data.isAbsorptionPaused());
    }
}

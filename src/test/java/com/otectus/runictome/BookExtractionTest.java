package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.capability.RunicTomeData;
import com.otectus.runictome.impl.AbsorptionPolicy;
import com.otectus.runictome.impl.BookExtraction;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookExtractionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void materializeReturnsOneDefensiveCopyWithOriginalData() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = new BookKey(new ResourceLocation("runictome", "test"),
                new ResourceLocation("minecraft", "written_book"));
        ItemStack source = new ItemStack(Items.WRITTEN_BOOK, 5);
        source.getOrCreateTag().putInt("stored_xp", 345);
        data.unlockBook(key, source);

        ItemStack extracted = BookExtraction.materialize(data, key);
        AbsorptionPolicy.markExtracted(extracted);

        assertEquals(1, extracted.getCount());
        assertEquals(345, extracted.getTag().getInt("stored_xp"));
        assertTrue(AbsorptionPolicy.isExtracted(extracted));
        assertFalse(AbsorptionPolicy.isExtracted(data.getBookStack(key)),
                "marking the returned copy must not mutate retained capability data");
    }
}

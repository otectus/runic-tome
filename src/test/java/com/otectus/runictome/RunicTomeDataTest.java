package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.capability.RunicTomeData;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunicTomeDataTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BookKey patchouli(String mod, String book) {
        return new BookKey(
                new ResourceLocation("runictome", "patchouli"),
                new ResourceLocation(mod, book));
    }

    @Test
    void unlockIsIdempotent() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = patchouli("botania", "lexicon");
        assertTrue(data.unlockBook(key));
        assertFalse(data.unlockBook(key));
        assertTrue(data.hasBook(key));
    }

    @Test
    void lockRemoves() {
        RunicTomeData data = new RunicTomeData();
        BookKey key = patchouli("ars_nouveau", "notebook");
        data.unlockBook(key);
        assertTrue(data.lockBook(key));
        assertFalse(data.hasBook(key));
        assertFalse(data.lockBook(key));
    }

    @Test
    void serializationRoundTripPreservesOrder() {
        RunicTomeData data = new RunicTomeData();
        List<BookKey> expected = List.of(
                patchouli("botania", "lexicon"),
                patchouli("ars_nouveau", "notebook"),
                patchouli("create", "encyclopedia"));
        expected.forEach(data::unlockBook);
        data.setReceivedTome(true);

        CompoundTag tag = data.serializeNBT();

        RunicTomeData copy = new RunicTomeData();
        copy.deserializeNBT(tag);

        assertTrue(copy.hasReceivedTome());
        assertIterableEquals(expected, new ArrayList<>(copy.getBooks()));
    }

    @Test
    void deserializeSkipsCorruptEntriesAndKeepsValidOnes() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        // valid
        list.add(patchouli("botania", "lexicon").toNbt());
        // corrupt: unparseable ResourceLocation strings
        CompoundTag bad = new CompoundTag();
        bad.putString("system", "NOT VALID");
        bad.putString("book", "still bad");
        list.add(bad);
        // valid
        list.add(patchouli("create", "encyclopedia").toNbt());
        tag.put("books", list);

        RunicTomeData data = new RunicTomeData();
        data.deserializeNBT(tag); // must not throw

        assertEquals(2, data.getBooks().size());
        assertTrue(data.hasBook(patchouli("botania", "lexicon")));
        assertTrue(data.hasBook(patchouli("create", "encyclopedia")));
    }

    @Test
    void deserializeCollapsesDuplicateEntries() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        list.add(patchouli("botania", "lexicon").toNbt());
        list.add(patchouli("botania", "lexicon").toNbt());
        tag.put("books", list);

        RunicTomeData data = new RunicTomeData();
        data.deserializeNBT(tag);

        assertEquals(1, data.getBooks().size());
    }

    @Test
    void copyFromReplacesState() {
        RunicTomeData a = new RunicTomeData();
        a.unlockBook(patchouli("botania", "lexicon"));
        a.setReceivedTome(true);

        RunicTomeData b = new RunicTomeData();
        b.unlockBook(patchouli("create", "encyclopedia"));
        b.copyFrom(a);

        assertEquals(1, b.getBooks().size());
        assertTrue(b.hasBook(patchouli("botania", "lexicon")));
        assertFalse(b.hasBook(patchouli("create", "encyclopedia")));
        assertTrue(b.hasReceivedTome());
    }
}

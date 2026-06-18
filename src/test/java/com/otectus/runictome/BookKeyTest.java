package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookKeyTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nbtRoundTripPreservesIdentity() {
        BookKey original = new BookKey(
                new ResourceLocation("runictome", "patchouli"),
                new ResourceLocation("botania", "lexicon"));
        CompoundTag tag = original.toNbt();
        BookKey parsed = BookKey.fromNbt(tag).orElseThrow();
        assertEquals(original, parsed);
        assertEquals(original.hashCode(), parsed.hashCode());
    }

    @Test
    void malformedNbtReturnsEmptyInsteadOfThrowing() {
        CompoundTag tag = new CompoundTag();
        tag.putString("system", "Not A Valid ID!!");
        tag.putString("book", "also bad");
        assertEquals(Optional.empty(), BookKey.fromNbt(tag));
    }

    @Test
    void missingNbtKeysReturnEmpty() {
        // tryParse("") yields null -> empty, so an absent/blank field can't crash data loading.
        assertEquals(Optional.empty(), BookKey.fromNbt(new CompoundTag()));
    }

    @Test
    void wellFormedNbtParses() {
        CompoundTag tag = new CompoundTag();
        tag.putString("system", "runictome:patchouli");
        tag.putString("book", "botania:lexicon");
        assertTrue(BookKey.fromNbt(tag).isPresent());
    }

    @Test
    void equalsDistinguishesSystems() {
        BookKey a = new BookKey(
                new ResourceLocation("runictome", "patchouli"),
                new ResourceLocation("mod", "book"));
        BookKey b = new BookKey(
                new ResourceLocation("runictome", "custom"),
                new ResourceLocation("mod", "book"));
        assertNotEquals(a, b);
    }

    @Test
    void toStringIsStable() {
        BookKey key = new BookKey(
                new ResourceLocation("runictome", "patchouli"),
                new ResourceLocation("ars_nouveau", "worn_notebook"));
        assertEquals("runictome:patchouli::ars_nouveau:worn_notebook", key.toString());
    }

    @Test
    void nullArgumentsRejected() {
        assertThrows(NullPointerException.class,
                () -> new BookKey(null, new ResourceLocation("x", "y")));
        assertThrows(NullPointerException.class,
                () -> new BookKey(new ResourceLocation("x", "y"), null));
    }
}

package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.impl.AbsorptionPolicy;
import com.otectus.runictome.integration.VanillaBookAdapter;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla book family shares five item ids across an unbounded number of distinct books, so the
 * key has to carry an NBT digest. These tests pin the two properties that matter: distinct books get
 * distinct keys, and the same book always gets the <em>same</em> key. An unstable digest would
 * orphan a stored entry and mint a duplicate the next time the book was crafted in.
 */
class VanillaBookKeyTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack writtenBook(String title, String author, String... pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("title", title);
        tag.putString("author", author);
        ListTag list = new ListTag();
        for (String page : pages) {
            list.add(StringTag.valueOf("{\"text\":\"" + page + "\"}"));
        }
        tag.put("pages", list);
        return stack;
    }

    private static BookKey keyOf(ItemStack stack) {
        Optional<BookKey> key = VanillaBookAdapter.keyFor(stack);
        assertTrue(key.isPresent(), "expected a vanilla book key for " + stack);
        return key.get();
    }

    @Test
    void plainBookGetsABareStableKey() {
        BookKey key = keyOf(new ItemStack(Items.BOOK));
        assertEquals(VanillaBookAdapter.SYSTEM_ID, key.systemId());
        assertEquals(new ResourceLocation("minecraft", "book"), key.bookId());
    }

    @Test
    void writtenBooksWithDifferentTitlesGetDifferentKeys() {
        assertNotEquals(keyOf(writtenBook("Volume I", "Ada", "one")).bookId(),
                keyOf(writtenBook("Volume II", "Ada", "one")).bookId());
    }

    @Test
    void writtenBooksWithDifferentContentGetDifferentKeys() {
        assertNotEquals(keyOf(writtenBook("Notes", "Ada", "first")).bookId(),
                keyOf(writtenBook("Notes", "Ada", "second")).bookId());
    }

    @Test
    void identicalBooksGetIdenticalKeys() {
        assertEquals(keyOf(writtenBook("Notes", "Ada", "a", "b")).bookId(),
                keyOf(writtenBook("Notes", "Ada", "a", "b")).bookId());
    }

    @Test
    void keyIsIndependentOfTagInsertionOrder() {
        // The digest is built from a key-sorted rendering rather than the tag's own map ordering,
        // so a book rebuilt in a different order still resolves to its existing library entry.
        ItemStack forwards = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag a = forwards.getOrCreateTag();
        a.putString("author", "Ada");
        a.putString("title", "Notes");
        a.putInt("generation", 0);

        ItemStack backwards = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag b = backwards.getOrCreateTag();
        b.putInt("generation", 0);
        b.putString("title", "Notes");
        b.putString("author", "Ada");

        assertEquals(keyOf(forwards).bookId(), keyOf(backwards).bookId());
    }

    @Test
    void extractionMarkerDoesNotChangeTheKey() {
        // A book extracted from the tome and later crafted back in must land on the entry it came
        // from, not create a second one.
        ItemStack plain = writtenBook("Notes", "Ada", "a");
        ItemStack marked = writtenBook("Notes", "Ada", "a");
        AbsorptionPolicy.markExtracted(marked);
        assertTrue(AbsorptionPolicy.isExtracted(marked));
        assertEquals(keyOf(plain).bookId(), keyOf(marked).bookId());
    }

    @Test
    void enchantedBooksWithDifferentEnchantmentsGetDifferentKeys() {
        ItemStack mending = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(Map.of(Enchantments.MENDING, 1), mending);
        ItemStack sharpness = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(Map.of(Enchantments.SHARPNESS, 5), sharpness);

        assertNotEquals(keyOf(mending).bookId(), keyOf(sharpness).bookId());
    }

    @Test
    void enchantedBooksWithDifferentLevelsGetDifferentKeys() {
        ItemStack one = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(Map.of(Enchantments.SHARPNESS, 1), one);
        ItemStack five = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(Map.of(Enchantments.SHARPNESS, 5), five);

        assertNotEquals(keyOf(one).bookId(), keyOf(five).bookId());
    }

    @Test
    void everyGeneratedBookIdIsAValidResourceLocation() {
        // The digest is appended to the item path, so it must stay inside the ResourceLocation
        // charset or BookKey.fromNbt would silently drop the entry on load.
        for (ItemStack stack : new ItemStack[] {
                new ItemStack(Items.BOOK),
                new ItemStack(Items.WRITABLE_BOOK),
                writtenBook("A Very Long Title: With Punctuation!", "Ada", "page"),
                new ItemStack(Items.KNOWLEDGE_BOOK),
        }) {
            ResourceLocation id = keyOf(stack).bookId();
            assertNotNull(ResourceLocation.tryParse(id.toString()), "invalid id " + id);
        }
    }

    @Test
    void baseItemIdStripsTheDigest() {
        BookKey key = keyOf(writtenBook("Notes", "Ada", "a"));
        assertEquals(new ResourceLocation("minecraft", "written_book"), VanillaBookAdapter.baseItemId(key));
        // And is a no-op for a key that carries no digest.
        assertEquals(new ResourceLocation("minecraft", "book"),
                VanillaBookAdapter.baseItemId(keyOf(new ItemStack(Items.BOOK))));
    }

    @Test
    void nonBookItemsAreNotClaimed() {
        assertTrue(VanillaBookAdapter.keyFor(new ItemStack(Items.STICK)).isEmpty());
        assertTrue(VanillaBookAdapter.keyFor(ItemStack.EMPTY).isEmpty());
        assertTrue(VanillaBookAdapter.keyFor(null).isEmpty());
    }

    @Test
    void theAdapterNeverClaimsAStackAmbiently() {
        // identify() is what pickup, the sweep and the smelt handler call. It must stay empty for
        // every vanilla book, or a Mending book on the ground would be absorbed.
        VanillaBookAdapter adapter = new VanillaBookAdapter();
        assertTrue(adapter.identify(new ItemStack(Items.ENCHANTED_BOOK)).isEmpty());
        assertTrue(adapter.identify(writtenBook("Notes", "Ada", "a")).isEmpty());
        assertTrue(adapter.identify(new ItemStack(Items.BOOK)).isEmpty());
    }

    @Test
    void displayNamePrefersTheRetainedStack() {
        ItemStack stack = writtenBook("The Wandering Trader", "Ada", "a");
        BookKey key = keyOf(stack);
        assertEquals(stack.getHoverName().getString(),
                new VanillaBookAdapter().displayName(key, stack).getString());
    }
}

package com.otectus.runictome.integration;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.NameFormat;
import com.otectus.runictome.impl.AbsorptionPolicy;
import com.otectus.runictome.impl.ItemRefs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Display, open and extraction support for the vanilla book family.
 *
 * <p><b>This adapter deliberately never claims a stack.</b> {@link #identify(ItemStack)} always
 * returns empty, so the adapter is invisible to {@link com.otectus.runictome.api.RunicTomeAPI#identify}
 * and cannot change what item pickup, the inventory sweep or the smelt handler absorb. Vanilla books
 * are functional items — an enchanted book is currency, a written book is somebody's writing — and
 * ambiently eating one off the ground would be indefensible. They enter the library only through the
 * deliberate crafting recipe, which calls {@link #keyFor(ItemStack)} directly.
 *
 * <p>The adapter still registers normally so that
 * {@link com.otectus.runictome.api.RunicTomeAPI#adapterFor} resolves {@link #SYSTEM_ID} for the tome
 * GUI row, the open action, and {@link com.otectus.runictome.impl.BookExtraction}.
 *
 * <h2>Why the key carries a hash</h2>
 * Every written book is {@code minecraft:written_book} and every enchanted book is
 * {@code minecraft:enchanted_book}. A {@link BookKey} built from the item id alone would collapse a
 * player's entire collection into one entry. The key therefore appends an eight-hex-character digest
 * of the stack's NBT, giving {@code minecraft:written_book/3f2a91c8}.
 *
 * <p>That digest must be stable across sessions — an unstable one would orphan the stored entry and
 * silently create a duplicate the next time the same book was crafted in — so the NBT is rendered
 * through {@link #canonical} with compound keys sorted, rather than relying on the hash-map ordering
 * behind a plain {@code CompoundTag} toString.
 */
public final class VanillaBookAdapter implements GuideSystemAdapter {

    public static final ResourceLocation SYSTEM_ID = new ResourceLocation(RunicTome.MOD_ID, "vanilla");

    /** The closed set of vanilla items this adapter speaks for. */
    private static final Set<Item> FAMILY = Set.of(
            Items.BOOK, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK, Items.ENCHANTED_BOOK, Items.KNOWLEDGE_BOOK);

    @Override
    public ResourceLocation systemId() {
        return SYSTEM_ID;
    }

    /**
     * Always empty by design — see the class javadoc. The crafting path calls
     * {@link #keyFor(ItemStack)} instead.
     */
    @Override
    public Optional<BookKey> identify(ItemStack stack) {
        return Optional.empty();
    }

    /**
     * Key for a vanilla book, used only by the crafting path. Pure and statically testable: it reads
     * no config and touches nothing but the stack.
     *
     * @return empty when {@code stack} is not one of the five vanilla book items.
     */
    public static Optional<BookKey> keyFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        if (!FAMILY.contains(stack.getItem())) return Optional.empty();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return Optional.empty();
        String discriminator = discriminatorFor(stack);
        String path = discriminator.isEmpty() ? id.getPath() : id.getPath() + "/" + discriminator;
        ResourceLocation bookId = ResourceLocation.tryBuild(id.getNamespace(), path);
        if (bookId == null) return Optional.empty();
        return Optional.of(new BookKey(SYSTEM_ID, bookId));
    }

    /** True for a key this adapter owns. */
    public static boolean isVanillaKey(BookKey key) {
        return key != null && SYSTEM_ID.equals(key.systemId());
    }

    /**
     * The plain item id behind a key, with the NBT discriminator stripped:
     * {@code minecraft:written_book/3f2a91c8} becomes {@code minecraft:written_book}.
     */
    public static ResourceLocation baseItemId(BookKey key) {
        ResourceLocation id = key.bookId();
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        if (slash > 0) path = path.substring(0, slash);
        return ResourceLocation.tryBuild(id.getNamespace(), path);
    }

    /**
     * The NBT digest for a stack, or an empty string when the stack carries nothing that
     * distinguishes it (a blank {@code minecraft:book}).
     *
     * <p>The extraction marker is removed first: it is bookkeeping this mod wrote, not part of the
     * book's identity, so a book extracted and later crafted back in hashes to the key it had going
     * out rather than becoming a second entry.
     */
    private static String discriminatorFor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) return "";
        CompoundTag copy = tag.copy();
        copy.remove(AbsorptionPolicy.EXTRACTED_MARKER);
        if (copy.isEmpty()) return "";
        return hash8(canonical(copy));
    }

    /**
     * Order-independent rendering of a tag. Compound keys are sorted so the same book always
     * produces the same string; list order is preserved because it is semantically meaningful
     * (book pages, stored enchantments) and because vanilla's own stack identity honours it.
     */
    static String canonical(Tag tag) {
        if (tag == null) return "";
        if (tag instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (String key : keys) {
                if (!first) sb.append(',');
                first = false;
                sb.append(key).append(':').append(canonical(compound.get(key)));
            }
            return sb.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(canonical(list.get(i)));
            }
            return sb.append(']').toString();
        }
        return tag.toString();
    }

    /** First four bytes of the SHA-256 of {@code canonical}, as lowercase hex. */
    static String hash8(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every conforming JRE. There is no fallback that would keep
            // keys stable, so fail loudly rather than mint a key that changes between sessions.
            throw new IllegalStateException("SHA-256 is unavailable; cannot derive a stable book key", e);
        }
    }

    @Override
    public void open(BookKey key, Player clientPlayer) {
        open(key, clientPlayer, ItemStack.EMPTY);
    }

    @Override
    public void open(BookKey key, Player clientPlayer, ItemStack sourceStack) {
        if (!clientPlayer.level().isClientSide) return;
        ItemStack opener = ItemRefs.openerFor(baseItemId(key), sourceStack);
        // Only written books and books-and-quills have pages to show. A blank, enchanted or
        // knowledge book is a legitimate library entry with nothing to read.
        if (opener.isEmpty() || !(opener.is(Items.WRITTEN_BOOK) || opener.is(Items.WRITABLE_BOOK))) {
            clientPlayer.displayClientMessage(
                    Component.translatable("runictome.vanilla.not_readable"), false);
            return;
        }
        ItemStack toOpen = opener;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.otectus.runictome.client.ClientHooks.openVanillaBook(toOpen));
    }

    /**
     * No-op. The inherited default replays the book item's {@code use()} server-side, which would
     * try to resolve {@code minecraft:written_book/3f2a91c8} as an item id; and vanilla books open
     * entirely client-side anyway.
     */
    @Override
    public void openServer(BookKey key, ServerPlayer serverPlayer) {
        // intentionally empty
    }

    @Override
    public Component displayName(BookKey key) {
        ItemStack base = ItemRefs.stackOf(baseItemId(key));
        if (!base.isEmpty()) return base.getHoverName();
        return Component.literal(NameFormat.titleCase(key.bookId().getPath()));
    }

    /** The retained stack is what carries a written book's actual title, so prefer it. */
    @Override
    public Component displayName(BookKey key, ItemStack sourceStack) {
        if (sourceStack != null && !sourceStack.isEmpty()) return sourceStack.getHoverName();
        return displayName(key);
    }

    @Override
    public ItemStack displayIcon(BookKey key) {
        return ItemRefs.stackOf(baseItemId(key));
    }

    /** Prefer the retained stack so an enchanted book renders with its glint. */
    @Override
    public ItemStack displayIcon(BookKey key, ItemStack sourceStack) {
        if (sourceStack != null && !sourceStack.isEmpty()) return sourceStack;
        return displayIcon(key);
    }
}

package com.otectus.runictome.integration;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.impl.ItemRefs;
import com.otectus.runictome.impl.UseSimulator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Positive override adapter: any item in the {@code #runictome:guide_books} tag is treated as a
 * guide book. Runs at priority 50 — above the keyword catch-all ({@code 0}) so a packmaker can
 * force-absorb an item the heuristic rejects, but below concrete adapters ({@code 100}) so explicit
 * integrations still win. The {@code #runictome:absorb_blocklist} tag still hard-blocks it, giving
 * precedence: never-absorb &gt; positive tag &gt; heuristic.
 */
public class TaggedGuideBookAdapter implements GuideSystemAdapter {

    private final ResourceLocation systemId;

    public TaggedGuideBookAdapter(ResourceLocation systemId) {
        this.systemId = systemId;
    }

    @Override
    public ResourceLocation systemId() {
        return systemId;
    }

    @Override
    public int priority() {
        // Between concrete adapters (100) and the keyword catch-all (0).
        return 50;
    }

    @Override
    public Optional<BookKey> identify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        if (UseSimulator.isVirtual(stack)) return Optional.empty();
        Item item = stack.getItem();
        if (item instanceof BlockItem) return Optional.empty();
        if (stack.is(ModTags.Items.ABSORB_BLOCKLIST)) return Optional.empty();
        if (!stack.is(ModTags.Items.GUIDE_BOOKS)) return Optional.empty();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return Optional.empty();
        return Optional.of(new BookKey(systemId, id));
    }

    @Override
    public void open(BookKey key, Player clientPlayer) {
        open(key, clientPlayer, ItemStack.EMPTY);
    }

    @Override
    public void open(BookKey key, Player clientPlayer, ItemStack sourceStack) {
        if (!clientPlayer.level().isClientSide) return;
        ItemStack opener = ItemRefs.openerFor(key.bookId(), sourceStack);
        if (opener.isEmpty()) {
            clientPlayer.displayClientMessage(
                    Component.translatable("runictome.unknown_item", key.bookId().toString()), false);
            return;
        }
        UseSimulator.simulateClientUse(opener, clientPlayer);
    }

    @Override
    public void openServer(BookKey key, net.minecraft.server.level.ServerPlayer serverPlayer,
                           ItemStack sourceStack) {
        ItemStack opener = ItemRefs.openerFor(key.bookId(), sourceStack);
        if (opener.isEmpty()) return;
        UseSimulator.simulateServerUse(opener, serverPlayer);
    }

    @Override
    public Component displayName(BookKey key) {
        Item item = ItemRefs.resolve(key.bookId());
        return item == null ? Component.literal(key.bookId().toString()) : item.getDescription().copy();
    }

    @Override
    public ItemStack displayIcon(BookKey key) {
        return ItemRefs.stackOf(key.bookId());
    }
}

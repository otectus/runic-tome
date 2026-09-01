package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Duplicates one library entry into a physical book while <em>keeping</em> the entry.
 *
 * <p>The difference from {@link BookExtraction} is the whole point: extraction moves a book out of
 * the tome, copying leaves the tome untouched and charges vanilla books instead. Because the entry
 * survives, the copy must carry {@link AbsorptionPolicy#EXTRACTED_MARKER} or the next inventory
 * sweep -- roughly one second later -- would absorb it straight back as an already-owned duplicate
 * and destroy it. Marking is what makes a copy usable as a shelf or item-frame book.
 */
public final class BookCopy {

    private BookCopy() {}

    public enum Result {
        COPIED,
        DISABLED,
        RATE_LIMITED,
        NOT_UNLOCKED,
        NO_PHYSICAL_ITEM,
        NO_COST_ITEM,
        DELIVERY_FAILED
    }

    public static Result copy(ServerPlayer player, BookKey key) {
        if (!RunicTomeConfig.allowBookCopying()) {
            player.displayClientMessage(Component.translatable("runictome.copy.disabled"), false);
            return Result.DISABLED;
        }

        long now = player.server == null ? 0L : player.server.getTickCount();
        if (!RequestLimits.allow(RequestLimits.Kind.COPY, player, now)) {
            // Silent: a player cannot click this fast, so anything hitting the limit is a script.
            return Result.RATE_LIMITED;
        }

        IRunicTomeData data = player.getCapability(RunicTomeCapabilities.PLAYER_DATA).orElse(null);
        if (data == null || !data.hasBook(key)) {
            player.displayClientMessage(Component.translatable("runictome.copy.not_unlocked"), false);
            return Result.NOT_UNLOCKED;
        }

        ItemStack copy = prepareCopy(data, key);
        if (copy.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("runictome.copy.no_item", key.bookId().toString()), false);
            return Result.NO_PHYSICAL_ITEM;
        }

        int cost = costFor(player);
        Inventory inv = player.getInventory();
        if (countPlainBooks(inv) < cost) {
            player.displayClientMessage(Component.translatable("runictome.copy.no_book", cost), false);
            return Result.NO_COST_ITEM;
        }

        // Name it before delivering: Inventory.add empties the stack it is handed on a full
        // transfer, at which point getHoverName() would report "Air".
        Component copyName = copy.getHoverName();

        // Charge first. Taking the books can only free inventory space, never consume it, so
        // delivery below can never fail *because* of the charge -- which keeps the refund path
        // below a genuine last resort rather than a routine occurrence.
        List<ItemStack> charged = takePlainBooks(inv, cost);

        if (!ItemDelivery.deliver(player, copy)) {
            for (ItemStack refund : charged) {
                ItemDelivery.deliver(player, refund);
            }
            player.displayClientMessage(Component.translatable("runictome.copy.delivery_failed"), false);
            return Result.DELIVERY_FAILED;
        }

        // Deliberately no lockBook and no capability re-sync: the library is unchanged, so the
        // client's cached copy of it is still correct.
        inv.setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
        player.displayClientMessage(
                Component.translatable("runictome.copy.success", copyName), false);
        return Result.COPIED;
    }

    /**
     * The physical book a copy hands out: one item, full original data, marked so it is not absorbed
     * again.
     *
     * <p>Pure and player-free so the invariant that matters can be unit-tested without a server --
     * that producing a copy leaves both the library entry and its retained stack untouched. That
     * holds because {@link com.otectus.runictome.capability.RunicTomeData#getBookStack} answers with
     * a defensive copy, so {@link BookExtraction#materialize} never returns a live reference into
     * capability storage.
     */
    public static ItemStack prepareCopy(IRunicTomeData data, BookKey key) {
        ItemStack copy = BookExtraction.materialize(data, key);
        if (copy.isEmpty()) return ItemStack.EMPTY;
        copy.setCount(1);
        AbsorptionPolicy.markExtracted(copy);
        return copy;
    }

    /** Creative players are never charged; everyone else pays the configured price. */
    public static int costFor(ServerPlayer player) {
        if (player.getAbilities().instabuild) return 0;
        return Math.max(0, RunicTomeConfig.bookCopyCost());
    }

    /**
     * Plain vanilla books held in the main inventory.
     *
     * <p>"Plain" is what {@link Inventory#findSlotMatchingItem} means by a match: same item
     * <em>and</em> same tags. A renamed or otherwise NBT-bearing book is therefore not spendable
     * here, which is the desired behaviour -- a player's named keepsake should not be consumed to
     * pay for a copy.
     */
    static int countPlainBooks(Inventory inv) {
        ItemStack plain = new ItemStack(Items.BOOK);
        int total = 0;
        for (ItemStack stack : inv.items) {
            if (ItemStack.isSameItemSameTags(plain, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Removes {@code count} plain books, spanning slots when no single stack holds enough.
     *
     * @return what was actually removed, so a failed delivery can hand it straight back. Callers
     *         must check {@link #countPlainBooks} first; this takes whatever it can find.
     */
    static List<ItemStack> takePlainBooks(Inventory inv, int count) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = count;
        ItemStack plain = new ItemStack(Items.BOOK);
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            if (!ItemStack.isSameItemSameTags(plain, inv.items.get(i))) continue;
            ItemStack removed = inv.removeItem(i, remaining);
            if (removed.isEmpty()) continue;
            remaining -= removed.getCount();
            taken.add(removed);
        }
        return taken;
    }
}

package com.otectus.runictome.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GuideSystemAdapter {

    ResourceLocation systemId();

    /**
     * Relative precedence when identifying a stack: adapters are probed highest-first, so a
     * concrete adapter (default {@code 100}) always wins over the keyword catch-all
     * ({@link com.otectus.runictome.integration.HeuristicBookAdapter}, which returns {@code 0}).
     * This makes precedence explicit instead of depending on registration order.
     */
    default int priority() {
        return 100;
    }

    Optional<BookKey> identify(ItemStack stack);

    /** Called on the logical client. Implementations should open the foreign book UI. */
    void open(BookKey key, Player clientPlayer);

    /**
     * Stack-aware opening hook. The default preserves source compatibility for existing adapters;
     * generic adapters override it to replay the retained, NBT-bearing source stack.
     */
    default void open(BookKey key, Player clientPlayer, ItemStack sourceStack) {
        open(key, clientPlayer);
    }

    /**
     * Called on the logical server when the player opens a book from the tome. The default
     * replays the book item's {@code use()} server-side, which covers books that open their
     * GUI by sending their own server-to-client packet (e.g. Minecraft Comes Alive). Adapters
     * whose {@link #open} already handles everything client-side should override this to a no-op.
     */
    default void openServer(BookKey key, ServerPlayer serverPlayer) {
        // Resolve through ItemRefs: ForgeRegistries.ITEMS is a defaulted registry and returns
        // minecraft:air (not null) for an unregistered id, which would otherwise hand an empty
        // stack to the use simulator. Adapters whose bookId is not an item registry id (e.g.
        // Patchouli, whose bookId is a book id) must override this to a no-op.
        ItemStack opener = com.otectus.runictome.impl.ItemRefs.stackOf(key.bookId());
        if (opener.isEmpty()) return;
        com.otectus.runictome.impl.UseSimulator.simulateServerUse(opener, serverPlayer);
    }

    /** Server-side counterpart of the stack-aware client hook. */
    default void openServer(BookKey key, ServerPlayer serverPlayer, ItemStack sourceStack) {
        openServer(key, serverPlayer);
    }

    default boolean supportsBulkEnumeration() {
        return false;
    }

    default Collection<BookKey> enumerateAll() {
        return List.of();
    }

    default Component displayName(BookKey key) {
        return Component.literal(NameFormat.titleCase(key.bookId().getPath()));
    }

    default ItemStack displayIcon(BookKey key) {
        return ItemStack.EMPTY;
    }
}

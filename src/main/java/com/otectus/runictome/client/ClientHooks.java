package com.otectus.runictome.client;

import com.otectus.runictome.client.screen.RunicTomeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.item.ItemStack;

/**
 * Entry points called from common code — kept as a thin indirection so the
 * item class does not reference client-only types at classload time.
 */
public final class ClientHooks {

    private ClientHooks() {}

    public static void openRunicTomeScreen() {
        Minecraft.getInstance().setScreen(new RunicTomeScreen());
    }

    /**
     * Opens the vanilla book reader for a written book or book-and-quill retained in the tome.
     * {@code BookAccess.fromItem} handles both page formats and yields an empty reader for anything
     * else, so callers must already have checked the item type.
     */
    public static void openVanillaBook(ItemStack stack) {
        Minecraft.getInstance().setScreen(new BookViewScreen(BookViewScreen.BookAccess.fromItem(stack)));
    }
}

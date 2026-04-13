package com.otectus.runictome.client;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.capability.RunicTomeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Collections;

public final class ClientDataCache {

    private static final RunicTomeData DATA = new RunicTomeData();

    private ClientDataCache() {}

    public static void acceptSync(CompoundTag tag) {
        if (tag != null) {
            DATA.deserializeNBT(tag);
        }
    }

    public static void acceptUnlock(BookKey key) {
        if (DATA.unlockBook(key)) {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().addToast(SystemToast.multiline(
                    mc,
                    SystemToast.SystemToastIds.NARRATOR_TOGGLE,
                    Component.translatable("runictome.toast.unlocked"),
                    Component.literal(key.bookId().toString())));
        }
    }

    public static Collection<BookKey> getBooks() {
        return Collections.unmodifiableCollection(DATA.getBooks());
    }

    public static boolean hasBook(BookKey key) {
        return DATA.hasBook(key);
    }
}

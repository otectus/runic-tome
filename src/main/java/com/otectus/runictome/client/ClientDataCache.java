package com.otectus.runictome.client;

import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public final class ClientDataCache {

    private static final RunicTomeData DATA = new RunicTomeData();

    private ClientDataCache() {}

    public static void acceptSync(CompoundTag tag) {
        if (tag != null) {
            DATA.deserializeNBT(tag);
        }
    }

    public static void acceptUnlock(BookKey key) {
        if (!DATA.unlockBook(key)) return;
        if (!RunicTomeConfig.COMMON.showUnlockToast.get()) return;
        Minecraft mc = Minecraft.getInstance();
        Optional<GuideSystemAdapter> adapter = RunicTomeAPI.adapterFor(key.systemId());
        Component body = adapter.map(a -> a.displayName(key))
                .orElseGet(() -> Component.literal(key.bookId().toString()));
        mc.getToasts().addToast(SystemToast.multiline(
                mc,
                SystemToast.SystemToastIds.NARRATOR_TOGGLE,
                Component.translatable("runictome.toast.unlocked"),
                body));
    }

    public static Collection<BookKey> getBooks() {
        return Collections.unmodifiableCollection(DATA.getBooks());
    }

    public static boolean hasBook(BookKey key) {
        return DATA.hasBook(key);
    }
}

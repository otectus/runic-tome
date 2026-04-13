package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.ImcMethods;
import com.otectus.runictome.api.RunicTomeAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ImcHandler {

    private static final ResourceLocation STATIC_SYSTEM =
            new ResourceLocation(RunicTome.MOD_ID, "imc_static");

    private static final StaticAdapter STATIC_ADAPTER = new StaticAdapter();
    private static boolean staticRegistered = false;

    private ImcHandler() {}

    public static void processImc(InterModProcessEvent event) {
        event.getIMCStream(ImcMethods.REGISTER_ADAPTER::equals).forEach(msg -> {
            try {
                Object payload = msg.messageSupplier().get();
                if (payload instanceof GuideSystemAdapter adapter) {
                    RunicTomeAPI.registerAdapter(adapter);
                    RunicTome.LOGGER.info("IMC registered adapter {} from {}", adapter.systemId(), msg.senderModId());
                } else {
                    RunicTome.LOGGER.warn("IMC register_adapter from {} sent non-adapter payload {}",
                            msg.senderModId(), payload);
                }
            } catch (Throwable t) {
                RunicTome.LOGGER.error("IMC register_adapter from {} failed", msg.senderModId(), t);
            }
        });

        event.getIMCStream(ImcMethods.REGISTER_BOOK::equals).forEach(msg -> {
            try {
                Object payload = msg.messageSupplier().get();
                if (payload instanceof BookKey key) {
                    ensureStaticRegistered();
                    STATIC_ADAPTER.add(key, ItemStack.EMPTY);
                } else {
                    RunicTome.LOGGER.warn("IMC register_book from {} sent non-BookKey payload {}",
                            msg.senderModId(), payload);
                }
            } catch (Throwable t) {
                RunicTome.LOGGER.error("IMC register_book from {} failed", msg.senderModId(), t);
            }
        });
    }

    public static void sendSelfTest(String senderModId, BookKey key) {
        InterModComms.sendTo(senderModId, RunicTome.MOD_ID, ImcMethods.REGISTER_BOOK, () -> key);
    }

    private static void ensureStaticRegistered() {
        if (!staticRegistered) {
            RunicTomeAPI.registerAdapter(STATIC_ADAPTER);
            staticRegistered = true;
        }
    }

    private static final class StaticAdapter implements GuideSystemAdapter {
        private final ConcurrentHashMap<BookKey, ItemStack> icons = new ConcurrentHashMap<>();

        void add(BookKey key, ItemStack icon) {
            icons.put(key, icon);
        }

        @Override
        public ResourceLocation systemId() {
            return STATIC_SYSTEM;
        }

        @Override
        public Optional<BookKey> identify(ItemStack stack) {
            return Optional.empty();
        }

        @Override
        public void open(BookKey key, Player clientPlayer) {
            clientPlayer.displayClientMessage(
                    Component.translatable("runictome.static_book", key.bookId().toString()), false);
        }

        @Override
        public boolean supportsBulkEnumeration() {
            return true;
        }

        @Override
        public java.util.Collection<BookKey> enumerateAll() {
            return icons.keySet();
        }

        @Override
        public ItemStack displayIcon(BookKey key) {
            return icons.getOrDefault(key, ItemStack.EMPTY);
        }
    }
}

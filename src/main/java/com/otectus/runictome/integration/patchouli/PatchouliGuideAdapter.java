package com.otectus.runictome.integration.patchouli;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reflective Patchouli adapter. Avoids a compile-time dependency on Patchouli;
 * every call into Patchouli's API goes through Method handles cached at init.
 * If Patchouli is not installed, {@link #isAvailable()} returns false and the
 * adapter is never registered by {@link PatchouliIntegration}.
 *
 * Identifies two flavors of Patchouli books:
 *   (1) Generic {@code patchouli:guide_book} stacks with NBT {@code patchouli:book}
 *       set to the book's ResourceLocation — handled via the NBT fast-path.
 *   (2) Custom book items (Patchouli books declared with {@code "noBook": true},
 *       e.g. Botania's lexicon, Ars Nouveau's worn notebook) — handled via a
 *       lazily-built Item→BookKey map derived from
 *       {@code vazkii.patchouli.common.book.BookRegistry.INSTANCE.books}.
 *
 * The custom-item map is invalidated on datapack reload by
 * {@link PatchouliReloadHandler}.
 */
public class PatchouliGuideAdapter implements GuideSystemAdapter {

    public static final ResourceLocation SYSTEM_ID = new ResourceLocation(RunicTome.MOD_ID, "patchouli");
    public static final ResourceLocation GUIDE_BOOK_ITEM = new ResourceLocation("patchouli", "guide_book");
    public static final String NBT_BOOK_KEY = "patchouli:book";

    private static Object API_INSTANCE;
    private static Method OPEN_BOOK_GUI_CLIENT;
    private static Method OPEN_BOOK_GUI_SERVER;
    private static Method GET_BOOK_STACK;
    private static Object BOOK_REGISTRY_INSTANCE;
    private static java.lang.reflect.Field BOOK_REGISTRY_BOOKS;
    private static java.lang.reflect.Field BOOK_NAME_FIELD;
    private static Method BOOK_GET_ITEM_METHOD;
    private static boolean AVAILABLE;

    private static volatile Map<Item, BookKey> CUSTOM_ITEM_MAP;

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static void tryInit() {
        try {
            Class<?> apiCls = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Method getInstance = apiCls.getMethod("get");
            API_INSTANCE = getInstance.invoke(null);
            // IPatchouliAPI is a nested interface inside PatchouliAPI, not a top-level class.
            Class<?> ifaceCls;
            try {
                ifaceCls = Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI");
            } catch (ClassNotFoundException e) {
                ifaceCls = Class.forName("vazkii.patchouli.api.IPatchouliAPI");
            }
            try {
                OPEN_BOOK_GUI_CLIENT = ifaceCls.getMethod("openBookGUI", ResourceLocation.class);
            } catch (NoSuchMethodException ignored) {
                OPEN_BOOK_GUI_CLIENT = null;
            }
            try {
                OPEN_BOOK_GUI_SERVER = ifaceCls.getMethod("openBookGUI",
                        Class.forName("net.minecraft.server.level.ServerPlayer"),
                        ResourceLocation.class);
            } catch (NoSuchMethodException ignored) {
                OPEN_BOOK_GUI_SERVER = null;
            }
            try {
                GET_BOOK_STACK = ifaceCls.getMethod("getBookStack", ResourceLocation.class);
            } catch (NoSuchMethodException ignored) {
                GET_BOOK_STACK = null;
            }

            try {
                Class<?> bookRegistryCls = Class.forName("vazkii.patchouli.common.book.BookRegistry");
                BOOK_REGISTRY_INSTANCE = bookRegistryCls.getField("INSTANCE").get(null);
                BOOK_REGISTRY_BOOKS = bookRegistryCls.getField("books");
                Class<?> bookCls = Class.forName("vazkii.patchouli.common.book.Book");
                BOOK_NAME_FIELD = bookCls.getField("name");
                BOOK_GET_ITEM_METHOD = bookCls.getMethod("getBookItem");
            } catch (Throwable t) {
                RunicTome.LOGGER.warn("Patchouli present but BookRegistry reflection failed — custom book items and titles will use fallbacks", t);
                BOOK_REGISTRY_INSTANCE = null;
                BOOK_REGISTRY_BOOKS = null;
                BOOK_NAME_FIELD = null;
                BOOK_GET_ITEM_METHOD = null;
            }

            AVAILABLE = true;
        } catch (ClassNotFoundException cnf) {
            AVAILABLE = false;
            RunicTome.LOGGER.info("Patchouli not present — skipping integration");
        } catch (Throwable t) {
            AVAILABLE = false;
            RunicTome.LOGGER.warn("Patchouli integration init failed", t);
        }
    }

    public static void invalidateCustomItemMap() {
        CUSTOM_ITEM_MAP = null;
    }

    public static void prewarmCustomItemMap() {
        if (!AVAILABLE || BOOK_REGISTRY_INSTANCE == null || BOOK_GET_ITEM_METHOD == null) return;
        customItemMap();
    }

    @Override
    public ResourceLocation systemId() {
        return SYSTEM_ID;
    }

    @Override
    public Optional<BookKey> identify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_BOOK_KEY)) {
            try {
                ResourceLocation bookId = new ResourceLocation(tag.getString(NBT_BOOK_KEY));
                return Optional.of(new BookKey(SYSTEM_ID, bookId));
            } catch (Exception ignored) {
            }
        }

        if (!AVAILABLE || BOOK_REGISTRY_INSTANCE == null || BOOK_GET_ITEM_METHOD == null) {
            return Optional.empty();
        }

        Map<Item, BookKey> map = customItemMap();
        BookKey key = map.get(stack.getItem());
        return key == null ? Optional.empty() : Optional.of(key);
    }

    private static Map<Item, BookKey> customItemMap() {
        Map<Item, BookKey> cached = CUSTOM_ITEM_MAP;
        if (cached != null) return cached;
        synchronized (PatchouliGuideAdapter.class) {
            cached = CUSTOM_ITEM_MAP;
            if (cached != null) return cached;
            cached = buildCustomItemMap();
            CUSTOM_ITEM_MAP = cached;
            return cached;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Item, BookKey> buildCustomItemMap() {
        Map<Item, BookKey> result = new HashMap<>();
        Item genericBook = ForgeRegistries.ITEMS.getValue(GUIDE_BOOK_ITEM);
        int totalBooks = 0;
        int skippedGeneric = 0;
        int skippedError = 0;
        try {
            Map<ResourceLocation, ?> books = (Map<ResourceLocation, ?>) BOOK_REGISTRY_BOOKS.get(BOOK_REGISTRY_INSTANCE);
            if (books == null) {
                RunicTome.LOGGER.warn("Patchouli: BookRegistry.books is null when building custom-item map");
                return result;
            }
            totalBooks = books.size();
            for (Map.Entry<ResourceLocation, ?> entry : books.entrySet()) {
                ResourceLocation id = entry.getKey();
                Object bookObj = entry.getValue();
                if (bookObj == null) { skippedError++; continue; }
                try {
                    Object bookStackObj = BOOK_GET_ITEM_METHOD.invoke(bookObj);
                    if (!(bookStackObj instanceof ItemStack bookStack) || bookStack.isEmpty()) {
                        RunicTome.LOGGER.warn("Patchouli: book {} produced empty stack, skipping", id);
                        skippedError++;
                        continue;
                    }
                    Item item = bookStack.getItem();
                    if (item == genericBook) {
                        skippedGeneric++;
                        continue;
                    }
                    result.put(item, new BookKey(SYSTEM_ID, id));
                    RunicTome.LOGGER.info("Patchouli: mapped custom book item {} → {}",
                            ForgeRegistries.ITEMS.getKey(item), id);
                } catch (Throwable perBook) {
                    RunicTome.LOGGER.warn("Patchouli: failed to resolve stack for book {}", id, perBook);
                    skippedError++;
                }
            }
        } catch (Throwable t) {
            RunicTome.LOGGER.warn("Patchouli: failed to build custom-item map", t);
        }
        RunicTome.LOGGER.info("Patchouli: indexed {} custom book item(s) out of {} total books ({} generic, {} errors)",
                result.size(), totalBooks, skippedGeneric, skippedError);
        return result;
    }

    @Override
    public void open(BookKey key, Player clientPlayer) {
        if (!AVAILABLE) {
            clientPlayer.displayClientMessage(
                    Component.translatable("runictome.patchouli_missing"), false);
            return;
        }
        // The screen runs client-side with a LocalPlayer; Patchouli's 1-arg openBookGUI
        // is the client entry point. The 2-arg server variant would reject a LocalPlayer.
        Method target = OPEN_BOOK_GUI_CLIENT != null ? OPEN_BOOK_GUI_CLIENT : OPEN_BOOK_GUI_SERVER;
        if (target == null) {
            clientPlayer.displayClientMessage(
                    Component.translatable("runictome.patchouli_missing"), false);
            return;
        }
        try {
            if (target.getParameterCount() == 1) {
                target.invoke(API_INSTANCE, key.bookId());
            } else {
                target.invoke(API_INSTANCE, clientPlayer, key.bookId());
            }
        } catch (Throwable t) {
            RunicTome.LOGGER.warn("Failed to open Patchouli book {}", key, t);
            clientPlayer.displayClientMessage(
                    Component.translatable("runictome.open_failed", key.bookId().toString()), false);
        }
    }

    @Override
    public Component displayName(BookKey key) {
        if (AVAILABLE && BOOK_REGISTRY_INSTANCE != null && BOOK_NAME_FIELD != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<ResourceLocation, ?> books = (Map<ResourceLocation, ?>) BOOK_REGISTRY_BOOKS.get(BOOK_REGISTRY_INSTANCE);
                Object book = books == null ? null : books.get(key.bookId());
                if (book != null) {
                    Object nameObj = BOOK_NAME_FIELD.get(book);
                    if (nameObj instanceof String raw && !raw.isEmpty()) {
                        return Component.translatable(raw);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return Component.literal(titleCase(key.bookId().getPath()));
    }

    private static String titleCase(String raw) {
        String spaced = raw.replace('_', ' ').replace('/', ' ');
        StringBuilder out = new StringBuilder(spaced.length());
        boolean atStart = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (Character.isWhitespace(c)) {
                out.append(c);
                atStart = true;
            } else if (atStart) {
                out.append(Character.toUpperCase(c));
                atStart = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    @Override
    public ItemStack displayIcon(BookKey key) {
        if (AVAILABLE && GET_BOOK_STACK != null) {
            try {
                Object result = GET_BOOK_STACK.invoke(API_INSTANCE, key.bookId());
                if (result instanceof ItemStack s) return s;
            } catch (Throwable ignored) {
            }
        }
        return new ItemStack(ForgeRegistries.ITEMS.getValue(GUIDE_BOOK_ITEM));
    }
}

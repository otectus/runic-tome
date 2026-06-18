package com.otectus.runictome.client.screen;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.client.ClientDataCache;
import com.otectus.runictome.network.OpenBookPacket;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.ToggleFavoritePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Searchable, scrollable Runic Tome UI. Lists unlocked guide books with their item icon and
 * display name, favorites pinned to the top. Left-click opens a book; right-click toggles its
 * favorite flag. Uses {@link ObjectSelectionList} so scrolling and selection are handled by the
 * vanilla widget rather than hand-rolled paging.
 */
public class RunicTomeScreen extends Screen {

    private static final int LIST_WIDTH = 240;
    private static final int ROW_HEIGHT = 22;

    private EditBox searchBox;
    private BookList list;
    private String filter = "";

    public RunicTomeScreen() {
        super(Component.translatable("screen.runictome.title"));
    }

    @Override
    protected void init() {
        super.init();

        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 18, 200, 18,
                Component.translatable("screen.runictome.search"));
        this.searchBox.setHint(Component.translatable("screen.runictome.search"));
        this.searchBox.setResponder(text -> {
            this.filter = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
            rebuildRows();
        });
        addRenderableWidget(this.searchBox);

        int listTop = 44;
        int listBottom = this.height - 36;
        this.list = new BookList(this.minecraft, this.width, this.height, listTop, listBottom, ROW_HEIGHT);
        addRenderableWidget(this.list);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());

        rebuildRows();
        setInitialFocus(this.searchBox);
    }

    private void rebuildRows() {
        if (this.list == null) return;
        this.list.refresh(this.filter);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        this.list.render(graphics, mouseX, mouseY, partialTick);

        // Title + unlocked count header.
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        int total = ClientDataCache.getBooks().size();
        Component count = Component.translatable("screen.runictome.count", total)
                .withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(this.font, count, this.width / 2, this.height - 14, 0xA0A0A0);

        // Widgets (search box, done button) render via super.
        super.render(graphics, mouseX, mouseY, partialTick);

        if (total == 0) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.runictome.empty"),
                    this.width / 2, this.height / 2, 0x909090);
        }
    }

    private void openEntry(BookKey key) {
        Optional<GuideSystemAdapter> adapter = RunicTomeAPI.adapterFor(key.systemId());
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (adapter.isEmpty()) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable("runictome.no_adapter", key.systemId().toString()), false);
            return;
        }
        // Tell the server first so adapters can run server-side open logic, then open client-side.
        RunicTomeNetwork.sendToServer(new OpenBookPacket(key));
        try {
            adapter.get().open(key, this.minecraft.player);
        } catch (Throwable t) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable("runictome.open_failed", key.bookId().toString()), false);
        }
    }

    private void toggleFavorite(BookKey key) {
        // Optimistic local flip for instant feedback; server reconciles on its full re-sync.
        ClientDataCache.toggleFavoriteOptimistic(key);
        RunicTomeNetwork.sendToServer(new ToggleFavoritePacket(key));
        rebuildRows();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void updateNarratedWidget(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.title);
    }

    /** Scrollable list of book rows. */
    private final class BookList extends ObjectSelectionList<BookList.Row> {

        BookList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
        }

        @Override
        public int getRowWidth() {
            return LIST_WIDTH;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + LIST_WIDTH / 2 + 4;
        }

        void refresh(String filter) {
            clearEntries();
            List<BookKey> books = new ArrayList<>(ClientDataCache.getBooks());

            List<Row> rows = new ArrayList<>();
            for (BookKey key : books) {
                Optional<GuideSystemAdapter> adapter = RunicTomeAPI.adapterFor(key.systemId());
                Component name = adapter.map(a -> a.displayName(key))
                        .orElse(Component.literal(key.bookId().toString()));
                ItemStack icon = adapter.map(a -> a.displayIcon(key)).orElse(ItemStack.EMPTY);
                if (!filter.isEmpty() && !name.getString().toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }
                rows.add(new Row(key, name, icon));
            }
            // Favorites first, then alphabetical by display name.
            rows.sort(Comparator
                    .comparing((Row r) -> ClientDataCache.isFavorite(r.key) ? 0 : 1)
                    .thenComparing(r -> r.name.getString().toLowerCase(Locale.ROOT)));
            rows.forEach(this::addEntry);
        }

        final class Row extends ObjectSelectionList.Entry<Row> {
            private final BookKey key;
            private final Component name;
            private final ItemStack icon;

            Row(BookKey key, Component name, ItemStack icon) {
                this.key = key;
                this.name = name;
                this.icon = icon;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
                Minecraft mc = Minecraft.getInstance();
                int midY = top + (height - 16) / 2;
                if (!this.icon.isEmpty()) {
                    graphics.renderItem(this.icon, left + 2, midY);
                }
                boolean fav = ClientDataCache.isFavorite(this.key);
                int textX = left + 22;
                if (fav) {
                    graphics.drawString(mc.font, "★", left + 22, top + (height - 8) / 2, 0xFFD24D);
                    textX = left + 34;
                }
                Component label = this.name;
                if (hovering) {
                    label = this.name.copy().withStyle(ChatFormatting.YELLOW);
                }
                String text = mc.font.plainSubstrByWidth(label.getString(), width - (textX - left) - 8);
                if (!text.equals(label.getString())) {
                    text = text + "...";
                }
                graphics.drawString(mc.font, text, textX, top + (height - 8) / 2,
                        hovering ? 0xFFFF99 : 0xE0E0E0);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 1) { // right-click toggles favorite
                    toggleFavorite(this.key);
                    return true;
                }
                if (button == 0) { // left-click opens
                    openEntry(this.key);
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return this.name;
            }
        }
    }
}

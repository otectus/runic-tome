package com.otectus.runictome.client.screen;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.client.ClientDataCache;
import com.otectus.runictome.network.CopyBookPacket;
import com.otectus.runictome.network.ExtractBookPacket;
import com.otectus.runictome.network.OpenBookPacket;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.SetAbsorptionPausedPacket;
import com.otectus.runictome.network.ToggleFavoritePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
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
 * display name, favorites pinned to the top. Left-click opens a book, right-click toggles its
 * favorite flag, and each row has buttons that copy or extract the physical book. Uses
 * {@link ObjectSelectionList} so scrolling and selection are handled by the
 * vanilla widget rather than hand-rolled paging.
 */
public class RunicTomeScreen extends Screen {

    // Widened from 240 in 0.9.0 to make room for the copy button without squeezing book names.
    // getScrollbarPosition() puts the bar at width/2 + 144, which still clears Minecraft's 320px
    // minimum scaled GUI width.
    private static final int LIST_WIDTH = 280;
    private static final int ROW_HEIGHT = 22;
    private static final int EXTRACT_WIDTH = 54;
    private static final int COPY_WIDTH = 44;
    /** Gap between the copy and extract buttons. */
    private static final int BUTTON_GAP = 2;

    private static final int BUTTON_BORDER = 0xFFA08050;
    private static final int BUTTON_FILL = 0xFF453824;
    private static final int BUTTON_FILL_HOVER = 0xFF6B5435;

    private EditBox searchBox;
    private BookList list;
    private Button pauseButton;
    private boolean pauseLabelShowsPaused;
    private boolean pauseLabelInitialized;
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

        // Left of DONE on the same row. 74 wide starting at width/2 - 128, so on a 320px minimum
        // scaled screen it begins at x=32 and is still fully visible.
        this.pauseButton = Button.builder(absorbLabel(), b -> toggleAbsorptionPaused())
                .bounds(this.width / 2 - 128, this.height - 28, 74, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.runictome.absorb.tooltip")))
                .build();
        addRenderableWidget(this.pauseButton);

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
        // Re-read the cache rather than trusting the optimistic flip in toggleAbsorptionPaused: if
        // the server rejects a toggle it answers with the true state, and the button must follow it.
        // The rows self-correct the same way, by reading favorites fresh on every render.
        syncPauseLabel();
        this.renderBackground(graphics);
        this.list.render(graphics, mouseX, mouseY, partialTick);

        // Title + unlocked count header.
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        int total = ClientDataCache.size();
        Component count = Component.translatable("screen.runictome.count", total)
                .withStyle(ChatFormatting.GRAY);
        // Exactly fills the 8px gap between the list's bottom edge (height - 36) and the top of the
        // button row (height - 28). At its old height-14 it ran underneath the Done button and was
        // only invisible because super.render draws widgets afterwards.
        graphics.drawCenteredString(this.font, count, this.width / 2, this.height - 36, 0xA0A0A0);

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
            adapter.get().open(key, this.minecraft.player, ClientDataCache.getBookStack(key));
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

    private void extractEntry(BookKey key) {
        RunicTomeNetwork.sendToServer(new ExtractBookPacket(key));
        // The server immediately sends an authoritative capability sync. Closing avoids leaving a
        // stale row clickable during that round trip; reopening shows the updated library.
        onClose();
    }

    private void copyEntry(BookKey key) {
        RunicTomeNetwork.sendToServer(new CopyBookPacket(key));
        // Deliberately does not close, unlike extraction: copying leaves the entry in place, so the
        // row stays valid and a player furnishing a shelf can click it several times.
    }

    private Component absorbLabel() {
        return Component.translatable(ClientDataCache.isAbsorptionPaused()
                ? "gui.runictome.absorb.off" : "gui.runictome.absorb.on");
    }

    private void toggleAbsorptionPaused() {
        // Optimistic local flip for instant feedback; the server answers with the authoritative
        // value, which converges because both sides set an absolute state rather than flipping.
        boolean paused = !ClientDataCache.isAbsorptionPaused();
        ClientDataCache.setAbsorptionPausedOptimistic(paused);
        RunicTomeNetwork.sendToServer(new SetAbsorptionPausedPacket(paused));
        syncPauseLabel();
    }

    /** Points the toggle's label at whatever the cache currently holds. */
    private void syncPauseLabel() {
        if (this.pauseButton == null) return;
        boolean paused = ClientDataCache.isAbsorptionPaused();
        if (paused == this.pauseLabelShowsPaused && this.pauseLabelInitialized) return;
        this.pauseLabelShowsPaused = paused;
        this.pauseLabelInitialized = true;
        this.pauseButton.setMessage(absorbLabel());
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
                // The retained stack is what names an entry whose key cannot: every written book
                // shares one item id and only the stack carries its title.
                ItemStack retained = ClientDataCache.getBookStack(key);
                Component name = adapter.map(a -> a.displayName(key, retained))
                        .orElse(Component.literal(key.bookId().toString()));
                ItemStack icon = adapter.map(a -> a.displayIcon(key, retained)).orElse(ItemStack.EMPTY);
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
            // Written during render() and read by mouseClicked, so hit-testing depends on the row
            // having been drawn at least once — which it always has by the time it can be clicked.
            private int copyX;
            private int extractX;
            private int buttonY;
            private int buttonHeight;

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
                this.extractX = left + width - EXTRACT_WIDTH - 3;
                this.copyX = this.extractX - COPY_WIDTH - BUTTON_GAP;
                this.buttonY = top + 2;
                this.buttonHeight = height - 4;
                drawRowButton(graphics, mc, this.copyX, COPY_WIDTH, mouseX, mouseY,
                        Component.translatable("gui.runictome.copy"));
                drawRowButton(graphics, mc, this.extractX, EXTRACT_WIDTH, mouseX, mouseY,
                        Component.translatable("gui.runictome.extract"));

                int availableTextWidth = Math.max(0, this.copyX - textX - 5);
                String text = mc.font.plainSubstrByWidth(label.getString(), availableTextWidth);
                if (!text.equals(label.getString())) {
                    text = text + "...";
                }
                graphics.drawString(mc.font, text, textX, top + (height - 8) / 2,
                        hovering ? 0xFFFF99 : 0xE0E0E0);
            }

            /** One row button, drawn in the shared border/fill/hover style. */
            private void drawRowButton(GuiGraphics graphics, Minecraft mc, int x, int buttonWidth,
                                       int mouseX, int mouseY, Component label) {
                int fill = hits(x, buttonWidth, mouseX, mouseY) ? BUTTON_FILL_HOVER : BUTTON_FILL;
                graphics.fill(x, this.buttonY, x + buttonWidth, this.buttonY + this.buttonHeight,
                        BUTTON_BORDER);
                graphics.fill(x + 1, this.buttonY + 1, x + buttonWidth - 1,
                        this.buttonY + this.buttonHeight - 1, fill);
                graphics.drawCenteredString(mc.font, label, x + buttonWidth / 2,
                        this.buttonY + (this.buttonHeight - 8) / 2, 0xFFFFFF);
            }

            private boolean hits(int x, int buttonWidth, double mouseX, double mouseY) {
                return mouseX >= x && mouseX < x + buttonWidth
                        && mouseY >= this.buttonY && mouseY < this.buttonY + this.buttonHeight;
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // Both button rects must be tested before the generic left-click-opens branch below,
                // or a click on either would open the book instead.
                if (button == 0 && hits(this.copyX, COPY_WIDTH, mouseX, mouseY)) {
                    copyEntry(this.key);
                    return true;
                }
                if (button == 0 && hits(this.extractX, EXTRACT_WIDTH, mouseX, mouseY)) {
                    extractEntry(this.key);
                    return true;
                }
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
                // The row carries two mouse-only controls that are drawn rather than being focusable
                // widgets, so name them here or a screen-reader user has no way to learn they exist.
                return Component.translatable("screen.runictome.row.narration", this.name);
            }
        }
    }
}

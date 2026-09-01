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
import net.minecraft.client.gui.Font;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Searchable, scrollable Runic Tome UI. Lists unlocked guide books with their item icon and
 * display name, favorites pinned to the top. Left-click opens a book, right-click toggles its
 * favorite flag, and each row carries icon buttons that copy or extract the physical book. Uses
 * {@link ObjectSelectionList} so scrolling and selection are handled by the vanilla widget rather
 * than hand-rolled paging.
 *
 * <p>Presentation is a fixed, centred panel drawn from {@link TomeSprites}, the same way vanilla's
 * container screens work: {@code leftPos}/{@code topPos} anchor everything, and every constant
 * below is an offset from that origin. The panel is 300x214, which fits inside the 320x240 floor
 * that {@code Window.calculateScale} guarantees at every legal GUI scale.
 */
public class RunicTomeScreen extends Screen {

    private static final int IMAGE_WIDTH = 300;
    private static final int IMAGE_HEIGHT = 214;

    private static final int TITLE_Y = 6;
    private static final int RULE_X = 12, RULE_Y = 15, RULE_W = 276;
    private static final int SEARCH_X = 7, SEARCH_Y = 19, SEARCH_W = 286, SEARCH_H = 14;
    // The EditBox is sized to its *text rect*, not the trough: setBordered(false) draws text at
    // exactly (getX(), getY()) with no padding and no vertical centring.
    private static final int SEARCH_TEXT_X = 11, SEARCH_TEXT_Y = 22;
    private static final int SEARCH_TEXT_W = 278, SEARCH_TEXT_H = 9;
    private static final int WELL_X = 7, WELL_Y = 35, WELL_W = 286, WELL_H = 138;
    private static final int LIST_X = 8, LIST_W = 284;
    private static final int LIST_TOP = 36, LIST_BOTTOM = 172;
    private static final int SCROLLBAR_X = 286;
    private static final int COUNT_Y = 175;
    private static final int BUTTON_Y = 186, BUTTON_W = 96, BUTTON_H = 20;
    private static final int ABSORB_X = 7, DONE_X = 197;

    // Vanilla draws a row at itemHeight - 4, so deriving the item height from the sprite keeps
    // the drawn row, the row background and mouseClicked's hit rects locked together: changing
    // one without the other would leave clicks landing a few pixels off what is on screen.
    // The well's interior is 136px tall and vanilla pads the first row by 4, so 6 rows of 22 fit
    // exactly. That is where IMAGE_HEIGHT comes from -- change one and you must change the other.
    private static final int ROW_HEIGHT = TomeSprites.ROW_SPRITE_H + 4;
    /** Cancels vanilla's own {@code + 2} in {@code getRowLeft()}; see {@link BookList}. */
    private static final int ROW_WIDTH = LIST_W + 2;
    /** What a row lays out inside, leaving a 3px gutter before the scrollbar. */
    private static final int ROW_CONTENT_W = 274;

    private static final int STAR_X = 20;
    private static final int NAME_X = 31;
    private static final int ICON_GAP = 2;
    private static final int COPY_DX = ROW_CONTENT_W - 2 * TomeSprites.ICON_SIZE - ICON_GAP;
    private static final int EXTRACT_DX = ROW_CONTENT_W - TomeSprites.ICON_SIZE;
    /** 205px, up from 138 under the old text buttons. */
    private static final int NAME_W = COPY_DX - 4 - NAME_X;

    // Vanilla's own container inks: 0x404040 unshadowed on the #C6C6C6 panel face, and white
    // with a shadow over the #8B8B8B well -- the same pairing vanilla uses for container titles
    // and for item names drawn over slots.
    private static final int TITLE_INK = 0x404040;
    private static final int COUNT_INK = 0x404040;
    private static final int NAME_INK = 0xFFFFFF;
    private static final int NAME_INK_HOVER = 0xFFFFA0;
    private static final int EMPTY_INK = 0x404040;
    private static final int SEARCH_INK = 0xFFFFFF;

    // Built once. Rows are drawn every frame for every visible entry, so allocating tooltip
    // components per frame would be the most expensive thing on the screen. Keys are written as
    // literals at the call site because that is the only form LangKeysTest can see.
    private static final List<Component> COPY_TIP = List.of(
            Component.translatable("gui.runictome.copy"),
            Component.translatable("gui.runictome.copy.tooltip").withStyle(ChatFormatting.GRAY));
    private static final List<Component> EXTRACT_TIP = List.of(
            Component.translatable("gui.runictome.extract"),
            Component.translatable("gui.runictome.extract.tooltip").withStyle(ChatFormatting.GRAY));
    private static final List<Component> FAVORITE_TIP = List.of(
            Component.translatable("gui.runictome.favorite"),
            Component.translatable("gui.runictome.favorite.add").withStyle(ChatFormatting.GRAY));
    private static final List<Component> UNFAVORITE_TIP = List.of(
            Component.translatable("gui.runictome.favorite"),
            Component.translatable("gui.runictome.favorite.remove").withStyle(ChatFormatting.GRAY));

    private int leftPos;
    private int topPos;

    private EditBox searchBox;
    private BookList list;
    private Button pauseButton;
    private boolean pauseLabelShowsPaused;
    private boolean pauseLabelInitialized;
    private String filter = "";

    /** Queued by whichever row is under the cursor, drawn once at the end of {@link #render}. */
    private List<Component> hoverTip;
    /** Wrapped once in {@link #init}: the message is far wider than the well. */
    private List<FormattedCharSequence> emptyLines = List.of();
    private Component countLabel;
    private int countShown = -1;

    public RunicTomeScreen() {
        super(Component.translatable("screen.runictome.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - IMAGE_WIDTH) / 2;
        this.topPos = (this.height - IMAGE_HEIGHT) / 2;

        // init() re-runs on every resize. Without carrying these over, the rebuilt search box is
        // empty while `filter` still holds the old query, leaving the list filtered behind a box
        // that looks blank.
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        double scroll = this.list == null ? 0.0D : this.list.getScrollAmount();

        this.searchBox = new EditBox(this.font,
                this.leftPos + SEARCH_TEXT_X, this.topPos + SEARCH_TEXT_Y,
                SEARCH_TEXT_W, SEARCH_TEXT_H, Component.translatable("screen.runictome.search"));
        this.searchBox.setBordered(false); // the trough behind it is the border now
        this.searchBox.setTextColor(SEARCH_INK);
        this.searchBox.setHint(Component.translatable("screen.runictome.search"));
        this.searchBox.setResponder(text -> {
            this.filter = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
            rebuildRows();
        });
        addRenderableWidget(this.searchBox);

        this.list = new BookList(this.minecraft, LIST_W, this.height,
                this.topPos + LIST_TOP, this.topPos + LIST_BOTTOM, ROW_HEIGHT);
        // Must follow the constructor, which sets x0 = 0. Never call updateSize() afterwards --
        // it resets x0/x1 and would drag the list back to the left edge of the screen.
        this.list.setLeftPos(this.leftPos + LIST_X);
        this.list.setRenderBackground(false);
        this.list.setRenderTopAndBottom(false);
        // Vanilla's selection is four fill() rects in white and black, which is wrong on leather;
        // the row draws its own selected sprite instead.
        this.list.setRenderSelection(false);
        // addWidget, not addRenderableWidget: render() draws the list itself, beneath the buttons.
        addWidget(this.list);

        this.pauseButton = Button.builder(absorbLabel(), b -> toggleAbsorptionPaused())
                .bounds(this.leftPos + ABSORB_X, this.topPos + BUTTON_Y, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.translatable("gui.runictome.absorb.tooltip")))
                .build(TomeButton::new);
        addRenderableWidget(this.pauseButton);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.leftPos + DONE_X, this.topPos + BUTTON_Y, BUTTON_W, BUTTON_H)
                .build(TomeButton::new));

        this.emptyLines = this.font.split(
                Component.translatable("screen.runictome.empty"), WELL_W - 24);

        this.searchBox.setValue(query); // fires the responder, which rebuilds the rows
        rebuildRows();
        this.list.setScrollAmount(scroll); // after the rebuild, so it clamps against the new count
        setInitialFocus(this.searchBox);
    }

    private void rebuildRows() {
        if (this.list == null) return;
        this.list.refresh(this.filter);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Cleared before anything can queue: rows publish their tooltip during their own render.
        this.hoverTip = null;
        // Re-read the cache rather than trusting the optimistic flip in toggleAbsorptionPaused: if
        // the server rejects a toggle it answers with the true state, and the button must follow it.
        // The rows self-correct the same way, by reading favorites fresh on every render.
        syncPauseLabel();

        this.renderBackground(graphics);
        renderChrome(graphics);
        renderLabels(graphics);
        this.list.render(graphics, mouseX, mouseY, partialTick);

        // Widgets (search box, buttons) render via super, on top of the chrome.
        super.render(graphics, mouseX, mouseY, partialTick);

        if (ClientDataCache.size() == 0) {
            renderEmptyState(graphics);
        }
        if (this.hoverTip != null) {
            graphics.renderComponentTooltip(this.font, this.hoverTip, mouseX, mouseY);
        }
    }

    private void renderChrome(GuiGraphics graphics) {
        TomeSprites.panel(graphics, this.leftPos, this.topPos, IMAGE_WIDTH, IMAGE_HEIGHT);
        TomeSprites.rule(graphics, this.leftPos + RULE_X, this.topPos + RULE_Y, RULE_W);
        TomeSprites.trough(graphics, this.leftPos + SEARCH_X, this.topPos + SEARCH_Y,
                SEARCH_W, SEARCH_H);
        TomeSprites.trough(graphics, this.leftPos + WELL_X, this.topPos + WELL_Y, WELL_W, WELL_H);
    }

    private void renderLabels(GuiGraphics graphics) {
        // Unshadowed dark ink on the panel face, the way vanilla draws container titles. The
        // drawCenteredString overloads always draw a shadow, so centre by hand instead.
        graphics.drawString(this.font, this.title,
                this.leftPos + (IMAGE_WIDTH - this.font.width(this.title)) / 2,
                this.topPos + TITLE_Y, TITLE_INK, false);

        Component count = countLabel();
        graphics.drawString(this.font, count,
                this.leftPos + (IMAGE_WIDTH - this.font.width(count)) / 2,
                this.topPos + COUNT_Y, COUNT_INK, false);
    }

    /**
     * The unlocked-count label, rebuilt only when the count actually changes. {@code size()} is
     * allocation-free by design and this runs every frame, so the component must be cached too.
     */
    private Component countLabel() {
        int total = ClientDataCache.size();
        if (this.countLabel == null || total != this.countShown) {
            this.countShown = total;
            this.countLabel = Component.translatable("screen.runictome.count", total);
        }
        return this.countLabel;
    }

    private void renderEmptyState(GuiGraphics graphics) {
        int y = this.topPos + WELL_Y + (WELL_H - this.emptyLines.size() * 10) / 2;
        for (FormattedCharSequence line : this.emptyLines) {
            graphics.drawString(this.font, line,
                    this.leftPos + (IMAGE_WIDTH - this.font.width(line)) / 2, y, EMPTY_INK, false);
            y += 10;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // setBordered(false) shrinks the search box's hit area to its 278x9 text rect, so without
        // this a click on the trough's padding would do nothing at all.
        if (button == 0 && this.searchBox != null && inSearchTrough(mouseX, mouseY)) {
            setFocused(this.searchBox);
            return true;
        }
        return false;
    }

    private boolean inSearchTrough(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + SEARCH_X && mouseX < this.leftPos + SEARCH_X + SEARCH_W
                && mouseY >= this.topPos + SEARCH_Y && mouseY < this.topPos + SEARCH_Y + SEARCH_H;
    }

    private void queueTooltip(List<Component> tip) {
        this.hoverTip = tip;
    }

    /** Clips a book name to the fixed name column, appending an ellipsis that still fits. */
    private static String ellipsize(String text) {
        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= NAME_W) {
            return text;
        }
        return font.plainSubstrByWidth(text, NAME_W - font.width("...")) + "...";
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
        }

        @Override
        public int getRowWidth() {
            // LIST_W + 2 cancels vanilla's own "+ 2" in getRowLeft(), landing rows on x0 + 1, one
            // pixel inside the well's border. Overriding getRowLeft() directly would not work:
            // getEntryAtPosition() recomputes the hit range without ever consulting it, so drawing
            // and hit-testing would silently disagree.
            return ROW_WIDTH;
        }

        @Override
        protected int getScrollbarPosition() {
            // Derived from the panel. Inside the list, this.width is the list's own width, so the
            // old width/2-based expression means nothing under a fixed panel.
            return RunicTomeScreen.this.leftPos + SCROLLBAR_X;
        }

        @Override
        protected void renderDecorations(GuiGraphics graphics, int mouseX, int mouseY) {
            // The scrollbar is drawn here rather than by overriding render(). Vanilla paints it as
            // three fill() rects with no hook of its own, and render() is also what assigns the
            // private `hovered` field, so overriding render() outright would cost every row its
            // hover state. renderDecorations runs last, so these blits simply cover the grey bar --
            // which is why the track sprite has to be fully opaque and exactly 6px wide.
            int x = getScrollbarPosition();
            int height = this.y1 - this.y0;
            TomeSprites.scrollTrack(graphics, x, this.y0, height);

            int maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                // Reproduced verbatim from AbstractSelectionList.render. mouseDragged recomputes
                // the same thumb height to derive its drag ratio, so any divergence here makes the
                // thumb drift out from under the cursor.
                int thumb = Mth.clamp((int) ((float) (height * height) / (float) getMaxPosition()),
                        32, height - 8);
                int thumbY = (int) getScrollAmount() * (height - thumb) / maxScroll + this.y0;
                if (thumbY < this.y0) {
                    thumbY = this.y0;
                }
                TomeSprites.scrollThumb(graphics, x, thumbY, thumb);
            }
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
            /**
             * Clipped once at construction. The name column is a fixed width under the panel
             * layout, so this cannot change while the row exists; a language change or resource
             * reload re-initialises the screen, which rebuilds every row.
             */
            private final String label;

            Row(BookKey key, Component name, ItemStack icon) {
                this.key = key;
                this.name = name;
                this.icon = icon;
                this.label = ellipsize(name.getString());
            }

            @Override
            public void renderBack(GuiGraphics graphics, int index, int top, int left, int width,
                                   int height, int mouseX, int mouseY, boolean hovering,
                                   float partialTick) {
                TomeSprites.rowBackground(graphics, left, top, ROW_CONTENT_W,
                        hovering, BookList.this.isSelectedItem(index));
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
                Minecraft mc = Minecraft.getInstance();

                // The slot goes down before the item: renderItem translates to z = 150 and flushes,
                // and larger z is nearer under the GUI ortho, so chrome blitted at z = 0 afterwards
                // is silently culled wherever it overlaps the item.
                TomeSprites.slot(graphics, left, top);
                if (!this.icon.isEmpty()) {
                    graphics.renderItem(this.icon, left + 1, top + 1);
                }

                boolean favorite = ClientDataCache.isFavorite(this.key);
                // The star gutter is a fixed width whether or not the book is favorited, so names
                // line up. The hollow star appears only on hover, to advertise right-click.
                if (favorite || hovering) {
                    TomeSprites.star(graphics, left + STAR_X,
                            top + (height - TomeSprites.STAR_SIZE) / 2, favorite);
                }

                graphics.drawString(mc.font, this.label, left + NAME_X, top + (height - 8) / 2,
                        hovering ? NAME_INK_HOVER : NAME_INK);

                int iconY = top + (height - TomeSprites.ICON_SIZE) / 2;
                boolean overCopy = hovering && hitsIcon(left + COPY_DX, iconY, mouseX, mouseY);
                boolean overExtract = hovering && hitsIcon(left + EXTRACT_DX, iconY, mouseX, mouseY);
                TomeSprites.copyIcon(graphics, left + COPY_DX, iconY, overCopy);
                TomeSprites.extractIcon(graphics, left + EXTRACT_DX, iconY, overExtract);

                // Rows are not widgets, so they cannot own a Tooltip. Publish it to the screen
                // instead, gated on `hovering` -- vanilla's hover flag already accounts for the
                // list bounds and the scrollbar column, so a clipped row cannot queue a phantom.
                if (overCopy) {
                    RunicTomeScreen.this.queueTooltip(COPY_TIP);
                } else if (overExtract) {
                    RunicTomeScreen.this.queueTooltip(EXTRACT_TIP);
                } else if (hovering && hitsStar(left, top, height, mouseX, mouseY)) {
                    RunicTomeScreen.this.queueTooltip(favorite ? UNFAVORITE_TIP : FAVORITE_TIP);
                }
            }

            private boolean hitsIcon(int x, int y, double mouseX, double mouseY) {
                return mouseX >= x && mouseX < x + TomeSprites.ICON_SIZE
                        && mouseY >= y && mouseY < y + TomeSprites.ICON_SIZE;
            }

            private boolean hitsStar(int left, int top, int height, double mouseX, double mouseY) {
                int y = top + (height - TomeSprites.STAR_SIZE) / 2;
                return mouseX >= left + STAR_X && mouseX < left + STAR_X + TomeSprites.STAR_SIZE
                        && mouseY >= y && mouseY < y + TomeSprites.STAR_SIZE;
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // Geometry is recomputed from the list rather than cached during render, so
                // hit-testing no longer depends on the row having been drawn at least once.
                int left = BookList.this.getRowLeft();
                int top = BookList.this.getRowTop(BookList.this.children().indexOf(this));
                int iconY = top + (TomeSprites.ROW_SPRITE_H - TomeSprites.ICON_SIZE) / 2;

                // Both icon rects must be tested before the generic left-click-opens branch below,
                // or a click on either would open the book instead.
                if (button == 0 && hitsIcon(left + COPY_DX, iconY, mouseX, mouseY)) {
                    copyEntry(this.key);
                    return true;
                }
                if (button == 0 && hitsIcon(left + EXTRACT_DX, iconY, mouseX, mouseY)) {
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
                // The row carries three mouse-only controls that are drawn rather than being
                // focusable widgets, so name them here or a screen-reader user has no way to learn
                // they exist.
                return Component.translatable("screen.runictome.row.narration", this.name);
            }
        }
    }
}

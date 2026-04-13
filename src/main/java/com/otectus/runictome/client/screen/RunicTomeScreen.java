package com.otectus.runictome.client.screen;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.client.ClientDataCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Vanilla-book-styled Runic Tome UI. Renders the standard book background
 * and lists unlocked guide books as clickable text lines across pages.
 * Clicking an entry closes the tome and delegates to its adapter's open().
 */
public class RunicTomeScreen extends Screen {

    private static final ResourceLocation BOOK_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/book.png");

    private static final int TEXTURE_WIDTH = 192;
    private static final int TEXTURE_HEIGHT = 192;

    private static final int TEXT_LEFT_X = 36;
    private static final int TEXT_TOP_Y = 30;
    private static final int TEXT_WIDTH = 116;
    private static final int LINE_HEIGHT = 10;
    private static final int LINES_PER_PAGE = 13;

    private int leftPos;
    private int topPos;

    private final List<Entry> entries = new ArrayList<>();
    private int currentPage = 0;
    private PageButton forwardButton;
    private PageButton backButton;
    private int hoveredIndex = -1;

    public RunicTomeScreen() {
        super(Component.translatable("screen.runictome.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - TEXTURE_WIDTH) / 2;
        this.topPos = (this.height - TEXTURE_HEIGHT) / 2;

        rebuildEntries();

        int buttonCenterY = this.topPos + 154;
        this.forwardButton = this.addRenderableWidget(new PageButton(
                this.leftPos + 116, buttonCenterY, true, b -> turnPage(1), true));
        this.backButton = this.addRenderableWidget(new PageButton(
                this.leftPos + 43, buttonCenterY, false, b -> turnPage(-1), true));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 50, this.topPos + TEXTURE_HEIGHT + 4, 100, 20)
                .build());

        updatePageButtons();
    }

    private void rebuildEntries() {
        entries.clear();
        for (BookKey key : ClientDataCache.getBooks()) {
            Optional<GuideSystemAdapter> adapter = RunicTomeAPI.adapterFor(key.systemId());
            Component name = adapter.map(a -> a.displayName(key))
                    .orElse(Component.literal(key.bookId().toString()));
            entries.add(new Entry(key, name));
        }
        entries.sort(Comparator.comparing(e -> e.name.getString().toLowerCase()));
        if (currentPage >= totalPages()) currentPage = Math.max(0, totalPages() - 1);
    }

    private int totalPages() {
        if (entries.isEmpty()) return 1;
        return (int) Math.ceil(entries.size() / (double) LINES_PER_PAGE);
    }

    private void turnPage(int delta) {
        currentPage = Math.max(0, Math.min(totalPages() - 1, currentPage + delta));
        updatePageButtons();
    }

    private void updatePageButtons() {
        this.backButton.visible = currentPage > 0;
        this.forwardButton.visible = currentPage < totalPages() - 1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.blit(BOOK_TEXTURE, this.leftPos, this.topPos, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        renderEntryPage(graphics, mouseX, mouseY);

        int pageIndicator = currentPage + 1;
        String indicator = pageIndicator + " / " + totalPages();
        int indicatorWidth = this.font.width(indicator);
        graphics.drawString(this.font, indicator,
                this.leftPos + (TEXTURE_WIDTH - indicatorWidth) / 2,
                this.topPos + 14,
                0, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEntryPage(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredIndex = -1;

        if (entries.isEmpty()) {
            Component empty = Component.translatable("screen.runictome.empty");
            List<FormattedCharSequence> wrapped = this.font.split(empty, TEXT_WIDTH);
            int y = this.topPos + 80;
            for (FormattedCharSequence line : wrapped) {
                int w = this.font.width(line);
                graphics.drawString(this.font, line,
                        this.leftPos + (TEXTURE_WIDTH - w) / 2, y, 0x606060, false);
                y += LINE_HEIGHT;
            }
            return;
        }

        int startIdx = currentPage * LINES_PER_PAGE;
        int endIdx = Math.min(startIdx + LINES_PER_PAGE, entries.size());

        int x = this.leftPos + TEXT_LEFT_X;
        int y = this.topPos + TEXT_TOP_Y;

        for (int i = startIdx; i < endIdx; i++) {
            Entry entry = entries.get(i);
            boolean hovering = mouseX >= x && mouseX < x + TEXT_WIDTH
                    && mouseY >= y && mouseY < y + LINE_HEIGHT;
            if (hovering) hoveredIndex = i;

            int color = hovering ? 0x0000CC : 0x000066;
            Style style = hovering
                    ? Style.EMPTY.withColor(color).withUnderlined(true)
                    : Style.EMPTY.withColor(color);
            Component styled = entry.name.copy().withStyle(style);

            List<FormattedCharSequence> wrapped = this.font.split(styled, TEXT_WIDTH);
            FormattedCharSequence firstLine = wrapped.isEmpty() ? FormattedCharSequence.EMPTY : wrapped.get(0);
            graphics.drawString(this.font, firstLine, x, y, 0, false);
            y += LINE_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < entries.size()) {
            Entry entry = entries.get(hoveredIndex);
            openEntry(entry);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openEntry(Entry entry) {
        Optional<GuideSystemAdapter> adapter = RunicTomeAPI.adapterFor(entry.key.systemId());
        if (adapter.isEmpty() || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        adapter.get().open(entry.key, this.minecraft.player);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Left/right arrow keys page the book.
        if (keyCode == 263) { turnPage(-1); return true; }
        if (keyCode == 262) { turnPage(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void updateNarratedWidget(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.title);
    }

    private record Entry(BookKey key, Component name) {}
}

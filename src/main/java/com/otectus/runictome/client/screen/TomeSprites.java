package com.otectus.runictome.client.screen;

import com.otectus.runictome.RunicTome;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The Runic Tome's GUI sprite sheet and every blit that reads from it.
 *
 * <p>The sheet is generated, not hand-drawn: {@code tools/gui/generate_gui_sheet.py} is the source
 * of truth for the palette, the geometry and the sprite map below, and it verifies the nine-slice
 * invariants before writing the PNG. Change the coordinates here and you must change them there
 * too; {@code GuiSheetTest} fails the build if the two drift apart.
 *
 * <p><b>The sheet must stay exactly 256x256.</b> Every {@code blitNineSliced} overload routes
 * through {@code blit(loc, x, y, u, v, w, h)} and {@code blitRepeating(...)}, both of which hardcode
 * a 256x256 texture in 1.20.1, so a differently sized sheet silently halves or doubles every UV.
 *
 * <p>Only the 10- and 11-argument {@code blitNineSliced} overloads are used. The 13-argument one
 * passes {@code pLeftSliceWidth} where the right column's destination width belongs, so asymmetric
 * slices render wrong; the shorter overloads force symmetry and dodge it.
 */
public final class TomeSprites {

    public static final ResourceLocation SHEET =
            new ResourceLocation(RunicTome.MOD_ID, "textures/gui/runic_tome.png");

    // Nine-slice sources: x, y, source size, corner slice.
    private static final int PANEL_X = 0, PANEL_Y = 0, PANEL_SRC = 96, PANEL_SLICE = 8;
    private static final int TROUGH_X = 100, TROUGH_Y = 0, TROUGH_SRC = 48, TROUGH_SLICE = 4;
    private static final int BTN_Y = 0, BTN_SRC = 20, BTN_SLICE = 4;
    private static final int RULE_X = 108, RULE_Y = 100, RULE_W = 32, RULE_H = 2, RULE_SLICE = 4;

    /** Button state columns, exposed so {@link TomeButton} can pick one. */
    public static final int BTN_NORMAL_U = 152;
    public static final int BTN_HOVER_U = 174;
    public static final int BTN_DISABLED_U = 196;

    private static final int TRACK_X = 218, TRACK_Y = 0;
    private static final int THUMB_X = 226, THUMB_Y = 0;
    private static final int BAR_W = 6, BAR_SRC_H = 32;
    private static final int TRACK_SLICE = 1, THUMB_SLICE = 3;

    // Row strips, authored exactly ROW_SPRITE_H tall so they blit 1:1 vertically.
    private static final int ROW_Y = 100, ROW_SRC_W = 32, ROW_SLICE = 4;
    /** Drawn row height: {@code itemHeight - 4}, and the height of {@link #slot}. */
    public static final int ROW_SPRITE_H = 18;
    private static final int ROW_LEDGER_X = 0;
    private static final int ROW_HOVER_X = 36;
    private static final int ROW_SELECTED_X = 72;

    private static final int SLOT_X = 0, SLOT_Y = 122;
    /** Vanilla slot size: an 18x18 trough around a 16x16 item. */
    public static final int SLOT_SIZE = 18;

    private static final int ICON_Y = 122;
    public static final int ICON_SIZE = 16;
    private static final int ICON_COPY_X = 20;
    private static final int ICON_COPY_HOVER_X = 38;
    private static final int ICON_EXTRACT_X = 56;
    private static final int ICON_EXTRACT_HOVER_X = 74;

    private static final int STAR_Y = 122;
    public static final int STAR_SIZE = 8;
    private static final int STAR_FILLED_X = 92;
    private static final int STAR_HOLLOW_X = 102;

    private TomeSprites() {
    }

    /** The main window: tooled leather with a stitched groove inside the bevel. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitNineSliced(SHEET, x, y, width, height, PANEL_SLICE, PANEL_SLICE,
                PANEL_SRC, PANEL_SRC, PANEL_X, PANEL_Y);
    }

    /** A recessed well. Used at two very different sizes: the search field and the book list. */
    public static void trough(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitNineSliced(SHEET, x, y, width, height, TROUGH_SLICE, TROUGH_SLICE,
                TROUGH_SRC, TROUGH_SRC, TROUGH_X, TROUGH_Y);
    }

    /** The engraved 2px rule under the title. */
    public static void rule(GuiGraphics graphics, int x, int y, int width) {
        graphics.blitNineSliced(SHEET, x, y, width, RULE_H, RULE_SLICE, RULE_SLICE,
                RULE_W, RULE_H, RULE_X, RULE_Y);
    }

    /** One button face. {@code u} is one of the {@code BTN_*_U} columns. */
    public static void button(GuiGraphics graphics, int x, int y, int width, int height, int u) {
        graphics.blitNineSliced(SHEET, x, y, width, height, BTN_SLICE, BTN_SLICE,
                BTN_SRC, BTN_SRC, u, BTN_Y);
    }

    /**
     * The scrollbar gutter.
     *
     * <p>Must be drawn fully opaque and exactly {@code BAR_W} wide at exactly the list's
     * {@code getScrollbarPosition()}: vanilla still paints its own black {@code fill()} track
     * immediately before {@code renderDecorations} runs, and this is what covers it.
     */
    public static void scrollTrack(GuiGraphics graphics, int x, int y, int height) {
        graphics.blitNineSliced(SHEET, x, y, BAR_W, height, TRACK_SLICE, TRACK_SLICE,
                BAR_W, BAR_SRC_H, TRACK_X, TRACK_Y);
    }

    /** The scrollbar thumb. Source width equals destination width, so it slices vertically only. */
    public static void scrollThumb(GuiGraphics graphics, int x, int y, int height) {
        graphics.blitNineSliced(SHEET, x, y, BAR_W, height, THUMB_SLICE, THUMB_SLICE,
                BAR_W, BAR_SRC_H, THUMB_X, THUMB_Y);
    }

    /** Row background. Ledger rows are transparent apart from a hairline along their bottom. */
    public static void rowBackground(GuiGraphics graphics, int x, int y, int width,
                                     boolean hovered, boolean selected) {
        int u = hovered ? ROW_HOVER_X : (selected ? ROW_SELECTED_X : ROW_LEDGER_X);
        graphics.blitNineSliced(SHEET, x, y, width, ROW_SPRITE_H, ROW_SLICE, ROW_SLICE,
                ROW_SRC_W, ROW_SPRITE_H, u, ROW_Y);
    }

    /**
     * The item well behind a row's book icon.
     *
     * <p>Always blit this <em>before</em> {@code GuiGraphics.renderItem}. That call translates to
     * z = 150 and flushes; larger z is nearer under the GUI's ortho projection and the depth test
     * is {@code LEQUAL}, so chrome drawn at z = 0 afterwards is silently culled where it overlaps.
     */
    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.blit(SHEET, x, y, SLOT_X, SLOT_Y, SLOT_SIZE, SLOT_SIZE);
    }

    /** Favourite marker: solid gold when favourited, an outline with a dark centre when not. */
    public static void star(GuiGraphics graphics, int x, int y, boolean filled) {
        graphics.blit(SHEET, x, y, filled ? STAR_FILLED_X : STAR_HOLLOW_X, STAR_Y,
                STAR_SIZE, STAR_SIZE);
    }

    public static void copyIcon(GuiGraphics graphics, int x, int y, boolean hovered) {
        graphics.blit(SHEET, x, y, hovered ? ICON_COPY_HOVER_X : ICON_COPY_X, ICON_Y,
                ICON_SIZE, ICON_SIZE);
    }

    public static void extractIcon(GuiGraphics graphics, int x, int y, boolean hovered) {
        graphics.blit(SHEET, x, y, hovered ? ICON_EXTRACT_HOVER_X : ICON_EXTRACT_X, ICON_Y,
                ICON_SIZE, ICON_SIZE);
    }
}

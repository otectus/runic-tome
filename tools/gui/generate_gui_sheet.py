#!/usr/bin/env python3
"""Generates the Runic Tome's GUI art: the sprite sheet and the Curios empty-slot icon.

    python tools/gui/generate_gui_sheet.py            # write + verify + print hashes
    python tools/gui/generate_gui_sheet.py --check    # verify the committed PNGs match

Outputs:
    src/main/resources/assets/runictome/textures/gui/runic_tome.png            256x256 RGBA
    src/main/resources/assets/runictome/textures/slot/empty_runic_tome_slot.png  16x16 RGBA

The 256x256 sheet size is load-bearing, not cosmetic. GuiGraphics.blitNineSliced routes through
blit(loc, x, y, u, v, w, h) and blitRepeating(...), both of which hardcode a 256x256 texture in
1.20.1. A differently sized sheet would halve or double every UV.

This script is the palette and geometry specification in executable form; the PNGs are derived
artifacts. Gradle never invokes it -- it is run by hand when the art changes. It lives outside
src/main/resources so processResources does not package a .py into the shipped jar, and outside
src/generated because that path is gitignored while the PNGs must be committed.

Every colour below was sampled from vanilla 1.20.1's own GUI textures rather than eyeballed:
generic_54.png for the panel and troughs, widgets.png for the buttons. The construction rules
were measured from the same files:

  * Raised surface (panel, buttons, thumb): fill FACE, 1px OUTLINE boundary, then for each interior
    pixel let m = min(x-1, y-1, (w-2)-x, (h-2)-y); where m < edge, paint HILIGHT if m == y-1 or
    m == x-1 (top/left wins ties) else SHADE. That single rule reproduces vanilla's exact 45-degree
    corner mitre -- generic_54 row 3 is 3 white pixels, row 4 is 2, and so on.
  * Chamfer: mask off the 2px corner nub at each corner, then outline every masked pixel with an
    unmasked 4-neighbour. Vanilla's panel corner really is chamfered -- generic_54 has (0,0), (1,0)
    and (0,1) fully transparent with the outline running diagonally through (2,0), (1,1), (0,2) --
    so this derives the corner rather than hand-placing it.
  * Trough / recess: 1px DEEP on top+left, 1px HILIGHT on bottom+right, fill inside; the two
    off-diagonal corner pixels take the fill colour, exactly as vanilla's 18x18 slots do.
  * Faces are FLAT. Vanilla button faces carry a couple of levels of dither, but a nine-slice centre
    is tiled by blitRepeating -- any noise there would repeat visibly, and it would break the
    uniform-centre invariant that verify() enforces. Flat matches vanilla's panels exactly and is
    imperceptibly different from its buttons.
  * Never anti-alias, blend, or resample. Every pixel is either fully transparent or an exact
    palette entry, which is what verify() asserts.

Determinism: only integer putpixel of solid colours, no ImageDraw primitives, no resize, no alpha
compositing, and an empty PngInfo so no tIME/tEXt chunk is written. Pixel content is therefore
deterministic across Pillow versions; the file bytes are deterministic only for a fixed Pillow/zlib,
so --check compares the pixel hash rather than the file hash.
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

from PIL import Image, PngImagePlugin

SIZE = 256
OUT = Path("src/main/resources/assets/runictome/textures/gui/runic_tome.png")
OUT_SLOT = Path("src/main/resources/assets/runictome/textures/slot/empty_runic_tome_slot.png")

# --------------------------------------------------------------------------------------------
# Palette -- vanilla 1.20.1's own greys, sampled not guessed.
#
#   generic_54.png  panel outline #000000, 2px #FFFFFF bevel, #C6C6C6 face, 2px #555555 shadow;
#                   slots are #373737 top/left, #8B8B8B fill, #FFFFFF bottom/right.
#   widgets.png     button #000000 outline, #AAAAAA top, #6F6F6F face, #555555 bottom;
#                   hovered swaps the outline to #FFFFFF and lifts the face to #757575;
#                   disabled is a flat #2C2C2C.
#
# Gold is the one non-grey and it is deliberate: it was already the favourite-star colour before
# this restyle, it is an accent rather than chrome, and vanilla uses the same yellow for emphasis.
# --------------------------------------------------------------------------------------------

TRANSPARENT = (0, 0, 0, 0)

PALETTE = {
    "OUTLINE":      (0x00, 0x00, 0x00, 255),  # panel/button outline, glyph ink
    "BTN_OFF":      (0x2C, 0x2C, 0x2C, 255),  # disabled button face (vanilla draws it flat)
    "DEEP":         (0x37, 0x37, 0x37, 255),  # trough top/left shadow, row hairline
    "SLOT_VOID":    (0x4A, 0x4A, 0x4A, 255),  # the item well inside a row, one step below the list
    "SHADE":        (0x55, 0x55, 0x55, 255),  # panel bottom/right, row fill, scroll groove, button
    "BTN_SHADE_LIT": (0x5C, 0x5C, 0x5C, 255),  # hovered button bottom/right
    "BTN_FACE":     (0x6F, 0x6F, 0x6F, 255),  # button and row-icon face
    "BTN_FACE_LIT": (0x75, 0x75, 0x75, 255),  # hovered button and row-icon face
    "DARK":         (0x8B, 0x8B, 0x8B, 255),  # trough fill -- vanilla's slot grey
    "BTN_HI":       (0xAA, 0xAA, 0xAA, 255),  # button top/left
    "BTN_HI_LIT":   (0xAF, 0xAF, 0xAF, 255),  # hovered button top/left
    "FACE":         (0xC6, 0xC6, 0xC6, 255),  # panel face, scroll thumb face
    "PAPER_BACK":   (0xD4, 0xD4, 0xD4, 255),  # copy icon, back page
    "HILIGHT":      (0xFF, 0xFF, 0xFF, 255),  # bevel highlight, hover outline, paper, hollow star
    "GOLD":         (0xFF, 0xD2, 0x4D, 255),  # filled star, extract arrow
    "GOLD_DARK":    (0xA8, 0x80, 0x2A, 255),  # gold shadow
}

P = PALETTE
LEGAL = set(PALETTE.values()) | {TRANSPARENT}

# --------------------------------------------------------------------------------------------
# Sprite map. Drives both drawing and verification: (x, y, w, h, slice) where slice is None for
# sprites blitted 1:1. Every rect is padded >= 2px from its neighbours, and y >= 144 is left
# entirely free for future work.
# --------------------------------------------------------------------------------------------

SPRITES = {
    # Nine-slice sources.
    "PANEL":              (0, 0, 96, 96, 8),
    "TROUGH":             (100, 0, 48, 48, 4),   # search inset AND list well, one sprite two sizes
    "BTN_NORMAL":         (152, 0, 20, 20, 4),
    "BTN_HOVER":          (174, 0, 20, 20, 4),
    "BTN_DISABLED":       (196, 0, 20, 20, 4),
    "SCROLL_TRACK":       (218, 0, 6, 32, 1),    # must be fully opaque; see TomeSprites
    "SCROLL_THUMB":       (226, 0, 6, 32, 3),    # w == uWidth, so it slices vertically only
    # Row strips, authored exactly 18 tall -- the drawn row height (itemHeight - 4).
    "ROW_LEDGER":         (0, 100, 32, 18, 4),
    "ROW_HOVER":          (36, 100, 32, 18, 4),
    "ROW_SELECTED":       (72, 100, 32, 18, 4),
    "RULE":               (108, 100, 32, 2, 4),
    # Fixed 1:1 sprites.
    "SLOT":               (0, 122, 18, 18, None),
    "ICON_COPY":          (20, 122, 16, 16, None),
    "ICON_COPY_HOVER":    (38, 122, 16, 16, None),
    "ICON_EXTRACT":       (56, 122, 16, 16, None),
    "ICON_EXTRACT_HOVER": (74, 122, 16, 16, None),
    "STAR_FILLED":        (92, 122, 8, 8, None),
    "STAR_HOLLOW":        (102, 122, 8, 8, None),
}

# --------------------------------------------------------------------------------------------
# Glyphs. Kept as ASCII so the art is readable and editable in source.
# --------------------------------------------------------------------------------------------

COPY_GLYPH = [
    "....########",
    "....#oooooo#",
    "....#oooooo#",
    "....#oooooo#",
    "########ooo#",
    "#O---OO#ooo#",
    "#OOOOOO#ooo#",
    "#O---OO#####",
    "#OOOOOO#....",
    "#O---OO#....",
    "#OOOOOO#....",
    "########....",
]

# The arrowhead is formed by the diagonals (9,3)->(10,4)->(11,5) and (9,8)->(10,7)->(11,6)
# converging on the shaft's tip. A single-pixel barb reads as noise at this size; two rows of
# shaft and a stepped head are the smallest form that still says "leaves the tome".
EXTRACT_GLYPH = [
    "............",
    "######......",
    "#ssoo#......",
    "#ssoo#...A..",
    "#ssoo#...AA.",
    "#ssoo#AAAAAA",
    "#ssoo#AAAAAA",
    "#ssoo#...aa.",
    "#ssoo#...a..",
    "#ssoo#......",
    "######......",
    "............",
]

GLYPH_INK = {
    "#": P["OUTLINE"],
    "s": P["DEEP"],
    "o": P["PAPER_BACK"],
    "O": P["HILIGHT"],
    "-": P["DARK"],
    "A": P["GOLD"],
    "a": P["GOLD_DARK"],
    ".": None,
}

STAR_MASK = [
    "...##...",
    "...##...",
    ".######.",
    "########",
    ".######.",
    "..####..",
    ".##..##.",
    ".#....#.",
]

# The Curios empty-slot icon. Curios' own eleven icons are all 16x16, exactly two RGBA values
# (transparent and #555555), 32-54 opaque pixels, and every one is a 1px hollow outline kept
# inside x/y 1..14; CuriosSlotResourcesTest asserts all of those constraints.
#
# A closed book with a spine, plus a rune cross on the cover. Without the rune the bare frame
# reads as a window rather than a book, and the mark is what distinguishes this from any other
# mod's book slot in the same inventory. 52 opaque pixels, near the top of Curios' range.
SLOT_ICON = [
    "................",
    "................",
    "...##########...",
    "...#..#.....#...",
    "...#..#.....#...",
    "...#..#.....#...",
    "...#..#..#..#...",
    "...#..#.###.#...",
    "...#..#..#..#...",
    "...#..#.....#...",
    "...#..#.....#...",
    "...#..#.....#...",
    "...##########...",
    "................",
    "................",
    "................",
]


# --------------------------------------------------------------------------------------------
# Primitives
# --------------------------------------------------------------------------------------------

def rect(img, x, y, w, h, colour):
    for j in range(h):
        for i in range(w):
            img.putpixel((x + i, y + j), colour)


def rounded_mask(w, h, chamfer):
    """Every pixel of a w x h rect except the `chamfer` corner nub at each corner.

    A chamfer of 2 removes (0,0), (1,0) and (0,1), which is exactly what vanilla's panel does.
    """
    dropped = set()
    if chamfer > 0:
        nub = [(0, 0)] + [(i, 0) for i in range(1, chamfer)] + [(0, j) for j in range(1, chamfer)]
        for dx, dy in nub:
            dropped.add((dx, dy))
            dropped.add((w - 1 - dx, dy))
            dropped.add((dx, h - 1 - dy))
            dropped.add((w - 1 - dx, h - 1 - dy))
    return frozenset((i, j) for j in range(h) for i in range(w) if (i, j) not in dropped)


def bevel_rect(img, x, y, w, h, face, edge=2, chamfer=0,
               hilight=None, shade=None, outline=None):
    """A raised vanilla-style surface: outline, mitred bevel, flat face."""
    hilight = hilight or P["HILIGHT"]
    shade = shade or P["SHADE"]
    outline = outline or P["OUTLINE"]
    mask = rounded_mask(w, h, chamfer)

    for j in range(h):
        for i in range(w):
            if (i, j) not in mask:
                img.putpixel((x + i, y + j), TRANSPARENT)
                continue
            # Boundary, or a pixel exposed by the chamfer, becomes the outline. Deriving the
            # diagonal this way keeps the corner correct for any chamfer size.
            exposed = any((i + dx, j + dy) not in mask
                          for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if i == 0 or j == 0 or i == w - 1 or j == h - 1 or exposed:
                img.putpixel((x + i, y + j), outline)
                continue
            m = min(i - 1, j - 1, (w - 2) - i, (h - 2) - j)
            if m < edge:
                # Top/left wins ties, which is what produces vanilla's 45-degree mitre.
                img.putpixel((x + i, y + j), hilight if (m == j - 1 or m == i - 1) else shade)
            else:
                img.putpixel((x + i, y + j), face)


def trough_rect(img, x, y, w, h, fill, top_left=None, bottom_right=None):
    """A recessed vanilla-style well: dark top/left, light bottom/right, flat fill."""
    top_left = top_left or P["DEEP"]
    bottom_right = bottom_right or P["HILIGHT"]
    rect(img, x, y, w, h, fill)
    for i in range(w):
        img.putpixel((x + i, y), top_left)
        img.putpixel((x + i, y + h - 1), bottom_right)
    for j in range(h):
        img.putpixel((x, y + j), top_left)
        img.putpixel((x + w - 1, y + j), bottom_right)
    # Vanilla puts the fill colour, not either bevel, at the two off-diagonal corners.
    img.putpixel((x + w - 1, y), fill)
    img.putpixel((x, y + h - 1), fill)


def glyph(img, x, y, rows, legend=None):
    legend = legend or GLYPH_INK
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            colour = legend[ch]
            if colour is not None:
                img.putpixel((x + i, y + j), colour)


def icon_button(img, name, rows, lit):
    """A row icon: a small button face in the same greys as the real buttons, plus its glyph."""
    x, y, w, h, _ = SPRITES[name]
    bevel_rect(img, x, y, w, h,
               P["BTN_FACE_LIT"] if lit else P["BTN_FACE"], edge=1,
               hilight=P["BTN_HI_LIT"] if lit else P["BTN_HI"],
               shade=P["BTN_SHADE_LIT"] if lit else P["SHADE"])
    glyph(img, x + 2, y + 2, rows)


def star(img, name, hollow):
    x, y, w, h, _ = SPRITES[name]
    mask = {(i, j) for j, row in enumerate(STAR_MASK) for i, ch in enumerate(row) if ch == "#"}
    for j in range(h):
        for i in range(w):
            if (i, j) not in mask:
                img.putpixel((x + i, y + j), TRANSPARENT)
                continue
            if hollow:
                # Boundary of the same mask, so the two stars register exactly. The interior is
                # DEEP rather than transparent: an outline-only star at 8x8 is mostly gaps and
                # reads as noise against the row, whereas a dark centre reads as "not filled in".
                edge = any((i + dx, j + dy) not in mask
                           for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                img.putpixel((x + i, y + j), P["HILIGHT"] if edge else P["DEEP"])
            else:
                img.putpixel((x + i, y + j), P["GOLD"])


def row_strip(img, name, fill, top_left=None, bottom_right=None, full_border=None, hairline=None):
    x, y, w, h, _ = SPRITES[name]
    rect(img, x, y, w, h, fill)
    if full_border is not None:
        for i in range(w):
            img.putpixel((x + i, y), full_border)
            img.putpixel((x + i, y + h - 1), full_border)
        for j in range(h):
            img.putpixel((x, y + j), full_border)
            img.putpixel((x + w - 1, y + j), full_border)
    else:
        if top_left is not None:
            for i in range(w):
                img.putpixel((x + i, y), top_left)
            for j in range(h):
                img.putpixel((x, y + j), top_left)
        if bottom_right is not None:
            for i in range(w):
                img.putpixel((x + i, y + h - 1), bottom_right)
            for j in range(h):
                img.putpixel((x + w - 1, y + j), bottom_right)
    if hairline is not None:
        for i in range(w):
            img.putpixel((x + i, y + h - 1), hairline)


def scroll_track(img):
    """A dark groove, one step below the list well so the thumb reads as sitting inside it."""
    x, y, w, h, _ = SPRITES["SCROLL_TRACK"]
    rect(img, x, y, w, h, P["SHADE"])
    for j in range(h):
        img.putpixel((x, y + j), P["DEEP"])
        img.putpixel((x + w - 1, y + j), P["DARK"])
    for i in range(w):
        img.putpixel((x + i, y), P["DEEP"])
        img.putpixel((x + i, y + h - 1), P["DARK"])


def scroll_thumb(img):
    x, y, w, h, _ = SPRITES["SCROLL_THUMB"]
    rect(img, x, y, w, h, P["FACE"])
    for j in range(h):
        img.putpixel((x, y + j), P["HILIGHT"])
        img.putpixel((x + w - 1, y + j), P["SHADE"])
    for i in range(w):
        img.putpixel((x + i, y), P["HILIGHT"])
        img.putpixel((x + i, y + h - 1), P["SHADE"])
    img.putpixel((x + w - 1, y), P["SHADE"])


# --------------------------------------------------------------------------------------------
# Sheets
# --------------------------------------------------------------------------------------------

def build():
    img = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)

    px, py, pw, ph, _ = SPRITES["PANEL"]
    bevel_rect(img, px, py, pw, ph, P["FACE"], edge=2, chamfer=2)

    tx, ty, tw, th, _ = SPRITES["TROUGH"]
    trough_rect(img, tx, ty, tw, th, P["DARK"])

    bx, by, bw, bh, _ = SPRITES["BTN_NORMAL"]
    bevel_rect(img, bx, by, bw, bh, P["BTN_FACE"], edge=1,
               hilight=P["BTN_HI"], shade=P["SHADE"])
    bx, by, bw, bh, _ = SPRITES["BTN_HOVER"]
    # Vanilla's hovered button really does swap its outline to white -- that is the focus ring.
    bevel_rect(img, bx, by, bw, bh, P["BTN_FACE_LIT"], edge=1,
               hilight=P["BTN_HI_LIT"], shade=P["BTN_SHADE_LIT"], outline=P["HILIGHT"])
    bx, by, bw, bh, _ = SPRITES["BTN_DISABLED"]
    # Vanilla draws the disabled button flat, with no bevel at all.
    bevel_rect(img, bx, by, bw, bh, P["BTN_OFF"], edge=1,
               hilight=P["BTN_OFF"], shade=P["BTN_OFF"])

    scroll_track(img)
    scroll_thumb(img)

    # Default rows are transparent so the well shows through; only a hairline separates them.
    # DEEP, not something a shade off the well: at 1px the separator has to out-contrast what it
    # sits on, or adjacent unhovered rows visually merge into one block.
    row_strip(img, "ROW_LEDGER", TRANSPARENT, hairline=P["DEEP"])
    row_strip(img, "ROW_HOVER", P["SHADE"], top_left=P["DARK"], bottom_right=P["DEEP"])
    # A dark fill inside a full white border is vanilla's own selection idiom.
    row_strip(img, "ROW_SELECTED", P["SHADE"], full_border=P["HILIGHT"])

    rx, ry, rw, rh, _ = SPRITES["RULE"]
    for i in range(rw):
        img.putpixel((rx + i, ry), P["SHADE"])
        img.putpixel((rx + i, ry + 1), P["HILIGHT"])

    sx, sy, sw, sh, _ = SPRITES["SLOT"]
    trough_rect(img, sx, sy, sw, sh, P["SLOT_VOID"])

    icon_button(img, "ICON_COPY", COPY_GLYPH, lit=False)
    icon_button(img, "ICON_COPY_HOVER", COPY_GLYPH, lit=True)
    icon_button(img, "ICON_EXTRACT", EXTRACT_GLYPH, lit=False)
    icon_button(img, "ICON_EXTRACT_HOVER", EXTRACT_GLYPH, lit=True)

    star(img, "STAR_FILLED", hollow=False)
    star(img, "STAR_HOLLOW", hollow=True)

    return img


def build_slot_icon():
    """The Curios empty-slot icon: a hollow book outline in Curios' own #555555."""
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    glyph(img, 0, 0, SLOT_ICON, {"#": P["SHADE"], ".": None})
    return img


# --------------------------------------------------------------------------------------------
# Verification. Most of the value of this script lives here: a broken nine-slice is invisible
# until it flickers in game at one particular panel width.
# --------------------------------------------------------------------------------------------

def verify(img):
    errors = []

    if img.size != (SIZE, SIZE):
        errors.append(f"sheet is {img.size}, must be {SIZE}x{SIZE} (blitNineSliced hardcodes it)")
    if img.mode != "RGBA":
        errors.append(f"sheet mode is {img.mode}, must be RGBA")
    if errors:
        return errors

    for y in range(SIZE):
        for x in range(SIZE):
            p = img.getpixel((x, y))
            if p[3] == 0:
                continue
            if p not in LEGAL:
                errors.append(f"non-palette pixel {p} at ({x},{y}) -- blending or AA crept in")
                break
        if errors:
            break

    seen = {}
    for name, (x, y, w, h, _) in SPRITES.items():
        if x < 0 or y < 0 or x + w > SIZE or y + h > SIZE:
            errors.append(f"{name} runs off the sheet")
            continue
        for j in range(y, y + h):
            for i in range(x, x + w):
                other = seen.get((i, j))
                if other is not None:
                    errors.append(f"{name} overlaps {other} at ({i},{j})")
                    break
                seen[(i, j)] = name
            else:
                continue
            break

    for name, (x, y, w, h, slice_size) in SPRITES.items():
        if slice_size is None:
            continue
        errors.extend(check_nine_slice(img, name, x, y, w, h, slice_size))

    return errors


def verify_slot_icon(img):
    """Mirrors CuriosSlotResourcesTest, so a bad icon fails here rather than in the Gradle run."""
    errors = []
    if img.size != (16, 16):
        return [f"slot icon is {img.size}, Curios icons are 16x16"]

    opaque = 0
    for y in range(16):
        for x in range(16):
            p = img.getpixel((x, y))
            if p[3] == 0:
                continue
            opaque += 1
            if p != P["SHADE"]:
                errors.append(f"slot icon pixel ({x},{y}) is {p}, not the Curios slot grey")
            if not (1 <= x <= 14 and 1 <= y <= 14):
                errors.append(f"slot icon pixel ({x},{y}) touches the outer border")
    if opaque == 0:
        errors.append("slot icon is entirely transparent")
    # Curios' own eleven icons span 32..54 opaque pixels; well outside that reads as a blob or a
    # smudge next to them in the same inventory screen.
    if not 32 <= opaque <= 54:
        errors.append(f"slot icon has {opaque} opaque pixels, outside Curios' 32..54 range")
    return errors


def check_nine_slice(img, name, x, y, w, h, slice_size):
    """Edge strips must be constant along their length and the centre a single colour.

    blitRepeating tiles those regions, and for a partial tile it samples the *centre* of the
    source slice -- so any variation there produces a seam that only appears at some sizes.
    """
    errors = []
    sw = min(slice_size, w // 2)
    sh = min(slice_size, h // 2)

    def px(i, j):
        return img.getpixel((x + i, y + j))

    for j in list(range(sh)) + list(range(h - sh, h)):
        row = [px(i, j) for i in range(sw, w - sw)]
        if row and len(set(row)) > 1:
            errors.append(f"{name}: horizontal strip row {j} is not constant -- will tile a seam")

    for i in list(range(sw)) + list(range(w - sw, w)):
        col = [px(i, j) for j in range(sh, h - sh)]
        if col and len(set(col)) > 1:
            errors.append(f"{name}: vertical strip column {i} is not constant -- will tile a seam")

    centre = {px(i, j) for j in range(sh, h - sh) for i in range(sw, w - sw)}
    if len(centre) > 1:
        errors.append(f"{name}: nine-slice centre is not a single colour ({len(centre)} colours)")

    return errors


# --------------------------------------------------------------------------------------------

def pixel_hash(img):
    return hashlib.sha256(img.tobytes()).hexdigest()


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sprite_table():
    lines = ["  sprite                x    y    w    h  slice"]
    for name, (x, y, w, h, s) in SPRITES.items():
        lines.append(f"  {name:<20}{x:>4} {y:>4} {w:>4} {h:>4}  {'-' if s is None else s:>4}")
    return "\n".join(lines)


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="PNG", optimize=True, compress_level=9,
             pnginfo=PngImagePlugin.PngInfo())  # empty: no tIME, no tEXt


def main(argv):
    check_only = "--check" in argv

    sheet = build()
    icon = build_slot_icon()

    errors = verify(sheet) + verify_slot_icon(icon)
    if errors:
        for e in errors:
            print(f"FAIL: {e}", file=sys.stderr)
        return 1

    if check_only:
        for path, built in ((OUT, sheet), (OUT_SLOT, icon)):
            if not path.exists():
                print(f"FAIL: {path} does not exist", file=sys.stderr)
                return 1
            if pixel_hash(Image.open(path).convert("RGBA")) != pixel_hash(built):
                print(f"FAIL: {path} does not match this script; re-run without --check",
                      file=sys.stderr)
                return 1
        print(f"OK: both PNGs match (sheet {pixel_hash(sheet)[:16]}, "
              f"icon {pixel_hash(icon)[:16]})")
        return 0

    save(sheet, OUT)
    save(icon, OUT_SLOT)

    print(sprite_table())
    print()
    print(f"wrote  {OUT}")
    print(f"       pixels sha256 {pixel_hash(sheet)}")
    print(f"       file   sha256 {file_hash(OUT)}")
    print(f"wrote  {OUT_SLOT}")
    print(f"       pixels sha256 {pixel_hash(icon)}")
    print(f"       file   sha256 {file_hash(OUT_SLOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

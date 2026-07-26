#!/usr/bin/env python3
"""
MC-AI-Assistant mechanical UI sprite generator.

All sprites are ORIGINAL pixel art defined inline in this script (ASCII grids
with explicit palettes). No Create mod assets are used, extracted, or recolored.

Usage:  python tools/sprites/generate_sprites.py
Output: src/main/resources/assets/agentchat/textures/gui/...
"""

from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT_STYLE = ROOT / "src/main/resources/assets/agentchat/textures/gui/create_style"
OUT_SPRITES = ROOT / "src/main/resources/assets/agentchat/textures/gui/sprites"

# ---------------------------------------------------------------------------
# Palette (ARGB values mirror ui/theme/GuideTheme tokens)
# ---------------------------------------------------------------------------
C = {
    "iron_base":      (0x23, 0x26, 0x22, 0xFF),
    "iron_raised":    (0x30, 0x35, 0x2F, 0xFF),
    "iron_inset":     (0x19, 0x1C, 0x18, 0xFF),
    "iron_edge_dark": (0x0E, 0x10, 0x0D, 0xFF),
    "steel_edge":     (0x59, 0x60, 0x57, 0xFF),
    "brass":          (0xD2, 0xA8, 0x4D, 0xFF),
    "brass_hi":       (0xFF, 0xD7, 0x7A, 0xFF),
    "brass_shade":    (0x8A, 0x6B, 0x2F, 0xFF),
    "copper":         (0xB9, 0x65, 0x42, 0xFF),
    "copper_hi":      (0xE0, 0x8B, 0x63, 0xFF),
    "copper_shade":   (0x7A, 0x3E, 0x28, 0xFF),
    "danger":         (0xC4, 0x4D, 0x3F, 0xFF),
    "danger_hi":      (0xE8, 0x7A, 0x6A, 0xFF),
    "danger_shade":   (0x8A, 0x2F, 0x24, 0xFF),
    "cream":          (0xF3, 0xEF, 0xD9, 0xFF),
    "ink":            (0x17, 0x19, 0x12, 0xFF),
    "verdigris":      (0x4C, 0x9B, 0x8C, 0xFF),
    "transparent":    (0, 0, 0, 0),
}


def new_image(w, h, fill="transparent"):
    img = Image.new("RGBA", (w, h))
    img.paste(C[fill], (0, 0, w, h)) if fill != "transparent" else None
    return img


def px(img, x, y, color):
    img.putpixel((x, y), C[color])


def fill_rect(img, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px(img, x, y, color)


def save(img, out_dir, name):
    out_dir.mkdir(parents=True, exist_ok=True)
    img.save(out_dir / name, "PNG")
    print(f"  wrote {out_dir.name}/{name} {img.size[0]}x{img.size[1]}")


# ---------------------------------------------------------------------------
# 9-slice panels (24x24, 8px slices). Drawn as full tiles; the renderer only
# ever stretches the center 8x8, edges stay 1:1.
# ---------------------------------------------------------------------------

def panel_iron():
    img = new_image(24, 24, "iron_base")
    fill_rect(img, 0, 0, 24, 1, "iron_edge_dark")   # outer border
    fill_rect(img, 0, 23, 24, 24, "iron_edge_dark")
    fill_rect(img, 0, 0, 1, 24, "iron_edge_dark")
    fill_rect(img, 23, 0, 24, 24, "iron_edge_dark")
    fill_rect(img, 1, 1, 23, 2, "steel_edge")       # top inner highlight
    fill_rect(img, 1, 1, 2, 23, "steel_edge")       # left inner highlight
    fill_rect(img, 1, 22, 23, 23, "iron_inset")     # bottom inner shade
    fill_rect(img, 22, 1, 23, 23, "iron_inset")     # right inner shade
    return img


def panel_inset():
    img = new_image(24, 24, "iron_inset")
    fill_rect(img, 0, 0, 24, 1, "iron_edge_dark")   # sunken: dark top/left inner
    fill_rect(img, 0, 23, 24, 24, "iron_edge_dark")
    fill_rect(img, 0, 0, 1, 24, "iron_edge_dark")
    fill_rect(img, 23, 0, 24, 24, "iron_edge_dark")
    fill_rect(img, 1, 1, 23, 2, "iron_edge_dark")
    fill_rect(img, 1, 1, 2, 23, "iron_edge_dark")
    fill_rect(img, 1, 22, 23, 23, "iron_raised")    # bottom light catch
    fill_rect(img, 22, 1, 23, 23, "iron_raised")
    return img


def frame_brass():
    img = new_image(24, 24, "iron_base")
    fill_rect(img, 0, 0, 24, 1, "iron_edge_dark")
    fill_rect(img, 0, 23, 24, 24, "iron_edge_dark")
    fill_rect(img, 0, 0, 1, 24, "iron_edge_dark")
    fill_rect(img, 23, 0, 24, 24, "iron_edge_dark")
    fill_rect(img, 1, 1, 23, 3, "brass")            # brass band top
    fill_rect(img, 1, 21, 23, 23, "brass")          # brass band bottom
    fill_rect(img, 1, 1, 3, 23, "brass")            # brass band left
    fill_rect(img, 21, 1, 23, 23, "brass")          # brass band right
    fill_rect(img, 1, 1, 23, 2, "brass_hi")         # top highlight line
    fill_rect(img, 1, 22, 23, 23, "brass_shade")    # bottom shade line
    fill_rect(img, 3, 3, 21, 4, "iron_inset")       # inner edge shadow
    fill_rect(img, 3, 20, 21, 21, "iron_inset")
    fill_rect(img, 3, 3, 4, 21, "iron_inset")
    fill_rect(img, 20, 3, 21, 21, "iron_inset")
    return img


def button_like(fill, hi, shade):
    img = new_image(24, 24, fill)
    fill_rect(img, 0, 0, 24, 1, "iron_edge_dark")
    fill_rect(img, 0, 23, 24, 24, "iron_edge_dark")
    fill_rect(img, 0, 0, 1, 24, "iron_edge_dark")
    fill_rect(img, 23, 0, 24, 24, "iron_edge_dark")
    fill_rect(img, 1, 1, 23, 2, hi)                 # top press highlight
    fill_rect(img, 1, 1, 2, 23, hi)
    fill_rect(img, 1, 22, 23, 23, shade)            # bottom shade
    fill_rect(img, 22, 1, 23, 23, shade)
    return img


def tab_segment():
    img = new_image(24, 24, "iron_raised")
    fill_rect(img, 0, 0, 24, 1, "iron_edge_dark")
    fill_rect(img, 0, 23, 24, 24, "iron_edge_dark")
    fill_rect(img, 0, 0, 1, 24, "iron_edge_dark")
    fill_rect(img, 23, 0, 24, 24, "iron_edge_dark")
    fill_rect(img, 1, 1, 23, 2, "steel_edge")
    return img


def scrollbar_thumb():
    img = new_image(8, 24, "brass")
    fill_rect(img, 0, 0, 8, 1, "brass_shade")
    fill_rect(img, 0, 23, 8, 24, "brass_shade")
    fill_rect(img, 0, 0, 1, 24, "brass_shade")
    fill_rect(img, 7, 0, 8, 24, "brass_shade")
    fill_rect(img, 1, 1, 7, 2, "brass_hi")
    return img


def rivet():
    img = new_image(4, 4)
    pattern = [
        ".ss.",
        "shbs",
        "sbds",
        ".ss.",
    ]
    mapping = {"s": "steel_edge", "h": "cream", "b": "iron_raised", "d": "iron_edge_dark"}
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch != ".":
                px(img, x, y, mapping[ch])
    return img


# ---------------------------------------------------------------------------
# Icons (12x12). Palettes: # = cream, + = brass_hi, d = ink, o = verdigris,
# x = danger. All original designs.
# ---------------------------------------------------------------------------

ICONS = {
    "send": [
        "............",
        "....#.......",
        "....###.....",
        ".....####...",
        ".#########..",
        ".#########..",
        ".....####...",
        "....###.....",
        "....#.......",
        "............",
        "............",
        "............",
    ],
    "send_dark": [
        "............",
        "....d.......",
        "....ddd.....",
        ".....dddd...",
        ".ddddddddd..",
        ".ddddddddd..",
        ".....dddd...",
        "....ddd.....",
        "....d.......",
        "............",
        "............",
        "............",
    ],
    "plus": [
        "............",
        "....##......",
        "....##......",
        "....##......",
        ".##########.",
        ".##########.",
        "....##......",
        "....##......",
        "....##......",
        "............",
        "............",
        "............",
    ],
    "settings": [
        "............",
        "...#..#.....",
        "..######....",
        ".########...",
        ".###..###...",
        "..##..##....",
        "..##..##....",
        ".###..###...",
        ".########...",
        "..######....",
        "...#..#.....",
        "............",
    ],
    "trash": [
        "............",
        "...#####....",
        "..#######...",
        ".#.#...#.#..",
        "..#.....#...",
        "..#.#.#.#...",
        "..#.#.#.#...",
        "..#.....#...",
        "..#######...",
        "............",
        "............",
        "............",
    ],
    "chevron_left": [
        "............",
        ".....##.....",
        "....##......",
        "...##.......",
        "..##........",
        "..##........",
        "...##.......",
        "....##......",
        ".....##.....",
        "............",
        "............",
        "............",
    ],
    "chevron_right": [
        "............",
        ".....##.....",
        "......##....",
        ".......##...",
        "........##..",
        "........##..",
        ".......##...",
        "......##....",
        ".....##.....",
        "............",
        "............",
        "............",
    ],
    "copy": [
        "............",
        "....#####...",
        "....#...#...",
        "..###.#####.",
        "..#.#.#...#.",
        "..#...#...#.",
        "..#####...#.",
        "....#.....#.",
        "....#######.",
        "............",
        "............",
        "............",
    ],
    "edit": [
        "............",
        ".......###..",
        "......#..#..",
        ".....#.#....",
        "....#..#....",
        "...#...#....",
        "..#....#....",
        ".#.....#....",
        ".##....#....",
        "..####......",
        "............",
        "............",
    ],
    "recall": [
        "............",
        "...###......",
        "..#...#.....",
        ".#....#.....",
        ".#...#####..",
        ".####...#...",
        "...#...#....",
        "....###.....",
        "............",
        "............",
        "............",
        "............",
    ],
    "recipe": [
        "............",
        ".###.###.##.",
        ".#.#.#.#.##.",
        ".###.###.##.",
        "............",
        ".###.###.##.",
        ".#.#.#.#.##.",
        ".###.###.##.",
        "............",
        "............",
        "............",
        "............",
    ],
    "usage": [
        "............",
        "...###......",
        "..#...#.....",
        "..#...##....",
        "...####.....",
        "....#.......",
        "....#.......",
        "....#.......",
        "....#.......",
        "............",
        "............",
        "............",
    ],
    "task": [
        "............",
        ".#########..",
        ".#.......#..",
        ".#.....o.#..",
        ".#....oo.#..",
        ".#.o..o..#..",
        ".#..oo...#..",
        ".#.......#..",
        ".#########..",
        "............",
        "............",
        "............",
    ],
    "source": [
        "............",
        "..####......",
        ".#....#.....",
        ".#....##....",
        "..###..#....",
        "....#..###..",
        "....#....#..",
        ".....#....#.",
        "......####..",
        "............",
        "............",
        "............",
    ],
    "warning": [
        "............",
        ".....xx.....",
        "....xxxx....",
        "....xxxx....",
        "...xxxxxx...",
        "...xxxxxx...",
        "..xxxxxxxx..",
        "..xxxxxxxx..",
        ".xxxxxxxxxx.",
        "............",
        "............",
        "............",
    ],
    "close": [
        "............",
        ".#........#.",
        "..#......#..",
        "...#....#...",
        "....#..#....",
        ".....##.....",
        ".....##.....",
        "....#..#....",
        "...#....#...",
        "..#......#..",
        ".#........#.",
        "............",
    ],
    "history": [
        "............",
        ".##.#####...",
        ".##.#####...",
        "............",
        ".##.#####...",
        ".##.#####...",
        "............",
        ".##.#####...",
        ".##.#####...",
        "............",
        "............",
        "............",
    ],
}


def gear_frame(highlight_angle):
    """8-tooth gear with one tooth highlighted; 4 frames rotate the highlight."""
    base = [
        ".....##.....",
        "..##.##.##..",
        "..########..",
        ".###....###.",
        ".###....###.",
        "..########..",
        "..##.##.##..",
        ".....##.....",
    ]
    rows = [list(r.ljust(12)) for r in base] + [list("............") for _ in range(12 - len(base))]
    hi_positions = {
        0: [(0, 5), (0, 6)],          # top tooth
        1: [(3, 10), (4, 10)],        # right tooth
        2: [(7, 5), (7, 6)],          # bottom tooth
        3: [(3, 1), (4, 1)],          # left tooth
    }
    for (y, x) in hi_positions[highlight_angle]:
        rows[y][x] = "+"
    return ["".join(r) for r in rows]


ICON_COLORS = {
    "#": "cream",
    "+": "brass_hi",
    "d": "ink",
    "o": "verdigris",
    "x": "danger",
}


def draw_icon(rows):
    img = new_image(12, 12)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:12]):
            if ch in ICON_COLORS:
                px(img, x, y, ICON_COLORS[ch])
    return img


def main():
    print("Generating original mechanical UI sprites...")
    save(panel_iron(), OUT_STYLE, "panel_iron.png")
    save(panel_inset(), OUT_STYLE, "panel_inset.png")
    save(frame_brass(), OUT_STYLE, "frame_brass.png")
    save(button_like("brass", "brass_hi", "brass_shade"), OUT_STYLE, "button_brass.png")
    save(button_like("copper", "copper_hi", "copper_shade"), OUT_STYLE, "button_copper.png")
    save(button_like("danger", "danger_hi", "danger_shade"), OUT_STYLE, "button_danger.png")
    save(tab_segment(), OUT_STYLE, "tab_segment.png")
    save(scrollbar_thumb(), OUT_STYLE, "scrollbar.png")
    save(rivet(), OUT_STYLE, "rivet.png")

    for name, rows in ICONS.items():
        save(draw_icon(rows), OUT_SPRITES, f"{name}.png")
    for i in range(4):
        save(draw_icon(gear_frame(i)), OUT_SPRITES, f"spinner_gear_{i}.png")
    print("Done.")


if __name__ == "__main__":
    main()

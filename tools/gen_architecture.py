#!/usr/bin/env python3
"""The landmark block set (CX-33): one table, everything generated from it.

Writes, for every block in BLOCKS:
  - textures/block/<id>*.png (16x16, drawn here in one consistent style: flat anime colours, a soft grain, one-pixel
    bevels lit from the top left, darker joints);
  - blockstates, block and item models (cube, top/bottom/side, pillar, glass, lamp; stairs, slabs and walls);
  - loot tables (slabs drop two when double), mineable/stairs/slabs/walls tags, en_us names;
  - src/main/java/com/dbzenith/world/arch/ArchitectureBlockList.java, the table the registry reads.
Re-run after editing: python3 tools/gen_architecture.py
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main')
RES = os.path.join(ROOT, 'resources')
ASSETS = os.path.join(RES, 'assets', 'dbzenith')
DATA = os.path.join(RES, 'data', 'dbzenith')
JAVA = os.path.join(ROOT, 'java', 'com', 'dbzenith', 'world', 'arch', 'ArchitectureBlockList.java')

# ------------------------------------------------------------------------------------------------ the table
# id, display name, shape (cube | cube_tb | pillar | glass | lamp), material (stone | hard | clay | cloth | glass | lamp
# | sand | metal), variants (stairs, slab, wall), map colour (MapColor field)
BLOCKS = [
    # ---- World Martial Arts Tournament
    ('tournament_stone', 'Tournament Stone', 'cube', 'stone', 'stairs slab wall', 'SNOW'),
    ('tournament_tiles', 'Tournament Stone Tiles', 'cube', 'stone', 'stairs slab', 'SNOW'),
    ('tournament_trim', 'Tournament Trim', 'cube_tb', 'stone', '', 'SNOW'),
    ('tournament_red_band', 'Tournament Red Band', 'cube', 'stone', '', 'COLOR_RED'),
    ('arena_tile', 'Arena Floor Tile', 'cube', 'stone', 'stairs slab', 'QUARTZ'),
    ('temple_plaster', 'Temple Plaster', 'cube', 'stone', 'slab', 'SAND'),
    ('temple_red_pillar', 'Red Lacquer Pillar', 'pillar', 'stone', '', 'COLOR_RED'),
    ('temple_gold_trim', 'Red-and-Gold Temple Trim', 'cube', 'stone', '', 'COLOR_RED'),
    ('temple_gold', 'Temple Gold Ornament', 'cube', 'metal', '', 'GOLD'),
    ('clay_roof_tiles', 'Clay Roof Tiles', 'cube', 'clay', 'stairs slab', 'TERRACOTTA_BROWN'),
    ('temple_lantern', 'Temple Lantern', 'lamp', 'lamp', '', 'COLOR_RED'),
    ('banner_cloth_red', 'Red Banner Cloth', 'cube', 'cloth', '', 'COLOR_RED'),
    ('banner_cloth_blue', 'Blue Banner Cloth', 'cube', 'cloth', '', 'COLOR_BLUE'),
    ('banner_cloth_yellow', 'Yellow Banner Cloth', 'cube', 'cloth', '', 'COLOR_YELLOW'),
    ('banner_cloth_green', 'Green Banner Cloth', 'cube', 'cloth', '', 'COLOR_GREEN'),
    # ---- Kami's Lookout
    ('lookout_marble', 'Lookout Marble', 'cube', 'stone', 'stairs slab wall', 'QUARTZ'),
    ('lookout_marble_pillar', 'Lookout Marble Pillar', 'pillar', 'stone', '', 'QUARTZ'),
    ('lookout_deck_tiles', 'Lookout Deck Tiles', 'cube', 'stone', 'slab', 'QUARTZ'),
    ('lookout_gold_dome', 'Lookout Gold Dome', 'cube', 'metal', 'stairs slab', 'GOLD'),
    ('lookout_red_band', 'Lookout Red Band', 'cube', 'stone', '', 'COLOR_RED'),
    ('lookout_hull_white', 'Lookout White Hull', 'cube', 'stone', '', 'SNOW'),
    ('lookout_hull_window', 'Lookout Hull Window', 'lamp', 'lamp', '', 'COLOR_LIGHT_BLUE'),
    ('lookout_hull_trim', 'Lookout Hull Trim', 'cube', 'stone', '', 'SAND'),
    ('lookout_hull_teal', 'Lookout Teal Hull', 'cube', 'stone', '', 'COLOR_CYAN'),
    ('lookout_awning', 'Lookout Pink Awning', 'cube', 'cloth', 'stairs slab', 'COLOR_PINK'),
    ('lookout_lamp', 'Lookout Lamp', 'lamp', 'lamp', '', 'SNOW'),
    # ---- Korin Tower
    ('korin_stone', 'Korin Stone', 'cube', 'hard', 'stairs slab wall', 'STONE'),
    ('korin_bricks', 'Korin Stone Bricks', 'cube', 'hard', '', 'STONE'),
    ('korin_panel', 'Carved Korin Panel', 'cube', 'hard', '', 'STONE'),
    # ---- Cell Games
    ('cell_tile', 'Cell Games Tile', 'cube', 'stone', 'stairs slab', 'QUARTZ'),
    ('cell_spire', 'Carved Spire Block', 'pillar', 'stone', '', 'QUARTZ'),
    ('cell_spire_cap', 'Spire Cap', 'cube', 'stone', 'wall', 'QUARTZ'),
    ('wasteland_rock', 'Wasteland Rock', 'cube', 'stone', 'stairs slab', 'TERRACOTTA_LIGHT_GRAY'),
    # ---- Frypan Mountains
    ('strata_red', 'Red Strata Rock', 'cube', 'stone', 'stairs slab', 'TERRACOTTA_RED'),
    ('strata_brown', 'Brown Strata Rock', 'cube', 'stone', 'stairs slab', 'TERRACOTTA_BROWN'),
    ('strata_tan', 'Tan Strata Rock', 'cube', 'stone', 'stairs slab', 'TERRACOTTA_ORANGE'),
    ('strata_cream', 'Cream Strata Rock', 'cube', 'stone', 'stairs slab', 'TERRACOTTA_WHITE'),
    ('frypan_sand', 'Pale Frypan Sand', 'cube', 'sand', '', 'SAND'),
    # ---- West City
    ('city_concrete', 'City Concrete', 'cube', 'stone', 'stairs slab wall', 'SNOW'),
    ('city_window', 'City Window Strip', 'lamp', 'glass', '', 'COLOR_LIGHT_BLUE'),
    ('city_glass', 'City Glass', 'glass', 'glass', '', 'COLOR_LIGHT_BLUE'),
    ('city_roof_red', 'Red City Roof', 'cube', 'clay', 'stairs slab', 'COLOR_RED'),
    ('city_roof_orange', 'Orange City Roof', 'cube', 'clay', 'stairs slab', 'COLOR_ORANGE'),
    ('city_road', 'City Road', 'cube', 'stone', 'slab', 'COLOR_GRAY'),
    ('city_road_line', 'City Road Marking', 'cube', 'stone', '', 'COLOR_GRAY'),
    ('city_sidewalk', 'City Sidewalk', 'cube', 'stone', 'slab', 'COLOR_LIGHT_GRAY'),
    ('city_streetlamp', 'City Street Lamp', 'lamp', 'lamp', '', 'SNOW'),
    ('city_pier_deck', 'Pier Decking', 'cube', 'clay', 'slab', 'WOOD'),
]


# ------------------------------------------------------------------------------------------------ painting kit

def hexrgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


class T:
    """A 16x16 canvas in float RGB."""

    def __init__(self, base, seed=0, grain=6.0):
        self.rng = np.random.default_rng(seed)
        self.c = np.zeros((16, 16, 3)) + hexrgb(base)
        if grain:
            g = self.rng.normal(0, 1, (16, 16))
            g = (g + np.roll(g, 1, 0) * 0.5 + np.roll(g, 1, 1) * 0.5) / 2
            self.c += g[..., None] * grain
        self.alpha = np.full((16, 16), 255.0)

    def rect(self, x0, y0, x1, y1, col, a=1.0):
        """Fill x0..x1, y0..y1 inclusive."""
        c = hexrgb(col) if isinstance(col, str) else np.array(col, float)
        self.c[y0:y1 + 1, x0:x1 + 1] = self.c[y0:y1 + 1, x0:x1 + 1] * (1 - a) + c * a

    def shade(self, x0, y0, x1, y1, k):
        """Lighten (k > 0) or darken a rectangle."""
        self.c[y0:y1 + 1, x0:x1 + 1] *= (1 + k)

    def px(self, x, y, col, a=1.0):
        if 0 <= x < 16 and 0 <= y < 16:
            c = hexrgb(col)
            self.c[y, x] = self.c[y, x] * (1 - a) + c * a

    def bevel(self, x0, y0, x1, y1, hi=0.12, lo=0.16):
        """A raised tile: top and left edges lit, bottom and right in shade."""
        self.c[y0, x0:x1 + 1] *= 1 + hi
        self.c[y0:y1 + 1, x0] *= 1 + hi
        self.c[y1, x0:x1 + 1] *= 1 - lo
        self.c[y0:y1 + 1, x1] *= 1 - lo

    def joints(self, col, xs=(), ys=(), a=0.85):
        for x in xs:
            self.rect(x, 0, x, 15, col, a)
        for y in ys:
            self.rect(0, y, 15, y, col, a)

    def save(self, name):
        img = np.clip(self.c, 0, 255).astype(np.uint8)
        rgba = np.dstack([img, np.clip(self.alpha, 0, 255).astype(np.uint8)])
        p = os.path.join(ASSETS, 'textures', 'block', name + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        Image.fromarray(rgba, 'RGBA').save(p)


def tiles(base, joint, n=2, seed=0, grain=4, hi=0.1, lo=0.14, inset=0):
    """n x n square tiles with beveled edges and joints between them."""
    t = T(base, seed, grain)
    s = 16 // n
    for i in range(n):
        for j in range(n):
            x0, y0 = i * s, j * s
            t.bevel(x0 + inset, y0 + inset, x0 + s - 1 - inset, y0 + s - 1 - inset, hi, lo)
    t.joints(joint, xs=[i * s for i in range(n)], ys=[j * s for j in range(n)], a=0.55)
    return t


def bricks(base, joint, rows=4, seed=0, grain=6, stagger=True):
    t = T(base, seed, grain)
    h = 16 // rows
    for r in range(rows):
        y0 = r * h
        t.rect(0, y0, 15, y0, joint, 0.75)
        off = (8 if (r % 2 and stagger) else 0)
        for x in (off % 16, (off + 8) % 16):
            t.rect(x, y0, x, y0 + h - 1, joint, 0.75)
        t.c[y0 + 1, :] *= 1.06
        # each brick its own shade
        for x0 in range(0, 16, 8):
            xs = (x0 + off) % 16
            k = t.rng.uniform(-0.04, 0.04)
            for x in range(xs + 1, xs + 8):
                t.c[y0 + 1:y0 + h, x % 16] *= 1 + k
    return t


def stripes(cols, widths, seed=0, grain=5):
    """Horizontal bands top to bottom."""
    t = T(cols[0], seed, grain)
    y = 0
    for c, w in zip(cols, widths):
        g = t.c[y:y + w].copy() - hexrgb(cols[0])
        t.c[y:y + w] = hexrgb(c) + g
        y += w
    return t


def cloth(base, seed=0):
    t = T(base, seed, 4)
    for y in range(16):
        t.c[y, :] *= 1 + 0.04 * math.sin(y * 1.6)
    for x in range(0, 16, 2):
        t.c[:, x] *= 0.97
    return t


def glow(base, core, seed=0):
    t = T(base, seed, 2)
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.sqrt((xx - 7.5) ** 2 + (yy - 7.5) ** 2) / 8
    t.c = t.c * (d[..., None].clip(0, 1)) + hexrgb(core) * (1 - d[..., None].clip(0, 1))
    return t


# ------------------------------------------------------------------------------------------------ the textures

def paint():
    W, SH = '#ECEAE4', '#CFCBC2'
    # ---- tournament: warm white stone
    t = T(W, 1, 5)
    t.bevel(0, 0, 15, 15, 0.05, 0.08)
    t.save('tournament_stone')
    t = tiles(W, SH, 2, 2, 4)
    t.save('tournament_tiles')
    t = T(W, 3, 4)                                                      # trim side: a carved band with a groove
    t.rect(0, 5, 15, 10, '#E2DED6')
    t.rect(0, 5, 15, 5, '#F8F6F2')
    t.rect(0, 10, 15, 10, '#B9B3A8')
    t.rect(0, 7, 15, 8, '#C9C3B8')
    t.save('tournament_trim')
    T(W, 4, 4).save('tournament_trim_top')
    t = stripes(['#F2F0EA', '#B8242A', '#9A1C22', '#F2F0EA'], [3, 5, 5, 3], 5, 4)   # the stands' red band
    t.rect(0, 3, 15, 3, '#D9B44A')
    t.rect(0, 12, 15, 12, '#D9B44A')
    t.save('tournament_red_band')
    t = tiles('#F4F2EC', '#BDB7AC', 2, 6, 3, 0.12, 0.18, 1)             # the ring: big beveled tiles
    t.save('arena_tile')
    t = T('#EAD9B8', 7, 5)                                              # plaster: warm cream, a faint mottle
    for _ in range(10):
        x, y = t.rng.integers(0, 16, 2)
        t.c[y, x] *= 0.96
    t.save('temple_plaster')
    t = T('#B52128', 8, 5)                                              # lacquered column: highlight down one side
    t.rect(3, 0, 4, 15, '#D2363C')
    t.rect(12, 0, 13, 15, '#8C161B')
    t.rect(0, 0, 15, 1, '#D9A932')
    t.rect(0, 14, 15, 15, '#D9A932')
    t.save('temple_red_pillar')
    t = T('#B52128', 9, 4)
    t.rect(0, 0, 15, 2, '#D9A932')
    t.rect(0, 13, 15, 15, '#D9A932')
    t.save('temple_red_pillar_top')
    t = T('#A51C22', 10, 4)                                             # red-and-gold trim: a gold meander band
    t.rect(0, 0, 15, 1, '#E0B43C')
    t.rect(0, 14, 15, 15, '#E0B43C')
    meander = ['................', '.###..###..###..', '.#.#..#.#..#.#..', '.#....#....#....', '.####.####.####.']
    for y, row in enumerate(meander):
        for x, ch in enumerate(row):
            if ch == '#':
                t.px(x, y + 5, '#E8C04A')
    t.save('temple_gold_trim')
    t = T('#E2B23A', 11, 6)                                             # gold: a sheen across
    for y in range(16):
        for x in range(16):
            t.c[y, x] *= 1 + 0.18 * math.exp(-((x - y) / 3.0) ** 2)
    t.bevel(0, 0, 15, 15, 0.1, 0.15)
    t.save('temple_gold')
    t = T('#8A5532', 12, 5)                                             # clay roof: overlapping scalloped rows
    for r in range(4):
        y0 = r * 4
        off = 2 if r % 2 else 0
        for x0 in range(-4 + off, 16, 4):
            for x in range(x0, x0 + 4):
                if 0 <= x < 16:
                    t.c[y0 + 3, x] *= 0.72
                    t.c[y0, x] *= 1.12
            if 0 <= x0 < 16:
                t.rect(x0, y0, x0, y0 + 3, '#5E3720', 0.7)
    t.save('clay_roof_tiles')
    t = glow('#C4262C', '#FFE7A0', 13)                                  # paper lantern: ribs and a warm core
    for y in (2, 7, 12):
        t.rect(0, y, 15, y, '#7A1418', 0.6)
    t.rect(0, 0, 15, 0, '#2A1A10')
    t.rect(0, 15, 15, 15, '#2A1A10')
    t.save('temple_lantern')
    for name, col in (('red', '#C8262C'), ('blue', '#2856B8'), ('yellow', '#E8B828'), ('green', '#2E9A48')):
        t = cloth(col, 14)
        t.rect(0, 0, 15, 0, '#F2F0EA', 0.6)
        t.save('banner_cloth_' + name)

    # ---- Kami's Lookout
    M = '#F3F1EC'
    t = T(M, 20, 5)                                                     # marble: soft grey veins
    for k in range(3):
        x = t.rng.uniform(0, 16)
        for y in range(16):
            xi = int(x + 2.5 * math.sin(y * 0.6 + k)) % 16
            t.c[y, xi] *= 0.93
    t.save('lookout_marble')
    t = T(M, 21, 4)
    t.rect(2, 0, 2, 15, '#D6D2CA')
    t.rect(7, 0, 8, 15, '#DCD8D0')
    t.rect(13, 0, 13, 15, '#D6D2CA')
    t.rect(3, 0, 3, 15, '#FFFFFF', 0.6)
    t.save('lookout_marble_pillar')
    t = T(M, 22, 4)
    t.bevel(1, 1, 14, 14, 0.06, 0.12)
    t.rect(4, 4, 11, 11, '#E6E2DA', 0.6)
    t.save('lookout_marble_pillar_top')
    t = tiles('#E8E6E0', '#B9B5AC', 4, 23, 3, 0.07, 0.1)
    t.save('lookout_deck_tiles')
    t = T('#E8B830', 24, 5)                                             # dome: gold shingles
    for r in range(4):
        y0 = r * 4
        off = 2 if r % 2 else 0
        for x0 in range(-4 + off, 16, 4):
            for x in range(x0, x0 + 4):
                if 0 <= x < 16:
                    t.c[y0 + 3, x] *= 0.78
                    t.c[y0, x] *= 1.15
    t.save('lookout_gold_dome')
    t = T('#B8242A', 25, 4)                                             # the red band's zigzag
    t.rect(0, 0, 15, 1, '#8E1A20')
    t.rect(0, 14, 15, 15, '#8E1A20')
    for x in range(16):
        y = 4 + abs((x % 8) - 4)
        t.px(x, y, '#E8D8C0')
        t.px(x, y + 4, '#E8D8C0', 0.7)
    t.save('lookout_red_band')
    T('#F2F0EA', 26, 4).save('lookout_hull_white')
    t = T('#9ED2EC', 27, 3)                                             # long windows: glass glowing from inside
    t.rect(0, 2, 15, 13, '#C8ECFA')
    t.rect(0, 2, 15, 2, '#5C8AAA')
    t.rect(0, 13, 15, 13, '#5C8AAA')
    t.rect(0, 5, 15, 5, '#FFFFFF', 0.5)
    t.rect(0, 0, 15, 1, '#F2F0EA')
    t.rect(0, 14, 15, 15, '#F2F0EA')
    t.save('lookout_hull_window')
    t = T('#E6D2A8', 28, 4)                                             # cream trim with gold studs
    for x in (2, 7, 12):
        t.rect(x, 6, x + 2, 8, '#C8A04A')
        t.px(x, 6, '#F0D888')
    t.save('lookout_hull_trim')
    t = T('#4CB8A0', 29, 6)                                             # teal hull: vertical panels
    for x in (0, 5, 10):
        t.rect(x, 0, x, 15, '#38927E', 0.8)
    t.rect(0, 0, 15, 0, '#6CD0B8', 0.6)
    t.save('lookout_hull_teal')
    t = cloth('#E07898', 30)
    for x in range(0, 16, 4):
        t.rect(x, 0, x + 1, 15, '#F4A8BC', 0.5)
    t.save('lookout_awning')
    glow('#E8F2FA', '#FFFFFF', 31).save('lookout_lamp')

    # ---- Korin Tower: grey stone with carved panels
    K = '#A9A8A4'
    t = T(K, 40, 7)
    t.bevel(0, 0, 15, 15, 0.04, 0.06)
    t.save('korin_stone')
    bricks(K, '#7E7D79', 4, 41, 6).save('korin_bricks')
    t = T(K, 42, 5)                                                     # a sunken carved panel with a cross motif
    t.rect(2, 2, 13, 13, '#9A9995')
    t.bevel(2, 2, 13, 13, -0.12, -0.1)
    t.rect(7, 4, 8, 11, '#8A8985')
    t.rect(4, 7, 11, 8, '#8A8985')
    t.rect(0, 0, 15, 0, '#BDBCB8')
    t.save('korin_panel')

    # ---- Cell Games
    t = tiles('#F0EEE8', '#B6B0A4', 2, 50, 3, 0.14, 0.2, 1)
    t.save('cell_tile')
    t = T('#E6E4DE', 51, 4)                                             # spire: carved vertical flutes and a band
    for x in (1, 5, 10, 14):
        t.rect(x, 0, x, 15, '#BDB8AE')
        t.rect(x + 1, 0, x + 1, 15, '#F8F6F2', 0.5)
    t.rect(0, 6, 15, 9, '#D6D2C8')
    t.rect(0, 6, 15, 6, '#F8F6F2')
    t.rect(0, 9, 15, 9, '#A8A296')
    t.save('cell_spire')
    t = T('#E6E4DE', 52, 4)
    t.bevel(0, 0, 15, 15, 0.08, 0.14)
    t.rect(5, 5, 10, 10, '#C8C4BA')
    t.save('cell_spire_top')
    t = T('#ECEAE4', 53, 4)                                             # cap: a pointed chevron
    for y in range(16):
        w = max(0, 7 - y // 2)
        t.rect(7 - w, y, 8 + w, y, '#D8D4CA', 0.6)
    t.bevel(0, 0, 15, 15, 0.08, 0.14)
    t.save('cell_spire_cap')
    t = T('#8C8478', 54, 10)                                            # wasteland rock: cracked, dusty
    for _ in range(3):
        x, y = t.rng.integers(0, 16, 2)
        for k in range(5):
            t.px(int(x + k) % 16, int(y + t.rng.integers(-1, 2)) % 16, '#5E574E')
    t.save('wasteland_rock')

    # ---- Frypan strata: each block a few fine layers of its colour
    for name, cols in (('red', ['#A64A38', '#B85A44', '#984030']), ('brown', ['#7A5238', '#8A6044', '#6C4830']),
                       ('tan', ['#C89A6A', '#D4A878', '#BC8E60']), ('cream', ['#E8D8B8', '#F0E4C8', '#DCCAA8'])):
        t = stripes([cols[0], cols[1], cols[0], cols[2], cols[1]], [3, 4, 3, 3, 3], 60 + len(name), 7)
        t.save('strata_' + name)
    t = T('#EADFC8', 66, 9)
    t.save('frypan_sand')

    # ---- West City
    C = '#F2F2EE'
    t = T(C, 70, 3)
    t.bevel(0, 0, 15, 15, 0.03, 0.05)
    t.save('city_concrete')
    t = T(C, 71, 2)                                                     # a blue band of windows with mullions
    t.rect(0, 3, 15, 12, '#4A8ED0')
    t.rect(0, 3, 15, 3, '#2E6AA8')
    for x in (0, 8):
        t.rect(x, 3, x, 12, '#E6EAF0')
    t.rect(1, 5, 6, 5, '#9CCAF0')
    t.rect(9, 5, 14, 5, '#9CCAF0')
    t.save('city_window')
    t = T('#9CC8E8', 72, 2)
    t.alpha[:, :] = 110
    t.rect(0, 0, 15, 0, '#E6EAF0')
    t.rect(0, 0, 0, 15, '#E6EAF0')
    t.alpha[0, :] = 255
    t.alpha[:, 0] = 255
    for k in range(3, 8):
        t.px(k, 10 - k, '#FFFFFF', 0.7)
        t.alpha[10 - k, k] = 180
    t.save('city_glass')
    for name, col in (('red', '#D03A30'), ('orange', '#E8822E')):
        t = T(col, 73, 4)
        for y in (3, 7, 11, 15):
            t.c[y, :] *= 0.82
            t.c[(y + 1) % 16, :] *= 1.08
        t.save('city_roof_' + name)
    t = T('#5A5C60', 75, 8)
    t.save('city_road')
    t = T('#5A5C60', 76, 8)
    t.rect(6, 0, 9, 15, '#F2F0E6')
    t.save('city_road_line')
    t = tiles('#C8C6C0', '#9E9C96', 2, 77, 4, 0.06, 0.1)
    t.save('city_sidewalk')
    glow('#F4F0E0', '#FFFFFF', 78).save('city_streetlamp')
    t = T('#9A7454', 79, 6)                                             # pier decking: boards
    for y in (0, 4, 8, 12):
        t.rect(0, y, 15, y, '#6E523A', 0.8)
    t.save('city_pier_deck')


# ------------------------------------------------------------------------------------------------ models and data

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def tex(bid):
    return 'dbzenith:block/' + bid


def faces(bid, shape):
    """The texture ids a block's sides, top and bottom use."""
    if shape in ('cube_tb',):
        return tex(bid), tex(bid + '_top'), tex(bid + '_top')
    if shape == 'pillar':
        return tex(bid), tex(bid + '_top'), tex(bid + '_top')
    return tex(bid), tex(bid), tex(bid)


def models(bid, shape, variants):
    side, top, bottom = faces(bid, shape)
    M = os.path.join(ASSETS, 'models')
    BS = os.path.join(ASSETS, 'blockstates')
    if shape == 'pillar':
        write(f'{M}/block/{bid}.json', {'parent': 'minecraft:block/cube_column', 'textures': {'end': top, 'side': side}})
        write(f'{M}/block/{bid}_horizontal.json', {'parent': 'minecraft:block/cube_column_horizontal', 'textures': {'end': top, 'side': side}})
        write(f'{BS}/{bid}.json', {'variants': {
            'axis=y': {'model': f'dbzenith:block/{bid}'},
            'axis=z': {'model': f'dbzenith:block/{bid}_horizontal', 'x': 90},
            'axis=x': {'model': f'dbzenith:block/{bid}_horizontal', 'x': 90, 'y': 90}}})
    elif shape == 'cube_tb':
        write(f'{M}/block/{bid}.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'top': top, 'bottom': bottom, 'side': side}})
        write(f'{BS}/{bid}.json', {'variants': {'': {'model': f'dbzenith:block/{bid}'}}})
    elif shape == 'glass':
        write(f'{M}/block/{bid}.json', {'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:translucent', 'textures': {'all': side}})
        write(f'{BS}/{bid}.json', {'variants': {'': {'model': f'dbzenith:block/{bid}'}}})
    else:
        write(f'{M}/block/{bid}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': side}})
        write(f'{BS}/{bid}.json', {'variants': {'': {'model': f'dbzenith:block/{bid}'}}})
    write(f'{M}/item/{bid}.json', {'parent': f'dbzenith:block/{bid}'})

    t3 = {'bottom': bottom, 'top': top, 'side': side}
    if 'stairs' in variants:
        s = bid + '_stairs'
        for suf, parent in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
            write(f'{M}/block/{s}{suf}.json', {'parent': f'minecraft:block/{parent}', 'textures': t3})
        write(f'{M}/item/{s}.json', {'parent': f'dbzenith:block/{s}'})
        write(f'{BS}/{s}.json', stairs_state(s))
    if 'slab' in variants:
        s = bid + '_slab'
        write(f'{M}/block/{s}.json', {'parent': 'minecraft:block/slab', 'textures': t3})
        write(f'{M}/block/{s}_top.json', {'parent': 'minecraft:block/slab_top', 'textures': t3})
        write(f'{M}/item/{s}.json', {'parent': f'dbzenith:block/{s}'})
        write(f'{BS}/{s}.json', {'variants': {'type=bottom': {'model': f'dbzenith:block/{s}'},
                                              'type=top': {'model': f'dbzenith:block/{s}_top'},
                                              'type=double': {'model': f'dbzenith:block/{bid}' if shape != 'pillar' else f'dbzenith:block/{bid}'}}})
    if 'wall' in variants:
        s = bid + '_wall'
        write(f'{M}/block/{s}_post.json', {'parent': 'minecraft:block/template_wall_post', 'textures': {'wall': side}})
        write(f'{M}/block/{s}_side.json', {'parent': 'minecraft:block/template_wall_side', 'textures': {'wall': side}})
        write(f'{M}/block/{s}_side_tall.json', {'parent': 'minecraft:block/template_wall_side_tall', 'textures': {'wall': side}})
        write(f'{M}/block/{s}_inventory.json', {'parent': 'minecraft:block/wall_inventory', 'textures': {'wall': side}})
        write(f'{M}/item/{s}.json', {'parent': f'dbzenith:block/{s}_inventory'})
        write(f'{BS}/{s}.json', wall_state(s))


def stairs_state(s):
    v = {}
    rot = {'east': 0, 'south': 90, 'west': 180, 'north': 270}
    for facing, y in rot.items():
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                model = f'dbzenith:block/{s}' + ('_inner' if 'inner' in shape else '_outer' if 'outer' in shape else '')
                yy = y
                if shape in ('inner_left', 'outer_left'):
                    yy = (y + 270) % 360
                x = 0
                if half == 'top':
                    x = 180
                    if shape in ('inner_left', 'outer_left'):
                        yy = y
                    elif shape in ('inner_right', 'outer_right'):
                        yy = (y + 90) % 360
                e = {'model': model}
                if x:
                    e['x'] = x
                if yy:
                    e['y'] = yy
                if x or yy:
                    e['uvlock'] = True
                v[f'facing={facing},half={half},shape={shape}'] = e
    return {'variants': v}


def wall_state(s):
    parts = [{'when': {'up': 'true'}, 'apply': {'model': f'dbzenith:block/{s}_post'}}]
    for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for h, m in (('low', '_side'), ('tall', '_side_tall')):
            a = {'model': f'dbzenith:block/{s}{m}', 'uvlock': True}
            if y:
                a['y'] = y
            parts.append({'when': {d: h}, 'apply': a})
    return {'multipart': parts}


def loot(bid, slab=False):
    entry = {'type': 'minecraft:item', 'name': f'dbzenith:{bid}'}
    if slab:
        entry['functions'] = [{'function': 'minecraft:set_count', 'count': 2, 'add': False,
                               'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'dbzenith:{bid}',
                                               'properties': {'type': 'double'}}]},
                              {'function': 'minecraft:explosion_decay'}]
    write(os.path.join(DATA, 'loot_tables', 'blocks', bid + '.json'),
          {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [entry], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def merge_tag(path, values):
    """Adds to a tag file (keeps what is there)."""
    have = []
    if os.path.exists(path):
        have = json.load(open(path)).get('values', [])
    out = list(dict.fromkeys(have + values))
    write(path, {'replace': False, 'values': out})


def main():
    paint()
    lang_path = os.path.join(ASSETS, 'lang', 'en_us.json')
    lang = json.load(open(lang_path))
    pick, axe, shovel, stairs, slabs, walls = [], [], [], [], [], []
    java_rows = []
    for bid, name, shape, mat, variants, color in BLOCKS:
        variants = variants.split()
        models(bid, shape, variants)
        ids = [(bid, '', name)]
        for v, label in (('stairs', 'Stairs'), ('slab', 'Slab'), ('wall', 'Wall')):
            if v in variants:
                ids.append((f'{bid}_{v}', v, f'{name} {label}'))
        for i, kind, label in ids:
            loot(i, slab=kind == 'slab')
            lang[f'block.dbzenith.{i}'] = label
            ns = f'dbzenith:{i}'
            if mat in ('stone', 'hard', 'clay', 'metal', 'lamp'):
                pick.append(ns)
            elif mat == 'sand':
                shovel.append(ns)
            {'stairs': stairs, 'slab': slabs, 'wall': walls}.get(kind, []).append(ns)
        java_rows.append(f'            new Spec("{bid}", Shape.{shape.upper()}, Material.{mat.upper()}, MapColor.{color}, '
                         f'{str("stairs" in variants).lower()}, {str("slab" in variants).lower()}, {str("wall" in variants).lower()})')
    lang['itemGroup.dbzenith.architecture'] = 'Dragon Block Zenith: Landmarks'
    with open(lang_path, 'w') as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write('\n')
    tags = os.path.join(RES, 'data', 'minecraft', 'tags', 'blocks')
    merge_tag(os.path.join(tags, 'mineable', 'pickaxe.json'), pick)
    merge_tag(os.path.join(tags, 'mineable', 'shovel.json'), shovel)
    merge_tag(os.path.join(tags, 'stairs.json'), stairs)
    merge_tag(os.path.join(tags, 'slabs.json'), slabs)
    merge_tag(os.path.join(tags, 'walls.json'), walls)
    itags = os.path.join(RES, 'data', 'minecraft', 'tags', 'items')
    merge_tag(os.path.join(itags, 'stairs.json'), stairs)
    merge_tag(os.path.join(itags, 'slabs.json'), slabs)
    merge_tag(os.path.join(itags, 'walls.json'), walls)

    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, 'w') as f:
        f.write('''package com.dbzenith.world.arch;

import net.minecraft.world.level.material.MapColor;

import java.util.List;

/**
 * The landmark block set (CX-33). GENERATED by tools/gen_architecture.py from its BLOCKS table (with the textures,
 * models, blockstates, loot tables, tags and names): edit the table there and re-run, do not edit this file.
 */
public final class ArchitectureBlockList {
    public enum Shape { CUBE, CUBE_TB, PILLAR, GLASS, LAMP }

    public enum Material { STONE, HARD, CLAY, CLOTH, GLASS, LAMP, SAND, METAL }

    /** One block: its id, shape, material, map colour, and whether it has stairs, slab and wall variants. */
    public record Spec(String id, Shape shape, Material material, MapColor color, boolean stairs, boolean slab, boolean wall) {}

    public static final List<Spec> ALL = List.of(
''' + ',\n'.join(java_rows) + ''');

    private ArchitectureBlockList() {}
}
''')
    print(len(BLOCKS), 'blocks,', len(stairs), 'stairs,', len(slabs), 'slabs,', len(walls), 'walls')


if __name__ == '__main__':
    main()

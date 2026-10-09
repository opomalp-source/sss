"""Writes the aura family templates and one aura file per form (CX-24).

    python3 tools/gen_auras.py

Families live in assets/dbzenith/auras/families/ and use $ colours (made from each file's "tint"). Each form gets
assets/dbzenith/auras/forms/<form>.json: "extends" its family, its own tint, sizes grown by tier, and its extras.
Hand-made files listed in KEEP are never overwritten. Re-run after changing the tables below; edit a single form's file
by hand when only that form should change (then add it to KEEP).
"""
import json, os, re

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'dbzenith', 'auras')
KEEP = {'super_saiyan_blue'}


def glow(scale=1.06, opacity=0.7, color='$glow', wraps=None):
    g = {"kind": "glow", "scale": scale, "opacity": opacity, "colors": {"rim": color}}
    if wraps is not None:
        g["wraps"] = wraps
    return g


FAMILIES = {
    # Super Saiyan style: sharp golden flames licking upward, a tall peak, a white-hot middle
    "fam_flame": {
        "silhouette": "jagged",
        "shape": {"width": 1.05, "height": 2.05, "bottom": -0.05, "widest": 0.36, "taper": 1.1, "tip": 1.15, "peak": 0.32, "flare": 0.03},
        "lobes": {"count": 3, "size": 0.02, "rise": 1.1, "rows": 1.5},
        "motion": {"pulse": 0.035, "pulseSpeed": 5.0, "sway": 0.03},
        "layers": [
            glow(1.06, 0.7),
            {"kind": "shell", "colors": {"core": "$core", "mid": "$mid", "edge": "$edge", "rim": "$white"},
             "alpha": {"core": 0.7, "edge": 1.0}, "rim": {"width": 0.04, "strength": 0.3},
             "spikes": {"count": 6, "size": 0.32, "sharpness": 0.75, "lean": 1.0},
             "motion": {"scroll": 1.8, "flicker": 0.35, "streaks": 0.5, "streakSpeed": 3.0, "stretch": 0.3, "tallFlames": 0.7, "warp": 0.35}},
            {"kind": "tongues", "colors": {"core": "$white", "mid": "$mid", "edge": "$edge"}, "alpha": {"core": 0.95, "edge": 0.9},
             "spikes": {"sharpness": 0.8},
             "tongues": {"count": 22, "length": 0.39, "width": 0.165, "life": 0.45, "top": 0.35, "rise": 0.3, "wave": 0.2},
             "motion": {"scroll": 2.2}},
            {"kind": "haze", "scale": 0.62, "blend": "add", "colors": {"core": "$white", "mid": "$core", "edge": "$mid"},
             "alpha": {"core": 0.3, "edge": 0.25}, "motion": {"speed": 2.0}},
        ],
        "particles": {"type": "sparkle", "rate": 0.6, "size": 0.8, "color": "$core"},
        "motes": {"rate": 1.2, "color": "$mid"},
        "react": {"charge": {"scale": 0.25, "height": 0.3, "wild": 1.4, "glow": 0.4, "shake": 0.03}, "move": {"trail": 1.6, "max": 0.45}},
        "light": 13, "ground": "dust",
    },
    # Rage and legendary power: huge, roaring, ragged tongues thrown high, embers, the ground shaking
    "fam_roar": {
        "silhouette": "jagged",
        "shape": {"width": 1.25, "height": 2.35, "bottom": -0.06, "widest": 0.34, "taper": 1.05, "tip": 1.2, "peak": 0.45, "flare": 0.06},
        "lobes": {"count": 3, "size": 0.025, "rise": 1.6, "rows": 1.5},
        "motion": {"pulse": 0.05, "pulseSpeed": 7.0, "sway": 0.05},
        "layers": [
            glow(1.08, 0.75),
            {"kind": "shell", "colors": {"core": "$core", "mid": "$mid", "edge": "$edge", "rim": "$deep"},
             "alpha": {"core": 0.6, "edge": 1.0}, "rim": {"width": 0.05, "strength": 0.5},
             "spikes": {"count": 7, "size": 0.38, "sharpness": 0.85, "lean": 1.2},
             "motion": {"scroll": 2.6, "flicker": 0.5, "streaks": 0.6, "streakSpeed": 4.0, "stretch": 0.34, "tallFlames": 0.9, "warp": 0.45}},
            {"kind": "tongues", "colors": {"core": "$white", "mid": "$mid", "edge": "$edge"}, "alpha": {"core": 0.95, "edge": 0.92},
             "spikes": {"sharpness": 1.0},
             "tongues": {"count": 30, "length": 0.494, "width": 0.154, "life": 0.38, "top": 0.35, "rise": 0.35, "wave": 0.25},
             "motion": {"scroll": 2.8}},
            {"kind": "haze", "scale": 0.6, "blend": "add", "colors": {"core": "$white", "mid": "$core", "edge": "$mid"},
             "alpha": {"core": 0.35, "edge": 0.3}, "motion": {"speed": 2.4}},
        ],
        "particles": {"type": "ember", "rate": 1.4, "size": 1.0, "color": "$rim"},
        "motes": {"rate": 2.0, "color": "$mid"},
        "react": {"charge": {"scale": 0.3, "height": 0.35, "wild": 1.8, "glow": 0.5, "shake": 0.05}, "move": {"trail": 1.8, "max": 0.5}},
        "light": 14, "ground": "dust",
    },
    # God ki: calm, rounded, billowing like Blue, slow soft licks, embers drifting up
    "fam_divine": {
        "silhouette": "lobed",
        "shape": {"width": 1.1, "height": 2.05, "bottom": -0.05, "widest": 0.36, "taper": 1.1, "tip": 1.05, "peak": 0.25, "flare": 0.03},
        "lobes": {"count": 3, "size": 0.02, "rise": 0.5, "rows": 1.5},
        "motion": {"pulse": 0.03, "pulseSpeed": 1.8, "sway": 0.03},
        "layers": [
            glow(1.07, 0.75),
            {"kind": "shell", "colors": {"core": "$white", "mid": "$mid", "edge": "$edge", "rim": "$core"},
             "alpha": {"core": 0.8, "edge": 1.0}, "rim": {"width": 0.05, "strength": 0.5},
             "spikes": {"count": 5, "size": 0.26, "sharpness": 0.5, "lean": 0.8},
             "motion": {"scroll": 0.9, "flicker": 0.12, "streaks": 0.35, "stretch": 0.25, "tallFlames": 0.5, "warp": 0.3}},
            {"kind": "tongues", "colors": {"core": "$white", "mid": "$mid", "edge": "$edge"}, "alpha": {"core": 0.7, "edge": 0.55},
             "spikes": {"sharpness": 0.1},
             "tongues": {"count": 9, "length": 0.364, "width": 0.2, "life": 1.1, "top": 0.45, "rise": 0.4, "wave": 0.35, "inset": 0.18},
             "motion": {"scroll": 0.8}},
            {"kind": "haze", "scale": 0.7, "colors": {"core": "$white", "mid": "$core", "edge": "$core"},
             "alpha": {"core": 0.22, "edge": 0.25}, "motion": {"speed": 1.6}},
        ],
        "particles": {"type": "ember", "rate": 0.9, "size": 0.8, "color": "$core"},
        "motes": {"rate": 0.8, "color": "$core"},
        "react": {"charge": {"scale": 0.2, "height": 0.25, "wild": 0.9, "glow": 0.45, "shake": 0.015}, "move": {"trail": 1.6, "max": 0.5}},
        "light": 12, "ground": "dust",
    },
    # Silver calm (Ultra Instinct, the ego and eye forms): thin, see-through, shimmering, wisps curling up
    "fam_wisp": {
        "silhouette": "lobed",
        "shape": {"width": 1.0, "height": 2.1, "bottom": -0.04, "widest": 0.38, "taper": 1.1, "tip": 1.1, "peak": 0.3, "flare": 0.0},
        "lobes": {"count": 3, "size": 0.02, "rise": 0.6, "rows": 1.5},
        "motion": {"pulse": 0.02, "pulseSpeed": 1.4, "sway": 0.05},
        "layers": [
            glow(1.05, 0.55),
            {"kind": "shell", "colors": {"core": "$white", "mid": "$core", "edge": "$rim", "rim": "$white"},
             "alpha": {"core": 0.18, "edge": 0.75}, "rim": {"width": 0.035, "strength": 0.6},
             "spikes": {"count": 6, "size": 0.24, "sharpness": 0.6, "lean": 1.2},
             "motion": {"scroll": 1.0, "flicker": 0.15, "streaks": 0.7, "streakSpeed": 3.5, "stretch": 0.25, "tallFlames": 0.5, "warp": 0.5}},
            {"kind": "tongues", "blend": "add", "colors": {"core": "$white", "mid": "$core", "edge": "$rim"}, "alpha": {"core": 0.8, "edge": 0.5},
             "spikes": {"sharpness": 0.2},
             "tongues": {"count": 18, "length": 0.546, "width": 0.077, "life": 1.0, "top": 0.4, "rise": 0.45, "wave": 0.6, "inset": 0.05},
             "motion": {"scroll": 1.0}},
        ],
        "particles": {"type": "sparkle", "rate": 1.6, "size": 0.8, "color": "$white"},
        "motes": {"rate": 0.7, "color": "$white"},
        "react": {"charge": {"scale": 0.15, "height": 0.2, "wild": 0.8, "glow": 0.5, "shake": 0.0}, "move": {"trail": 2.0, "max": 0.6}},
        "light": 11, "ground": "none",
    },
    # Evil and blood: a dark smouldering flame lit at its edge, embers
    "fam_dark": {
        "silhouette": "jagged",
        "shape": {"width": 1.1, "height": 2.15, "bottom": -0.05, "widest": 0.35, "taper": 1.1, "tip": 1.15, "peak": 0.32, "flare": 0.04},
        "lobes": {"count": 3, "size": 0.02, "rise": 0.9, "rows": 1.5},
        "motion": {"pulse": 0.04, "pulseSpeed": 3.0, "sway": 0.04},
        "layers": [
            glow(1.07, 0.65, '$edge'),
            {"kind": "shell", "colors": {"core": "$dark", "mid": "$deep", "edge": "$edge", "rim": "$rim"},
             "alpha": {"core": 0.55, "edge": 0.95}, "rim": {"width": 0.05, "strength": 0.7},
             "spikes": {"count": 6, "size": 0.34, "sharpness": 0.8, "lean": 1.1},
             "motion": {"scroll": 1.5, "flicker": 0.3, "streaks": 0.4, "streakSpeed": 2.5, "stretch": 0.3, "tallFlames": 0.7, "warp": 0.4}},
            {"kind": "tongues", "colors": {"core": "$deep", "mid": "$c", "edge": "$dark"}, "alpha": {"core": 0.9, "edge": 0.85},
             "spikes": {"sharpness": 0.9},
             "tongues": {"count": 22, "length": 0.429, "width": 0.154, "life": 0.5, "top": 0.35, "rise": 0.3, "wave": 0.25},
             "motion": {"scroll": 1.8}},
        ],
        "particles": {"type": "ember", "rate": 1.2, "size": 0.9, "color": "$rim"},
        "motes": {"rate": 1.2, "color": "$c"},
        "react": {"charge": {"scale": 0.25, "height": 0.3, "wild": 1.4, "glow": 0.35, "shake": 0.035}, "move": {"trail": 1.6, "max": 0.45}},
        "light": 8, "ground": "dust",
    },
    # Machines: clean and steady, fine crackling teeth, data streaming up, sparks
    "fam_tech": {
        "silhouette": "jagged",
        "shape": {"width": 1.05, "height": 2.0, "bottom": -0.05, "widest": 0.38, "taper": 1.2, "tip": 1.1, "peak": 0.2, "flare": 0.02},
        "lobes": {"count": 3, "size": 0.015, "rise": 0.6, "rows": 1.5},
        "motion": {"pulse": 0.025, "pulseSpeed": 6.0, "sway": 0.01},
        "layers": [
            glow(1.05, 0.8),
            {"kind": "shell", "colors": {"core": "$core", "mid": "$mid", "edge": "$edge", "rim": "$white"},
             "alpha": {"core": 0.45, "edge": 1.0}, "rim": {"width": 0.03, "strength": 0.6},
             "spikes": {"count": 8, "size": 0.24, "sharpness": 0.95, "lean": 0.6},
             "motion": {"scroll": 3.0, "flicker": 0.5, "streaks": 0.9, "streakSpeed": 6.0, "stretch": 0.45, "tallFlames": 0.4, "warp": 0.1}},
            {"kind": "tongues", "blend": "add", "colors": {"core": "$white", "mid": "$mid", "edge": "$edge"}, "alpha": {"core": 0.9, "edge": 0.7},
             "spikes": {"sharpness": 1.0},
             "tongues": {"count": 12, "length": 0.36, "width": 0.099, "life": 0.25, "top": 0.25, "rise": 0.2, "wave": 0.05},
             "motion": {"scroll": 3.0}},
        ],
        "particles": {"type": "sparkle", "rate": 1.0, "size": 0.6, "color": "$white"},
        "motes": {"rate": 1.4, "color": "$mid"},
        "lightning": {"rate": 1.5, "size": 0.6, "color": "$core"},
        "react": {"charge": {"scale": 0.18, "height": 0.2, "wild": 1.2, "glow": 0.6, "shake": 0.02}, "move": {"trail": 1.4, "max": 0.4}},
        "light": 12, "ground": "dust",
    },
    # Majin: pink steam, round puffs boiling up
    "fam_majin": {
        "silhouette": "lobed",
        "shape": {"width": 1.15, "height": 2.05, "bottom": -0.05, "widest": 0.36, "taper": 1.1, "tip": 1.0, "peak": 0.25, "flare": 0.05},
        "lobes": {"count": 3, "size": 0.02, "rise": 0.7, "rows": 1.5},
        "motion": {"pulse": 0.05, "pulseSpeed": 2.2, "sway": 0.04},
        "layers": [
            glow(1.06, 0.6),
            {"kind": "shell", "colors": {"core": "$core", "mid": "$mid", "edge": "$edge", "rim": "$white"},
             "alpha": {"core": 0.55, "edge": 0.9}, "rim": {"width": 0.06, "strength": 0.4},
             "spikes": {"count": 5, "size": 0.3, "sharpness": 0.35, "lean": 0.9},
             "motion": {"scroll": 1.2, "flicker": 0.2, "streaks": 0.3, "stretch": 0.28, "tallFlames": 0.6, "warp": 0.5}},
            {"kind": "tongues", "colors": {"core": "$core", "mid": "$mid", "edge": "$edge"}, "alpha": {"core": 0.7, "edge": 0.6},
             "spikes": {"sharpness": 0.0},
             "tongues": {"count": 12, "length": 0.36, "width": 0.2, "life": 1.0, "top": 0.5, "rise": 0.5, "wave": 0.4, "inset": 0.2},
             "motion": {"scroll": 0.7}},
            {"kind": "haze", "scale": 0.7, "colors": {"core": "$white", "mid": "$core", "edge": "$mid"}, "alpha": {"core": 0.25, "edge": 0.3}},
        ],
        "particles": {"type": "sparkle", "rate": 0.5, "size": 1.0, "color": "$core"},
        "motes": {"rate": 1.0, "color": "$mid"},
        "react": {"charge": {"scale": 0.25, "height": 0.25, "wild": 1.2, "glow": 0.35, "shake": 0.03}, "move": {"trail": 1.6, "max": 0.5}},
        "light": 11, "ground": "dust",
    },
    # Frost demons and perfect forms: sleek and tall, fine sharp spikes, a sheen running up
    "fam_regal": {
        "silhouette": "jagged",
        "shape": {"width": 0.98, "height": 2.2, "bottom": -0.05, "widest": 0.36, "taper": 1.05, "tip": 1.3, "peak": 0.35, "flare": 0.02},
        "lobes": {"count": 3, "size": 0.015, "rise": 0.8, "rows": 1.5},
        "motion": {"pulse": 0.025, "pulseSpeed": 2.5, "sway": 0.02},
        "layers": [
            glow(1.05, 0.8),
            {"kind": "shell", "colors": {"core": "$white", "mid": "$mid", "edge": "$edge", "rim": "$white"},
             "alpha": {"core": 0.6, "edge": 1.0}, "rim": {"width": 0.03, "strength": 0.6},
             "spikes": {"count": 7, "size": 0.3, "sharpness": 0.9, "lean": 1.4},
             "motion": {"scroll": 1.4, "flicker": 0.2, "streaks": 0.7, "streakSpeed": 3.5, "stretch": 0.27, "tallFlames": 0.6, "warp": 0.25}},
            {"kind": "tongues", "colors": {"core": "$white", "mid": "$mid", "edge": "$edge"}, "alpha": {"core": 0.95, "edge": 0.85},
             "spikes": {"sharpness": 0.9},
             "tongues": {"count": 14, "length": 0.468, "width": 0.094, "life": 0.6, "top": 0.4, "rise": 0.35, "wave": 0.12},
             "motion": {"scroll": 1.6}},
        ],
        "particles": {"type": "sparkle", "rate": 1.2, "size": 0.9, "color": "$white"},
        "motes": {"rate": 1.0, "color": "$core"},
        "react": {"charge": {"scale": 0.2, "height": 0.25, "wild": 1.1, "glow": 0.45, "shake": 0.02}, "move": {"trail": 1.6, "max": 0.45}},
        "light": 12, "ground": "dust",
    },
    # Super Saiyan 4: red flames round a gold heart
    "fam_ssj4": {
        "extends": "fam_flame",
        "shape": {"width": 1.15, "height": 2.15, "peak": 0.35, "flare": 0.04},
        "layers": [
            glow(1.07, 0.75, '$c'),
            {"kind": "shell", "colors": {"core": "#FFF2C8", "mid": "#FF9A3A", "edge": "$edge", "rim": "#FFD060"},
             "alpha": {"core": 0.65, "edge": 1.0}, "rim": {"width": 0.05, "strength": 0.6},
             "spikes": {"count": 6, "size": 0.34, "sharpness": 0.8, "lean": 1.1},
             "motion": {"scroll": 1.9, "flicker": 0.35, "streaks": 0.45, "streakSpeed": 3.0, "stretch": 0.3, "tallFlames": 0.7, "warp": 0.35}},
            {"kind": "tongues", "colors": {"core": "#FFF4D0", "mid": "#FF7A30", "edge": "$edge"}, "alpha": {"core": 0.95, "edge": 0.9},
             "spikes": {"sharpness": 0.85},
             "tongues": {"count": 24, "length": 0.416, "width": 0.165, "life": 0.45, "top": 0.35, "rise": 0.3, "wave": 0.2},
             "motion": {"scroll": 2.2}},
            {"kind": "haze", "scale": 0.62, "blend": "add", "colors": {"core": "#FFF8E0", "mid": "#FFD080", "edge": "#FFA050"},
             "alpha": {"core": 0.3, "edge": 0.25}, "motion": {"speed": 2.0}},
        ],
        "particles": {"type": "ember", "rate": 1.0, "size": 0.9, "color": "#FFD080"},
    },
    # The base form while charging: a plain flame in the fighter's own aura colour
    "fam_base": {
        "extends": "fam_flame",
        "followFighter": True, "idle": False,
        "shape": {"width": 1.0, "height": 1.95, "peak": 0.25, "flare": 0.03},
        "lobes": {"size": 0.02, "rise": 1.0},
        "particles": {"type": "none"},
        "react": {"charge": {"scale": 0.15, "height": 0.2, "wild": 1.2, "glow": 0.3, "shake": 0.03}},
    },
}

# form: (family, tint, tier, extras)
FORMS = {}


def form(fid, fam, tint, tier, **extra):
    FORMS[fid] = (fam, tint, tier, extra)


GOLD, LEGEND, SSJ4 = '#FFD23C', '#7CFF4A', '#FF3A2A'
# Saiyan line
form('super_saiyan', 'fam_flame', GOLD, 1, lightning={"rate": 0.5, "size": 0.45, "color": "#FFFBD8"})
form('super_saiyan_g2', 'fam_flame', '#FFCF30', 2, shape={"width": 1.2, "height": 2.15})
form('super_saiyan_g3', 'fam_roar', '#FFC828', 3, shape={"width": 1.4, "height": 2.3, "peak": 0.35}, motion={"pulseSpeed": 3.5},
     particles={"type": "sparkle", "rate": 0.5})
form('super_saiyan_2', 'fam_flame', '#FFD840', 2, lightning={"rate": 3.5, "size": 1.0, "color": "#7FD4FF"},
     layers_mod={1: {"spikes": {"count": 7, "size": 0.36, "sharpness": 0.85}, "motion": {"streaks": 0.65}}})
form('super_saiyan_3', 'fam_flame', '#FFDA48', 3, shape={"height": 2.45, "peak": 0.55, "flare": 0.04},
     lightning={"rate": 5, "size": 1.2, "color": "#7FD4FF"},
     react={"charge": {"shake": 0.05, "wild": 1.8}})
form('super_saiyan_god', 'fam_divine', '#FF3B3B', 4, layers_mod={1: {"colors": {"edge": "#E81E3A", "mid": "#FF8A7A", "rim": "#FFD0A0"}}},
     particles={"type": "ember", "rate": 1.2, "color": "#FFB080"})
form('ultimate', 'fam_divine', '#F4F8FF', 4, particles={"type": "sparkle", "rate": 1.2})
form('super_saiyan_4', 'fam_ssj4', SSJ4, 5)
form('ssj4_full_power', 'fam_ssj4', '#FF5A3A', 6, lightning={"rate": 2.5, "size": 0.9, "color": "#FFE0A0"},
     react={"charge": {"wild": 1.7, "shake": 0.045}})
form('ssj4_limit_breaker', 'fam_divine', '#FF8AE0', 7, lightning={"rate": 2.0, "size": 0.9, "color": "#FFE8FA"},
     layers_mod={1: {"colors": {"edge": "#FF4A9A", "rim": "#FFD060"}}})
form('super_saiyan_rage', 'fam_flame', '#9AD8FF', 5, lightning={"rate": 4.5, "size": 1.1, "color": "#E8F8FF"},
     layers_mod={1: {"colors": {"edge": "#3AA8FF", "rim": "#FFE65C"}}}, react={"charge": {"wild": 1.8}})
form('beast_awakening', 'fam_roar', '#D070FF', 5, lightning={"rate": 3.5, "size": 1.1, "color": "#FF4A6A"},
     layers_mod={1: {"colors": {"core": "#FFFFFF", "mid": "#C25CFF", "edge": "#9A2CFF", "rim": "#FF3A6A"}}},
     particles={"type": "ember", "color": "#FF6A8A"})
# Legendary
form('wrathful', 'fam_roar', '#B8FF6A', 1, shape={"width": 1.15, "height": 2.2})
form('lssj', 'fam_roar', LEGEND, 1)
form('lssj2', 'fam_roar', LEGEND, 2, lightning={"rate": 3, "size": 1.0, "color": "#E8FFD0"})
form('lssj3', 'fam_roar', '#6AFF3A', 3, lightning={"rate": 4.5, "size": 1.2, "color": "#E8FFD0"}, shape={"peak": 0.6})
form('lssj_c_type', 'fam_flame', LEGEND, 2)
form('lssj_full_power', 'fam_roar', LEGEND, 3, lightning={"rate": 4, "size": 1.1, "color": "#E8FFD0"})
form('lssj_controlled', 'fam_divine', LEGEND, 4, lightning={"rate": 2, "size": 0.9, "color": "#E8FFD0"})
form('lssj4', 'fam_ssj4', SSJ4, 5, shape={"width": 1.35, "height": 2.45})
form('lssj4_full_power', 'fam_ssj4', '#FF5A3A', 6, shape={"width": 1.4, "height": 2.55}, lightning={"rate": 3, "size": 1.0, "color": "#FFE0A0"})
form('lssj4_limit_breaker', 'fam_divine', '#FF8AE0', 7, shape={"width": 1.3}, lightning={"rate": 2.5, "size": 1.0, "color": "#FFE8FA"})
# Ultra Instinct
form('ultra_instinct_sign', 'fam_wisp', '#CFE0FF', 5, layers_mod={1: {"colors": {"edge": "#7FA8FF", "rim": "#E8F0FF"}}})
form('ultra_instinct', 'fam_wisp', '#EEF4FF', 6, layers_mod={1: {"alpha": {"core": 0.28}, "colors": {"edge": "#B8D0FF"}}},
     particles={"rate": 2.4}, tongues={"count": 26})
# Human
form('full_power', 'fam_flame', '#F2F6FF', 1)
form('buffed', 'fam_roar', '#FFE8C0', 2, shape={"width": 1.2, "height": 2.15}, particles={"type": "sparkle", "rate": 0.4})
form('potential_unleashed', 'fam_divine', '#FFFFFF', 3, layers_mod={1: {"colors": {"edge": "#D8E4FF"}}})
form('transcendent', 'fam_wisp', '#E0F0FF', 4)
form('surge', 'fam_divine', '#F0E6C8', 2)
form('surge_overflow', 'fam_divine', '#F8EAC0', 3, lightning={"rate": 1.0, "size": 0.6})
form('godly_surge', 'fam_divine', '#FFF4D0', 4, particles={"type": "sparkle", "rate": 1.4})
form('no_ego_zone', 'fam_wisp', '#E8F4FF', 3)
form('godly_ego_zone', 'fam_wisp', '#C8E0FF', 4, particles={"rate": 2.0})
form('inner_eye', 'fam_wisp', '#D8C8FF', 2)
form('awakened_eye', 'fam_wisp', '#C0A8FF', 3)
form('godly_eye', 'fam_wisp', '#E8D8FF', 4, particles={"rate": 2.0})
# Namekian
form('giant_namekian', 'fam_flame', '#B8FFB0', 1)
form('super_namekian', 'fam_flame', '#8CFF7A', 2)
form('orange_namekian', 'fam_roar', '#FF9A3C', 3)
form('dragon_clan', 'fam_divine', '#40FFB0', 4, particles={"type": "sparkle", "rate": 1.2})
form('namekian_warlord', 'fam_roar', '#FFB050', 4, lightning={"rate": 3, "size": 1.0})
form('dragon_sage', 'fam_divine', '#E8FFF0', 3)
form('demon_namekian', 'fam_dark', '#B070FF', 3)
form('demon_king', 'fam_dark', '#8A40E0', 4, lightning={"rate": 3, "size": 1.0, "color": "#E0B0FF"})
# Frost demon
form('second_form', 'fam_regal', '#E6C8FF', 1)
form('third_form', 'fam_regal', '#D8B0FF', 2)
form('final_form', 'fam_regal', '#C890FF', 3)
form('golden_form', 'fam_regal', '#FFD23C', 4, layers_mod={1: {"colors": {"rim": "#FFFFFF", "edge": "#FFB000"}}},
     particles={"rate": 2.0, "color": "#FFF4C0"})
form('mutant_overlord', 'fam_roar', '#FF5AC8', 4)
form('mutant_god', 'fam_divine', '#C040FF', 5, lightning={"rate": 2.5, "size": 1.0, "color": "#F0C0FF"})
form('metal_shell', 'fam_tech', '#C8D8E8', 1)
form('metal_overclock', 'fam_tech', '#A8C0E0', 2, lightning={"rate": 3, "size": 0.9})
form('metal_god_core', 'fam_tech', '#E8F0FF', 3, lightning={"rate": 3.5, "size": 1.0})
# Majin
form('evil_majin', 'fam_majin', '#C05080', 1)
form('super_majin', 'fam_majin', '#FF70B0', 2)
form('pure_majin', 'fam_majin', '#FF4FA0', 3, react={"charge": {"wild": 1.6, "shake": 0.04}}, motion={"pulseSpeed": 4.0})
form('primordial_majin', 'fam_majin', '#FF2080', 4, lobes={"rise": 1.2})
form('pure_corruption', 'fam_dark', '#9A70C0', 3, lightning={"rate": 2.5, "size": 0.9})
# Android and cyborg
form('upgrade_mk2', 'fam_tech', '#9AD0FF', 1)
form('upgrade_mk3', 'fam_tech', '#70B8FF', 2)
form('super_android', 'fam_tech', '#FF6060', 3, lightning={"rate": 3, "size": 1.0})
form('infinite_core', 'fam_tech', '#60E0FF', 4, lightning={"rate": 3.5, "size": 1.0})
form('overclock', 'fam_tech', '#FFC060', 1)
form('full_conversion', 'fam_tech', '#C0C8D0', 2)
form('machine_mutant', 'fam_tech', '#60FFB0', 3)
form('omega_frame', 'fam_tech', '#FF6040', 4, lightning={"rate": 4, "size": 1.1})
# Vampire
form('thirst', 'fam_dark', '#A01028', 1)
form('blood_rush', 'fam_dark', '#C01030', 2, motion={"pulseSpeed": 6.0})
form('nightborn', 'fam_dark', '#8A0A20', 2)
form('elder_blood', 'fam_dark', '#600818', 3, lightning={"rate": 2.5, "size": 0.9, "color": "#FF4060"})
form('crimson_sovereign', 'fam_divine', '#FF1838', 4, layers_mod={1: {"colors": {"core": "#FFE0E4"}}})
form('blood_moon_monarch', 'fam_dark', '#FF0030', 5, lightning={"rate": 3.5, "size": 1.1, "color": "#FF8090"},
     shape={"width": 1.35, "height": 2.45})
# Bio-android
form('cell_surge', 'fam_flame', '#7ADA60', 1)
form('semi_perfect', 'fam_flame', '#9AFF70', 2)
form('perfect', 'fam_regal', '#70FF90', 2)
form('super_perfect', 'fam_regal', '#C0FF70', 3, lightning={"rate": 3, "size": 1.0})
form('ultimate_perfect', 'fam_divine', '#E8FFC0', 4)
form('zenith_perfect', 'fam_regal', '#60FFE0', 5, lightning={"rate": 3.5, "size": 1.1})
# Tuffle
form('focus_protocol', 'fam_tech', '#C8F0A0', 1)
form('neural_overclock', 'fam_tech', '#D0FF90', 2)
form('machine_ascendant', 'fam_tech', '#A0E870', 2)
form('revenge_engine', 'fam_roar', '#FF6040', 3, lightning={"rate": 3, "size": 1.0})
form('tuffle_king', 'fam_divine', '#FFE08A', 4)
form('golden_tuffle', 'fam_flame', '#FFC23C', 5, lightning={"rate": 3, "size": 1.0})
# Genetic alien
form('adrenal_rush', 'fam_flame', '#9AD8F0', 1)
form('mutation_alpha', 'fam_flame', '#8AE0FF', 2)
form('mutation_beta', 'fam_roar', '#6AC8FF', 2)
form('berserker', 'fam_roar', '#FF8A3A', 3, lightning={"rate": 3, "size": 1.0})
form('apex_mutation', 'fam_regal', '#FFD0FF', 4)
form('cosmic_apex', 'fam_divine', '#9A8AFF', 5, lightning={"rate": 2.5, "size": 1.0}, particles={"type": "sparkle", "rate": 2.0})
# Core person
form('serene_mind', 'fam_divine', '#FFF8E0', 1)
form('divine_focus', 'fam_divine', '#FFF2C8', 2)
form('kai_awakening', 'fam_divine', '#FFE8A0', 2)
form('supreme_kai', 'fam_divine', '#FFF8E0', 3, particles={"type": "sparkle", "rate": 1.5})
form('grand_kai_mantle', 'fam_wisp', '#FFFFFF', 4)
form('demon_blood', 'fam_dark', '#D04040', 1)
form('demon_mark', 'fam_dark', '#FF5050', 2)
form('dark_evolution', 'fam_dark', '#D02040', 2)
form('demon_lord', 'fam_roar', '#A01030', 3, lightning={"rate": 3, "size": 1.0, "color": "#FF6070"},
     layers_mod={1: {"colors": {"core": "#3A0810", "mid": "#801020"}}})
form('demon_god', 'fam_dark', '#6A0A20', 4, lightning={"rate": 3.5, "size": 1.1, "color": "#FF4060"}, shape={"width": 1.3, "height": 2.35})


def deep_merge(base, top):
    out = json.loads(json.dumps(base))
    for k, v in top.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = deep_merge(out[k], v)
        else:
            out[k] = v
    return out


def resolved_family(name):
    f = FAMILIES[name]
    if 'extends' in f:
        base = resolved_family(f['extends'])
        top = {k: v for k, v in f.items() if k != 'extends'}
        return deep_merge(base, top)
    return f


def build(fid, fam, tint, tier, extra):
    famj = resolved_family(fam)
    out = {"extends": fam, "forms": [fid], "tint": tint}
    shape = dict(extra.get('shape', {}))
    w = shape.get('width', famj['shape']['width'])
    h = shape.get('height', famj['shape']['height'])
    if 'shape' in extra and 'width' in extra['shape']:
        w = w * WIDTH_K                                # (the family's own width is scaled when its file is written)
    if 'shape' in extra and 'height' in extra['shape']:
        h = h * HEIGHT_K
    if 'peak' in shape:
        shape['peak'] = round(shape['peak'] * 0.3, 3)
    fam_w, fam_h = famj['shape']['width'], famj['shape']['height']
    if 'shape' not in extra or 'width' not in extra['shape']:
        w = fam_w * WIDTH_K
    if 'shape' not in extra or 'height' not in extra['shape']:
        h = fam_h * HEIGHT_K
    shape['width'] = round(w * (1 + 0.02 * tier), 3)
    shape['height'] = round(h * (1 + 0.05 * tier), 3)
    out['shape'] = shape
    for key in ('lobes', 'motion', 'react', 'lightning'):
        if key in extra:
            out[key] = extra[key]
    if 'particles' in extra and fam == 'fam_wisp':
        out['particles'] = extra['particles']
    elif 'particles' in extra and 'color' in extra['particles']:
        out['particles'] = {"color": extra['particles']['color']}
    # layers: a form changes only what it names, so copy the family's list and lay the changes over it
    layers = json.loads(json.dumps(famj['layers']))
    mods = dict(extra.get('layers_mod', {}))
    for i, l in enumerate(layers):
        if l['kind'] == 'tongues':
            t = l.setdefault('tongues', {})
            t['count'] = int(t.get('count', 18) + 2 * tier)
            if 'tongues' in extra:
                t.update(extra['tongues'])
    for i, m in mods.items():
        if i == 1:
            for j, l in enumerate(layers):
                if l['kind'] == 'shell':
                    layers[j] = deep_merge(l, m)
        else:
            layers[i] = deep_merge(layers[i], m)
    out['layers'] = layers
    if tier >= 3:
        r = out.setdefault('react', {}).setdefault('charge', {})
        r.setdefault('shake', round(famj['react']['charge']['shake'] * (1 + 0.15 * tier), 3))
    return out


def kaioken():
    """Kaioken over any form (CX-24): a far bigger red shell, sharp and flaring, pink to near-white inside."""
    def layers(spikes, size, flicker, scroll, tongues, length):
        return [
            glow(1.06, 0.85, '$edge'),
            {"kind": "shell", "silhouette": "jagged",
             "colors": {"core": "#FFE6EA", "mid": "#FF8C9C", "edge": "$edge", "rim": "#FF1030"},
             "alpha": {"core": 0.22, "edge": 1.0}, "rim": {"width": 0.06, "strength": 0.8},
             "spikes": {"count": spikes, "size": size, "sharpness": 0.9, "lean": 1.2},
             "motion": {"scroll": scroll, "flicker": flicker, "streaks": 0.85, "streakSpeed": 6.0, "stretch": 0.34,
                        "tallFlames": 1.0, "warp": 0.7}},
            {"kind": "tongues", "silhouette": "jagged",
             "colors": {"core": "#FFF0F2", "mid": "#FF6A80", "edge": "$edge"}, "alpha": {"core": 0.95, "edge": 0.95},
             "spikes": {"sharpness": 1.0},
             "tongues": {"count": tongues, "length": length, "width": 0.132, "life": 0.33, "top": 0.4, "rise": 0.35, "wave": 0.2},
             "motion": {"scroll": scroll}},
            {"kind": "shell", "blend": "add", "scale": 0.9, "silhouette": "lobed",
             "colors": {"core": "#FFF4F6", "mid": "#FFB0C0", "edge": "#FF5068"}, "alpha": {"core": 0.12, "edge": 0.3},
             "rim": {"strength": 0.0}, "spikes": {"count": 6, "size": 0.2, "sharpness": 0.6},
             "motion": {"scroll": scroll * 0.8, "flicker": 0.2, "streaks": 0.6, "streakSpeed": 6.0}, "seed": 9.1},
        ]
    return {
        "technique": "kaioken", "tint": "#FF2A1E",
        "wrap": {"scale": 1.15, "height": 1.22},
        "silhouette": "jagged",
        "shape": {"width": 1.3, "height": 2.4, "bottom": -0.08, "widest": 0.34, "taper": 1.05, "tip": 1.2, "peak": 0.5, "flare": 0.08},
        "lobes": {"count": 3, "size": 0.02, "rise": 1.8, "rows": 1.5},
        "motion": {"pulse": 0.015, "pulseSpeed": 3.0, "sway": 0.015},
        "layers": layers(7, 0.38, 0.3, 2.2, 26, 0.4),
        "particles": {"type": "ember", "rate": 1.6, "size": 0.8, "color": "#FFD8DE"},
        "motes": {"rate": 1.6, "color": "#FF8090"},
        "react": {"charge": {"scale": 0.25, "height": 0.3, "wild": 1.4, "glow": 0.5, "shake": 0.03}, "move": {"trail": 1.8, "max": 0.5}},
        "grow": {"scale": 0.02, "height": 0.025, "wild": 0.03},
        "tiers": [
            {"from": 1},
            {"from": 11, "wrap": {"scale": 1.25, "height": 1.35},
             "shape": {"peak": 0.75, "flare": 0.1}, "motion": {"pulse": 0.02, "pulseSpeed": 3.5},
             "layers": layers(8, 0.42, 0.4, 2.8, 36, 0.5),
             "particles": {"rate": 2.6}, "lightning": {"rate": 2.5, "size": 1.0, "color": "#FFE0E6"},
             "react": {"charge": {"wild": 1.8, "shake": 0.04}},
             "grow": {"scale": 0.01, "height": 0.012, "wild": 0.02}},
        ],
        "light": 13, "ground": "dust",
    }


WIDTH_K, HEIGHT_K = 1.5, 0.75    # about as wide as the fighter is tall, about 1.45 times as tall (with the peak)


# How each family moves, drawn like anime: drawings a second (0: smooth) and how far its tongues smear upward.
TIMING = {'fam_flame': (15, 0.085), 'fam_roar': (15, 0.1), 'fam_divine': (10, 0.07), 'fam_wisp': (0, 0.14),
          'fam_dark': (12, 0.085), 'fam_tech': (20, 0.05), 'fam_majin': (10, 0.08), 'fam_regal': (12, 0.08),
          'fam_ssj4': (15, 0.085), 'fam_base': (15, 0.085), 'kaioken': (10, 0.1)}


def anime(fam, keep_particles=False, timing=(15, 0.12), ghost=True):
    """The hollow anime look (after the user's reference video): each flame shell becomes a bright band behind its
    flame edge with an almost clear middle; the layers that fill the middle (inner flames, hazes, added light shells)
    go; small streaks of light rise inside instead of sparkles or embers."""
    layers = []
    for l in fam.get('layers', []):
        if l['kind'] in ('tongues', 'haze') or (l['kind'] == 'shell' and l.get('blend') == 'add'):
            continue
        if l['kind'] == 'shell':
            l = json.loads(json.dumps(l))
            l['band'] = 0.17
            l.setdefault('alpha', {})['core'] = 0.12                         # a light tint in the middle, not a fill
            l['alpha']['edge'] = 1.0
            cols = l.setdefault('colors', {})
            if cols.get('mid') in ('$mid', '$rim', '$glow'):
                cols['mid'] = '$c'                     # the band just inside the edge: the form's full colour
            sp = l.setdefault('spikes', {})
            sp['size'] = round(min(0.37, max(sp.get('size', 0.3) * 0.85, 0.27)), 3)   # steep tongues, wilder auras still deeper
            sp['sharpness'] = max(sp.get('sharpness', 0.8), 0.9)               # slim, needle-sharp tips
            l.setdefault('motion', {})['stretch'] = 0.5                      # about five up each side
            l['motion']['fps'], l['motion']['smear'] = timing
        if l['kind'] == 'glow':
            l = json.loads(json.dumps(l))
            l['scale'] = 1.13
            l['opacity'] = 1.0                         # a strong, wide halo round the band
            if l.get('colors', {}).get('rim') == '$glow':
                l['colors']['rim'] = '$c'
        layers.append(l)
    shells = [l for l in layers if l['kind'] == 'shell']
    if shells:
        # a wide, soft bloom round everything, outermost
        main = shells[0]
        layers.insert(0, {"kind": "glow", "scale": 1.24, "opacity": 0.38, "colors": {"rim": main['colors'].get('edge', '$c')}})
    if shells and ghost:
        # ghost flames: a smaller, dimmer copy of the band inside the first, out of step with it, for depth
        main = shells[0]
        ghost = json.loads(json.dumps(main))
        ghost.update({"scale": 0.84, "heightScale": 0.9, "band": 0.12, "seed": 23.7, "opacity": 0.5})
        ghost['motion'] = dict(main['motion'])
        ghost['motion']['scroll'] = round(main['motion'].get('scroll', 1.5) * 1.3, 3)
        layers.append(ghost)
    if 'layers' in fam:
        fam['layers'] = layers
    if not keep_particles and 'particles' in fam:
        fam['particles'] = {"type": "flecks", "rate": 1.4, "size": 1.0, "color": fam['particles'].get('color', '$core')}
    return fam


def flame_proportions(obj):
    """The aura's outline around a Minecraft fighter: about as wide as it is tall, a flame top narrowing to a point."""
    sh = obj.get('shape')
    if isinstance(sh, dict):
        if 'width' in sh: sh['width'] = round(sh['width'] * WIDTH_K, 3)
        if 'height' in sh: sh['height'] = round(sh['height'] * HEIGHT_K, 3)
        sh['bottom'] = -0.08
        if 'peak' in sh: sh['peak'] = round(sh['peak'] * 0.3, 3)   # a crown of tongues on top, not one needle
        if 'taper' in sh: sh['taper'] = 1.8
        if 'tip' in sh: sh['tip'] = 0.62
        if 'widest' in sh: sh['widest'] = 0.4
    for t in obj.get('tiers', []):
        flame_proportions(t)
    return obj


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if 'auras' in path and 'families' in path or os.path.basename(path) == 'kaioken.json':
        obj = flame_proportions(json.loads(json.dumps(obj)))
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def main():
    for name, fam in FAMILIES.items():
        anime(fam, keep_particles=(name == 'fam_wisp'), timing=TIMING.get(name, (15, 0.12)))
    for name, fam in FAMILIES.items():
        write(os.path.join(ROOT, 'families', name + '.json'), fam)
    for fid, (fam, tint, tier, extra) in FORMS.items():
        if fid in KEEP:
            continue
        write(os.path.join(ROOT, 'forms', fid + '.json'), build(fid, fam, tint, tier, extra))
    write(os.path.join(ROOT, 'base.json'), {"extends": "fam_base", "forms": ["base"], "tint": "#7FC8FF"})
    k = kaioken()
    # Kaioken goes over another aura: no ghost copy of its own (four bands snapping read as jitter), and it keeps
    # time with the calm god auras it is mostly worn over
    anime(k, timing=TIMING['kaioken'], ghost=False)
    for t in k['tiers']:
        anime(t, timing=TIMING['kaioken'], ghost=False)
    write(os.path.join(ROOT, 'techniques', 'kaioken.json'), k)
    print(len(FAMILIES), 'families,', len(FORMS), 'forms')


if __name__ == '__main__':
    main()

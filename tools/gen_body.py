#!/usr/bin/env python3
"""The generated bodies' anatomy, painted in high detail (CX-28; replaces the 3D physique of CX-27 at the user's
request: "no 3D at all, just make them highly textured").

Writes 256x256 skins in the player layout (four texture pixels per model pixel) for the three builds, to both
textures/entity/body_painted/ and body_hd/: greyscale luminance that BodySkinLayer tints by the skin tone. Every muscle
is a lit dome (light from above, so its top catches the light and its underside falls into shade) with anime-style ink
along its lower edges; the hands are painted as relaxed fists (knuckles, finger creases, a thumb with its nail) and the
feet as bare feet (five toes with nails, ankle bones, arch, heel), all on the vanilla blocks, no extra geometry.
The head keeps its old painting (FaceLayer draws the face). Re-run after editing: python3 tools/gen_body.py
"""
import os
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'dbzenith', 'textures', 'entity')
S = 4                                          # texture pixels per model pixel
BASE, INK = 0.93, 0.50
LIGHT = np.array([0.0, -0.72, 0.69])          # (u right, v down, out of the face): from above and in front

# parts: texture origin, width, height, depth in model pixels (64x64 layout)
PARTS = {'head': (0, 0, 8, 8, 8), 'body': (16, 16, 8, 12, 4), 'rarm': (40, 16, 4, 12, 4), 'larm': (32, 48, 4, 12, 4),
         'rleg': (0, 16, 4, 12, 4), 'lleg': (16, 48, 4, 12, 4)}


def rect(part, face):
    U, V, W, H, D = PARTS[part]
    return {'front': (U + D, V + D, W, H), 'back': (U + 2 * D + W, V + D, W, H), 'west': (U, V + D, D, H),
            'east': (U + D + W, V + D, D, H), 'top': (U + D, V, W, D), 'bottom': (U + D + W, V, W, D)}[face]


class Face:
    """One face's canvas: u 0..1 left to right as seen from outside, v 0..1 top to bottom, aspect = w/h."""

    def __init__(self, w, h, shade=0.0, seed=0):
        self.w, self.h = w * S, h * S
        self.aspect = w / h
        uu = (np.arange(self.w) + 0.5) / self.w
        vv = (np.arange(self.h) + 0.5) / self.h
        self.u, self.v = np.meshgrid(uu, vv)
        rng = np.random.default_rng(seed)
        grain = rng.normal(0, 1, (self.h, self.w))
        k = np.ones(3) / 3                                                       # soft skin grain, not speckle
        grain = np.apply_along_axis(lambda r: np.convolve(r, k, 'same'), 1, grain)
        grain = np.apply_along_axis(lambda r: np.convolve(r, k, 'same'), 0, grain)
        self.lum = BASE + shade + 0.012 * grain
        self.ink = np.zeros_like(self.lum)                                       # 0..1 line strength
        self.height = np.zeros_like(self.lum)                                    # texture pixels

    def muscle(self, cu, cv, ru, rv, ang=0.0, depth=1.0, line=1.0, under=0.75, light=1.0):
        """A muscle: a smooth bump in the face's height map (centre, radii as shares of the face, turned by ang
        radians, height by depth); strongly drawn ones (line > 0.6) also get a thin ink arc along their lower edge."""
        du = (self.u - cu) * self.aspect
        dv = self.v - cv
        c, s = np.cos(ang), np.sin(ang)
        a = (du * c + dv * s) / (ru * self.aspect)
        b = (-du * s + dv * c) / rv
        r2 = a * a + b * b
        size = min(ru * self.aspect * self.h, rv * self.h)                       # texture pixels
        bump = np.clip(1 - r2, 0, 1) ** 1.6 * depth * light * size * 0.55
        self.height = np.maximum(self.height, self.height * 0.35 + bump)
        if line > 0.6:
            r = np.sqrt(r2)
            band = np.clip(1 - np.abs(r - 0.97) / 0.06, 0, 1)
            lower = np.clip((b - 0.15) / 0.6, 0, 1)
            self.ink = np.maximum(self.ink, band * lower * (line - 0.45) * 0.9)

    def stroke(self, pts, width=0.6, strength=1.0):
        """An ink polyline in face coordinates (u, v pairs); width in texture pixels."""
        px, py = self.u * self.w, self.v * self.h
        best = np.full_like(self.lum, 1e9)
        for (u0, v0), (u1, v1) in zip(pts[:-1], pts[1:]):
            ax, ay, bx, by = u0 * self.w, v0 * self.h, u1 * self.w, v1 * self.h
            dx, dy = bx - ax, by - ay
            l2 = dx * dx + dy * dy or 1e-9
            t = np.clip(((px - ax) * dx + (py - ay) * dy) / l2, 0, 1)
            best = np.minimum(best, np.hypot(ax + t * dx - px, ay + t * dy - py))
        self.ink = np.maximum(self.ink, np.clip(1 - (best - width * 0.5) / 0.7, 0, 1) * strength)

    def tone(self, mask, dl):
        self.lum = np.where(mask, self.lum + dl, self.lum)

    def ellipse(self, cu, cv, ru, rv):
        return ((self.u - cu) / ru) ** 2 + ((self.v - cv) / rv) ** 2 < 1

    def edges(self, left=0.05, right=0.05, top=0.0, bottom=0.0):
        """Shade where the face turns away (its edges), like a rounded limb."""
        e = np.clip(1 - self.u / 0.18, 0, 1) * left + np.clip(1 - (1 - self.u) / 0.18, 0, 1) * right
        e = e + np.clip(1 - self.v / 0.1, 0, 1) * top + np.clip(1 - (1 - self.v) / 0.1, 0, 1) * bottom
        self.lum = self.lum - e

    def mirror(self):
        """Left half copied onto the right (symmetric faces are painted on their left half only)."""
        half = self.w // 2
        for m in (self.lum, self.ink, self.height):
            m[:, self.w - half:] = m[:, :half][:, ::-1]

    def flip(self):
        self.lum = self.lum[:, ::-1].copy()
        self.ink = self.ink[:, ::-1].copy()
        self.height = self.height[:, ::-1].copy()

    def result(self, k):
        # light from the height map's slope (from above and in front), and shade in the valleys between muscles
        h = self.height * (0.6 + 0.4 * min(1.3, k))
        gy, gx = np.gradient(h)
        n = np.stack([-gx, -gy, np.ones_like(h)], -1)
        n /= np.linalg.norm(n, axis=-1, keepdims=True)
        lit = (n @ np.array([0.0, -0.6, 0.8])) - 0.8
        blur = h
        for _ in range(3):
            blur = (np.roll(blur, 2, 0) + np.roll(blur, -2, 0) + np.roll(blur, 2, 1) + np.roll(blur, -2, 1) + blur) / 5
        cavity = np.clip(blur - h, 0, None)
        lum = self.lum + 0.55 * lit - 0.035 * cavity + 0.006 * h
        ink = self.ink * (0.55 + 0.45 * min(1.0, k))                            # lean builds draw lighter lines
        lum = lum * (1 - ink) + INK * ink
        # a touch of cel banding over the soft shading: the painted look
        cel = np.round(lum / 0.035) * 0.035
        return np.clip(0.6 * lum + 0.4 * cel, 0, 1)


# ------------------------------------------------------------------------------------------------ the torso

def torso_front(k):
    f = Face(8, 12, 0.0, 1)
    d = k
    # pecs: heavy slabs, the outer edge sweeping up to the shoulder, a deep line underneath
    f.muscle(0.27, 0.19, 0.25, 0.13 + 0.02 * d, ang=-0.18, depth=0.9 + 0.4 * d, line=1.0)
    # the clavicle and the sternum valley
    f.stroke([(0.08, 0.035), (0.3, 0.06), (0.46, 0.05)], 0.8, 0.45)
    f.stroke([(0.5, 0.1), (0.5, 0.28)], 0.5, 0.3)
    # serratus fingers under the arm
    for i, cv in enumerate((0.335, 0.405, 0.47)):
        f.muscle(0.08, cv, 0.06, 0.028, ang=-0.45, depth=0.7, line=0.55 * min(1.0, d), light=0.8)
    # the six-pack: three rows, the lowest a little narrower, a deep centre line
    for i, (cv, rv) in enumerate(((0.405, 0.062), (0.525, 0.064), (0.645, 0.062))):
        f.muscle(0.39, cv, 0.11 if i < 2 else 0.1, rv, depth=0.8 + 0.5 * d, line=0.8)
    f.stroke([(0.5, 0.34), (0.5, 0.72)], 0.6, 0.45)
    # lower abs and the V of the hips
    f.muscle(0.4, 0.75, 0.09, 0.07, depth=0.6, line=0.5)
    f.stroke([(0.2, 0.72), (0.28, 0.82), (0.38, 0.9)], 0.6, 0.45)
    # obliques down the side
    f.muscle(0.1, 0.63, 0.075, 0.16, ang=0.15, depth=0.6, line=0.45, light=0.8)
    f.edges(0.06, 0.06)
    f.mirror()
    return f


def torso_back(k):
    f = Face(8, 12, -0.04, 2)
    d = k
    # traps: a diamond from the neck to mid back
    f.muscle(0.5, 0.12, 0.2, 0.11, depth=0.5, line=0.35)
    # shoulder blades (infraspinatus, teres) and the lats sweeping down to the waist
    f.muscle(0.29, 0.25, 0.17, 0.12, ang=0.2, depth=0.8 + 0.3 * d, line=0.85)
    f.muscle(0.2, 0.48, 0.17, 0.2, ang=-0.25, depth=0.7 + 0.3 * d, line=0.7)
    # spinal erectors either side of a deep spine
    f.muscle(0.42, 0.68, 0.075, 0.17, depth=0.6, line=0.55)
    f.stroke([(0.5, 0.16), (0.5, 0.84)], 0.6, 0.4)
    f.stroke([(0.36, 0.06), (0.44, 0.18)], 0.6, 0.35)
    f.edges(0.06, 0.06)
    f.mirror()
    return f


def torso_side(k, front_right):
    """A side of the torso: lats under the arm, serratus and obliques; the front is on the right if front_right."""
    f = Face(4, 12, -0.03, 3)
    f.muscle(0.45, 0.32, 0.4, 0.2, ang=0.3, depth=0.6, line=0.5)                  # lat
    for cv in (0.36, 0.44):
        f.muscle(0.78, cv, 0.18, 0.03, ang=-0.4, depth=0.6, line=0.4, light=0.8)  # serratus at the front
    f.muscle(0.6, 0.64, 0.3, 0.15, depth=0.5, line=0.35)                          # oblique
    f.edges(0.04, 0.04)
    if not front_right:
        f.flip()
    return f


# ------------------------------------------------------------------------------------------------ arms and fists

def arm_front(k, inner_right):
    f = Face(4, 12, 0.0, 4)
    f.muscle(0.5, 0.07, 0.48, 0.1, depth=0.7, line=0.45)                        # front deltoid
    f.muscle(0.5, 0.31, 0.36, 0.15 + 0.01 * k, depth=0.8 + 0.4 * k, line=0.95)  # biceps
    f.stroke([(0.2, 0.5), (0.5, 0.52), (0.8, 0.49)], 0.6, 0.55)                 # the elbow crease
    f.muscle(0.36, 0.6, 0.3, 0.1, ang=0.3, depth=0.6, line=0.6)                 # forearm (brachioradialis)
    f.muscle(0.66, 0.66, 0.26, 0.09, ang=-0.2, depth=0.5, line=0.4, light=0.8)  # forearm flexors
    f.stroke([(0.2, 0.77), (0.8, 0.775)], 0.5, 0.35)                            # the wrist
    # the fist seen from the front: the thumb lying across the curled index finger
    f.tone(f.v > 0.79, -0.02)
    f.muscle(0.5, 0.86, 0.42, 0.06, depth=0.5, line=0.6, light=0.7)             # index finger, curled
    f.muscle(0.5, 0.955, 0.4, 0.045, depth=0.5, line=0.5, light=0.7)            # its tip tucked under
    f.muscle(0.62, 0.885, 0.3, 0.035, ang=-0.25, depth=0.6, line=0.8)           # the thumb across it
    f.tone(f.ellipse(0.83, 0.875, 0.08, 0.022), 0.05)                          # thumbnail
    f.stroke([(0.76, 0.86), (0.9, 0.86), (0.9, 0.89), (0.76, 0.89)], 0.4, 0.35)
    f.edges(0.06, 0.06, bottom=0.03)
    if not inner_right:
        f.flip()
    return f


def arm_back(k, inner_right):
    f = Face(4, 12, -0.04, 5)
    f.muscle(0.5, 0.07, 0.48, 0.1, depth=0.7, line=0.45)                        # rear deltoid
    f.muscle(0.32, 0.3, 0.22, 0.15, depth=0.7 + 0.3 * k, line=0.8)              # triceps, long head
    f.muscle(0.7, 0.27, 0.22, 0.12, depth=0.7, line=0.7)                        # lateral head
    f.stroke([(0.2, 0.44), (0.5, 0.47), (0.8, 0.44)], 0.6, 0.45)                # the tendon above the elbow
    f.tone(f.ellipse(0.5, 0.53, 0.1, 0.025), -0.05)                             # the elbow point
    f.muscle(0.5, 0.64, 0.4, 0.12, depth=0.5, line=0.45)                        # forearm extensors
    f.stroke([(0.2, 0.775), (0.8, 0.775)], 0.5, 0.3)
    # the fist from the little-finger side: the pinky curled, the heel of the hand above it
    f.muscle(0.5, 0.86, 0.38, 0.07, depth=0.4, line=0.4, light=0.6)
    f.muscle(0.42, 0.95, 0.36, 0.045, depth=0.5, line=0.55, light=0.7)
    f.edges(0.06, 0.06, bottom=0.03)
    if not inner_right:
        f.flip()
    return f


def arm_outer(k, front_right):
    """The outside of the arm: the deltoid cap, triceps, forearm, and the back of the fist with its four knuckles."""
    f = Face(4, 12, -0.02, 6)
    f.muscle(0.5, 0.1, 0.5, 0.14, depth=0.9 + 0.3 * k, line=0.85)              # side deltoid, the round cap
    f.muscle(0.32, 0.33, 0.24, 0.12, depth=0.6, line=0.55)                      # triceps
    f.muscle(0.72, 0.36, 0.2, 0.1, depth=0.6, line=0.5)                         # biceps edge
    f.muscle(0.6, 0.6, 0.32, 0.12, ang=0.25, depth=0.6, line=0.55)              # brachioradialis
    f.stroke([(0.15, 0.775), (0.85, 0.775)], 0.5, 0.3)
    # back of the hand: tendons running down to four knuckles along the bottom
    for i in range(4):
        cu = 0.16 + i * 0.227
        f.muscle(cu, 0.935, 0.1, 0.04, depth=0.8, line=0.7)
    f.stroke([(0.06, 0.985), (0.94, 0.985)], 0.5, 0.5)
    f.edges(0.05, 0.05, bottom=0.03)
    if not front_right:
        f.flip()
    return f


def arm_inner(k, front_right):
    """The inside of the arm (towards the body): biceps, forearm, and the palm side of the fist: four curled fingers."""
    f = Face(4, 12, -0.03, 7)
    f.muscle(0.6, 0.3, 0.32, 0.14, depth=0.7 + 0.3 * k, line=0.7)               # biceps inner head
    f.stroke([(0.2, 0.5), (0.8, 0.5)], 0.5, 0.4)
    f.muscle(0.5, 0.63, 0.38, 0.12, depth=0.5, line=0.45)                       # forearm flexors
    f.stroke([(0.2, 0.775), (0.8, 0.775)], 0.5, 0.3)
    # four fingers folded into the palm, side by side from the front (index) to the back (little finger),
    # each with the crease of its middle joint
    for i in range(4):
        cu = 0.83 - i * 0.22
        f.muscle(cu, 0.885, 0.1, 0.085, depth=0.6, line=0.75, light=0.8)
        f.stroke([(cu - 0.08, 0.87), (cu + 0.08, 0.87)], 0.4, 0.3)
    f.edges(0.05, 0.05, bottom=0.03)
    if not front_right:
        f.flip()
    return f


def arm_bottom():
    """The flat face of the fist: the four first finger bones side by side."""
    f = Face(4, 4, -0.05, 8)
    for i in range(1, 4):
        f.stroke([(i / 4, 0.1), (i / 4, 0.9)], 0.5, 0.5)
    f.edges(0.04, 0.04, 0.04, 0.04)
    return f


# ------------------------------------------------------------------------------------------------ legs and feet

def leg_front(k, inner_right):
    f = Face(4, 12, 0.0, 9)
    io = 1 if inner_right else -1
    c = lambda x: x if inner_right else 1 - x                                    # inner side towards u = 1
    f.muscle(c(0.32), 0.2, 0.3, 0.2, ang=0.15 * io, depth=0.7 + 0.3 * k, line=0.75)      # vastus lateralis (outer sweep)
    f.muscle(c(0.55), 0.15, 0.2, 0.16, depth=0.6, line=0.5)                            # rectus femoris
    f.muscle(c(0.75), 0.36, 0.22, 0.1, ang=-0.3 * io, depth=0.8 + 0.3 * k, line=0.85)  # the teardrop above the knee
    f.muscle(0.5, 0.47, 0.17, 0.05, depth=0.7, line=0.6)                               # kneecap
    f.muscle(c(0.4), 0.66, 0.2, 0.15, depth=0.5, line=0.4, light=0.8)                  # tibialis down the shin
    # rows 10..12 (v 0.833..1) are the foot block's top, heel to toe tips (LimbSegments): the instep, then five toes
    # (big toe on the inside), rounded, each with its nail at the tip, a dark gap between them
    f.tone(f.v > 0.833, 0.02)
    f.stroke([(0.1, 0.836), (0.9, 0.836)], 0.5, 0.35)                                 # where the shin meets the foot
    for i in range(4):
        f.stroke([(c(0.62 - i * 0.12), 0.85), (c(0.84 - i * 0.2), 0.905)], 0.35, 0.12)  # tendons over the instep
    widths = [0.25, 0.19, 0.18, 0.17, 0.16]
    edge = 0.99
    for i, w in enumerate(widths):
        cu = c(edge - w / 2)
        top = 0.915 + i * 0.006
        f.muscle(cu, (top + 1.0) / 2, w / 2 * 0.98, (1.0 - top) / 2 + 0.004, depth=1.0, line=0.8)
        f.tone(f.ellipse(cu, 0.975, w * 0.3, 0.014), 0.1)                               # the nail
        f.stroke([(cu - w * 0.3, 0.962), (cu + w * 0.3, 0.962)], 0.35, 0.3)
        if i < 4:
            g = c(edge - w - 0.004)
            f.stroke([(g, top + 0.01), (g, 0.998)], 0.7, 0.8)                            # the gap to the next toe
        edge -= w + 0.008
    f.edges(0.06, 0.06)
    return f


def leg_back(k, inner_right):
    f = Face(4, 12, -0.04, 10)
    c = lambda x: x if inner_right else 1 - x
    f.muscle(0.5, 0.2, 0.42, 0.2, depth=0.5, line=0.45)                          # hamstrings
    f.stroke([(0.5, 0.06), (0.5, 0.36)], 0.5, 0.35)
    f.stroke([(0.2, 0.47), (0.5, 0.5), (0.8, 0.47)], 0.5, 0.4)                   # the back of the knee
    f.muscle(c(0.68), 0.63, 0.24, 0.13, depth=0.8 + 0.4 * k, line=0.85)          # calf, inner head (lower, fuller)
    f.muscle(c(0.3), 0.6, 0.22, 0.11, depth=0.8 + 0.3 * k, line=0.8)            # calf, outer head
    f.stroke([(0.5, 0.74), (0.5, 0.9)], 0.6, 0.35)                               # the Achilles tendon
    f.muscle(0.5, 0.95, 0.38, 0.06, depth=0.6, line=0.55)                        # the heel
    f.edges(0.06, 0.06)
    return f


def leg_side(k, outer, front_right):
    f = Face(4, 12, -0.02, 11 + outer)
    if outer:
        f.muscle(0.45, 0.22, 0.42, 0.2, depth=0.6 + 0.3 * k, line=0.6)          # outer quad sweep
    else:
        f.muscle(0.55, 0.3, 0.4, 0.16, depth=0.5, line=0.45)                    # adductors
    f.tone(f.ellipse(0.55, 0.47, 0.25, 0.04), -0.03)
    f.muscle(0.3, 0.62, 0.28, 0.13, depth=0.6, line=0.5)                        # calf from the side
    # the foot: the ankle bone, the instep and the sole's line, the heel at the back, toes at the front
    f.muscle(0.45, 0.87, 0.14, 0.035, depth=0.8, line=0.6)                      # ankle bone
    f.stroke([(0.04, 0.985), (0.4, 0.975), (0.75, 0.96 if not outer else 0.975), (0.97, 0.985)], 0.5, 0.45)
    if not outer:
        f.tone((f.v > 0.93) & (f.u > 0.35) & (f.u < 0.75), -0.04)              # the arch
    f.muscle(0.92, 0.955, 0.08, 0.035, depth=0.6, line=0.55)                    # big toe / little toe from the side
    f.edges(0.04, 0.04)
    if not front_right:
        f.flip()
    return f


def leg_bottom(inner_right):
    """The sole: toe pads along the front edge (v = 1), the ball of the foot, the heel pad."""
    f = Face(4, 4, -0.08, 13)
    c = lambda x: x if inner_right else 1 - x
    f.muscle(0.5, 0.2, 0.36, 0.16, depth=0.4, line=0.35)                         # heel
    f.muscle(c(0.55), 0.62, 0.42, 0.13, depth=0.4, line=0.35)                    # ball of the foot
    edge = 0.98
    for w in [0.27, 0.19, 0.18, 0.17, 0.15]:
        f.muscle(c(edge - w / 2), 0.9, w / 2 * 0.9, 0.08, depth=0.4, line=0.4)
        edge -= w + 0.008
    return f


# ------------------------------------------------------------------------------------------------ the skin

def paint(k, old):
    img = np.zeros((64 * S, 64 * S), dtype=float)
    alpha = np.zeros_like(img)

    def put(part, face, fc, kk=k):
        x, y, w, h = rect(part, face)
        img[y * S:(y + h) * S, x * S:(x + w) * S] = fc.result(kk)
        alpha[y * S:(y + h) * S, x * S:(x + w) * S] = 1

    put('body', 'front', torso_front(k))
    put('body', 'back', torso_back(k))
    put('body', 'west', torso_side(k, front_right=True))
    put('body', 'east', torso_side(k, front_right=False))
    t = Face(8, 4, 0.03, 20)
    t.edges(0.05, 0.05)
    put('body', 'top', t)
    put('body', 'bottom', Face(8, 4, -0.1, 21))
    for part, right in (('rarm', True), ('larm', False)):
        # the right arm's inner side is its east face (towards the body), the left arm's its west face; on the
        # front face the inner side is towards u = 1 for the right arm
        put(part, 'front', arm_front(k, inner_right=right))
        put(part, 'back', arm_back(k, inner_right=not right))
        put(part, 'west' if right else 'east', arm_outer(k, front_right=right))
        put(part, 'east' if right else 'west', arm_inner(k, front_right=not right))
        tp = Face(4, 4, 0.03, 22)
        tp.edges(0.05, 0.05, 0.05, 0.05)
        put(part, 'top', tp)
        put(part, 'bottom', arm_bottom())
    for part, right in (('rleg', True), ('lleg', False)):
        put(part, 'front', leg_front(k, inner_right=right))
        put(part, 'back', leg_back(k, inner_right=not right))
        put(part, 'west' if right else 'east', leg_side(k, outer=1, front_right=right))
        put(part, 'east' if right else 'west', leg_side(k, outer=0, front_right=not right))
        put(part, 'top', Face(4, 4, -0.32, 23))                                # inside the shorts: in shadow
        put(part, 'bottom', leg_bottom(right))

    g = (np.clip(img, 0, 1) * 255).round().astype(np.uint8)
    out = np.zeros((64 * S, 64 * S, 4), dtype=np.uint8)
    out[..., 0] = out[..., 1] = out[..., 2] = g
    out[..., 3] = (alpha * 255).astype(np.uint8)
    # the head keeps its old painting, scaled up
    head = old.resize((64 * S, 64 * S), Image.NEAREST)
    a = np.array(head)
    out[0:16 * S, 0:32 * S] = a[0:16 * S, 0:32 * S]
    return Image.fromarray(out, 'RGBA')


def main():
    for name, k in (('lean', 0.6), ('athletic', 1.0), ('bulky', 1.45)):
        old = Image.open(os.path.join(ROOT, 'body_painted', name + '.png')).convert('RGBA')
        im = paint(k, old)
        for folder in ('body_painted', 'body_hd'):
            im.save(os.path.join(ROOT, folder, name + '.png'))
    print('bodies painted')


if __name__ == '__main__':
    main()

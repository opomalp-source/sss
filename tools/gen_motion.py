#!/usr/bin/env python3
"""Every movement clip, generated (CX-31: "rework all the animations ... a million times better").

The old clips were mostly two keys swinging between two poses. Here each clip is written as motion: a key pose plus
layered waves that follow the classic principles, so figures breathe, shift their weight, bounce and drag:

  - weight: the hips drop on every footfall and rise through the passing pose; the body sways over the standing leg;
  - overlap and follow-through: shoulders, arms and head follow the torso a little later (phase lag), legs trail the
    body when hovering, arms trail turns (the engine adds more);
  - counter-rotation: the shoulders twist against the hips when walking and running;
  - acting beats: idles hold a pose but now and then do something (look round, roll a shoulder, scratch the head);
  - charge clips tremble with ki and surge in waves.

Default clips (idle, walk, sprint, jump, fall, flight, charge, the fighting stance and race/form idles) and the
sixteen fighting styles' nine slots each come from here; every style has its own profile so no two move alike.
Clips are sampled densely and the engine's Catmull-Rom curves smooth between keys.
Re-run after editing: python3 tools/gen_motion.py   (writes assets/dbzenith/motion/clips/*.json)

Conventions (docs/ANIMATION.md): rot = [pitch, yaw, roll] degrees; arm pitch -90 points forward, + swings back; leg
pitch < 0 swings forward; roll + is outward on the right side, - outward on the left; torso yaw + brings the right
shoulder forward; torso/body pitch + leans forward; pos = [x, y, z] pixels, y down (+ crouches), -z forward, x - is
toward the figure's right; bend = elbow/knee flex in degrees.
"""
import json
import math
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'dbzenith', 'motion', 'clips')
TAU = 2 * math.pi
OUT = {}


# ------------------------------------------------------------------------------------------------ wave helpers

def sn(t, k=1, ph=0.0):
    return math.sin(TAU * (k * t + ph))


def cs(t, k=1, ph=0.0):
    return math.cos(TAU * (k * t + ph))


def smooth(a, b, x):
    x = max(0.0, min(1.0, (x - a) / (b - a)))
    return x * x * (3 - 2 * x)


def win(t, a, b, ramp=0.06):
    """A periodic window: 0 outside [a, b], 1 inside, eased in and out over `ramp` (all in loop time 0..1)."""
    t %= 1.0
    return smooth(a - ramp, a, t) * (1 - smooth(b, b + ramp, t))


def noise(t, seed, amp=1.0, harmonics=(1, 2, 3)):
    """Smooth periodic noise (loops seamlessly)."""
    v = 0.0
    for i, h in enumerate(harmonics):
        ph = math.sin(seed * 12.9898 + i * 78.233) * 43758.5453 % 1.0
        v += sn(t, h, ph) / (1 + i * 0.7)
    return v * amp / 1.6


def jitter(t, seed, amp, k=9):
    """Fast tremble (ki pressure): high harmonics only."""
    return noise(t, seed, amp, (k, k + 4, k + 9))


def mir(arm):
    """The left limb's pose from the right one's: yaw and roll flip."""
    p, y, r, b = arm
    return (p, -y, -r, b)


def add(a, b, k=1.0):
    return tuple(x + y * k for x, y in zip(a, b))


def lerp(a, b, k):
    return tuple(x + (y - x) * k for x, y in zip(a, b))


# arm and leg poses: (pitch, yaw, roll, bend), right side; hand-tuned on the model (from the CX-18/20 clips)
CROSSED_R, CROSSED_L = (-20, -48, 14, 114), (-26, 48, -14, 110)
POCKET = (6, -22, 8, 40)
BEHIND = (35, 30, -10, 60)
HIP = (5, -60, 45, 100)
HEAD = (-160, 40, 22, 128)
RELAXED = (2, 0, 7, 12)


# ------------------------------------------------------------------------------------------------ clip building

class Clip:
    def __init__(self, cid, doc, sync='time', length=40, loop=True, symmetric=False, stride=0.0, keys=24, ease=None):
        self.cid, self.doc, self.sync, self.length, self.loop = cid, doc, sync, length, loop
        self.symmetric, self.stride, self.keys, self.ease = symmetric, stride, keys, ease
        self.bones = {}

    def limb(self, bone, f):
        """f(t) -> (pitch, yaw, roll, bend)"""
        self.bones[bone] = ('limb', f)
        return self

    def rot(self, bone, f):
        """f(t) -> (pitch, yaw, roll)"""
        self.bones[bone] = ('rot', f)
        return self

    def body(self, pos, rot=None):
        """pos(t) -> (x, y, z); rot(t) -> (pitch, yaw, roll) or None"""
        self.bones['body'] = ('body', (pos, rot))
        return self

    def build(self):
        n = self.keys
        ts = [i / n for i in range(n)] if self.loop else [i / (n - 1) for i in range(n)]
        bones = {}
        for name, (kind, f) in self.bones.items():
            keys = []
            for t in ts:
                k = {'t': round(t, 4)}
                if kind == 'limb':
                    p, y, r, b = f(t)
                    k['rot'] = [round(p, 1), round(y, 1), round(r, 1)]
                    k['bend'] = round(max(0.0, b), 1)
                elif kind == 'rot':
                    k['rot'] = [round(v, 1) for v in f(t)]
                else:
                    pos, rot = f
                    k['pos'] = [round(v, 2) for v in pos(t)]
                    if rot:
                        k['rot'] = [round(v, 1) for v in rot(t)]
                keys.append(k)
            bones[name] = keys
        j = {'_doc': self.doc, 'sync': self.sync}
        if self.sync == 'time':
            j['length'] = self.length
        else:
            j['stride'] = self.stride
        j['loop'] = self.loop
        if self.symmetric:
            j['symmetric'] = True
        if self.ease:
            j['ease'] = self.ease
        j['bones'] = bones
        OUT[self.cid] = j


# ------------------------------------------------------------------------------------------------ gaits

def gait(cid, doc, stride, A, K, arm_swing, elbow, elbow_fwd, torso_pitch, twist, bob, sway, arm_roll=6, lag=0.06,
         head=0.0, arms=None, torso_extra=None, body_y=0.0, bounce_up=0.0, leg_roll=0.0, keys=20, heel=0.0, left_arm=None):
    """A walk or run cycle (stride-synced, symmetric: the left limbs are the right ones half a cycle later).
    A: leg swing (degrees each way); K: knee lift in the swing; t = 0 is the right heel striking."""
    c = Clip(cid, doc, sync='stride', stride=stride, symmetric=True, keys=keys)
    leg_keys = [(0.0, -A, 4 + heel), (0.08, -0.8 * A, 12 + 0.25 * K), (0.25, -0.15 * A, 6), (0.45, 0.8 * A, 8),
                (0.56, 0.9 * A, 0.55 * K), (0.7, 0.35 * A, K), (0.86, -0.7 * A, 0.4 * K), (1.0, -A, 4 + heel)]

    def leg(t):
        for (t0, p0, b0), (t1, p1, b1) in zip(leg_keys, leg_keys[1:]):
            if t0 <= t <= t1:
                u = smooth(0, 1, (t - t0) / (t1 - t0))
                return (p0 + (p1 - p0) * u, 0, leg_roll, b0 + (b1 - b0) * u)
        return (-A, 0, leg_roll, 4)

    c.limb('rightLeg', leg)
    if arms:
        c.limb('rightArm', lambda t: arms(t))
    else:
        def arm(t):
            fwd = -cs(t, 1, -lag)                                             # 1 when the right arm is forward
            return (-arm_swing * fwd + 4, 0, arm_roll + 2 * sn(t, 2), elbow + elbow_fwd * max(0.0, fwd))
        c.limb('rightArm', arm)

    if left_arm:
        c.limb('leftArm', left_arm)

    def torso(t):
        p = torso_pitch + 0.8 * cs(t, 2, -0.1)
        y = -twist * cs(t, 1, -lag * 0.5)
        r = 1.2 * sn(t)
        if torso_extra:
            p, y, r = add((p, y, r), torso_extra(t))
        return (p, y, r)

    c.rot('torso', torso)
    c.rot('head', lambda t: (head - 0.5 * bob * cs(t, 2, -0.12), twist * 0.4 * cs(t, 1), -0.8 * sn(t)))
    # hips: down on each footfall (loading), up through the passing pose; over the standing leg
    c.body(lambda t: (-sway * sn(t), body_y + bob * cs(t, 2, -0.08) - bounce_up * max(0.0, -cs(t, 2, -0.08)), 0))
    c.build()


# ------------------------------------------------------------------------------------------------ the default set

def defaults():
    # ---- idle: a confident fighter at rest. Breathes, shifts its weight from leg to leg, looks about, and once a
    # loop rolls its shoulders loose.
    def idle_arm(side):
        def f(t):
            breath = sn(t, 2)
            roll = 8 + 1.4 * breath + 1.5 * sn(t, 1, 0.1 * side)
            shrug = win(t, 0.62, 0.74, 0.06)                                  # the shoulder roll
            return (2 - 2 * breath + 10 * shrug * sn(t, 4), 0, (roll + 6 * shrug) * side, 14 + 3 * breath)
        return f
    c = Clip('idle', 'Standing: a confident fighter at rest. Breathes (two breaths a loop), shifts weight from leg to '
             'leg, glances about, and once a loop rolls the shoulders loose.', length=160, keys=40)
    c.rot('torso', lambda t: (-1.5 * sn(t, 2) - 1, 0, 1.4 * sn(t)))
    c.rot('head', lambda t: (1 - 1.2 * sn(t, 2, -0.05) + 3 * win(t, 0.3, 0.42), 9 * win(t, 0.12, 0.3) - 7 * win(t, 0.42, 0.55) + noise(t, 3, 2),
                             2 * sn(t) + 4 * win(t, 0.64, 0.72)))
    c.limb('rightArm', idle_arm(1))
    c.limb('leftArm', lambda t: mir(idle_arm(1)((t + 0.97) % 1)))                # a beat later than the right
    # weight on the right leg for the first half (it straightens, the left knee eases), then the left
    c.limb('rightLeg', lambda t: (-1, 0, 4 + 1.2 * sn(t), 3 + 4 * max(0.0, -sn(t))))
    c.limb('leftLeg', lambda t: (-2, 0, -4 + 1.2 * sn(t), 3 + 4 * max(0.0, sn(t))))
    c.body(lambda t: (-0.6 * sn(t), 0.15 - 0.3 * sn(t, 2) + 0.25 * abs(sn(t)), 0))
    c.build()

    gait('walk', 'Walking: heel strike, the hips dropping as the weight lands and rising through the passing pose, the '
         'body over the standing leg, shoulders twisting against the hips, arms swinging a beat behind the legs with '
         'the elbows bending as they come forward, the head steady. Stride-synced and symmetric.',
         stride=2.5, A=30, K=46, arm_swing=26, elbow=10, elbow_fwd=16, torso_pitch=2, twist=7, bob=0.55, sway=0.45)
    gait('sprint', 'Sprinting: long driving strides with the knee punching high, arms pumping hard beside the body '
         '(elbows at a right angle), a strong forward lean, the body bounding up off each push. Stride-synced.',
         stride=3.4, A=46, K=78, arm_swing=58, elbow=62, elbow_fwd=22, torso_pitch=12, twist=10, bob=0.9, sway=0.25,
         arm_roll=5, lag=0.04, head=-6, bounce_up=0.5)

    # ---- the fighting stance (PvP on): light on the balls of the feet, bouncing, the guard breathing, the lead hand
    # pawing now and then, the head slipping side to side
    c = Clip('fight_idle', 'Fighting stance (PvP on): side-on with the knees bent, light on the balls of the feet and '
             'bouncing twice a second, the guard rising and falling a beat behind, the lead hand pawing out, the head '
             'slipping side to side.', length=40, keys=40)
    bounce = lambda t: max(0.0, sn(t, 4))                                     # four bounces a loop
    c.body(lambda t: (0.4 * sn(t), 1.1 + 0.55 * bounce(t), 0))
    c.rot('torso', lambda t: (6 + 1.5 * bounce(t), -16 + 3 * sn(t, 1), 2 * sn(t, 1, 0.25)))
    c.rot('head', lambda t: (2, 8 - 2 * sn(t, 1), 3 * sn(t, 1, 0.25)))
    paw = lambda t: win(t, 0.55, 0.62, 0.05)
    c.limb('rightArm', lambda t: (-38 + 4 * bounce((t - 0.04) % 1), -8, 12, 110 + 4 * bounce((t - 0.04) % 1)))
    c.limb('leftArm', lambda t: (-62 + 3 * bounce((t - 0.06) % 1) - 26 * paw(t), 10, -8, 88 - 4 * bounce((t - 0.06) % 1) - 60 * paw(t)))
    c.limb('rightLeg', lambda t: (14, 0, 8, 20 + 10 * bounce(t)))
    c.limb('leftLeg', lambda t: (-16, 0, -5, 22 + 10 * bounce(t)))
    c.build()

    gait('fight_walk', 'Moving in the fighting stance: short gliding steps on bent knees, the guard held high and '
         'steady, the body staying low and side-on. Stride-synced, symmetric.',
         stride=1.8, A=20, K=34, arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=6, twist=2, bob=0.35, sway=0.3,
         body_y=1.1, arms=lambda t: (-37 + 3 * cs(t, 2), -8, 12, 110), torso_extra=lambda t: (0, -14, 0))
    # the symmetric engine mirrors the right arm onto the left half a cycle later: the stance's lead hand is set apart
    OUT['fight_walk']['bones']['leftArm'] = [{'t': 0.0, 'rot': [-60, 10, -8], 'bend': 88}, {'t': 0.5, 'rot': [-62, 10, -8], 'bend': 86}]

    # ---- in the air
    c = Clip('jump', 'Rising from a jump: the knees tuck up, the arms throw upward then spread for balance.',
             length=10, loop=False, keys=6, ease='out')
    c.limb('rightLeg', lambda t: (-10 - 34 * t, 0, 2, 14 + 50 * t))
    c.limb('leftLeg', lambda t: (10 - 18 * t, 0, -2, 10 + 30 * t))
    c.limb('rightArm', lambda t: (10 - 60 * t + 20 * t * t, 0, 8 + 22 * t, 14 + 14 * t))
    c.limb('leftArm', lambda t: (10 - 50 * t + 18 * t * t, 0, -8 - 24 * t, 14 + 14 * t))
    c.rot('torso', lambda t: (8 - 6 * t, 0, 0))
    c.build()

    c = Clip('fall', 'Falling: arms up and out wheeling slowly for balance, the legs reaching down for the ground.',
             length=30, keys=16)
    c.limb('rightArm', lambda t: (-42 + 10 * sn(t), 0, 46 + 6 * cs(t), 20 + 6 * sn(t, 1, 0.2)))
    c.limb('leftArm', lambda t: (-42 + 10 * sn(t, 1, 0.5), 0, -46 - 6 * cs(t, 1, 0.5), 20 + 6 * sn(t, 1, 0.7)))
    c.limb('rightLeg', lambda t: (-10 + 7 * sn(t), 0, 3, 18 + 6 * sn(t)))
    c.limb('leftLeg', lambda t: (-2 + 7 * sn(t, 1, 0.5), 0, -3, 14 + 6 * sn(t, 1, 0.5)))
    c.rot('torso', lambda t: (-4, 0, 2 * sn(t)))
    c.rot('head', lambda t: (6, 0, 0))
    c.build()

    hover('hover', 'Hovering: weightless, one knee drawn up and the other leg hanging, arms loose and a little out; the '
          'body bobs slowly and the limbs float a beat behind it, drifting.', style=None)

    c = Clip('cruise', 'Cruising flight (the engine tips the body toward horizontal with speed): arms swept back along '
             'the sides, legs trailing together and fluttering in the wind, the head up to see ahead.', length=30, keys=20)
    c.limb('rightArm', lambda t: (30 + 4 * sn(t, 1, 0.1), 0, 12 + 3 * sn(t, 2), 10 + 4 * sn(t, 1)))
    c.limb('leftArm', lambda t: (32 + 4 * sn(t, 1, 0.6), 0, -12 - 3 * sn(t, 2, 0.5), 10 + 4 * sn(t, 1, 0.5)))
    c.limb('rightLeg', lambda t: (8 + 5 * sn(t, 2), 0, 2, 8 + 10 * max(0.0, sn(t, 2))))
    c.limb('leftLeg', lambda t: (12 + 5 * sn(t, 2, 0.5), 0, -2, 12 + 10 * max(0.0, sn(t, 2, 0.5))))
    c.rot('head', lambda t: (-14, 0, 0))
    c.rot('torso', lambda t: (0, 2 * sn(t), 0))
    c.build()

    c = Clip('fast', 'Flying flat out: the classic pose, one fist punched ahead, the other arm swept back along the side, '
             'legs straight and together, everything trembling in the wind.', length=12, keys=12)
    c.limb('rightArm', lambda t: (-170 + jitter(t, 1, 1.2), 0, -4, 0))
    c.limb('leftArm', lambda t: (26 + jitter(t, 2, 1.5), 0, -10, 6))
    c.limb('rightLeg', lambda t: (4 + jitter(t, 3, 1.2), 0, 1.5, 3))
    c.limb('leftLeg', lambda t: (6 + jitter(t, 4, 1.2), 0, -1.5, 6))
    c.rot('head', lambda t: (-8, 0, 0))
    c.rot('torso', lambda t: (0, 4, 0))
    c.build()

    c = Clip('ascend', 'Rising straight up: a fist driving overhead, the other arm along the side, legs together and '
             'trailing below, the whole figure stretched and trembling with speed.', length=16, keys=16)
    c.limb('rightArm', lambda t: (-172 + jitter(t, 5, 1.0), 0, -6, 4))
    c.limb('leftArm', lambda t: (14 + 2 * sn(t, 2), 0, -9, 10))
    c.limb('rightLeg', lambda t: (4 + 3 * sn(t, 2), 0, 1, 4))
    c.limb('leftLeg', lambda t: (8 + 3 * sn(t, 2, 0.4), 0, -1, 14))
    c.rot('head', lambda t: (-14, 0, 0))
    c.build()

    c = Clip('descend', 'Dropping down: arms out for balance, the knees drawn up to land, hair-trigger ready.',
             length=30, keys=16)
    c.limb('rightArm', lambda t: (-20 + 4 * sn(t), 0, 38 + 4 * sn(t, 1, 0.3), 18))
    c.limb('leftArm', lambda t: (-20 + 4 * sn(t, 1, 0.5), 0, -38 - 4 * sn(t, 1, 0.8), 18))
    c.limb('rightLeg', lambda t: (-28 + 4 * sn(t), 0, 3, 52 + 6 * sn(t)))
    c.limb('leftLeg', lambda t: (2 + 4 * sn(t, 1, 0.5), 0, -3, 22 + 6 * sn(t, 1, 0.5)))
    c.rot('head', lambda t: (10, 0, 0))
    c.build()

    c = Clip('backward', 'Flying backwards: leaning back (the engine), the arms forward and braking against the air, '
             'the legs swung forward.', length=24, keys=12)
    c.limb('rightArm', lambda t: (-38 + 4 * sn(t), 0, 10, 32))
    c.limb('leftArm', lambda t: (-38 + 4 * sn(t, 1, 0.5), 0, -10, 32))
    c.limb('rightLeg', lambda t: (-26 + 3 * sn(t), 0, 2, 32))
    c.limb('leftLeg', lambda t: (-14 + 3 * sn(t, 1, 0.5), 0, -2, 18))
    c.rot('torso', lambda t: (-4, 0, 0))
    c.build()

    charge('charge', 'Powering up: a wide low stance, fists clenched down and out, the whole body trembling under the '
           'ki, surging twice a loop: the hips sink, the chest heaves and the head is thrown back with a roar.', None)

    race_idles()


# ------------------------------------------------------------------------------------------------ shared builders

def hover(cid, doc, style=None, arms=None, legs=None, torso=(2, 0, 0), head=(0, 0, 0), bob=1.0, length=80, drift=1.0):
    """Hovering: a pose that bobs with the limbs floating a beat behind the body."""
    c = Clip(cid, doc, length=length, keys=32)
    arms = arms or ((-6, 0, 14, 26), (-6, 0, -14, 26))
    legs = legs or ((-30, 0, 4, 60), (6, 0, -2, 18))
    lag = 0.1
    c.body(lambda t: (0.35 * drift * sn(t, 1, 0.25), 0.2 - bob * sn(t), 0), lambda t: (0, 0, 1.2 * drift * sn(t, 1, 0.3)))
    c.rot('torso', lambda t: add(torso, (1.2 * sn(t, 1, -0.05), 0, 0)))
    c.rot('head', lambda t: add(head, (-1.0 * sn(t, 1, -0.15), 3 * drift * noise(t, 7), 0)))
    c.limb('rightArm', lambda t: add(arms[0], (-2.5 * sn(t, 1, -lag), 0, 2.5 * sn(t, 1, -lag), 4 * sn(t, 1, -lag))))
    c.limb('leftArm', lambda t: add(arms[1], (-2.5 * sn(t, 1, -lag - 0.03), 0, -2.5 * sn(t, 1, -lag - 0.03), 4 * sn(t, 1, -lag - 0.03))))
    c.limb('rightLeg', lambda t: add(legs[0], (3.5 * sn(t, 1, -lag * 1.4), 0, 0, 5 * sn(t, 1, -lag * 1.4))))
    c.limb('leftLeg', lambda t: add(legs[1], (3.5 * sn(t, 1, -lag * 1.6), 0, 0, 4 * sn(t, 1, -lag * 1.6))))
    c.build()


def charge(cid, doc, style, arms=None, legs=None, torso=(-6, 0, 0), head=(-18, 0, 0), crouch=2.6, shake=1.0,
           surge_arms=None, length=24):
    """Powering up: a held pose trembling with ki, surging twice a loop (hips sink, chest heaves, head thrown back)."""
    c = Clip(cid, doc, length=length, keys=48, ease='linear')
    arms = arms or ((16, 0, 24, 40), (16, 0, -24, 40))
    legs = legs or ((-6, 0, 18, 34), (-6, 0, -18, 34))
    surge_arms = surge_arms or (6, 0, 8, 10)
    surge = lambda t: max(0.0, sn(t, 2)) ** 2
    c.body(lambda t: (jitter(t, 11, 0.35 * shake), crouch + 0.8 * surge(t) + jitter(t, 12, 0.25 * shake), 0))
    c.rot('torso', lambda t: add(torso, (-4 * surge(t) + jitter(t, 13, 1.2 * shake), jitter(t, 14, 1.0 * shake), 0)))
    c.rot('head', lambda t: add(head, (-10 * surge(t) + jitter(t, 15, 1.5 * shake), jitter(t, 16, 1.5 * shake), 0)))
    c.limb('rightArm', lambda t: add(add(arms[0], surge_arms, surge(t)), (jitter(t, 17, 2 * shake), 0, jitter(t, 18, 2 * shake), 0)))
    c.limb('leftArm', lambda t: add(add(arms[1], mir(surge_arms), surge(t)), (jitter(t, 19, 2 * shake), 0, jitter(t, 20, 2 * shake), 0)))
    c.limb('rightLeg', lambda t: add(legs[0], (0, 0, jitter(t, 21, 0.8 * shake), 6 * surge(t))))
    c.limb('leftLeg', lambda t: add(legs[1], (0, 0, jitter(t, 22, 0.8 * shake), 6 * surge(t))))
    c.build()


def held_idle(cid, doc, arms, legs=((0, 0, 4, 2), (-3, 0, -4, 6)), torso=(0, 0, 0), head=(0, 0, 0), body_y=0.0,
              length=140, breath=1.0, sway=1.0, look=1.0, beats=None, keys=40):
    """An idle around a held pose: breathing, a slow weight shift, glances; `beats(t)` adds acting moments as a dict
    of bone -> offset tuple."""
    c = Clip(cid, doc, length=length, keys=keys)
    b = beats or (lambda t: {})
    c.body(lambda t: add((-0.5 * sway * sn(t), body_y - 0.25 * breath * sn(t, 2) + 0.2 * sway * abs(sn(t)), 0), b(t).get('body', (0, 0, 0))))
    c.rot('torso', lambda t: add(add(torso, (-1.4 * breath * sn(t, 2), 0, 1.2 * sway * sn(t))), b(t).get('torso', (0, 0, 0))))
    c.rot('head', lambda t: add(add(head, (-1.0 * breath * sn(t, 2, -0.06), look * (6 * win(t, 0.15, 0.32) - 5 * win(t, 0.45, 0.58)) + look * noise(t, 5, 1.5), 1.5 * sway * sn(t))),
                                 b(t).get('head', (0, 0, 0))))
    ra, la = arms
    c.limb('rightArm', lambda t: add(add(ra, (-1.5 * breath * sn(t, 2, -0.04), 0, 1.2 * breath * sn(t, 2, -0.04), 2 * breath * sn(t, 2))), b(t).get('rightArm', (0, 0, 0, 0))))
    c.limb('leftArm', lambda t: add(add(la, (-1.5 * breath * sn(t, 2, -0.07), 0, -1.2 * breath * sn(t, 2, -0.07), 2 * breath * sn(t, 2))), b(t).get('leftArm', (0, 0, 0, 0))))
    rl, ll = legs
    c.limb('rightLeg', lambda t: add(add(rl, (0, 0, 1.0 * sway * sn(t), 4 * sway * max(0.0, -sn(t)))), b(t).get('rightLeg', (0, 0, 0, 0))))
    c.limb('leftLeg', lambda t: add(add(ll, (0, 0, 1.0 * sway * sn(t), 4 * sway * max(0.0, sn(t)))), b(t).get('leftLeg', (0, 0, 0, 0))))
    c.build()


def toward(base, target, w):
    """The offset that takes a pose `w` of the way to another (for acting beats)."""
    return tuple((y - x) * w for x, y in zip(base, target))


def flight(cid, doc, ra, la, rl=(6, 0, 1, 8), ll=(10, 0, -1, 14), head=-12, flutter=1.0, length=24, torso=(0, 0, 0)):
    """Cruising flight (the engine pitches the body): arms held, legs trailing and fluttering, arms swaying a little."""
    c = Clip(cid, doc, length=length, keys=24)
    f = flutter
    c.limb('rightArm', lambda t: add(ra, (3 * f * sn(t, 1, 0.1), 0, 2 * f * sn(t, 2), 3 * f * sn(t, 1))))
    c.limb('leftArm', lambda t: add(la, (3 * f * sn(t, 1, 0.6), 0, -2 * f * sn(t, 2, 0.5), 3 * f * sn(t, 1, 0.5))))
    c.limb('rightLeg', lambda t: add(rl, (5 * f * sn(t, 2), 0, 0, 9 * f * max(0.0, sn(t, 2)))))
    c.limb('leftLeg', lambda t: add(ll, (5 * f * sn(t, 2, 0.5), 0, 0, 9 * f * max(0.0, sn(t, 2, 0.5)))))
    c.rot('head', lambda t: (head, 0, 0))
    c.rot('torso', lambda t: add(torso, (0, 2 * f * sn(t), 1.5 * f * sn(t, 1, 0.25))))
    c.build()


def fastfly(cid, doc, ra, la, rl=(4, 0, 1.5, 3), ll=(6, 0, -1.5, 6), head=-8, tremble=1.0, torso=(0, 4, 0)):
    """Flat-out flight: a held, streamlined pose trembling in the wind."""
    c = Clip(cid, doc, length=12, keys=12)
    k = tremble
    c.limb('rightArm', lambda t: add(ra, (jitter(t, 1, 1.2 * k), 0, jitter(t, 6, 0.8 * k), 0)))
    c.limb('leftArm', lambda t: add(la, (jitter(t, 2, 1.5 * k), 0, jitter(t, 7, 0.8 * k), 0)))
    c.limb('rightLeg', lambda t: add(rl, (jitter(t, 3, 1.2 * k), 0, 0, 0)))
    c.limb('leftLeg', lambda t: add(ll, (jitter(t, 4, 1.2 * k), 0, 0, 0)))
    c.rot('head', lambda t: (head, 0, 0))
    c.rot('torso', lambda t: torso)
    c.build()


def stance(cid, doc, ra, la, rl, ll, torso, head, body_y, bounces=2, bounce=0.5, length=32, weave=1.0, paw=0.0,
           sway=0.3, breath=0.0, extra=None):
    """A fighting stance: held guard, bouncing on the balls of the feet (the guard following a beat behind), the
    head slipping, the lead (left) hand pawing out if `paw`; `extra(t)` adds acting offsets per bone."""
    c = Clip(cid, doc, length=length, keys=max(24, bounces * 10))
    bo = lambda t: max(0.0, sn(t, bounces)) if bounces else 0.0
    ex = extra or (lambda t: {})
    pw = lambda t: win(t, 0.55, 0.62, 0.05) * paw
    c.body(lambda t: add((sway * sn(t), body_y + bounce * bo(t) - 0.3 * breath * sn(t, 1), 0), ex(t).get('body', (0, 0, 0))))
    c.rot('torso', lambda t: add(add(torso, (2.5 * bounce * bo(t) - 1.5 * breath * sn(t), 3 * weave * sn(t), 2 * weave * sn(t, 1, 0.25))), ex(t).get('torso', (0, 0, 0))))
    c.rot('head', lambda t: add(add(head, (0, -2 * weave * sn(t), 3 * weave * sn(t, 1, 0.25))), ex(t).get('head', (0, 0, 0))))
    c.limb('rightArm', lambda t: add(add(ra, (6 * bounce * bo((t - 0.04) % 1), 0, 0, 6 * bounce * bo((t - 0.04) % 1))), ex(t).get('rightArm', (0, 0, 0, 0))))
    c.limb('leftArm', lambda t: add(add(la, (5 * bounce * bo((t - 0.06) % 1) - 26 * pw(t), 0, 0, -6 * bounce * bo((t - 0.06) % 1) - 60 * pw(t))), ex(t).get('leftArm', (0, 0, 0, 0))))
    c.limb('rightLeg', lambda t: add(add(rl, (0, 0, 0, 16 * bounce * bo(t))), ex(t).get('rightLeg', (0, 0, 0, 0))))
    c.limb('leftLeg', lambda t: add(add(ll, (0, 0, 0, 16 * bounce * bo(t))), ex(t).get('leftLeg', (0, 0, 0, 0))))
    c.build()


def steps(cid, doc, ra, la, torso, body_y, stride=1.8, A=20, K=34, bob=0.35, sway=0.3, head=0.0, twist=2):
    """Moving in a fighting stance: short steps with the guard held (breathing with the steps)."""
    gait(cid, doc, stride=stride, A=A, K=K, arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=torso[0], twist=twist, bob=bob,
         sway=sway, body_y=body_y, head=head, arms=lambda t: add(ra, (3 * cs(t, 2), 0, 0, 0)),
         left_arm=lambda t: add(la, (3 * cs(t, 2, 0.1), 0, 0, 0)), torso_extra=lambda t: (0, torso[1], torso[2]))


def race_idles():
    held_idle('idle_crossed', 'Arms folded across the chest (Namekians), weight on one leg, breathing slowly, the head '
              'turning to watch now and then.', (CROSSED_R, CROSSED_L), legs=((0, 0, 4, 0), (-4, 0, -4, 10)), torso=(-2, 0, 0),
              head=(6, 0, 0), length=150)
    held_idle('idle_regal', 'Hands clasped behind the back, chest out and chin raised (Frost Demons, Core People, '
              'machines): almost still but for breath and a slow, disdainful look round.', (BEHIND, mir(BEHIND)),
              legs=((0, 0, 2, 0), (-2, 0, -2, 4)), torso=(-6, 0, 0), head=(-4, 0, 0), sway=0.4, length=170)
    held_idle('idle_loose', 'Loose and lazy (Majin, Vampires, Bio-Androids, Gen Aliens): shoulders rolled forward, arms '
              'hanging, a slack sway from foot to foot and the head lolling.', ((-12, 0, 12, 20), (-10, 0, -14, 22)),
              legs=((-2, 0, 6, 12), (-2, 0, -6, 12)), torso=(10, 0, 4), body_y=0.8, sway=1.8, length=90,
              beats=lambda t: {'head': (4 * sn(t, 1, 0.2), 0, 8 * sn(t))})
    charge_lite = lambda t: {'rightArm': (jitter(t, 31, 1.2), 0, jitter(t, 32, 1.2), 0), 'leftArm': (jitter(t, 33, 1.2), 0, jitter(t, 34, 1.2), 0),
                             'torso': (jitter(t, 35, 0.6), 0, 0)}
    held_idle('idle_powered', 'Powered up (Super Saiyan forms): chest out, fists clenched a little away from the body, '
              'feet planted wide, the ki making the arms tremble; the breath is deep and slow.', ((-4, 0, 16, 26), (-4, 0, -16, 26)),
              legs=((-2, 0, 8, 10), (-2, 0, -8, 10)), torso=(-3, 0, 0), head=(-2, 0, 0), body_y=0.7, sway=0.5, breath=1.6,
              length=120, beats=charge_lite, keys=60)
    held_idle('idle_heavy', 'Hulking (Legendary and buffed forms): feet wide, shoulders forward, arms held out by the '
              'bulk, heavy slow breaths that lift the whole upper body.', ((-8, 0, 20, 24), (-8, 0, -20, 24)),
              legs=((0, 0, 9, 10), (0, 0, -9, 10)), torso=(6, 0, 0), head=(-4, 0, 0), body_y=1.0, breath=2.4, sway=0.6, length=90)
    held_idle('idle_hunch', 'Beastly (Saibamen and the like): hunched low, knees bent, arms dangling forward with the '
              'claws ready, twitching and sniffing about.', ((-24, 0, 10, 34), (-20, 0, -12, 30)),
              legs=((-14, 0, 6, 34), (-10, 0, -6, 30)), torso=(24, 0, 2), head=(-8, 6, 0), body_y=1.8, sway=1.4, length=48,
              beats=lambda t: {'head': (3 * jitter(t, 41, 1), 12 * win(t, 0.3, 0.4, 0.03) - 12 * win(t, 0.7, 0.8, 0.03), 0)})
    gait('walk_heavy', 'A heavy walk (Legendary and buffed forms, brutes): wide planted steps, the whole bulk rolling '
         'from side to side over each foot, arms held out and swinging slowly.', stride=2.4, A=24, K=34, arm_swing=18,
         elbow=22, elbow_fwd=8, torso_pitch=6, twist=9, bob=0.9, sway=1.2, arm_roll=18, leg_roll=5, body_y=0.9,
         torso_extra=lambda t: (0, 0, 3 * sn(t)))
    gait('lope', 'Loping (beasts): hunched low, long bounding strides with the arms swinging loose ahead of the body.',
         stride=3.0, A=42, K=60, arm_swing=34, elbow=30, elbow_fwd=10, torso_pitch=28, twist=6, bob=1.1, sway=0.3,
         body_y=2.4, head=-14, bounce_up=0.6)


def main():
    defaults()
    import gen_styles
    gen_styles.styles()
    os.makedirs(ROOT, exist_ok=True)
    for cid, j in OUT.items():
        with open(os.path.join(ROOT, cid + '.json'), 'w') as f:
            json.dump(j, f, indent=1)
            f.write('\n')
    print(len(OUT), 'clips')


if __name__ == '__main__':
    import sys
    sys.path.insert(0, os.path.dirname(__file__))
    sys.modules['gen_motion'] = sys.modules[__name__]
    main()

"""The sixteen fighting styles' clips (CX-31), nine slots each, built with tools/gen_motion.py's helpers.

Each style is a character with its own body language, kept the same through every slot: how it stands, walks, runs,
floats, flies, powers up and fights. Clip ids are style_<who>_<slot> (idle, walk, sprint, hover, flight, fast, charge,
fight, steps); data/dbzenith/styles/*.json map the slots to them.
"""
from gen_motion import *

NEUTRAL_LEGS = ((0, 0, 4, 2), (-3, 0, -4, 6))
WIDE_LEGS = ((-2, 0, 9, 10), (-2, 0, -9, 10))


def scratch(base, at=0.55, until=0.75):
    """Acting beat: the right hand goes up to scratch the back of the head (a sheepish grin)."""
    return lambda t: {'rightArm': add(toward(base, HEAD, win(t, at, until, 0.05)), (0, 0, 0, 10 * win(t, at, until, 0.05) * sn(t, 12))),
                      'head': (8 * win(t, at, until), 6 * win(t, at, until), 6 * win(t, at, until))}


def styles():
    # ================================================================== Goku: wild_saiyan. Easy, bouncy, cheerful
    goku_arm = (4, 0, 12, 18)
    held_idle('style_goku_idle', 'Goku: easy and loose, rocking from foot to foot with the arms swinging free; now and '
              'then a hand goes up to scratch the back of his head with a grin.', (goku_arm, mir(goku_arm)),
              legs=((-3, 0, 6, 8), (0, 0, -5, 2)), torso=(-2, 4, 2), head=(-3, -6, 0), body_y=0.3, sway=1.8, length=120,
              beats=lambda t: {**scratch(goku_arm)(t), 'leftArm': (4 * sn(t, 2), 0, 3 * sn(t, 2), 0)})
    gait('style_goku_walk', 'Goku: a bouncy, carefree walk with a big easy arm swing and a spring in every step.',
         stride=2.6, A=32, K=54, arm_swing=32, elbow=12, elbow_fwd=18, torso_pitch=0, twist=10, bob=0.75, sway=0.6,
         arm_roll=9, bounce_up=0.35, head=-2)
    gait('style_goku_sprint', 'Goku: an all-out athlete\'s run, knees punching high, arms pumping hard, leaning into it.',
         stride=3.6, A=50, K=84, arm_swing=62, elbow=60, elbow_fwd=24, torso_pitch=14, twist=12, bob=0.95, sway=0.25,
         arm_roll=5, head=-10, bounce_up=0.55)
    hover('style_goku_hover', 'Goku: floating easily, one knee up, arms loose and out, bobbing and drifting.',
          arms=((-6, 0, 22, 26), (-10, 0, -28, 32)), legs=((-34, 0, 6, 62), (6, 0, -4, 18)), torso=(-3, 0, 0), bob=1.2, drift=1.4, length=70)
    flight('style_goku_flight', 'Goku: one fist out ahead, the other arm along the side, legs trailing.',
           (-166, -4, -6, 4), (18, 0, -8, 6), head=-8, flutter=1.2)
    fastfly('style_goku_fast', 'Goku flat out: the fist punched ahead, everything streamlined behind it.',
            (-172, -2, -4, 0), (24, 0, -8, 4))
    charge('style_goku_charge', 'Goku: a deep wide crouch, fists drawn to the hips, head thrown back with a roar, '
           'surging.', None, arms=((22, 0, 18, 96), (22, 0, -16, 98)), legs=((-10, 0, 24, 46), (-10, 0, -24, 46)),
           torso=(-6, 0, 0), head=(-24, 2, 0), crouch=3.0, shake=1.2)
    stance('style_goku_fight', 'Goku: a wide springy stance, the lead hand open and forward, the rear fist at the hip, '
           'bouncing eagerly and pawing with the lead hand.', (14, -14, 26, 112), (-78, 18, -14, 34),
           (22, 0, 16, 34), (-30, 0, -10, 36), (12, -26, 0), (-8, 20, 0), 1.8, bounces=4, bounce=0.6, length=40, paw=1.0)
    steps('style_goku_steps', 'Goku: quick light steps keeping the wide stance.', (14, -14, 26, 112), (-78, 18, -14, 34),
          (12, -24, 0), 1.8, A=24, K=40, bob=0.5)

    # ================================================================== Vegeta: saiyan_prince. Proud, rigid, impatient
    held_idle('style_vegeta_idle', 'Vegeta: arms folded, chin up, weight on one leg, utterly unimpressed; now and then '
              'he taps a foot and turns his head away.', (CROSSED_R, CROSSED_L), legs=((4, 0, 6, 0), (-8, 0, -4, 10)),
              torso=(-5, 6, 0), head=(-8, -10, 0), body_y=0.0, sway=0.4, length=110, look=0.4,
              beats=lambda t: {'leftLeg': (-6 * win(t, 0.6, 0.8) * max(0.0, sn(t, 10)), 0, 0, 6 * win(t, 0.6, 0.8) * max(0.0, sn(t, 10))),
                               'head': (0, -18 * win(t, 0.3, 0.45), 0)})
    gait('style_vegeta_walk', 'Vegeta: a brisk, upright march with the arms folded and the chin up.', stride=2.3, A=26, K=40,
         arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=-4, twist=3, bob=0.4, sway=0.35, head=-7,
         arms=lambda t: add(CROSSED_R, (1.5 * cs(t, 2), 0, 0, 0)), left_arm=lambda t: add(CROSSED_L, (1.5 * cs(t, 2, 0.1), 0, 0, 0)))
    gait('style_vegeta_sprint', 'Vegeta: a tight, driving run, fists clenched and pumping close to the body.',
         stride=3.4, A=46, K=76, arm_swing=50, elbow=82, elbow_fwd=12, torso_pitch=16, twist=8, bob=0.8, sway=0.2,
         arm_roll=4, head=-12)
    hover('style_vegeta_hover', 'Vegeta: arms folded, legs straight and together, chin up, barely moving.',
          arms=(CROSSED_R, CROSSED_L), legs=((4, 0, 1, 2), (5, 0, -1, 4)), torso=(-4, 0, 0), head=(-4, 0, 0), bob=0.6, drift=0.4, length=90)
    flight('style_vegeta_flight', 'Vegeta: rigid, the arms straight back along the sides, fists clenched.',
           (22, 0, 10, 0), (24, 0, -10, 0), rl=(4, 0, 1, 2), ll=(4, 0, -1, 2), head=-6, flutter=0.5)
    fastfly('style_vegeta_fast', 'Vegeta flat out: arms locked back, head down into the wind.', (34, 0, 16, 4), (32, 0, -17, 4),
            head=2, tremble=1.2, torso=(0, 0, 0))
    charge('style_vegeta_charge', 'Vegeta: fists drawn in at the sides, the whole body clenched and shaking, head '
           'thrown back screaming.', None, arms=((-8, 0, 52, 70), (-8, 0, -50, 72)), legs=((-6, 0, 22, 34), (-6, 0, -22, 34)),
           torso=(-10, 0, 0), head=(-30, 3, 0), crouch=2.4, shake=1.5, surge_arms=(4, 0, 10, 14))
    stance('style_vegeta_fight', 'Vegeta: coiled low with his weight forward, elbows tucked and both fists close in '
           'front of his face, shoulders hunched, glaring through them; a tight, short bounce.', (-34, -22, 8, 132), (-52, 20, -6, 124),
           (24, 0, 14, 42), (-28, 0, -10, 46), (20, -18, 0), (-20, 12, 0), 2.8, bounces=2, bounce=0.35, length=26, weave=0.6)
    steps('style_vegeta_steps', 'Vegeta: low stalking steps behind the fists.', (-34, -22, 8, 132), (-52, 20, -6, 124),
          (20, -16, 0), 2.8, A=18, K=30, head=-20)

    # ================================================================== Piccolo: demon_clan. Tall, still, watchful
    held_idle('style_piccolo_idle', 'Piccolo: arms folded, head bowed and eyes closed in meditation, barely breathing; '
              'once a loop he lifts his head to look.', (CROSSED_R, CROSSED_L), legs=((0, 0, 7, 0), (0, 0, -7, 0)),
              torso=(2, 0, 0), head=(12, 0, 0), sway=0.2, look=0.0, breath=0.7, length=160,
              beats=lambda t: {'head': (-18 * win(t, 0.5, 0.68, 0.08), 10 * win(t, 0.56, 0.64, 0.04), 0)})
    gait('style_piccolo_walk', 'Piccolo: long, measured strides, arms folded, head level.', stride=2.9, A=26, K=36,
         arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=1, twist=2, bob=0.3, sway=0.3, head=4,
         arms=lambda t: CROSSED_R, left_arm=lambda t: CROSSED_L)
    gait('style_piccolo_sprint', 'Piccolo: long loping strides, arms swept low and back.', stride=3.8, A=48, K=70,
         arm_swing=40, elbow=20, elbow_fwd=12, torso_pitch=14, twist=6, bob=0.7, sway=0.2, arm_roll=10, head=-6)
    hover('style_piccolo_hover', 'Piccolo: meditating cross-legged in the air, arms folded, rising and sinking slowly.',
          arms=(CROSSED_R, CROSSED_L), legs=((-86, 28, 10, 116), (-86, -28, -10, 116)), torso=(0, 0, 0), head=(14, 0, 0), bob=0.8, drift=0.3, length=110)
    flight('style_piccolo_flight', 'Piccolo: arms folded even in flight, legs together trailing.', CROSSED_R, CROSSED_L,
           rl=(3, 0, 1, 2), ll=(4, 0, -1, 4), head=-4, flutter=0.7)
    fastfly('style_piccolo_fast', 'Piccolo flat out: arms folded tight, streamlined.', CROSSED_R, CROSSED_L, head=-4, tremble=0.8)
    charge('style_piccolo_charge', 'Piccolo: two fingers pressed to the forehead (Special Beam Cannon), the other hand '
           'gripping the forearm, side-on and shaking.', None, arms=((-150, -36, 0, 126), (-70, 50, -10, 120)),
           legs=((6, 0, 18, 24), (-14, 0, -16, 28)), torso=(2, -10, 0), head=(6, 4, 0), crouch=1.8, shake=0.9,
           surge_arms=(0, 0, 0, 0))
    stance('style_piccolo_fight', 'Piccolo: tall and side-on, the lead hand open and reaching, the rear hand at the '
           'chest; a slow, wary sway rather than a bounce.', (-40, -30, 22, 118), (-92, 26, -10, 30),
           (16, 0, 16, 30), (-24, 0, -12, 14), (4, -34, 0), (-4, 26, 0), 1.0, bounces=1, bounce=0.25, length=44, weave=1.4, sway=0.6)
    steps('style_piccolo_steps', 'Piccolo: a side-on glide.', (-40, -30, 22, 118), (-92, 26, -10, 30), (4, -32, 0), 1.0,
          stride=2.2, A=22, K=28, bob=0.25)

    # ================================================================== Gohan: mystic. Calm, modest, grounded
    held_idle('style_gohan_idle', 'Gohan: relaxed, a hand on the hip, weight easy; now and then he rubs the back of '
              'his head, a little shy.', (HIP, (2, 0, -6, 10)), legs=((0, 0, 5, 0), (-6, 0, -3, 14)), torso=(-1, 4, -3),
              head=(0, -6, 5), sway=0.9, length=110,
              beats=lambda t: {'leftArm': add(toward((2, 0, -6, 10), mir(HEAD), win(t, 0.62, 0.78)), (0, 0, 0, 10 * win(t, 0.62, 0.78) * sn(t, 10))),
                               'head': (8 * win(t, 0.62, 0.78), 0, -4 * win(t, 0.62, 0.78))})
    gait('style_gohan_walk', 'Gohan: a calm, natural walk, easy on the feet.', stride=2.5, A=28, K=44, arm_swing=24,
         elbow=12, elbow_fwd=14, torso_pitch=1, twist=6, bob=0.5, sway=0.45, head=0)
    gait('style_gohan_sprint', 'Gohan: a smooth athlete\'s run.', stride=3.5, A=46, K=76, arm_swing=56, elbow=64,
         elbow_fwd=20, torso_pitch=12, twist=9, bob=0.8, sway=0.2, head=-8)
    hover('style_gohan_hover', 'Gohan: floating upright, legs together and slightly bent, arms easy at the sides.',
          arms=((-4, 0, 14, 16), (-6, 0, -16, 20)), legs=((-6, 0, 1, 14), (-2, 0, -1, 10)), bob=0.9, drift=0.8, length=80)
    flight('style_gohan_flight', 'Gohan: smooth and streamlined, legs together, arms along the body.',
           (12, 0, 6, 6), (12, 0, -7, 8), rl=(6, 0, 1, 4), ll=(6, 0, -1, 4), head=-10, flutter=0.6)
    fastfly('style_gohan_fast', 'Gohan flat out: both fists forward, legs together.', (-168, -6, -4, 4), (-168, 6, 4, 4))
    charge('style_gohan_charge', 'Gohan: both hands raised over the head (Masenko), braced wide and trembling.', None,
           arms=((-158, -18, 0, 40), (-158, 18, 0, 40)), legs=((16, 0, 12, 24), (-20, 0, -10, 30)), torso=(-12, 0, 0),
           head=(-14, 0, 0), crouch=1.6, surge_arms=(-6, 0, 0, -10))
    stance('style_gohan_fight', 'Gohan: low and wide, both hands open, the lead reaching, rocking smoothly.',
           (-56, -26, 18, 86), (-58, 18, -18, 30), (12, 0, 24, 42), (-16, 0, -22, 44), (2, -18, 0), (0, 14, 0), 2.8,
           bounces=2, bounce=0.35, length=36, sway=0.5)
    steps('style_gohan_steps', 'Gohan: low wide steps.', (-56, -26, 18, 86), (-58, 18, -18, 30), (2, -16, 0), 2.8, A=20, K=32)

    # ================================================================== Krillin: turtle_school. Scrappy and nimble
    held_idle('style_krillin_idle', 'Krillin: on his toes, fists loose and ready, glancing about a bit nervously.',
              ((-10, 0, 10, 40), (-12, 0, -11, 44)), legs=((-4, 0, 6, 12), (-4, 0, -6, 12)), torso=(3, 0, 0), head=(0, -10, 0),
              body_y=0.6, sway=1.4, look=2.2, length=70)
    gait('style_krillin_walk', 'Krillin: quick, short, purposeful steps, elbows bent.', stride=2.0, A=28, K=50, arm_swing=28,
         elbow=40, elbow_fwd=20, torso_pitch=4, twist=8, bob=0.6, sway=0.4, head=-2)
    gait('style_krillin_sprint', 'Krillin: a low, scrambling run, arms pumping fast.', stride=2.8, A=48, K=80, arm_swing=56,
         elbow=84, elbow_fwd=10, torso_pitch=18, twist=10, bob=0.85, sway=0.25, head=-12)
    hover('style_krillin_hover', 'Krillin: knees tucked up, arms out for balance, bobbing.', arms=((-20, 0, 34, 30), (-24, 0, -38, 34)),
          legs=((-54, 0, 6, 90), (-58, 0, -6, 96)), torso=(8, 0, 0), bob=1.1, drift=1.0, length=50)
    flight('style_krillin_flight', 'Krillin: both hands reaching forward overhead, legs trailing.', (-150, -12, -4, 24), (-152, 12, 4, 22),
           head=-10, flutter=1.0)
    fastfly('style_krillin_fast', 'Krillin flat out: both arms straight ahead.', (-170, -6, -2, 4), (-170, 6, 2, 4))
    charge('style_krillin_charge', 'Krillin: one hand raised flat overhead (Destructo Disc), the other on the hip.', None,
           arms=((-176, 0, -6, 4), mir(HIP)), legs=((-4, 0, 14, 20), (-4, 0, -14, 20)), torso=(0, 0, 0),
           head=(-24, 0, 0), crouch=1.4, shake=0.8, surge_arms=(-4, 0, 0, 0))
    stance('style_krillin_fight', 'Krillin: the lead palm out, the rear hand cocked by the ribs, low and bouncing fast.',
           (20, -20, 18, 104), (-90, 22, -6, 14), (30, 0, 10, 6), (-34, 0, -6, 44), (6, -30, 0), (-4, 24, 0), 2.0,
           bounces=4, bounce=0.6, length=32, paw=0.6)
    steps('style_krillin_steps', 'Krillin: low quick steps.', (20, -20, 18, 104), (-90, 22, -6, 14), (6, -28, 0), 2.0, stride=1.4, A=20, K=36)

    # ================================================================== Roshi: turtle_hermit. An old master
    held_idle('style_roshi_idle', 'Master Roshi: stooped, hands behind the back, chin up; once a loop he strokes his '
              'beard, thinking.', (BEHIND, mir(BEHIND)), legs=((-6, 0, 4, 14), (-2, 0, -4, 10)), torso=(24, 0, 0),
              head=(-22, 0, 0), body_y=0.8, sway=0.6, length=120,
              beats=lambda t: {'rightArm': add(toward(BEHIND, (-118, 30, -6, 140), win(t, 0.55, 0.78)), (6 * win(t, 0.58, 0.75) * sn(t, 8), 0, 0, 0)),
                               'head': (6 * win(t, 0.55, 0.78), 0, 0)})
    gait('style_roshi_walk', 'Master Roshi: an old man\'s shuffle, stooped, hands clasped behind.', stride=1.4, A=16, K=22,
         arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=22, twist=3, bob=0.3, sway=0.6, head=-20, body_y=0.8,
         arms=lambda t: BEHIND, left_arm=lambda t: mir(BEHIND))
    gait('style_roshi_sprint', 'Master Roshi: a surprisingly quick, hunched scuttle.', stride=2.6, A=40, K=60, arm_swing=30,
         elbow=70, elbow_fwd=10, torso_pitch=24, twist=6, bob=0.6, sway=0.3, head=-26, arm_roll=14)
    hover('style_roshi_hover', 'Master Roshi: hunched in the air, hands behind his back, knees bent.',
          arms=(BEHIND, mir(BEHIND)), legs=((-20, 0, 4, 40), (-10, 0, -4, 30)), torso=(18, 0, 0), head=(-18, 0, 0), bob=0.7, length=90)
    flight('style_roshi_flight', 'Master Roshi: hands behind his back, legs together.', BEHIND, mir(BEHIND), head=-14, flutter=0.6)
    fastfly('style_roshi_fast', 'Master Roshi flat out: still with his hands behind his back.', BEHIND, mir(BEHIND), head=-10)
    charge('style_roshi_charge', 'Master Roshi: Max Power, both arms flexed up in a double biceps pose, swelling.', None,
           arms=((-4, -10, 88, 108), (-4, 10, -86, 112)), legs=((-6, 0, 20, 30), (-6, 0, -20, 30)), torso=(-8, 0, 0),
           head=(-10, 0, 0), crouch=2.0, shake=1.3, surge_arms=(-6, 0, 6, 10))
    stance('style_roshi_fight', 'Master Roshi: knees deeply bent, one open hand raised high, the other low; tottering '
           'slowly like an old man until he strikes.', (-46, -16, 20, 30), (-128, 14, -16, 40), (12, 0, 14, 52), (-30, 0, -6, 22),
           (6, -22, 0), (-6, 18, 0), 2.2, bounces=1, bounce=0.3, length=48, sway=0.8, weave=1.2)
    steps('style_roshi_steps', 'Master Roshi: a crouched shuffle.', (-46, -16, 20, 30), (-128, 14, -16, 40), (6, -20, 0), 2.2,
          stride=1.4, A=16, K=30)

    # ================================================================== Tien: crane_school. Disciplined, precise
    held_idle('style_tien_idle', 'Tien: standing to attention, arms straight at the sides, utterly still and composed.',
              ((2, 0, 3, 4), (2, 0, -3, 4)), legs=((0, 0, 1, 0), (0, 0, -1, 0)), torso=(-3, 0, 0), head=(-2, 0, 0),
              sway=0.2, look=0.5, breath=0.8, length=140)
    gait('style_tien_walk', 'Tien: an upright march, straight arms swinging precisely.', stride=2.5, A=30, K=46, arm_swing=30,
         elbow=2, elbow_fwd=2, torso_pitch=-4, twist=4, bob=0.35, sway=0.3, head=-2, arm_roll=3)
    gait('style_tien_sprint', 'Tien: a disciplined, powerful run.', stride=3.5, A=48, K=80, arm_swing=56, elbow=70,
         elbow_fwd=16, torso_pitch=12, twist=8, bob=0.75, sway=0.2, head=-8)
    hover('style_tien_hover', 'Tien: upright, the forearms held out in front, legs straight, perfectly level.',
          arms=((-56, -40, 0, 70), (-56, 40, 0, 70)), legs=((2, 0, 1, 2), (2, 0, -1, 2)), head=(6, 0, 0), bob=0.5, drift=0.2, length=120)
    flight('style_tien_flight', 'Tien: arms straight along the sides, legs straight.', (14, 0, 6, 0), (14, 0, -6, 0),
           rl=(3, 0, 1, 0), ll=(3, 0, -1, 0), head=-10, flutter=0.4)
    fastfly('style_tien_fast', 'Tien flat out: arms straight back.', (30, 0, 8, 0), (30, 0, -8, 0))
    charge('style_tien_charge', 'Tien: both hands forward with thumbs and fingers joined in the Tri-Beam\'s triangle.',
           None, arms=((-84, -24, 0, 20), (-84, 24, 0, 20)), legs=((14, 0, 14, 30), (-22, 0, -12, 34)), torso=(6, 0, 0),
           head=(-6, 0, 0), crouch=2.2, shake=1.0, surge_arms=(-4, 0, 0, -6))
    stance('style_tien_fight', 'Tien: the crane: balanced on one leg with the other knee drawn high, the rear arm '
           'raised like a beak, steady and poised.', (-166, -10, 20, 76), (-40, 14, -16, 46), (2, 0, 3, 12), (-84, 0, -4, 110),
           (0, -20, 0), (0, 16, 0), 0.7, bounces=0, bounce=0.0, length=60, sway=0.25, weave=0.5, breath=1.0)
    steps('style_tien_steps', 'Tien: precise high-kneed steps, keeping the crane\'s arms.', (-166, -10, 20, 76), (-40, 14, -16, 46),
          (2, -20, 0), 1.2, stride=1.6, A=20, K=70)

    # ================================================================== Yamcha: wolf_fang. Cocky, prowling
    held_idle('style_yamcha_idle', 'Yamcha: cocky and relaxed, one hand behind the head, the other on the hip, rocking '
              'on his heels.', (HEAD, mir(HIP)), legs=((-6, 0, 4, 14), (0, 0, -6, 0)), torso=(-2, -6, 3), head=(-2, 8, -4),
              sway=1.3, length=90)
    gait('style_yamcha_walk', 'Yamcha: a swaggering stroll, one hand behind the head.', stride=2.5, A=30, K=46, arm_swing=26,
         elbow=14, elbow_fwd=12, torso_pitch=-2, twist=10, bob=0.6, sway=0.9, head=-2,
         arms=lambda t: add(HEAD, (2 * sn(t, 2), 0, 0, 0)))
    gait('style_yamcha_sprint', 'Yamcha: a bounding wolf\'s run, leaning low with the arms swept back.', stride=3.8, A=52,
         K=84, arm_swing=36, elbow=24, elbow_fwd=10, torso_pitch=22, twist=8, bob=1.0, sway=0.25, head=-20, bounce_up=0.6,
         arms=lambda t: (44 + 10 * cs(t, 1), 0, 14, 24))
    hover('style_yamcha_hover', 'Yamcha: crouched in the air, both knees bent, hands up like claws, ready.',
          arms=((-60, -10, 16, 60), (-70, 10, -14, 64)), legs=((-36, 0, 6, 70), (-24, 0, -6, 60)), torso=(10, 0, 0), bob=1.0, length=60)
    flight('style_yamcha_flight', 'Yamcha: arms swept back, hands clawed, legs trailing.', (34, 0, 18, 30), (34, 0, -18, 30), head=-12)
    fastfly('style_yamcha_fast', 'Yamcha flat out: a clawed hand reaching ahead.', (-166, -6, -6, 20), (30, 0, -12, 30))
    charge('style_yamcha_charge', 'Yamcha: one hand up and forward guiding the Spirit Ball, the other low.', None,
           arms=((-96, -8, 6, 40), (-30, 0, -34, 20)), legs=((12, 0, 12, 22), (-16, 0, -12, 26)), torso=(0, 0, 0),
           head=(-8, -6, 0), crouch=1.4, shake=0.8, surge_arms=(-6, 0, 0, -6))
    stance('style_yamcha_fight', 'Yamcha: the wolf, crouched low with the hands up like claws, prowling side to side '
           'and bouncing fast, ready to pounce.', (-58, -10, 16, 52), (-80, 10, -14, 60), (14, 0, 14, 60), (-34, 0, -10, 64),
           (24, -16, 0), (-24, 12, 0), 3.2, bounces=4, bounce=0.5, length=28, sway=0.9, weave=1.3)
    steps('style_yamcha_steps', 'Yamcha: low prowling steps.', (-58, -10, 16, 52), (-80, 10, -14, 60), (24, -14, 0), 3.2,
          stride=1.6, A=22, K=40, sway=0.6)

    # ================================================================== Trunks: future_swordsman. Cool, composed
    strap = (-38, -42, 4, 120)
    held_idle('style_trunks_idle', 'Future Trunks: composed, a hand on the sword strap at his chest, the other at his '
              'side, weight easy.', (strap, (0, 0, -5, 6)), legs=((0, 0, 4, 0), (-3, 0, -4, 6)), torso=(-1, -4, 0),
              head=(2, 6, 0), sway=0.6, length=110)
    gait('style_trunks_walk', 'Future Trunks: a steady walk, a hand on the strap.', stride=2.5, A=28, K=44, arm_swing=24,
         elbow=12, elbow_fwd=12, torso_pitch=2, twist=6, bob=0.45, sway=0.4, arms=lambda t: add(strap, (1.5 * cs(t, 2), 0, 0, 0)))
    gait('style_trunks_sprint', 'Future Trunks: a fast, low run with the arms swept back.', stride=3.6, A=48, K=78,
         arm_swing=20, elbow=6, elbow_fwd=4, torso_pitch=18, twist=6, bob=0.8, sway=0.2, head=-16,
         arms=lambda t: (62 + 6 * cs(t, 1), 0, 12, 6))
    hover('style_trunks_hover', 'Future Trunks: upright, arms loose, legs slightly apart.', arms=((-4, 0, 12, 14), (-4, 0, -12, 14)),
          legs=((-10, 0, 3, 20), (2, 0, -3, 10)), bob=0.9, length=80)
    flight('style_trunks_flight', 'Future Trunks: a hand back on the hilt over the shoulder, legs trailing.', (-150, 20, 10, 120),
           (14, 0, -6, 6), head=-8)
    fastfly('style_trunks_fast', 'Future Trunks flat out: a hand on the hilt, the other back.', (-150, 20, 10, 120), (28, 0, -8, 6))
    charge('style_trunks_charge', 'Future Trunks: both arms thrust forward (Burning Attack), braced.', None,
           arms=((-100, -20, 0, 30), (-100, 20, 0, 30)), legs=((16, 0, 14, 28), (-20, 0, -12, 32)), torso=(4, 0, 0),
           head=(-6, 0, 0), crouch=2.0, surge_arms=(-6, 0, 0, -10))
    stance('style_trunks_fight', 'Future Trunks: both hands up over the right shoulder on the sword\'s hilt, steady.',
           (-96, -34, 8, 54), (-88, 30, -8, 70), (20, 0, 12, 30), (-22, 0, -10, 32), (8, -26, 0), (-6, 20, 0), 1.5,
           bounces=2, bounce=0.3, length=34)
    steps('style_trunks_steps', 'Future Trunks: measured steps, the sword ready.', (-96, -34, 8, 54), (-88, 30, -8, 70), (8, -24, 0), 1.5)

    # ================================================================== Frieza: galactic_emperor. Graceful contempt
    held_idle('style_frieza_idle', 'Frieza: hands clasped behind the back, chin raised, almost perfectly still; now and '
              'then a slow, mocking tilt of the head.', (BEHIND, mir(BEHIND)), legs=((2, 0, 2, 0), (-4, 4, -2, 8)),
              torso=(-6, 0, 0), head=(-4, 0, 6), sway=0.3, look=0.6, length=130,
              beats=lambda t: {'head': (-4 * win(t, 0.6, 0.8), 12 * win(t, 0.6, 0.8), 10 * win(t, 0.6, 0.8)), 'torso': (0, 0, 2 * win(t, 0.62, 0.78) * sn(t, 8))})
    gait('style_frieza_walk', 'Frieza: gliding, unhurried steps, hands behind the back, hips swaying.', stride=1.7, A=18, K=24,
         arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=-6, twist=3, bob=0.12, sway=0.6, head=-4,
         arms=lambda t: BEHIND, left_arm=lambda t: mir(BEHIND))
    gait('style_frieza_sprint', 'Frieza: a smooth, fast glide, still with the hands behind.', stride=3.0, A=28, K=36,
         arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=6, twist=3, bob=0.2, sway=0.4, head=-6,
         arms=lambda t: BEHIND, left_arm=lambda t: mir(BEHIND))
    hover('style_frieza_hover', 'Frieza: hands behind his back, ankles crossed, hanging almost motionless.',
          arms=(BEHIND, mir(BEHIND)), legs=((4, -8, -4, 6), (6, 8, 4, 10)), torso=(-6, 0, 0), head=(6, 0, 0), bob=0.4, drift=0.3, length=120)
    flight('style_frieza_flight', 'Frieza: hands behind his back, upright and serene.', BEHIND, mir(BEHIND), rl=(4, -6, -3, 4),
           ll=(6, 6, 3, 8), head=-14, flutter=0.4)
    fastfly('style_frieza_fast', 'Frieza flat out: hands still behind his back.', BEHIND, mir(BEHIND), head=-10, tremble=0.6)
    charge('style_frieza_charge', 'Frieza: one finger raised to the sky (Supernova), the other hand behind the back, '
           'calm and smiling.', None, arms=((-178, 0, -4, 0), BEHIND[:3] + (60,)), legs=((2, 0, 2, 0), (-4, 4, -2, 8)),
           torso=(-6, 0, 0), head=(-12, 0, 4), crouch=0.0, shake=0.3, surge_arms=(0, 0, 0, 0))
    OUT['style_frieza_charge']['bones']['leftArm'] = [{'t': 0, 'rot': [35, -30, 10], 'bend': 60}]
    stance('style_frieza_fight', 'Frieza: upright and contemptuous, one hand raised casually, the other behind the back, '
           'the head tilted.', (-82, -10, 8, 18), mir(BEHIND), (6, 0, 4, 4), (-8, 4, -3, 8), (-4, -12, 0), (-2, 10, 7), 0.1,
           bounces=0, bounce=0.0, length=60, sway=0.2, weave=0.6, breath=0.8)
    steps('style_frieza_steps', 'Frieza: gliding steps, the hand raised.', (-82, -10, 8, 18), mir(BEHIND), (-4, -12, 0), 0.1,
          stride=1.6, A=16, K=22, bob=0.1)

    # ================================================================== Cell: perfect_warrior. Smug, tall, showy
    held_idle('style_cell_idle', 'Cell: hands on the hips, head cocked, supremely smug; once a loop he laughs, '
              'shoulders shaking.', (HIP, mir(HIP)), legs=((0, 0, 8, 0), (0, 0, -8, 0)), torso=(-6, 0, 0), head=(-6, 0, 8),
              sway=0.6, length=120,
              beats=lambda t: {'torso': (-6 * win(t, 0.6, 0.76) + 2 * win(t, 0.6, 0.76) * sn(t, 16), 0, 0),
                               'head': (-12 * win(t, 0.6, 0.76), 0, 0)})
    gait('style_cell_walk', 'Cell: long confident strides, chest out, arms swinging wide and slow.', stride=2.8, A=30, K=42,
         arm_swing=22, elbow=12, elbow_fwd=8, torso_pitch=-6, twist=6, bob=0.4, sway=0.5, arm_roll=16, head=-6)
    gait('style_cell_sprint', 'Cell: a long powerful run.', stride=3.8, A=48, K=72, arm_swing=54, elbow=58, elbow_fwd=18,
         torso_pitch=10, twist=8, bob=0.75, sway=0.2, head=-6)
    hover('style_cell_hover', 'Cell: arms folded, legs straight, looking down on everything.', arms=(CROSSED_R, CROSSED_L),
          legs=((3, 0, 2, 4), (3, 0, -2, 4)), head=(-4, 0, 6), bob=0.6, drift=0.4, length=90)
    flight('style_cell_flight', 'Cell: arms swept wide and back, legs together.', (30, 0, 30, 10), (32, 0, -32, 12),
           rl=(4, 0, 1, 4), ll=(4, 0, -1, 4), head=-10, flutter=0.7)
    fastfly('style_cell_fast', 'Cell flat out: arms swept back.', (36, 0, 20, 6), (36, 0, -20, 6))
    charge('style_cell_charge', 'Cell: turned, both hands cupped at the hip gathering a Kamehameha, sinking low.', None,
           arms=((24, -20, 20, 96), (-10, 64, -6, 112)), legs=((18, 0, 12, 30), (-22, 0, -12, 36)), torso=(10, 34, 0),
           head=(-4, -28, 0), crouch=2.2, surge_arms=(4, 0, 0, 4))
    stance('style_cell_fight', 'Cell: a wide, open-handed guard, upright and menacing, swaying slowly with the head '
           'cocked.', (-54, -10, 40, 50), (-58, 10, -40, 48), (10, 0, 16, 24), (-12, 0, -16, 26), (4, -8, -3), (-4, 6, 8), 1.4,
           bounces=1, bounce=0.3, length=40, sway=0.5)
    steps('style_cell_steps', 'Cell: wide confident steps.', (-54, -10, 40, 50), (-58, 10, -40, 48), (4, -8, 0), 1.4, stride=2.0)

    # ================================================================== Broly: legendary_berserker. Heavy and feral
    held_idle('style_broly_idle', 'Broly: hunched and heavy, arms held out by the bulk, glaring from under the brow, '
              'each breath heaving the whole upper body; a twitch of the neck now and then.', ((-14, 0, 20, 28), (-14, 0, -20, 28)),
              legs=((-6, 0, 12, 18), (-6, 0, -12, 18)), torso=(18, 0, 0), head=(-20, 0, 0), body_y=1.4, breath=3.0,
              sway=0.5, length=60,
              beats=lambda t: {'head': (0, 0, 10 * win(t, 0.7, 0.74, 0.02)), 'rightArm': (0, 0, 0, 10 * win(t, 0.7, 0.74, 0.02))})
    gait('style_broly_walk', 'Broly: heavy stomping strides, the bulk rolling over each foot, arms swinging wide.',
         stride=2.6, A=26, K=36, arm_swing=20, elbow=26, elbow_fwd=10, torso_pitch=12, twist=10, bob=1.2, sway=1.4,
         arm_roll=22, leg_roll=6, body_y=1.2, head=-16, torso_extra=lambda t: (0, 0, 3 * sn(t)))
    gait('style_broly_sprint', 'Broly: a bounding charge, huge strides, arms flung wide and back with the hands clawed.',
         stride=4.2, A=54, K=74, arm_swing=26, elbow=40, elbow_fwd=6, torso_pitch=22, twist=8, bob=1.1, sway=0.5,
         head=-24, bounce_up=0.7, arms=lambda t: (40 + 14 * cs(t, 1), 0, 34, 40))
    hover('style_broly_hover', 'Broly: hanging menacingly, arms spread down and out, legs apart, head low.',
          arms=((-10, 0, 34, 40), (-10, 0, -34, 40)), legs=((-6, 0, 8, 20), (-4, 0, -8, 16)), torso=(14, 0, 0), head=(-18, 0, 0), bob=0.8, length=60)
    flight('style_broly_flight', 'Broly: shoulders forward, arms flung back wide with the hands clawed.', (40, 0, 40, 34),
           (40, 0, -40, 34), head=-20, flutter=1.0)
    fastfly('style_broly_fast', 'Broly flat out: a shoulder-first charge, arms back.', (44, 0, 26, 20), (44, 0, -26, 20), head=-16, tremble=1.5)
    charge('style_broly_charge', 'Broly: roaring, arms spread up and back, the body shaking with rage.', None,
           arms=((-40, 0, 70, 30), (-40, 0, -66, 34)), legs=((-6, 0, 22, 30), (-6, 0, -22, 30)), torso=(-14, 0, 0),
           head=(-36, 3, 0), crouch=2.0, shake=2.0, surge_arms=(-10, 0, 10, 0))
    stance('style_broly_fight', 'Broly: hunched over, arms spread wide with the hands clawed, swaying heavily and '
           'breathing hard.', (-40, 0, 46, 58), (-36, 0, -50, 62), (6, 0, 20, 46), (-10, 0, -20, 48), (22, -4, -4), (-24, 4, 4), 2.8,
           bounces=1, bounce=0.45, length=30, sway=0.8, breath=2.0)
    steps('style_broly_steps', 'Broly: heavy, rolling steps.', (-40, 0, 46, 58), (-36, 0, -50, 62), (22, -4, -6), 2.8,
          stride=2.0, A=24, K=36, bob=0.9, sway=1.0)

    # ================================================================== Buu: majin_play. Childish, bouncy
    held_idle('style_buu_idle', 'Majin Buu: bouncing on the spot like a child, swinging the arms, rocking the head '
              'side to side, belly out.', ((-24, 0, 26, 20), (10, 0, -14, 14)), legs=((0, 0, 8, 8), (-4, 0, -6, 14)),
              torso=(-4, 0, 0), head=(-4, 0, 8), body_y=0.2, sway=1.5, length=40,
              beats=lambda t: {'body': (0, -0.8 * max(0.0, sn(t, 4)), 0), 'head': (0, 0, 10 * sn(t, 2)),
                               'rightArm': (-14 * sn(t, 2), 0, 8 * sn(t, 2), 0), 'leftArm': (14 * sn(t, 2), 0, -8 * sn(t, 2), 0)})
    gait('style_buu_walk', 'Majin Buu: skipping along, knees high, arms flung out, bouncing up on every step.',
         stride=2.4, A=36, K=70, arm_swing=34, elbow=16, elbow_fwd=10, torso_pitch=-2, twist=8, bob=0.6, sway=0.8,
         arm_roll=36, bounce_up=1.2, head=-4, torso_extra=lambda t: (0, 0, 4 * sn(t)))
    gait('style_buu_sprint', 'Majin Buu: a wild, flailing run, arms waving.', stride=3.2, A=48, K=80, arm_swing=50,
         elbow=20, elbow_fwd=10, torso_pitch=6, twist=12, bob=1.0, sway=0.6, arm_roll=40, bounce_up=0.8, head=-8)
    hover('style_buu_hover', 'Majin Buu: floating cross-legged, swaying side to side, arms bobbing.',
          arms=((-46, -6, 14, 30), (-46, 6, -14, 30)), legs=((-86, 28, 10, 116), (-86, -28, -10, 116)), torso=(6, 0, 0),
          head=(-4, 0, 8), bob=1.2, drift=2.0, length=40)
    flight('style_buu_flight', 'Majin Buu: flying like an aeroplane, arms straight out, banking from side to side.',
           (0, 0, 86, 4), (0, 0, -80, 4), rl=(10, 0, 6, 20), ll=(14, 0, -6, 26), head=-10, flutter=1.6, torso=(0, 0, 6))
    fastfly('style_buu_fast', 'Majin Buu flat out: the aeroplane, arms out.', (0, 0, 80, 0), (0, 0, -80, 0), tremble=1.4)
    charge('style_buu_charge', 'Majin Buu: arms flung up over the head, bouncing with excitement.', None,
           arms=((-170, 0, 14, 10), (-164, 0, -20, 16)), legs=((-20, 0, 8, 30), (0, 0, -8, 4)), torso=(0, 0, 0),
           head=(-20, 0, 6), crouch=0.8, shake=1.0, surge_arms=(10, 0, 10, 10))
    stance('style_buu_fight', 'Majin Buu: arms loose and out, bouncing and bobbing the head, playful.', (-40, 0, 66, 20),
           (-50, 0, -60, 26), (8, 0, 10, 18), (-14, 0, -10, 40), (6, -6, 5), (-4, 6, 8), 0.8, bounces=4, bounce=0.8,
           length=28, sway=0.6, weave=1.6)
    steps('style_buu_steps', 'Majin Buu: skippy steps.', (-40, 0, 66, 20), (-50, 0, -60, 26), (6, -6, 4), 0.8, stride=1.8,
          A=26, K=50, bob=0.8)

    # ================================================================== Android 17: park_ranger. Cool and effortless
    held_idle('style_android17_idle', 'Android 17: hands in the pockets, weight on one leg with the hip out, head tilted; '
              'bored, glancing away.', (POCKET, mir(POCKET)), legs=((0, 0, 5, 0), (-8, 0, -6, 14)), torso=(2, 0, 4),
              head=(2, -14, 4), body_y=0.1, sway=0.3, look=1.2, length=140)
    gait('style_android17_walk', 'Android 17: a casual stroll, hands in the pockets, shoulders loose.', stride=2.4, A=24,
         K=38, arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=3, twist=6, bob=0.4, sway=0.7, head=2,
         arms=lambda t: add(POCKET, (2 * cs(t, 1), 0, 0, 0)), left_arm=lambda t: add(mir(POCKET), (-2 * cs(t, 1), 0, 0, 0)))
    gait('style_android17_sprint', 'Android 17: an easy, efficient run.', stride=3.6, A=44, K=70, arm_swing=48, elbow=60,
         elbow_fwd=16, torso_pitch=10, twist=8, bob=0.7, sway=0.2, head=-6)
    hover('style_android17_hover', 'Android 17: hands in the pockets, one knee up, lounging in the air.',
          arms=(POCKET, mir(POCKET)), legs=((-20, 0, 4, 40), (4, 0, -3, 8)), torso=(-4, 0, 3), head=(0, 0, 5), bob=0.8, length=90)
    flight('style_android17_flight', 'Android 17: hands in the pockets, legs trailing.', POCKET, mir(POCKET), head=-10, flutter=0.7)
    fastfly('style_android17_fast', 'Android 17 flat out: hands still in the pockets.', POCKET, mir(POCKET), head=-8)
    charge('style_android17_charge', 'Android 17: arms spread low and wide, palms out, raising a barrier.', None,
           arms=((-30, 0, 60, 20), (-30, 0, -60, 20)), legs=((-4, 0, 14, 20), (-4, 0, -14, 20)), torso=(0, 0, 0),
           head=(-6, 0, 0), crouch=1.4, shake=0.8, surge_arms=(-6, 0, 8, 0))
    stance('style_android17_fight', 'Android 17: a loose low guard, relaxed, almost lazy, rocking gently.', (-14, -12, 16, 70),
           (-46, 14, -12, 36), (10, 0, 8, 16), (-12, 0, -6, 18), (6, -14, 0), (-2, 10, 3), 0.7, bounces=2, bounce=0.3, length=36, sway=0.5)
    steps('style_android17_steps', 'Android 17: easy steps, guard low.', (-14, -12, 16, 70), (-46, 14, -12, 36), (6, -12, 0), 0.7)

    # ================================================================== Jiren: pride_trooper. Immovable
    held_idle('style_jiren_idle', 'Jiren: arms folded, feet planted wide, eyes closed: an unmoving wall, only the slow '
              'breath shows.', (CROSSED_R, CROSSED_L), legs=((0, 0, 10, 2), (0, 0, -10, 2)), torso=(-2, 0, 0), head=(6, 0, 0),
              body_y=0.5, sway=0.1, look=0.2, breath=0.8, length=170)
    gait('style_jiren_walk', 'Jiren: heavy, deliberate steps with the arms folded, upright and unshakeable.', stride=2.4,
         A=22, K=32, arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=0, twist=2, bob=0.6, sway=0.4, head=2,
         arms=lambda t: CROSSED_R, left_arm=lambda t: CROSSED_L)
    gait('style_jiren_sprint', 'Jiren: a powerful, driving run.', stride=3.6, A=44, K=66, arm_swing=50, elbow=70,
         elbow_fwd=10, torso_pitch=10, twist=6, bob=0.8, sway=0.2, head=-4)
    hover('style_jiren_hover', 'Jiren: arms folded, legs straight, utterly still.', arms=(CROSSED_R, CROSSED_L),
          legs=((2, 0, 2, 2), (2, 0, -2, 2)), head=(6, 0, 0), bob=0.3, drift=0.1, length=160)
    flight('style_jiren_flight', 'Jiren: arms folded even at speed.', CROSSED_R, CROSSED_L, rl=(2, 0, 1, 2), ll=(2, 0, -1, 2),
           head=-6, flutter=0.4)
    fastfly('style_jiren_fast', 'Jiren flat out: arms folded.', CROSSED_R, CROSSED_L, head=-4, tremble=0.6)
    charge('style_jiren_charge', 'Jiren: every muscle tensed, fists clenched at the sides, the ground shaking.', None,
           arms=((6, 0, 20, 12), (6, 0, -20, 12)), legs=((-4, 0, 18, 24), (-4, 0, -18, 24)), torso=(-6, 0, 0), head=(-8, 0, 0),
           crouch=1.6, shake=1.6, surge_arms=(-4, 0, 8, 20))
    stance('style_jiren_fight', 'Jiren: arms folded even in a fight, feet planted wide, not a wasted movement.',
           CROSSED_R, CROSSED_L, (6, 0, 14, 16), (-6, 0, -14, 16), (6, -6, 0), (-6, 4, 0), 1.0, bounces=0, bounce=0.0,
           length=80, sway=0.1, weave=0.2, breath=1.0)
    steps('style_jiren_steps', 'Jiren: planted, heavy steps.', CROSSED_R, CROSSED_L, (6, -6, 2), 1.0, stride=2.0, A=20, K=30, bob=0.5)

    # ================================================================== Hit: assassin. Total stillness
    held_idle('style_hit_idle', 'Hit: hands in the pockets, perfectly still; only a slow breath and the faintest turn '
              'of the head.', (POCKET, mir(POCKET)), legs=((0, 0, 3, 0), (0, 0, -3, 0)), torso=(-2, 0, 0), head=(3, 0, 0),
              sway=0.1, look=0.4, breath=0.6, length=180)
    gait('style_hit_walk', 'Hit: an unhurried, smooth glide, hands in the pockets, the head never bobbing.', stride=2.2,
         A=22, K=30, arm_swing=0, elbow=0, elbow_fwd=0, torso_pitch=-2, twist=2, bob=0.12, sway=0.2, head=3,
         arms=lambda t: POCKET, left_arm=lambda t: mir(POCKET))
    gait('style_hit_sprint', 'Hit: a fast, leaning glide, hands still in the pockets.', stride=3.4, A=36, K=50, arm_swing=0,
         elbow=0, elbow_fwd=0, torso_pitch=14, twist=2, bob=0.2, sway=0.1, head=-8, arms=lambda t: POCKET, left_arm=lambda t: mir(POCKET))
    hover('style_hit_hover', 'Hit: hands in the pockets, legs straight, motionless.', arms=(POCKET, mir(POCKET)),
          legs=((2, 0, 2, 2), (2, 0, -2, 2)), head=(4, 0, 0), bob=0.25, drift=0.1, length=160)
    flight('style_hit_flight', 'Hit: hands in the pockets, upright and still.', POCKET, mir(POCKET), rl=(2, 0, 1, 2),
           ll=(2, 0, -1, 2), head=-4, flutter=0.3)
    fastfly('style_hit_fast', 'Hit flat out: hands in the pockets.', POCKET, mir(POCKET), head=-2, tremble=0.4)
    charge('style_hit_charge', 'Hit: hands in the pockets, a slight hunch as the time-skip gathers.', None,
           arms=(POCKET, mir(POCKET)), legs=((0, 0, 6, 6), (0, 0, -6, 6)), torso=(6, 0, 0), head=(4, 0, 0), crouch=0.4,
           shake=0.3, surge_arms=(0, 0, 0, 0))
    stance('style_hit_fight', 'Hit: hands in the pockets, side-on, perfectly still but for a faint weave.', POCKET,
           mir(POCKET), (14, 0, 8, 16), (-14, 0, -6, 18), (2, -24, 0), (0, 20, 0), 0.6, bounces=0, bounce=0.0,
           length=50, sway=0.15, weave=0.5)
    steps('style_hit_steps', 'Hit: gliding steps, hands in the pockets.', POCKET, mir(POCKET), (2, -22, 0), 0.6, A=18, K=24, bob=0.1)

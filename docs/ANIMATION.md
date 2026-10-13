# Animation v5: the motion engine (CX-18)

One shared system animates the player and every humanoid NPC: walking, sprinting, jumping, falling, landing, takeoff
and flight (hover, cruise, fast, ascend, descend, backward, strafing). Entities hold no animation code. The engine reads
what they are doing and plays data-driven clips with cross-fades and procedural layers on top. Combat actions (punches,
kicks, transformations, technique casts) still come from the older playerAnimator clips in `Anims.java`, layered above.

## How it fits together

| Piece | Where | What it does |
|---|---|---|
| `MotionEngine` | `client/motion` | Ticks every figure, works out its state, cross-fades clips, adds the procedural layers, couples the rig. |
| `Motion` | `client/motion` | One figure's runtime state (weights, phases, smoothed angles). Allocated once per entity. |
| `MotionAnimation` | `client/motion` | The pose as a playerAnimator animation. A layer under the actions for players; NPCs use it through `MotionModel`. |
| `MotionModel` | `client/motion` | The player model every `FighterRenderer` NPC uses: it runs the same pose through playerAnimator's part applier, so NPCs get elbow and knee bends too. |
| `Clip`, `AnimSet`, `MotionData` | `client/motion` | The data: clips, sets, profiles and tuning from `assets/dbzenith/motion`, reloaded with F3+T. |
| `Tuning` | `client/motion` | Every number that sets the feel, overridable from `motion/tuning.json` or live with `/dbzanim set`. |
| `MotionDebug` | `client/motion` | State labels above heads, and the `/dbzanim` client command. |
| `Animated` | `npc` | Optional hints from an entity (flying on purpose, charging ki). Most entities need nothing. |

**State is worked out from the entity itself:**
- speed in its own frame (forward, sideways, vertical), turn rate, on ground or in the air, how long in the air, fall speed;
- water, swimming, riding, sleeping, crouching, dying, held and used items, swings;
- for players, the synced flying flag and their race and form;
- a body that hangs in the air without gravity pulling it down counts as flying, whatever it is.

Nothing new is sent over the network; everything is client-side and visual only. Hitboxes and server logic are unchanged.

**The engine steps aside, fading out, so vanilla handles it:** swimming, riding, sleeping, crouching, dying, the Great
Ape form, and figures beyond 80 blocks. An arm that holds or uses an item, or that an NPC swings, fades back to vanilla
on its own. First person is untouched (vanilla arms).

**Level of detail:** beyond 40 blocks the procedural layers are skipped and figures update every other tick; beyond
80 blocks they get vanilla poses.

## Adding a new NPC

1. Register its renderer as a `FighterRenderer` (as every humanoid NPC already is). That is all: it walks, runs, jumps,
   falls, lands and flies with the default `fighter` set.
2. Optional, one line in `assets/dbzenith/motion/profiles.json` to give it a different set, or a race or form whose
   overrides apply:
   ```json
   "dbzenith:my_npc": {"set": "brute"},
   "dbzenith:my_kai": {"race": "core_person"}
   ```
3. Optional, if it flies or charges on purpose and you want that shown before it moves: implement
   `com.dbzenith.npc.Animated` (`isFlyingNow`, `isChargingKi`), backed by synced entity data.

## Generated clips (CX-31)

Every movement clip (the defaults, race and form idles, and the sixteen fighting styles' nine slots each) is written by
`tools/gen_motion.py` (shared builders: `gait`, `held_idle`, `hover`, `flight`, `fastfly`, `charge`, `stance`, `steps`)
and `tools/gen_styles.py` (one block per style). Edit those and re-run `python3 tools/gen_motion.py`; hand edits to the
JSON are overwritten. They layer motion over a key pose: weight shift, breathing, the hips dropping on each footfall,
shoulders counter-twisting, arms and legs following a beat behind (overlap), acting beats in idles (a glance, a shoulder
roll, scratching the head), tremble and surges when charging.

Combat actions in `Anims.java` share `strike(...)`: a wind-up deeper than the pose, a snap to the hit, follow-through
past it, a beat held, and a recoil just past the guard before settling.

Dev views: the devshot `posestrip_<clip>_<turn>[_<pitch>]` draws one clip at eight moments; `posesheet_<slot>_<phase>`
draws the default and every style for a slot; `motionreload` re-reads the clips; `anim_<NAME>_f<tick>` freezes a combat
clip on a tick.

## Adding or changing an animation

Clips live in `assets/dbzenith/motion/clips/<name>.json`. A resource pack can override any of them, and F3+T reloads them.

```json
{
  "sync": "stride",          // "time" (plays over "length" ticks) or "stride" (a gait: one cycle per stride, locked to the ground)
  "length": 20,              // ticks, for time-synced clips
  "loop": true,
  "symmetric": true,         // the left arm and leg are the right ones half a cycle later, mirrored
  "stride": 0,               // blocks per cycle; 0 works it out from the leg swing so the feet do not slide
  "ease": "smooth",          // default between keys: smooth (Catmull-Rom), linear, in, out, inout, step
  "bones": {
    "rightLeg": [
      {"t": 0.0, "rot": [-38, 0, 0], "bend": 4},
      {"t": 0.5, "rot": [30, 0, 0], "bend": 10, "ease": "out"}
    ],
    "body": [{"t": 0.0, "pos": [0, 0.5, 0]}]
  }
}
```

- **Bones:** `head`, `torso`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`, and `body` (the whole figure).
- **Keys:** `t` runs 0..1 over the clip. A key may set any of `rot` [pitch, yaw, roll] (degrees), `pos` [x, y, z]
  (pixels, y down, -z forward) and `bend` (elbow or knee, degrees).
- **Sign conventions:**
  - An arm pitch of -90 points it forward; positive swings it back.
  - A leg pitch below 0 swings the leg forward.
  - Roll is positive outward on the right side and negative outward on the left.
  - A positive torso yaw brings the right shoulder forward.
  - A positive torso or body pitch leans forward. The torso leans from the waist and carries the head and shoulders
    with it; the whole body turns about the feet on the ground and about its middle in the air.
- **The head:** its keys are added to where the figure is looking.

**Using a clip:** sets live in `assets/dbzenith/motion/sets/<id>.json`. They map states to clips (`idle`, `walk`,
`sprint`, `jump`, `fall`, `hover`, `cruise`, `fast`, `ascend`, `descend`, `backward`, `charge`), can inherit from a
`parent`, and swap clips for races, forms or single entities:

```json
{ "parent": "fighter",
  "clips": {"idle": "idle_heavy"},
  "overrides": {"form:super_saiyan": {"idle": "idle_powered"}, "race:namekian": {"idle": "idle_crossed"},
                "entity:dbzenith:beerus": {"idle": "idle_regal"}} }
```

**Shipped sets:**
- `fighter`: the default, with race idles and form stances (Super Saiyan stance, heavy Legendary and Buff forms, regal Frieza Race).
- `brute`: heavy walk and idle (Broly, Spopovich, the ogres, King Yemma).
- `beast`: hunched idle and lope (Saibamen, Bubbles).

## Tweaking the feel

- **Live:** `/dbzanim set <name> <value>`. Tab-completion lists every value; `/dbzanim get` shows them all.
- **Permanently:** put the same names in `assets/dbzenith/motion/tuning.json` (or a resource pack's copy).

| Values | Controls |
|---|---|
| `blendGait`, `blendFly`, `blendAir`, `blendLand`, `blendEngine` | Cross-fade times (ticks). |
| `walkSpeed`, `sprintSpeed`, `flyCruise`, `flyFast`, `flyVertical`, `moveThreshold` | Speed thresholds. |
| `runLean`, `runLeanMax`, `turnLean` | Leaning on the ground. |
| `flyPitchCruise`, `flyPitchMax`, `flyClimb`, `flyBank`, `strafeBank` | Body pitch and banking in the air. |
| `headCounter`, `armLag`, `breathe`, `landCrouch`, `smoothing` | Secondary motion. |
| `lodSimple`, `lodOff` | Level-of-detail distances. |

**Debugging:**
- `/dbzanim labels` shows each figure's state, speed and engine weight above its head (or set `labels` in the client config).
- `/dbzanim toggle` switches back to the old animations (also the `engine` client config option).
- `/dbzanim info` lists the clips your own figure is using.

## Files you need to provide

None. Every clip ships with the mod and your models and textures are unchanged: the engine drives the same vanilla
player-model parts everything already uses, so nothing needs converting. If you author new clips, they are plain JSON in
the format above; any text editor works.

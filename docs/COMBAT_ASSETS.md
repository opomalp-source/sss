# Combat v4: assets you can provide

Everything the combat system shows or plays works today with **original placeholders**: textures and sounds made by
the generators in `tools/` (`ArtGen`, `AssetGen`, `SfxGen`), vanilla particles, and animation clips written in code
(`client/anim/Anims`). Anything below can be swapped for hand-made work **without touching code**: drop a file at the
same path, in the mod's resources or in a resource pack. Keep the size, format and (for sounds and animations) the length.

**Golden rule for generated files:** if you replace one that a generator writes (anything under `textures/` or
`sounds/` listed here), remove its line from the generator, or the next run overwrites your file.

## 1. Textures

All PNG with alpha, `assets/dbzenith/textures/...`.

| File | Size | What it is | Now |
|---|---|---|---|
| `entity/fighter/training_dummy.png` | 64x64, player skin layout (outer layers allowed) | The training dummy: a burlap sparring figure, X-stitched eyes, a target on the chest, wooden-post legs | ArtGen (`java tools/ArtGen.java only training_dummy`) |
| `entity/impact_star.png` | 64x64, white on transparent | The flash of every blow (tinted at runtime: white blows, gold counters, red criticals, blue guards) | ArtGen |
| `entity/shock_ring.png` | 64x64, a thin white ring, soft edges | The expanding shockwave of heavy blows, clashes, parries | ArtGen |
| `entity/crater.png` | 64x64, dark, alpha fading to the edge | Ground decal where fighters slam down | ArtGen |
| `entity/ground_cracks.png` | 128x128, cracks on transparent | Ground decal for long charges | ArtGen |
| `entity/fx_streak.png` | 16x16, a streak with a bright core | Afterimage and dash streaks | ArtGen |
| `entity/ki_glow.png` | 32x32, white radial glow | Ki blasts (tinted by aura colour) | AssetGen |
| `entity/ki_beam.png`, `entity/beam_flow.png` | 32x32 / 32x64, white, tileable along the length | Beams and their flowing core | AssetGen / ArtGen |
| `entity/spirit_bomb.png` | 128x128 | The Spirit Bomb sphere | ArtGen |
| `gui/hud/*.png` (`portrait_ring`, `release_tab`, `main_bar`, `health_frame`, `health_fill`, `stamina_frame`, `stamina_fill`) | Drawn at 4x their on-screen size (see `SagaHud` constants) | The Saga HUD's frames and fills | ArtGen |
| `gui/hud/meter_*.png`, `form_meter_fill`, `tech_meter_fill` (CX-20) | 4x on screen: `meter_frame` 44x448, `meter_back` and the fills 28x416 (greyscale, tinted when drawn), `meter_stud`/`meter_stud_lit` 52x12, `meter_stud_glow` 64x32 (white, tinted, added) | The PvP meters, bottom right (`MeterBars`) | ArtGen (`only meters`) |

**Drawn in code, no texture (tell me if you want a texture slot):**
- the special-meter segments;
- the enemy panel;
- the callout bands;
- the combo counter;
- the frame bar;
- the lock-on brackets;
- speed lines;
- the duel ring (particles).

A 9-slice panel texture (`gui/combat_panel.png`, 32x32 with 8-pixel borders) and a callout band (`gui/callout.png`, 256x32) would be the natural next ones.

## 2. Sounds

All mono Ogg Vorbis, 44.1 kHz, peaking around -1 dBFS (the game sets the volume), at
`assets/dbzenith/sounds/<name>_<n>.ogg`. Several variants are picked at random, which keeps repeats from sounding
mechanical; the counts below are what `sounds.json` lists (add more files and lines freely).

| Event | Variants | Length | When | Character |
|---|---|---|---|---|
| `punch_light` | 4 | 0.15-0.25 s | A light blow lands | Tight slap with a low body, no tail |
| `punch_heavy` | 3 | 0.4-0.6 s | A heavy lands | Deep body, crack on top |
| `impact_boom` | 2 | 0.6-0.9 s | Under heavy blows and guard breaks | Sub-bass thump, short air tail |
| `hit_guarded` | 2 | 0.2-0.3 s | A blow on a raised guard (with `guard_block`) | Dull, muffled |
| `guard_block` | 3 | 0.2-0.4 s | Blocking | Hard block, slight ring |
| `parry` | 2 | 0.6-0.9 s | Perfect guard | Bright metallic ring |
| `guard_break` | 2 | 0.6-0.8 s | The guard breaks | Glass-like shatter, falling shards |
| `hit_crit` | 2 | 0.3-0.5 s | A critical | Sharp crack, short metallic ring |
| `counter_hit` | 2 | 0.5-0.7 s | A counter lands | A quick rising sting into a hit |
| `whoosh` | 4 | 0.15-0.3 s | Swings, the air of light blows | Air movement, no impact |
| `dash` | 3 | 0.2-0.35 s | Dashes, the super dash | Fast whoosh with a pop |
| `vanish` | 2 | 0.3-0.5 s | Vanish, chase | A zip that cuts off |
| `deflect` | 2 | 0.3-0.5 s | A ki blast swatted away | Ricochet |
| `ki_fire` | 3 | 0.3-0.5 s | Ki blasts | Charged whoomp |
| `ki_hit` | 3 | 0.3-0.5 s | Ki blasts landing | Fizzing crackle |
| `beam_fire` | 2 | 1.0-1.5 s | Beams | Rising roar |
| `explosion`, `explosion_big` | 3, 2 | 1.2 s, 2.6 s | Charged blasts, ultimates, slams | Deep blast and rumble |
| `stun` | 2 | 0.5-0.7 s | Stunned | Electric buzz |
| `land` | 2 | 0.4-0.6 s | Hard landings, ground slams | Thud with debris |
| `aura_charge` | 1, looping | 0.25 s seamless loop | Charging a ki blast, ki | Rising hum, loops cleanly |

Subtitles are `subtitles.dbzenith.<event>` in the language files.

## 3. Particles

The combat effects use vanilla particles today:
- `CRIT`, `ENCHANTED_HIT` (blows, criticals, counters);
- `ELECTRIC_SPARK` (guard, guard break, deflect);
- `END_ROD` (ki, the duel ring, guard break);
- `CLOUD`, `POOF` (dust, vanish, the dummy going home);
- block debris (craters).

Custom particles would each need a 16x16 sprite sheet (`textures/particle/<name>_0..n.png`, 4-8 frames) and a small
registration. Worth making:

| Particle | Frames | For |
|---|---|---|
| `ki_spark` | 4-6, white (tinted) | Ki impacts and blasts, instead of END_ROD |
| `impact_shard` | 4, white | Flying flecks on heavy blows, instead of CRIT |
| `guard_hex` | 6, pale blue | Guard hits: a hexagon flicker |
| `dust_puff` | 6-8, grey-brown | Dashes, slams and landings, instead of CLOUD |
| `afterimage_flake` | 4, white | Vanish and chase |

## 4. Animations

Every built-in clip can be replaced by an animation file in **playerAnimator's emote format** (the Emotecraft JSON
format, version 1; exported by Blockbench's playerAnimator/Emotecraft plugin). Put it at
`assets/dbzenith/player_animation/<anything>.json`, in the mod or a resource pack.

**Important:** the clip it replaces is set by the **`"name"` field inside the file**, not the file name. The name must be
the clip's name in lower case (for example `"name": "jab_right"`), with only `a-z 0-9 _ - . /`. A name with a space or
capital letters stops the game loading the resource pack (playerAnimator's own loader rejects it). Tested: a
`jab_right` file replaced the jab in game.

Specs:
- **Rate:** 20 ticks a second; tick 0 is the start.
- **Bones:** `head`, `body` (the whole figure: turn, lean, move), `torso`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`.
  - Elbows and knees bend: use `bend` (axis and amount) on the arms and legs.
  - `degrees: false` means radians.
- **Length:** match the length below, or the clip runs short or long against the move.
  - Moves: the length is the move's startup + active + recovery. The blow should visibly connect during the active ticks.
  - One-shots (`"isLoop": "false"`) end at `endTick` and fade out by `stopTick`.
  - Loops: `"isLoop": "true"`, with `returnTick` the frame they loop back to.

**Moves** (and their move files):

| Clip name | Ticks (startup + active + recovery) | Used by |
|---|---|---|
| `jab_right` | 9 (2+2+5) | `light_1` |
| `cross_left` | 10 (2+2+5) | `light_2` |
| `hook` | 11 (3+2+6); heavy: 16 (4+2+10) | `light_3`, `heavy_hook` |
| `kick` | 14 (3+2+6, finishing pose) | `light_4` |
| `zhit` | 12 (1+3+8 for the counter, 4+2+12 for the straight) | `light_5`, `counter_strike` |
| `heavy_punch` | 22 (6+2+14) | `heavy_smash` |
| `rush` | 21 (6+3+12) | `heavy_rush` |
| `uppercut` | 19 (5+2+12) | `heavy_uppercut` |
| `launcher` | 19 (5+2+12) | `heavy_launcher` |
| `spike` | 21 (5+2+14) | `heavy_spike` |
| `sweep` | 19 (4+3+12) | `heavy_sweep` |

A new move can name a clip that exists only as a file: give the move `"anim": "my_clip"` and the file `"name": "my_clip"`.

**Reactions and stances** (players and NPCs):

| Clip name | Ticks | When |
|---|---|---|
| `hit_light` | 10 | Struck |
| `hit_heavy` | 18 | Struck by a heavy |
| `stunned` | loop 24 | Stunned: dazed sway |
| `launched` | loop 16 | Flying from a launch |
| `downed` | loop 40 | Knocked down, lying |
| `get_up` | 16 | Getting up |
| `guard` | loop 20 | Guarding |
| `combat_stance` | loop 24 | Just fought |

**Defense, ki and the rest:**

| Clip name | Ticks | When |
|---|---|---|
| `dash` | 8 | Dash |
| `spot_dodge` | 11 | Spot dodge |
| `side_left`, `side_right` | 8 | Side steps |
| `air_recover` | 10 | Air recovery |
| `roll_up` | 12 | Tech roll |
| `breaker` | 15 | Burst |
| `ki_blast` | 10 | Quick and charged blasts |
| `ki_volley` | 20 | Volleys |
| `ki_wave` | 16 | Explosive wave |
| `ki_focus` | 16 | Charging, self techniques |
| `charge` | loop 8 | Charging ki |
| `heavy_windup` | loop 10 | Heavy wind-up |
| `transform` | 44 | Transforming |
| `victory` | 40 | Winning |

(Beams use a pose built for each beam's length, which is not replaceable yet.)

## 5. Priority

If you only make a few, these change the feel most:
1. **Animations:** `jab_right`, `cross_left`, `hook`, `kick`, `heavy_punch`, `uppercut`, `hit_light`, `hit_heavy`, `stunned`, `launched`, `get_up`, `dash`.
2. **Sounds:** `punch_light`, `punch_heavy`, `impact_boom`, `guard_block`, `parry`, `counter_hit`.
3. **Textures:** `impact_star`, `shock_ring`, `training_dummy`.
4. **Particles:** `ki_spark`, `dust_puff`.

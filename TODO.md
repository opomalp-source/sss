# TODO — authoritative roadmap

Resume from the first unchecked item. Each phase ends with a git tag.

## Phase 0 — Scaffold ✅ (tag `phase-0-scaffold`)
- [x] NeoForge 1.20.1 (47.1.106) project from the official `1.20.1-legacy` MDK, ModDevGradle legacyforge plugin
- [x] git repo, `.gitignore` for run dirs
- [x] Mod loads (client + dedicated server), shows in mod list
- [x] Example registry: Senzu Bean item (full heal + ki/stamina refill) + creative tab
- [x] Player-data capability: attach, NBT persistence, survives death/End return (Clone), relog
- [x] Server→client sync packet (rate-limited, dirty-flag driven)
- [x] Config system: `dbzenith-server.toml` (balance) + `dbzenith-client.toml` (display)
- [x] `/dbz` debug command: stats / set / tp add / race / path / refill / reset
- [x] GameTest suite (`gradlew runGameTestServer`) — 7 tests green
- [x] Scripted end-to-end check: dev server + RCON + auto-joining client + screenshot

## Phase 1 — Ki, stats & combat
- [x] **Body/health model**: DBC-style body pool is the real health; vanilla health mirrors the ratio (`combat.BodyHealth`), damage routed in `combat.CombatEvents` (LivingHurtEvent, LOW priority)
- [x] Ki / stamina / body regeneration (`ki.KiTicker`), body regen after a no-damage delay
- [x] TP economy: TP from damage dealt, kills (by victim max health) and charging (spiritual training); MIND bonus
- [x] Spend TP: `UpgradeAttributePacket`, server-validated in `stats.AttributeTraining` (capped per request)
- [x] Training / stat screen (K): attributes, TP cost, + buttons (shift = +10), derived stats
- [x] Release %: charging (G, hold) raises it + fills ki, drains stamina, aura particles; Z lowers it; scales ki damage + cost
- [x] `combat.DamageCalculator`: melee, ki, defense floor, guard, evasion, ki cost
- [x] Melee: STR-scaled damage, combo counter + bonus, extra knockback, stamina cost, exhausted penalty, guard (Left Alt)
- [x] Melee: charged heavy hit (H hold/release: 1.5x-3x on the next hit, extra knockback, stamina cost)
- [x] Ki blast entity framework (`skill.KiBlastEntity`: size, color, speed, pierce, homing, explosion, life)
- [x] Techniques: Ki Blast, Wave Beam, Rapid Volley, Cutter Disk, Homing Orb (R fire, Y cycle)
- [x] True beams: `KiBeamEntity` anchored to the caster, follows aim, pulses damage along its line; ribbon + glow beads renderer. Wave Beam converted, Finger Beam added
- [x] Flight toggle (V), ki drain, DEX-scaled speed, ends when ki runs out
- [x] Dash (B): input-direction burst (aims vertically while flying), stamina + ki cost, cooldown, afterimage evasion window
- [x] DBZ HUD: body/ki/stamina bars, power level, release %, charging/flying/guard chips, combo counter, technique panel + cooldown (debug overlay now off by default)
- [x] DEX attack/move speed bonuses as vanilla attribute modifiers (`stats.SpeedModifiers`)
- [x] Public state (charging/flying/guard/heavy, release, aura color) synced to trackers; aura glow rendered around charging players (`client.render.AuraRenderer`)
- [x] GameTests: 22 total green (combat + movement/beam suites)
- [x] In-client visual check (scripted, screenshots): HUD, charging, all 5 techniques in flight, flight + guard chips, stat screen
- [x] Tag `phase-1-combat`

## Phase 2 — Transformations
- [x] Form framework (`transform.Form`/`Forms`: multipliers on STR/DEX/KI_POWER, ki+stamina drain, colors, hair style, unlock level + parent mastery + flags, scale, trigger)
- [x] Saiyan line: Super Saiyan, Ascended, Ultra, SSJ2, SSJ3, God (needs `god_ki` flag), Blue; Great Ape via full moon or Moon Orb false moon (tail required, 3x size)
- [x] God ki ritual: the "Divine Ritual" quest (level 1000 + Time Chamber) grants `god_ki`
- [x] Tail cutting (blades, 10%; a Great Ape shrinks back) and regrowth after 3 days
- [x] Mastery per form (time in form x spirit / tier; up to -75% drain, +20% bonus)
- [x] J transform / Shift+J revert; Forms screen (from K) with target selection; hair (4 styles), eye color, form aura, lightning sparks
- [ ] Real art: hair model/textures, Great Ape model (GeckoLib) instead of a scaled player
- [x] ~~Overdrive~~ (removed in v0.25.1 at the user's request: Kaioken covers it, and does it better) (N / Shift+N): x2-x20 levels gated by mastery, body+stamina drain, backlash; stacks with base and Blue only
- [x] Tag `phase-2-transformations`

## Phase 3 — Races & character creation
- [x] Race passives + growth weighting + signature mechanic for all 8 races (`race.RaceTraits`/`Races`/`RacePassives`): Zenkai, Namekian/Majin regen, Majin kill-heal, Android no hunger/fatigue + ki absorb, Cyborg, Frost Demon breathless, Human TP/ki-cost bonuses
- [x] Namekian fusion and Majin absorption (Phase 5). Frost Demon space survival: travel is instant (no vacuum yet), so their breathlessness covers water only
- [x] One transformation line per race (Human, Namekian incl. Giant, Frost Demon, Majin, Android upgrades, Cyborg; Half-Saiyan Ultimate gated by flag)
- [x] Technique library (18 incl. 8 racial), TP learning with level gates, Technique Scrolls, deck (4 + 1 per 250 levels, max 8), Techniques screen; R/Y use the deck
- [x] Custom status effects (stun, ki-seal), grab-and-throw, ball-drop techniques (Phase 5)
- [x] Character-creation screen on first join (race, path, body type, hair style + color, eye color, alignment) with live preview; racial features (antennae, horns, tentacle, tail)
- [ ] Skin color / full racial skins (art)
- [x] Tag `phase-3-races`

## Phase 4 — Progression & world
- [x] Training: Gravity Chamber block (1-100g; tolerance from STR + CON; slowdown and strain above it; TP while moving), Punching Bag, meditation (sneak and stand still for 3 s), Training Weights
- [x] Hyperbolic Time Chamber dimension (door block, 10g, x4 training, one-day stay limit, sends you back where you entered)
- [x] Dragon Balls (7, scattered; turn to stone after a wish, then scatter again), Dragon Radar HUD, Eternal Dragon, 8 wishes (power, restoration, senzu, immortality, hidden potential, godly ki, eternal youth, riches)
- [x] Scouter (power-level readout; shatters above the limit), Capsule (27 slots), gi sets with a set bonus, training weights
- [x] NPC enemies (Sproutling, Ki Soldier, Android Unit) that scale to the nearest player; 2 bosses (Tyrant Lord, Rampage Brute) with summon items and loot
- [x] Quest system: Martial Arts Master questline (7 quests, ends with the divine ritual), quest NPCs placed near spawn, quest screen
- [x] Galactic Patrol: Patrol Officer, 4 repeatable bounties, 5 ranks from reputation
- [x] Planets: Namek (1g) and Northern Planet (10g) dimensions, Space Pod travel screen, planet gravity
- [x] Life sim (config-toggleable): aging by race, STR/DEX decline in old age, eternal youth wish, earned titles shown before the name
- [x] Life-sim extras: thirst, temperature, partners (Promise Ring), scars and tattoos (Life screen)
- [x] Namekian fusion, Majin absorption (Phase 5)
- [x] Dojo and Patrol outpost for the quest NPCs (new worlds; `/dbz build` for existing ones)
- [ ] Real art for every Phase 4 block/item/mob (see ASSETS_TODO.md)
- [x] Tag `phase-4-world`

## Phase 5 — Feature completion (v0.7.0)
- [x] Combat depth: Stunned / Ki Sealed effects, Ki Transfer, Grab & Throw, Paralysis Wave, Seal Orb, Gathering Sphere (ball-drop), air combos, knock-ups, spikes
- [x] Namekian fusion (Namekian Warriors on Namek, consenting players), Majin absorption
- [x] Technique mastery, prestige, god ki edge
- [x] Alignment effects, path bonuses, mental age / wisdom, tail cutting and regrowth
- [x] Life sim: thirst, temperature, partners (Promise Ring), scars / tattoos (Life screen)
- [x] Quest NPC buildings, config audit; every FEATURE_MATRIX row is `done`
- [x] Tag `phase-5-complete`

## Next
- [x] Balance pass 1 (model-based, see BALANCE.md): pacing targets, enemy scaling, TP rewards, training caps, Zenkai, fusion, quests, late forms
- [x] Balance pass 2 (model-based, BALANCE.md): techniques per ki, Overdrive bursts, race curves, gear vs vanilla armour
- [ ] Real-play feel check: fight readability, racial passives in close duels, pacing vs actual play
- [x] Art pass (v0.8.0, tools/ArtGen.java): items, blocks and models, gi/armor, NPC skins, race looks, Great Ape model, Eternal Dragon model
- [x] Remaining art (v0.8.1): tiered hair, textured HUD, Namek trees and dome houses, Space Pod entity with launch and landing
- [ ] More content: more bosses and questlines, more planets, a space dimension (Frost Demon vacuum survival), children for partners

# Brief v2 roadmap (BRIEF.md, 2026-10-05)
Brief v2 raises the bar: every feature needs real models, animation, VFX, sound and UI (Part 18). The earlier
"Phase 5 — Feature completion" above was this project's own phase; brief v2's **Phase 5 is Story mode** (milestone V2-G).

## V2-A — Animation foundation
- [x] playerAnimator (KosmX, `1.0.2-rc1+1.20`) as a real dependency, build + runtime verified
- [ ] GeckoLib (1.20.1), added with the first converted entity (V2-H bosses)
- [x] Player animation set, keyframed in code (30 animations; still to add: idle breathing, descend, launched, victory): idle breathing, charge stance, flight (cruise / fast / hover / descend), dash, light combo 1-3, heavy, launcher, spike, block, ki fire (one hand / two-hand beam / volley / throw), transformation sequence, hit reactions (light / heavy / launched), power-down, victory
- [x] Animations synced to other players (public state + AnimEventPacket)
## V2-B — VFX and game feel
- [x] Flame-shaped layered aura (per form colour/shape), charge dust ring, rising sparks, lightning for tier-2 forms
- [x] Hitstop, screen shake, camera kick on heavy hits, impact flashes and shockwave rings, landing craters
- [x] Afterimage trails on dashes and fast flight
## V2-C — UI v2
- [x] HUD: portrait ring with the live player head, angled body / ki / stamina bars, BP and release readout, form badge, per-form colour shift, animated fills
- [x] Hotbar skin, radial action menu (forms, flight, techniques, menus)
- [x] Menu theme: stats, techniques, life, quests, radar, planets screens restyled to one look
- [x] Transformation cut-in (portrait slash, flash)
## V2-D — Appearance
- [x] Voxel hair editor (strands placed on a head grid, saved as a hair code), presets, per-form hair from the base hair
- [x] Skin tone, height, body shape (lean / athletic / bulky), muscle-shaded body textures
## V2-E — Combat depth
- [x] Guard meter, guard break, perfect-guard parry window, deflecting ki blasts
- [x] Beam struggles (beam-vs-beam contest)
- [x] Ki Creator: build techniques from method / shape / type / modifiers (DBV-style)
## V2-F — Sound
- [ ] Original synthesized sound set: charge hum, blasts, beams, impacts, whooshes, transformation burst, UI clicks, ambience
## V2-G — Phase 5: Story mode
- [ ] Story engine: chapters, dialogue boxes with portraits, cut-ins, objectives, rewards, `/dbz story` debug
- [ ] Nine saga chapters with original characters and multi-phase bosses
## V2-H — World and content
- [ ] Home-world plateau pillars, a sky lookout hub, more planets (a cold tyrant's world, a sacred realm), dungeons
- [ ] Original weapons with 3D item models; GeckoLib bosses with animations
- [ ] Bloom / post-processing for energy (custom post chain)

# Content expansion (CX) — see docs/CONTENT_EXPANSION.md
- [x] CX-1 Settings menu (pause menu, mod list Config, wheel; HUD / effects / camera / controls)
- [x] CX-2 Races v2: variants (clans, rare destinies, paths), 5 new races, race and variant skins, 3 new head and back features
- [x] CX-3 Forms v2: 70 new forms on the mapped DBV scale, ranged (mastery-grown) and rising (combat) multipliers. Done since: power-ups below 75% mastery (interruptible), God Ki levels 1-10 (v0.14.2)
- [x] CX-4 Racial skills: 92 skills (70 passives built from condition and modifier data, 22 actives), Racial key (U, shift to browse), Racial Skills screen, HUD readout, wheel slice, /dbz racial (v0.15.0)
- [x] CX-5 Universal skills: Ki Sense (3 levels), Kaioken (x2/x4/x10/x20), Rising Charge, Echo Strike (3), Spirit Shock, Ki Barrier, Desperate Gambit, Limit Break, Instant Transmission; learned with TP on the Universal tab (v0.16.0)
- [x] CX-6 Combat v3: Z-hits, directional heavies (rush/uppercut/hook), sweep, chase and chase counter, Revenge Counter, Breaker Wave, snap recovery and ground slide, spot dodge and side step, clashes, downed state; moves guide in Settings > Controls (v0.17.0)
- [x] CX-7 Ki Creator v2: 9 shapes (+laser, wave, nova, rain), methods (fired, charged, placed mines), origins, 7 ki types, 14 modifiers (third slot at level 800) (v0.18.0)
- [x] CX-8 Character creator v2 + better hair presets + hair physics. Done: 19 presets rebuilt on a volume layer (9 new: Prince flame, Rebel lock, Legend mane, Sage, Curtains, Buzz cut, Cloud puff, Twin tails, Side cut), spring-damped hair physics (movement, falling, turning, idle, aura updraft, gravity on head tilt; setting "Hair physics"). Done since: face parts (eyes 8, brows 6, mouths 6, noses 4, pointed ears, 8 extras), hair-tip highlight, own aura colour, height 80-125% (v0.19.0)
- [x] CX-9 Animation v3: idle breathing, combat stance, sprint, flight ascend/descend, get-up, victory, racial transformation flourishes (regal/calm/feral), procedural banking/pitch/run lean/landing squash (v0.20.0)
- [x] CX-10 Sound: 29 synthesized sound events / 60 variants (tools/SfxGen.java + ffmpeg libvorbis), impacts, swings, landings, aura loops, transformations, skills, UI (v0.21.0)
- [x] CX-8c Transformation looks (user request): forms change the body, not just hair and eyes. Done: recoloured race skins (Orange Namekian, Demon King, Golden Frost Demon, Metal God Core, Mutant God, evil and pure Majin, Pure Corruption, crimson Vampires, Perfect and Zenith Bio-Androids, Golden Tuffle, Apex Gen Alien, Demon God, Supreme Kai), generated-body tints, SSJ4 red and silver fur (v0.22.0)
- [>] CX-8d Transformation shapes (moved into CX-14f): bigger frames for Buffed, Legendary and Giant forms (model scale per part), Frost Demon form shapes (second-form horns, third-form crest, final-form smooth head), Super Saiyan 3 brow ridge, Golden Great Ape fur, Primal tails, Bio-Android wings, Majin size changes
- [x] CX-8e Face customization in first-time character creation (user request): Body / Face tabs with a zoomed head preview; race skins now wear the face parts too (v0.22.0)
- [x] CX-13 Art and design overhaul (v0.23.0 - v0.27.0) (user request: "way too simplistic"; more detailed forms, body art, UI and HUD, switchable in the settings; the user's reference pictures are style inspiration only, nothing copied)
  - [x] 13a HD bodies (v0.23.0): 128x128 generated bodies (muscle definition, rim light, ambient occlusion), detailed outfits (gi folds, belts, wristbands, boots), HD face parts (16x16 faces: eye highlights, iris gradients, lashes)
  - [x] 13b HD race skins (v0.24.0): every race and lineage repainted at 128x128 (scales, plates, spots, markings), their transformation recolours regenerated
  - [x] 13c Form detail (v0.25.0): HD hair texture (strand gradients, rim highlights), glowing eye highlights, form body markings (god-ki sheen, kaioken flush, silver Ultra-style sheen), lightning on the body
  - [x] 13d UI v2 (v0.26.0): ornate themed panels, buttons and frames; a "UI style" setting (Zenith / Classic)
  - [x] 13e HUD v2 (v0.27.0): detailed portrait frames per form, animated bars, new layouts; a "HUD style" setting (Zenith / Classic / Minimal)
  - [x] 13f Art detail setting (v0.23.0): Zenith Settings > Style > HD art
- [x] CX-14 Art direction v2 (user request with reference pictures, saved locally in reference/ (git-ignored, inspiration only, nothing copied): chunky anime-styled Minecraft characters). This comes next and replaces the noisy HD look as the default.
  - [x] 14a Painted skins (v0.28.0): every race skin and generated body repainted in the reference direction:
    - clean dark line art for the anatomy (pecs, abs, obliques, biceps, deltoids, back muscles, knees);
    - 2-3 tone cel shading with soft highlights, readable at 64x64;
    - race markings drawn as clean shapes (Namekian arm and leg muscle patches, Frost Demon plates and gems, Majin dots).
  - [x] 14b Anime faces v2 (v0.29.0 eyes, brows, nose, mouths; v0.36.0 expressions: shouting, gritting, blinking): a bold upper lid line, white sclera, coloured irises with pupils and a highlight, sharp angled brows, a nose hook line and a small mouth; expressions (calm, angry, shouting, hurt); every race default reworked.
  - [x] 14c Hair v3 (v0.30.0): chunky voxel clumps (stacked, tapering cuboids per spike, wrapping the back and sides, darker roots to lighter tips). All presets and every form's hair (SSJ, SSJ2, SSJ3 long, SSJ4 mane, God, Blue, LSSJ) rebuilt.
  - [x] 14g Proportions (v0.30.0, user request: "the chest is a little bigger than the torso"): a chest block wider and deeper than the waist, per build
  - [x] 14d 3D body parts (v0.31.0):
    - Namekian pointed ears and curved antennae;
    - Majin head tentacle and ear holes;
    - Frost Demon horns, head dome, shoulder domes and ear pieces;
    - a segmented furry Saiyan tail with physics (swings, wraps round the waist);
    - other race tails, Demon horns, Bio-Android wings and crest.
  - [x] 14e Clothes v2 (v0.32.0; v0.36.0 zip hoodie set and Frost Demon armour), all original designs with 3D parts:
    - Saiyan battle armour (chest plate, shoulder pads, undersuit);
    - a gi with undershirt, sash and wristbands;
    - weighted boots, hoodie and jacket;
    - a Namekian cape and turban;
    - a Majin vest, baggy pants and an emblem belt (an original emblem);
    - Frost Demon armour.
  - [x] 14f Transformation shapes (v0.33.0; v0.36.0 golden and legendary ape fur, Primal tails) (was CX-8d): bigger frames for Buffed, Legendary and Giant forms (model scale per part), Frost Demon form shapes (second-form horns, third-form crest, final-form smooth head), Super Saiyan 3 brow ridge, Golden Great Ape fur, Primal tails, Bio-Android wings, Majin size changes.
- [x] CX-15 Animations v4 (v0.34.0) (user request: "serious animations that are actually fitting and look amazing"): rework every animation with anticipation, follow-through and weight, and review each frame by frame in game:
  - jab, cross, hook and kick combo strings;
  - heavy wind-ups;
  - beam charge and fire (cupped hands, two-handed pushes);
  - ki volleys;
  - guard, parry, dodge and vanish;
  - flight poses (cruise, dash, hover);
  - a tensed powering-up scream;
  - per-race transformation sequences;
  - hit reactions, knockback tumbles and the get-up;
  - per-race idle stances.
- [x] CX-11a Aura v3 (v0.35.0) (user: "the auras don't look finished"):
  - layered flame-shaped shells (a bright core, outer tongues licking up from the feet, flicker and turbulence);
  - a burst on starting a charge, ground dust and lifting rocks;
  - looks per form: SSJ spiky gold, SSJ2 crackling, God calm flame, Blue glow, silver wisps, red Kaioken, dark evil.
- [x] CX-11 VFX v3 (v0.35.0 aura, v0.37.0 vanishes, ground cracks, ground arcs, beam scorch, speed lines; real bloom and heat haze deferred: they need post-processing shaders)
- [x] CX-12 World (user: "make it REALLY GOOD (npc designs and authentic)")
  - [x] 12a The other world (v0.38.0): souls of the dead (halo), the check-in station and Enma, ogre clerks, Snake Way, the Kai of the north's planet (10x gravity; catch the monkey, strike the cricket: Kaioken, Gathering Sphere), Limbo for evil souls, the revive wish; every NPC repainted in the painted style with 3D hair, race parts and gear
  - [x] 12b God Ki pools (v0.38.0): the springs of the Grand Kai's paradise (meditate to awaken and grow godly ki)
  - [x] 12c Fusion v2: the fusion dance (timed duet) and the Kai earrings (v0.39.0)
  - [x] 12d Black Star and Super Dragon Ball wishes (variant reroll, true immortality) (v0.40.0)
  - [x] 12e Metals and alloys tiers (v0.45.0)
- [x] CX-16 Design overhaul (user: "the character creation design is ugly and cramped... I want a design overhaul")
  - [x] 16a (v0.41.0) UI v3 foundation and character creation rebuilt: full-screen layout, a large rotatable preview, tabs (Race, Body, Face, Hair, Path), roomy cards; new characters start in shorts only; better body proportions (thicker arms, sturdier limbs)
  - [x] 16b (v0.42.0) Race customization: per-race colours and body parts (Frost Demon horns, shell and skin colours; Namekian antennae and markings; Majin antenna and skin; Saiyan tail colour; Kai and demon ears and horns; Bio-Android spots and crest; and more), editable later too
  - [x] 16c (v0.43.0) HUD v3: cleaner, better-designed health, ki and stamina bars (not flat or "robloxy")
  - [x] 16d (v0.44.0) The other screens in the new style, with tabs: stats, techniques, racial and universal skills, forms, quests
  - [x] 16e (v0.46.0) The Saga HUD to the user's spec: round portrait with the hair over the ring, Release tab, smoky BP and Ki bar, slanted health and stamina bars with values inside
- [ ] CX-17 Originals (the original additions planned in docs/CONTENT_EXPANSION.md)
  - [x] 17a (v0.47.0) The Spirit Bomb: raised overhead for up to twenty seconds, it draws ki from its thrower, from every player within 48 blocks who holds Charge, and a trickle from the living things around; it grows with what it gathers and spares everyone who gave to it; cast again to throw it. Taught by the Kai of the north.
  - [x] 17b (v0.48.0) Beerus's Planet: Beerus teaches Hakai, Whis teaches Ultra Instinct (-Sign- and Mastered, any race); the real Dragon Ball names everywhere (King Kai, Shenron, Frieza Race, Kamehameha...)
  - [x] 17c (v0.49.0) The World Martial Arts Tournament: grounds near spawn, the Announcer, an eight-fighter bracket (players and the real roster: Mr. Satan, Spopovich, Pintar, Jewel, Nam, Ranfan, Yamu), ring-outs, knockouts and decisions, prizes and the World Champion title
  - [ ] 17d A dojo of training robots (agility drill)
  - [ ] 17e Family (children with 5% of each parent's stats), planets and sectors, claiming
- [ ] CX-18 Animation v5 (user: an overhaul, "dynamic and universal": one system for the player and every NPC, data-driven, smooth, speed-scaled, procedural)
  - [x] 18a (v0.50.0) The motion engine: walk, sprint, jump, fall, landing, takeoff and flight (hover, cruise, fast, ascend, descend, backward, strafe banking) for the player and every humanoid NPC; JSON clips, sets with race and form overrides, entity profiles and tuning; /dbzanim; guide in docs/ANIMATION.md
  - [ ] 18b The combat actions (punches, kicks, transformations, casts, charge, guard) moved into the data clips too, and given to NPCs
- [x] CX-19 Combat v4 (user: a full PvP combat system, Sparking! ZERO feel with Dragon Block depth; phase by phase, stopping for feedback after each)
  - [x] 19a (v0.51.0) PvP mode: key P and /pvp, server rules (cooldown, fight timer, pull-in on being struck, both must be in PvP mode), safe zones (spawn, other world, tournament grounds, /dbz pvpzone), effects judged like blows, the HUD badge and the name mark
  - [x] 19b (v0.52.0) Melee combos from data: the combat engine for players and NPCs (light chain, directional heavies: smash, rush, uppercut, launcher, spike, sweep, hook), cancel windows, input buffering, hitboxes, combo scaling, juggles, wall and ground slams, knockdowns, clashes, Z-hits; replaces Combat v3 melee
  - [x] 19c (v0.53.0) Guard from the front only, perfect guard, vanish step, counters, Burst, super dash, and the Dash moves rebuilt on the engine (tech roll, air recovery, side step, spot dodge, chase)
  - [x] 19d (v0.54.0) Ki blasts on key C (tap: quick, hold: charged; from data), the special meter (3 bars; built by landing and taking blows, perfect guards, vanishes), supers (1 bar) and ultimates (3 bars, a cut-in cinematic) from data, the meter surge in beam clashes, the meter on every HUD style
  - [x] 19e (v0.55.0) Hit feedback: hitstop on both fighters (server-side, knockback after the freeze), criticals (from the back, or catching a wind-up), counter and Z-hit looks, damage popups and words, layered hit sounds (four new synthesized), speed lines, the ultimate camera, fighter states synced to clients (stunned and launched stances), and NPC combat animations (moves, reactions, stun, launch, knockdown, get-up)
  - [x] 19f (v0.56.0) Lock-on: key N (Shift: next foe), a target picker, a camera that keeps the foe framed with free look and free movement (circle-strafing, flight), over-the-shoulder framing in third person, a bracket marker with name and distance, automatic release, and the server-side lock (super dash to the locked foe from any angle, ki blasts curving toward it)
  - [x] 19g (v0.57.0) Netcode: input rate limits and sanity checks (InputGuard), lag compensation (position history, rewind by ping), the pools-only sync packet, client prediction of melee starts, quick ki blasts and plain dashes with server reconciliation, /dbz netstats
  - [x] 19h (v0.58.0) HUD: the enemy panel (lock or last foe; health, and ki, guard and special for players), combat callouts (vanish, perfect guard, parried, counter, clash, beam struggle, burst, guard broken and crushed), the combo counter with its damage and time left, the Controls & Move List screen (keys, every move from data with input and frame data, dodge, guard, ki, lock-on), a Combat settings tab and the lock-on settings
  - [x] 19i (v0.59.0) Duels (/duel: challenge, countdown, arena, rounds, ring out, time, melee rules, results screen, Elo ladder, /duel watch with the duel camera), the training dummy (seven behaviours, the string readout, never dies, goes home), the hitbox and frame-data overlay (/dbzhitbox), the combat log
  - [x] 19j (v0.60.0) The PvP balance curve (fight length by equalJabsToKo, the power ratio from the blows themselves, softened and capped, no one-shots, a combo cap), modelled and checked by the balance report; moves for some forms (forms, form_damage); artist animations replacing any clip; docs/COMBAT_GUIDE.md and docs/COMBAT_ASSETS.md
- [x] CX-20 PvP change request (user: a plain switch, normal Minecraft out of PvP, the fighting stance in it, auto-tag, a dot crosshair, form and technique meters in two bottom-right bars, styles from masters; phase by phase, stopping for feedback)
  - [x] 20a (v0.61.0) A plain switch: every PvP indicator and effect removed, no toggle cooldown by default
  - [x] 20b (v0.62.0) PvP off = plain Minecraft (vanilla hits, no combat moves), PvP on = the fighting stance (blended), and the PvP camera over the right shoulder
  - [x] 20c (v0.63.0) Auto-tag: any hit by a player, NPC or mob tags and switches PvP on; the tag blocks switching off; tagForcesPvp, hurtOutOfPvp; /dbz pvptag
  - [x] 20d (v0.64.0) The crosshair: a round dot in PvP at the real aim point (shoulder view), a four-pointed star out of PvP; size, colour, opacity, dot always or in PvP only
  - [x] 20e (v0.65.0) The bars (visuals): two studded bars bottom right in PvP, the form bar in the aura colour, the technique bar in the technique's; steps from unlocked forms, Kaioken and Ultra Instinct; /dbz meter
  - [x] 20f (v0.66.0) The bars (logic): filling from fighting, J and O gated in PvP, drains and drops, Ultra Instinct a technique on O, data-driven rules synced to clients; /dbz unlockform
  - [x] 20g (v0.67.0) Styles from masters: 16 masters (NPCs, skins, spawn eggs), 16 styles over nine animation slots (100 clips, clip inheritance), requirements, affinity, training, the K menu Styles page (each slot on its own), NPC styles; /dbz style
  - [x] 20h (v0.68.0) Polish: styles proved cosmetic (no buffs or debuffs), data checks; docs/PVP_GUIDE.md (new master, style, form threshold) and docs/PVP_ASSETS.md (textures and animations with sizes)
- [x] CX-21 (v0.69.0) Knockback and destruction: combos and hard blows send fighters flying by force; craters sized by force and impact speed on walls and ground; ki blasts and beams leave craters and carve; a Craters/Calm toggle in the Ki creator; [destruction] config
- [x] CX-22 (v0.70.0) Proportions (tapered torso, fuller limbs, NPCs shaped too), the floating torso fixed for every layer (RigFix), all 16 styles redone (108 clips) with verified poses, the dev pose sheet
- [x] CX-23 (v0.71.0) Charged ki attacks (hold R: up to 30 s for beams, longer for ultimates, bigger and stronger, ki drain, orb and pose, gauge) and held transformations (hold J, the bar falls back when let go, paid on completion)
- [ ] CX-24 Auras reworked (animated, volumetric, data-driven), after the user's Godot prototype
  - [x] phase 1 (v0.72.0) The shell (own shader, glow, inner haze, sparkles, plain fallback for shader packs), data files, Super Saiyan Blue; /dbzaura
  - [x] phase 2 (v0.73.0) Layers as data, animation and states (idle, charging, flying trail, hit flare, burst), extends, live tweaking
  - [x] phase 3 (v0.74.0) Kaioken (X2-X10, X20+ tiers) over other auras, stacking; flame tongues; every form from data; charge lock; auto-deploy
  - [x] phase 4 (v0.74.0) Super Saiyan and Super Saiyan God from data (and the other forms)
  - [ ] phase 5 Ultra Instinct (silver shimmer, afterimages)
  - [ ] phase 6 Transformation burst, ground effects, light
  - [ ] phase 7 Performance and config (quality, light, ground, own aura, intensity), live tweaking
- Paused for CX-19: 17d (the robot dojo), 18b (the combat animations as data; folded into 19b)

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
- [x] Overdrive (N / Shift+N): x2-x20 levels gated by mastery, body+stamina drain, backlash; stacks with base and Blue only
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
- [ ] CX-8d Transformation shapes (user request, follow-up): bigger frames for Buffed, Legendary and Giant forms (model scale per part), Frost Demon form shapes (second-form horns, third-form crest, final-form smooth head), Super Saiyan 3 brow ridge, Golden Great Ape fur, Primal tails, Bio-Android wings, Majin size changes
- [x] CX-8e Face customization in first-time character creation (user request): Body / Face tabs with a zoomed head preview; race skins now wear the face parts too (v0.22.0)
- [ ] CX-13 Art and design overhaul (user request: "way too simplistic"; more detailed forms, body art, UI and HUD, switchable in the settings; the user's reference pictures are style inspiration only, nothing copied)
  - [x] 13a HD bodies (v0.23.0): 128x128 generated bodies (muscle definition, rim light, ambient occlusion), detailed outfits (gi folds, belts, wristbands, boots), HD face parts (16x16 faces: eye highlights, iris gradients, lashes)
  - [x] 13b HD race skins (v0.24.0): every race and lineage repainted at 128x128 (scales, plates, spots, markings), their transformation recolours regenerated
  - [x] 13c Form detail (v0.25.0): HD hair texture (strand gradients, rim highlights), glowing eye highlights, form body markings (god-ki sheen, kaioken flush, silver Ultra-style sheen), lightning on the body
  - [ ] 13d UI v2: ornate themed panels, buttons and frames; a "UI style" setting (Zenith / Classic)
  - [ ] 13e HUD v2: detailed portrait frames per form, animated bars, new layouts; a "HUD style" setting (Zenith / Classic / Minimal)
  - [x] 13f Art detail setting (v0.23.0): Zenith Settings > Style > HD art
- [ ] CX-11 VFX v3
- [ ] CX-12 World (Otherworld, God Ki pools, fusion v2, wishes, metals)

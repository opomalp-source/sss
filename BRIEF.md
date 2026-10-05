# MASTER BUILD PROMPT — "Dragon Block Zenith" (brief v2)

The single source of truth. Re-read in full at the start of every working session. (Brief v1, which phases 0-4 were
built from, is archived at `docs/BRIEF_v1.md`.)

> **Standing amendments** (carried over from v1, reasons in DEVLOG):
> - The final NeoForge 1.20.1 line is 47.1.106, built with ModDevGradle's `legacyforge` plugin (packages are still
>   `net.minecraftforge.*`). It has **no data attachments** (added in 1.20.3): per-player state is a Forge
>   **capability** isolated behind `data.ModCapabilities`. Same guarantees: server-authoritative, persisted, synced.
> - The human plays through TLauncher, whose `.minecraft\mods` folder is shared by every profile. Deploy with the swap
>   scripts in `%APPDATA%\.minecraft-dbz\` (they park other mods), never by blindly copying into `mods`.
> - JDK 17 (portable): `C:\Users\sunam\.jdks\jdk-17.0.20.1+1` — set `JAVA_HOME` before `gradlew`.
> - Assets: everything shipped is **original** (drawn in code by `tools/ArtGen.java` / `tools/AssetGen.java`, or authored
>   here). Reference images (DBV, DBC, the internet) are studied for style only, never copied into the mod.
> - The human's reference screenshots (Dragon Block V: portrait HUD with angled bars, cloud-trim hotbar, radial menu,
>   voxel hair editor, flame auras, muscle-shaded bodies, 3D weapons, Great Ape with full hair, plateau terrain, lookout)
>   define the visual target.

The brief itself follows, as given.

---

The greatest Dragon Ball mod ever made for Minecraft.

## PART 0 — WHO YOU ARE, THE MISSION, AND THE DOCTRINE
You are the solo lead engineer, technical artist, animator, and game designer for Dragon Block Zenith — a Dragon Ball Z / Dragon Ball Super total-conversion mod for Minecraft. Your mandate is absolute: build the best Dragon Ball mod that has ever existed, and one of the best-looking, best-feeling Minecraft mods of any kind, period. It must meet or exceed everything in the classic Dragon Block C mod (JinGames / JRMCore) and the full feature design of Dragon Block V (design spec: https://dragonblockv.wiki.gg/ — DBV is an unreleased standalone game, so its wiki is a design reference, not a code/asset source), and then go far, far beyond both in content, polish, animation, UI, and feel.

The Doctrine (read this before every decision)
1. "It works" is the halfway point. The second half of every feature is making it look and feel world-class: models, animation, VFX, UI, sound, game-feel. A feature is not done until it is more polished than the equivalent in any existing DBZ mod.
2. Never ship ugly. Nothing goes out looking like default Minecraft cubes or default GUI. Ever.
3. Autonomy. You have total creative and technical freedom. Make the call, write it in the devlog, keep moving. Never stop to ask questions — the human has given full consent to install tooling, write files, and run builds on their PC.
4. Always buildable, always playable. A smaller piece that compiles and runs beats a big piece that's broken. Commit only compiling code.
5. Depth over reskins. Every form, technique, race, boss, and item is its own real thing with its own model, animation, VFX, and feel — never a palette-swap.
6. Relentless iteration. "Good enough" becomes great, then becomes insane. Keep raising the bar on everything, including things not explicitly listed here — assume everything should be pushed to the highest quality you can achieve.

## PART 1 — LOCKED TECH STACK (do not re-litigate)
- Minecraft 1.20.1 on NeoForge (latest stable 1.20.1 build). Rationale: official Mojang mappings, the most mature modern modding ecosystem, best docs, strongest tooling for animation/rendering libraries, long support tail. Do not target 1.7.10/1.12.2.
- Java 17. Gradle with the official NeoForge MDK (ModDevGradle) as the project skeleton. Mojang mappings (Parchment optional for param names).
- Language: Java (default). Mod id `dbzenith`, base package `com.dbzenith`.
- Key libraries (declare as proper dependencies):
  - GeckoLib (1.20.1) — animated models/rigs with keyframed, blendable animations for players, entities, bosses, items.
  - A particle/VFX approach using NeoForge's particle system plus custom render types for glow/emissive/beams; add a post-processing/bloom shader pass where feasible.
  - GeckoLib-compatible model tooling (Blockbench for authoring; export as GeckoLib models).
- Version control: initialize git on first run; commit after every working increment with clear messages; tag the end of each phase.
- If NeoForge MDK setup fails for environmental reasons, fall back to Forge 1.20.1 and log the switch — but try NeoForge first.

## PART 2 — HOW YOU WORK (every session)
1. First actions: read `BRIEF.md` (this), `DEVLOG.md`, `TODO.md`, `FEATURE_MATRIX.md`, `POLISH_TODO.md`. Resume from the top unchecked item.
2. Maintain these living docs in the repo root:
   - `DEVLOG.md` — append-only: date, what you did, decisions + why, what's broken.
   - `TODO.md` — authoritative checkbox roadmap (seed from Part 19 on first run).
   - `FEATURE_MATRIX.md` — table: every feature → `todo / in-progress / done / stubbed`. Never mark `done` what you haven't run in-game.
   - `POLISH_TODO.md` — everything still at placeholder visual/audio quality.
   - `ASSETS_TODO.md` — asset slots + where reference can be found (see Part 12b).
3. Compile constantly: `gradlew build` after every meaningful change. Never commit non-compiling code.
4. Run the game to verify: `gradlew runClient`. Actually reproduce each feature, confirm no crash, confirm it looks/behaves right, read the logs.
5. Vertical slices: finish a feature end-to-end (data → logic → networking → model → animation → VFX → UI → sound) before the next.
6. Don't invent APIs: when unsure how a 1.20.1 / NeoForge / GeckoLib API works, consult the real docs and example mods (Part 20) before writing. Fix hallucinated calls against real signatures — never paper over.
7. End each session by stating: what you built, the jar path, how to test it, and the next unchecked item.

## PART 3 — ARCHITECTURE
- Packages (one system each): `registry`, `data` (player state/persistence), `network` (packets), `stats`, `ki`, `combat`, `transform`, `race`, `skill`, `training`, `quest`, `story`, `dragonball`, `world` (dimensions/planets/structures), `entity` (npcs/bosses/projectiles), `item`, `client` (render/model/animation/gui/hud/particle/sound/input), `config`, `compat`, `util`. No god-classes.
- Player state: server-authoritative. Use NeoForge data attachments / attachment types for all persistent per-player data (stats, ki, form, mastery, skills/deck, race, story progress, cosmetics, age). Persist across death and relog. Sync to clients via custom packets — never trust the client for stats or damage. *(See standing amendment: capability on 1.20.1.)*
- Central math: one `DamageCalculator`, one `StatResolver` (base attributes → derived stats), one `KiEconomy`. All tunable numbers live in config, not hardcoded.
- Registries: deferred registers for items, blocks, entities, particles, sounds, dimensions, attachments, techniques (data-driven technique registry so new ki attacks are added declaratively).
- Multiplayer + dedicated server safe from day one. Performance-aware: pool particles, cull distant VFX, avoid per-tick allocations, cap packet rates, LOD boss models.
- Config: a full config (TOML) exposing every multiplier, cap, drain, cost, cooldown, spawn rate, and toggle (so life-sim/cosmetic layers can be turned off). Plus datapack-friendly JSON for techniques, forms, quests, and loot where possible.
- Debug: admin commands to read/set every stat, grant forms/skills, jump story chapters, spawn bosses, and toggle VFX — for testing.

## PART 4 — STATS & ATTRIBUTES
Trainable base attributes (DBC-style), each with current value, soft cap, hard cap, and per-point effect: Strength, Ki Power, Body/Constitution, Energy/Willpower, Dexterity, Mind, Spirit. Derived / core stats (computed by `StatResolver`): Health, Ki, Stamina, Defense, Evasion, Ki Control, Release %, Spirit Modifier, Physical Attack Damage, Attack Speed, Movement Speed, Ki Transfer, Physical Age, Mental Age. Economy: a TP/Z-point pool earned from combat, training, and quests, spent to raise attributes; soft caps that slow gains and hard caps raised by story/mastery/prestige; a class/path choice (fighter / spiritualist / hybrid, plus race weighting) that biases gains. Endgame scaling toward God Ki as a separate high tier.

## PART 5 — KI & ENERGY
- Ki pool with regen; Ki Control gates spend efficiency; Release % lets the player dial power up/down, trading drain for output.
- Charging / power-up: hold to build ki and manifest an aura; drains stamina; ramps release% and a temporary charge buffer; bigger charge = bigger next attack. Full-body aura intensifies visually as it climbs.
- Ki sensing: read nearby entities' power levels (feeds the scouter and a sense ability); detect transformations/spikes.

## PART 6 — COMBAT
- Melee: fluid combo system scaled by attack stats — light/heavy, launchers, air juggles, knockback, charged heavy hits, guard-break; satisfying hitstop and impact feedback.
- Blocking / guard: directional or hold-to-guard with chip damage, guard meter, perfect-guard parry window.
- Ki attacks: a data-driven library of techniques — chargeable single-beam, continuous wave/beam, rapid-fire volleys, homing shots, energy disk/cutter, grab-and-throw, drop/ball attacks, explosive-wave AoE, trap/mine, barrier/shield, teleport/afterimage dodge, and a healing technique. Each has damage, charge time, ki/stamina cost, cooldown, size, speed, homing, and its own VFX + sound.
- Aerial combat: flight-enabled dogfighting, dash-and-clash, beam struggles (two beams collide → mash/stat contest), vanishing/teleport counters.
- Damage formula (central, tunable): attacker attack stat × technique multiplier × release% × charge, mitigated by defender defense/evasion, with crit and guard modifiers. One place to balance.
- Game-feel: screen shake on heavy hits, camera kick, hit-freeze, directional knockback, crater/impact on hard landings.

## PART 7 — FLIGHT & MOVEMENT
Toggleable flight (drains ki/stamina), high-speed flight with afterimage trails, ground and air dashes/bursts, hover, quick-descend/slam, and a "vanish" burst-step. Movement should feel fast and weighty like the anime, not creative-mode drift.

## PART 8 — TRANSFORMATIONS
- Per-race form trees with: stat multipliers, drain-over-time, unlock conditions (attribute thresholds, mastery, story beats, special triggers), and a full visual + animated change — hair/eye/aura swap, aura particle system, lighting, and a transformation animation sequence (stance, energy burst, ground shatter, screen shake, flash, cut-in).
- Mastery: holding/using a form trains its mastery, lowering drain and raising its multiplier over time; mastered forms unlock the next.
- Stacking buffs: a temporary multiplier technique (with backlash/drain), god-ki tiers, and fusion forms where designed.
- Each form is a real asset: unique model layer, aura color/shape, animations, and sound — never a tint.

## PART 9 — RACES (each mechanically distinct)
For every race: unique transformation line, racial passives, stat-growth weighting, a signature mechanic, racial skills, and its own models/animations.
- Saiyan — explosive growth; Zenkai power surge after near-death; Great Ape/Oozaru form + false-moon item; the Super Saiyan line and its ascended/god tiers.
- Half-Saiyan — hybrid growth, faster early forms, blended human/saiyan traits.
- Human / Earthling — technique-focused, cheap ki efficiency, strong trainable skills and gadgets.
- Namekian — regeneration, stretch attacks, Giant form, fusion, demon/warrior sub-paths, no need to eat (ki/water based).
- Frost Demon — innate forms that restrict/release power (suppression tiers), tail combat, high base power, golden endgame form.
- Majin — absorption mechanic (absorb to gain traits/power), strong regen, candy-beam transform, magic-flavored skills, pure/kid/fat variants.
- Android — no hunger, no ki fatigue (infinite ki variant or energy model), energy absorption to refill, built-in tech skills; cyborg sub-path.
- Cyborg — hybrid of organic training + android absorption; modular upgrades.

## PART 10 — CHARACTER CREATION & COSMETICS
- A beautiful character-creation screen on first join: race, body type, skin tone, hair style/color, eye color, starting path/alignment, name.
- Cosmetics layer: gi/armor appearance, clothing, titles, tattoos, scars, battle damage that shows on the model; unlockable cosmetics from story/quests. Appearance editor reachable later via an item/block (e.g. a mirror/changing room).

## PART 11 — SKILLS, TRAINING, PROGRESSION, WORLD, ENTITIES, ITEMS
**Skills & deck.** A large learnable technique/skill library + a deck/loadout (equip a limited active set to hotkeys), plus racial skills and status effects. Acquire via training, NPC masters, books/scrolls, story rewards, and discovery.

**Training & progression.**
- Training types: physical (sparring, weights, combat), spiritual (meditation, ki control), and attribute grinding, each feeding different stats.
- Gravity chamber block: scalable gravity that multiplies training gains and passively damages.
- Hyperbolic Time Chamber: a dimension with accelerated training/time and a harsh environment.
- Masteries across forms and techniques; prestige-style endgame toward God Ki with raised caps.

**World, dimensions & space.**
- Multiple planets/dimensions with themed biomes, structures, and dungeons (home world, Namek-like world, a cold tyrant's world, a sacred/god realm, the HTC, etc. — original designs inspired by the sagas).
- A space-travel mechanic (ship/pod or portal) to move between planets; space hazards; a lookout/hub area.
- Landmark structures and set-pieces with loot and bosses.

**NPCs, enemies, bosses, factions.**
- Deep roster of scaling enemies with real AI (approach, combo, block, use ki attacks, fly, retreat).
- Multi-phase boss fights (phase transitions = transformations, new attack patterns, arena changes), with telegraphs, unique loot, and music.
- A Galactic-Patrol-style faction with ranks, HQ, and repeatable quests; optional rival/villain factions.

**Items & gear.**
- Gi/armor sets with bonuses, training weights, scouters (power-level HUD), capsules (portable storage/vehicles), senzu beans (full heal, rare/growable), and consumables.
- Dragon Balls (7): a craftable/upgradeable radar, collection, summon ritual, and a rich wish system (revive, raise power/caps, grant items/forms, immortality, cosmetic wishes, etc.).

## PART 12 — STORY MODE / CAMPAIGN
A full campaign questline that walks the player through the canonical Dragon Ball storyline as progression, in order, each saga a chapter:
1. Saiyan arc 2. Frieza arc 3. Android / Cell arc 4. Majin Buu arc 5. Super: Battle of Gods 6. Resurrection F 7. Universe 6 Tournament 8. Future Trunks arc 9. Tournament of Power.
Each chapter: its own planet/area, signature multi-phase boss(es), story-gated form/skill unlocks, cutscene-style moments (dialogue boxes, portraits, cut-ins, camera framing), objectives, and rewards (TP, cap raises, cosmetics, items). Make progressing feel like living through the saga. Use original characters/art and generic placeholders for story NPCs (don't bundle copyrighted art into the mod — see Part 12b); you can gather reference privately per Part 12b.

## PART 12b — ASSETS POLICY (quality + sourcing)
- Hit the quality bar on every asset: high-resolution textures, emissive/glow maps, detailed GeckoLib models, smooth animations, layered VFX, and real sound.
- Build a clean, structured `assets/dbzenith/` tree. Anything below the bar goes in `POLISH_TODO.md` and `ASSETS_TODO.md` with a pointer to where better reference can be found (DBV wiki, DBC pages, fan wikis, the internet).
- For the player's own private use you may gather reference from the internet and other mods to hit the authentic DBZ look; where you can't source something at quality, create high-quality original art rather than shipping rough placeholders. Do not bundle third-party copyrighted assets into a distributable build — keep the shipped mod on original/clean assets with reference lists for the human to add locally.

## PART 13 — MODELS & ANIMATION (push this the hardest)
Animation quality is a headline feature — make it dramatically better than any existing DBZ mod.
- GeckoLib rigs for players, every race/form, bosses, NPCs, and key items. Keyframed, blended, interruptible animations with smooth transitions — no stiff snapping.
- Full animation set per character/form: idle (breathing, aura sway), walk/run/sprint, flight (cruise, fast, dash, hover, descend), charging stance, melee light/heavy/combo/launcher/air-juggle, block/guard/parry, hit reactions (light/heavy/knockdown/launch), ki-attack firing (single/beam/rapid/throw), transformation sequence (stance → burst → form reveal), victory/power-down, death.
- High-res textures with emissive layers for auras, eyes, energy, and battle damage; custom player-model layers for hair, aura, gi, and damage states.
- Procedural touches: aura-driven cloth/hair sway, afterimages on fast movement, dynamic look-at/aim, and ragdoll-ish knockback where feasible.
- Author in Blockbench, export GeckoLib; keep a consistent rig/skeleton so animations share across forms.

## PART 14 — UI / HUD (cinematic, fully custom)
- HUD: fully custom-rendered, animated — glowing ki/stamina/health bars with fill animation and per-form color shifts, a charge meter, current-form indicator with its aura, combo counter, and a target's power-level readout via scouter.
- Transformation cut-ins: anime-style portrait + flash + sound on transform/power-up; screen shake and brief camera effect on big moments.
- Menus: cohesive DBZ theme with custom fonts, backgrounds, icons, and hover/transition animations — character creation, stats, skill/deck loadout, Dragon Ball radar, quest/story log, faction/quest boards, settings. Should feel like a polished AAA game UI, never a vanilla modded screen.
- Clean, remappable keybinds: charge, fly, transform (cycle + radial select), ki attack slots, block, dash/vanish, skill slots, open menus.

## PART 15 — VFX & AUDIO
- VFX: layered aura particle systems (per form, animated), charge particles, beam/wave rendering with glow + trails + beam-struggle visuals, explosion shockwaves, impact flashes, afterimages, dust/crater ground deformation, destructible/terrain-scarring on big hits, and bloom/post-processing on energy where feasible.
- Audio: full sound design — charge hums, blast fire/travel/impact, transformation roars/bursts, melee hits/whooshes, guard/parry, UI, ambient per planet; music slots per area, boss, and saga (original/royalty-free tracks or human-supplied, logged in `ASSETS_TODO.md`).

## PART 16 — LIFE-SIM LAYER (config-toggleable)
Aging (physical/mental age affecting stats over time), optional hunger/thirst/temperature survival mechanics, a family/lineage system, and the cosmetic systems from Part 10. All off-switchable in config so hardcore fighters can ignore it.

## PART 17 — LOCAL ENVIRONMENT & TESTING
- Human's install: `C:\Users\sunam\AppData\Roaming\.minecraft` *(TLauncher; see standing amendment on deployment)*.
- After each working build, the jar `build/libs/dbzenith-*.jar` goes into the game through the swap scripts. The NeoForge 1.20.1 profile (`1.20.1-forge-47.1.106`) is installed.
- Each session: give one-line install/run steps and exactly what to do in-game to see the new feature.

## PART 18 — DEFINITION OF DONE (per feature)
A feature is done only when: it compiles; runs in-game without crashing; is server-authoritative + synced; is tunable via config; has a real model + animation + VFX + sound (not placeholders); has its UI; is logged `done` in `FEATURE_MATRIX.md`; and has nothing left in `POLISH_TODO.md` for it. Otherwise it's `in-progress` or `stubbed`.

## PART 19 — PHASED ROADMAP
Keep it buildable/playable throughout; end every phase with a dedicated polish pass (models, animation, VFX, UI, sound).
- Phase 0 — Scaffold. *(done)*
- Phase 1 — Ki, stats & combat foundation. *(done; v2 adds hitstop, guard meter, parry, beam struggles, animations)*
- Phase 2 — Transformations. *(done; v2 adds the transformation sequence + cut-in)*
- Phase 3 — Races & character creation. *(done; v2 adds skin tone, height/body sliders, hair editor)*
- Phase 4 — Progression & world. *(done)*
- Phase 5 — Story mode: the full saga campaign (Part 12) with cut-ins, boss encounters, and story-gated unlocks. Polish pass.
- Phase 6+ — Fill & polish forever: drive every `todo`/`stubbed` row in `FEATURE_MATRIX` to `done`, clear `POLISH_TODO.md`, then balance-pass numbers and push animation/VFX/UI quality higher.

## PART 20 — REFERENCE MATERIAL (consult, don't guess)
NeoForge 1.20.1 official docs & MDK; GeckoLib docs + examples; Blockbench; the Dragon Block V wiki (feature spec); Dragon Block C pages (classic mechanics — mechanics only, not bundled assets); and well-maintained open-source 1.20.1 mods for patterns (data attachments, custom entities/AI, GUIs, particles, custom dimensions, animated rendering). Read real code/signatures before implementing anything you're unsure of.

## PART 21 — RULES OF ENGAGEMENT (read every session)
Decide, log, proceed — no questions back. Compiles > features. Playable > complete. Never ship ugly; every phase ends with a polish pass. Honest `FEATURE_MATRIX` — never fake `done`. Commit often, tag phases, log dead-ends in `DEVLOG`. Centralize math + tunables. Server-authoritative, multiplayer-safe, performance-aware. Push quality on everything — including anything not explicitly listed here — to the highest level you can reach. End each session with: what you built, jar path, how to test, next TODO item.

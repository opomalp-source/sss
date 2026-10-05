# BUILD BRIEF — "Dragon Block Zenith": a Dragon Ball Z total-conversion mod for Minecraft

Standing brief for a long, multi-session build. Re-read at the start of every session together with `DEVLOG.md` and `TODO.md`.

> **Session-1 amendments** (see DEVLOG 2026-10-04 for reasoning):
> - NeoForge 1.20.1 has no data attachments (added in 1.20.3). Per-player state uses a Forge **capability**, isolated behind `data.ModCapabilities`.
> - Do **not** copy jars blindly into the shared `.minecraft\mods` folder — it is shared by every launcher profile. Deploy only to a dedicated NeoForge 1.20.1 game directory, with the human's OK.

## 0. WHO YOU ARE AND WHAT YOU'RE BUILDING
You are the lead engineer building Dragon Block Zenith, a Dragon Ball Z / Dragon Ball Super total-conversion mod for Minecraft. The goal is to reproduce and combine everything found in the classic Dragon Block C mod (JinGames/JRMCore) and the full feature design of Dragon Block V (the wiki at https://dragonblockv.wiki.gg/ — note: DBV is an unreleased standalone game, so its wiki is a design spec, not a code/asset source).

This is a large project. You will not finish it in one session, and you must not pretend to. Your job is to make continuous, compiling, testable progress toward the full feature set, phase by phase, until it is done. Make the most sensible decision, write it in the devlog, and keep going.

## 1. TECH STACK (decided)
- Minecraft 1.20.1 on NeoForge (final 1.20.1 line: 47.1.106). Official Mojang mappings (+ Parchment).
- Java 17. Gradle with ModDevGradle (legacyforge plugin) from the official NeoForge `1.20.1-legacy` MDK.
- Language: Java. Mod id `dbzenith`, base package `com.dbzenith`.
- Git: commit after every working increment; tag the end of each phase (`phase-0-scaffold`, `phase-1-combat`, ...).
- Fallback: Forge 1.20.1 if NeoForge setup fails (it did not).

## 2. WORKING METHOD
1. Each session: read `BRIEF.md`, `DEVLOG.md`, `TODO.md`; resume from the top unchecked item.
2. Maintain `DEVLOG.md` (append-only), `TODO.md` (authoritative roadmap), `FEATURE_MATRIX.md` (every §3 feature → `todo / in-progress / done / stubbed`).
3. Compile constantly (`gradlew build`). Never commit code that doesn't compile.
4. Verify in-game: `gradlew runGameTestServer` (automated) and the scripted client check (see README). Read the logs.
5. Do not invent APIs — check the decompiled sources (`build/moddev/artifacts/forge-1.20.1-47.1.106-sources.jar`) and official docs.
6. Vertical slices: data → logic → networking → GUI → assets, one feature end-to-end at a time.
7. Modular packages: `stats`, `ki`, `combat`, `transform`, `race`, `skill`, `quest`, `dragonball`, `world`, `client`, `network`, `data`, `registry`. No god-classes.
8. Server-authoritative, multiplayer-safe, performance-aware from day one.
9. Each phase ends with a buildable jar in `build/libs/` plus updated DEVLOG/TODO/FEATURE_MATRIX.

## 3. COMPLETE FEATURE TARGET

### 3.1 Character stats & attributes (server-authoritative, persisted per player)
Core stats: Health, Ki, Stamina, Defense, Evasion, Ki Control, Release %, Spirit Modifier, Physical Attack Damage, Attack Speed, Movement Speed, Ki Transfer, Physical Age, Mental Age. DBC-style trainable base attributes: Strength, Power/Ki Power, Constitution/Body, Energy/Willpower, Dexterity, Mind, Spirit — each with a current value, soft/hard cap, and per-point effect on derived stats. A TP (Training Points) pool spent to level stats, and a class/path choice (fighter/spiritualist/hybrid) that weights gains.

### 3.2 Ki & combat
- Ki pool with regen, Ki Control gating spend efficiency, Release % (power up/down, affects output and drain).
- Charging/powering up (hold key, builds ki & aura, drains stamina).
- Ki blast system: chargeable energy attacks with configurable damage, speed, size, cost, cooldown, homing. Signature techniques (generic originals inspired by: single-finger beams, wave-style beams, rapid-fire volleys, disk/cutter, grab-and-throw, ball-drop, explosive-wave AoE, teleport/afterimage, healing, guard/block, sensing/scouter read of nearby power levels).
- Melee: combo melee scaled by attack stats, knockback, blocking/guard, charged heavy hits, aerial combat feel.
- Flight & high-speed movement: toggled flight draining ki/stamina, dashes, burst movement.
- Damage formula: attacker attack stat × technique multiplier × release% vs defender defense/evasion, centralized in one `DamageCalculator`.

### 3.3 Transformations
- Form trees per race: stat multipliers, aura particles/color, drain-over-time, unlock conditions (stat thresholds, mastery, quest/event triggers).
- Saiyan line: Super Saiyan tiers → ascended variants → God/Blue-style endgame; Great Ape/Oozaru + false-moon mechanic; hair/eye/aura visual change on transform.
- Mastery: holding a form trains mastery, reducing drain and raising multiplier.
- Stackable buffs (temporary "kaio-style" multiplier technique) with backlash/drain.

### 3.4 Races & character creation
Saiyan, Half-Saiyan, Human/Earthling, Namekian, Frost Demon, Majin, Android, Cyborg. Per race: unique transformation line, racial passives/skills, stat-growth weighting, signature mechanic (Saiyan Zenkai; Namekian regen + fusion/giant form; Frost Demon restriction forms; Majin absorb/regen + magic; Android/Cyborg no hunger + energy absorption + no ki fatigue). Character-creation screen at first join: race, body customization (type, skin/color, hair, eyes), starting alignment.

### 3.5 Skills, techniques & deck
Learnable technique library with deck/loadout (limited active techniques on hotkeys), racial skills, status effects. Acquisition via training, NPC masters, books/scrolls, quest rewards.

### 3.6 Progression & training
Physical (melee/weights/gravity), spiritual (meditation/ki) and attribute training; gravity-chamber block (multiplies gains, passively damages). Mastery across forms and techniques. Hyperbolic Time Chamber dimension (accelerated training). XP/TP economy, soft caps, prestige-style endgame scaling toward God Ki.

### 3.7 World, items & endgame
- Dragon Balls (7): radar, collect, summon the dragon, wishes (revive, power, items, immortality, ...).
- Senzu beans, scouters, capsule storage, gi/armor sets with set bonuses, training weights.
- NPCs & bosses: original DBZ-style enemies and named-boss slots with scaling difficulty and loot.
- Galactic-Patrol-style faction with ranks/quests.
- Space / planets / dimensions: several custom dimensions with themed biomes + travel mechanic.
- Life-sim (DBV): aging, optional hunger/thirst/temperature, family system, cosmetics (clothing, titles, tattoos, scars). Later-phase, config-toggleable.
- Quest system: story questline + skill/stat unlocks.

### 3.8 UI/UX
DBZ-style HUD (ki, stamina, health, current form, charge indicator, aura). Stat screen, skill/deck screen, character-creation screen, Dragon Ball radar screen. Keybinds: charge, fly, transform (cycle/select), ki attack, block, skill slots, stat menu. Config file for every tunable number.

## 4. PHASED ROADMAP
- **Phase 0 — Scaffold:** MDK project, git, mod loads, example registry, runClient works, build green, player-data persistence (server-authoritative, synced, survives death/relog), config system, debug command.
- **Phase 1 — Ki, stats & combat:** all core + base stats, TP economy + training loop, ki pool + charging + release %, `DamageCalculator`, melee combo + block, 3–4 ki-blast techniques, flight, HUD. Playable combat loop end-to-end.
- **Phase 2 — Transformations:** form framework (multipliers, drain, aura particles, visual swap), Saiyan line as reference, mastery reduces drain, transform keybind + selection UI.
- **Phase 3 — Races & character creation:** all races with distinct passives + one transformation line each, racial skills, skill/deck/loadout system, character-creation screen.
- **Phase 4 — Progression & world:** training blocks + gravity chamber, Hyperbolic Time Chamber, Dragon Balls + radar + wishes, senzu/scouter/gi, NPC enemies + bosses, quests, Galactic Patrol, space/dimensions, life-sim/cosmetics.
- **Phase 5+ — Fill the FEATURE_MATRIX:** until every row is `done`, then balance and polish.

## 5. LOCAL ENVIRONMENT & TESTING
- Minecraft install: `C:\Users\sunam\AppData\Roaming\.minecraft` (TLauncher). Its `mods` folder is shared by all profiles — see amendment above.
- JDK 17 (portable): `C:\Users\sunam\.jdks\jdk-17.0.20.1+1` — set `JAVA_HOME` to it before running `gradlew`.
- End each session with: what was built, jar path, how to test, next unchecked TODO.

## 6. ASSETS POLICY
- Original placeholder assets only; every placeholder listed in `ASSETS_TODO.md` (generated reproducibly by `tools/AssetGen.java`).
- Never bundle copyrighted Dragon Ball or Dragon Block C assets. `ASSETS_TODO.md` lists where reference art can be found for private use.
- For animated models prefer GeckoLib (1.20.1 build), declared properly with `mod*` configurations.

## 7. REFERENCE MATERIAL
NeoForge/Forge 1.20.1 docs and decompiled sources; Dragon Block V wiki (feature spec); Dragon Block C CurseForge/wiki (mechanics only); open-source 1.20.1 mods for patterns.

## 8. RULES OF ENGAGEMENT
Keep going autonomously; decide, log, proceed. Compiles > features. Playable > complete. Honest FEATURE_MATRIX — never mark `done` without running it in-game (GameTest server counts). Commit often, tag phases, log dead-ends. Centralize tunables in config, stat math in `StatCalculator`, damage math in `DamageCalculator`. Server-authoritative, multiplayer-safe, performance-aware.

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
- [ ] Balance pass 2 with real play: ki techniques per ki, Overdrive late, PvP between races, gear sets
- [ ] Real art (see ASSETS_TODO.md): hair, Great Ape, dragon, race skins, NPC models, item and block textures
- [ ] More content: more bosses and questlines, more planets, a space dimension (Frost Demon vacuum survival), children for partners

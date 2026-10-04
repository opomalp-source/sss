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
- [ ] Form framework (data-driven form definitions: multipliers, drain, aura color, unlock conditions)
- [ ] Saiyan line reference implementation (SSJ tiers → ascended → God/Blue-style), Great Ape + false moon
- [ ] Mastery per form (reduces drain, raises multiplier)
- [ ] Transform keybind + selection radial/UI; hair/eye/aura visual swap
- [ ] Kaio-style stackable buff with backlash
- [ ] Tag `phase-2-transformations`

## Phase 3 — Races & character creation
- [ ] Race passives + growth weighting + signature mechanic for all 8 races
- [ ] One transformation line per race (later forms may be stubbed)
- [ ] Skill library + deck/loadout + hotkey slots
- [ ] Character-creation screen on first join (race, body type, colors, hair, eyes, alignment)
- [ ] Tag `phase-3-races`

## Phase 4 — Progression & world
- [ ] Training blocks + gravity chamber
- [ ] Hyperbolic Time Chamber dimension
- [ ] Dragon Balls (7) + radar + dragon + wishes
- [ ] Scouter, capsules, gi/armor sets, training weights
- [ ] NPC enemies + boss slots + loot
- [ ] Quest system + questline
- [ ] Galactic Patrol faction
- [ ] Space/planets/dimensions + travel
- [ ] Life-sim layer (aging, needs, family, cosmetics) — config-toggleable
- [ ] Tag `phase-4-world`

## Phase 5+
- [ ] Work every FEATURE_MATRIX row to `done`, then balance + polish

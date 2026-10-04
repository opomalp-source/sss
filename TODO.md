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
- [ ] **Body/health model decision** — DBC-style "body" pool absorbing damage vs scaling vanilla max health (record in DEVLOG); wire `LivingHurtEvent`/`LivingDamageEvent`
- [ ] Ki & stamina regeneration ticks (config rates, scaled by WIL/CON), stamina drain on sprint/melee
- [ ] TP economy: earn TP from combat (damage dealt/taken) and training; MIND bonus via `StatCalculator.scaleTpGain`
- [ ] Spend TP: C2S packet `UpgradeAttributePacket` (server validates cost via `StatCalculator.tpCost`)
- [ ] Stat screen (keybind) showing attributes, derived stats, TP, upgrade buttons
- [ ] Release %: keybinds to raise/lower, scales output + drain; charging (hold key) builds ki, drains stamina, aura particle placeholder
- [ ] `combat.DamageCalculator`: attack × technique multiplier × release% vs defense/evasion; apply to melee
- [ ] Melee: STR-scaled damage, combo counter, knockback, guard/block keybind (stamina cost, damage reduction), charged heavy hit
- [ ] Ki blast entity framework (damage, speed, size, cost, cooldown, homing; all config/data driven)
- [ ] Techniques: basic ki blast, wave beam, rapid volley, disk/cutter (3–4 total)
- [ ] Flight toggle (ki/stamina drain), dash/burst movement
- [ ] DBZ HUD: body/ki/stamina bars, release %, charge indicator (replace debug overlay; keep it behind config)
- [ ] Apply DEX attack-speed/move-speed bonuses via vanilla attribute modifiers
- [ ] Sync a public subset (release %, charging, flying, form) to tracking players for visuals
- [ ] GameTests for damage formula, TP spend validation, regen
- [ ] Tag `phase-1-combat`

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

# FEATURE MATRIX

Status: `todo` · `in-progress` · `stubbed` (data/enum exists, no gameplay) · `done` (verified in-game or on the GameTest server).
Source: **C** = Dragon Block C mechanic, **V** = Dragon Block V design spec, **B** = both.

## 3.1 Stats & attributes
| Feature | Src | Status | Notes |
|---|---|---|---|
| Per-player persistent, server-authoritative data | B | done | Capability; survives death/relog; GameTests |
| Server→client sync | B | done | Verified end-to-end (RCON + client screenshot) |
| 7 base attributes (STR/DEX/CON/KIP/WIL/MND/SPI) | C | done | Values, soft/hard caps, per-point config |
| Derived: body, ki, stamina max | B | done | `StatCalculator` |
| Derived: defense, evasion, ki control, melee/ki damage | B | done | Applied in DamageCalculator; GameTests |
| Derived: attack/move speed | B | done | Vanilla attribute modifiers; GameTest |
| Derived: spirit modifier | B | done | Scales form + Overdrive mastery gain |
| Derived: ki transfer | B | stubbed | Phase 3 |
| Release % | B | done | Scales ki damage + ki cost; charging raises, key lowers; GameTests |
| Physical / mental age | V | stubbed | Stored only |
| TP pool: earning | C | done | Damage dealt, kills, charging; MIND bonus |
| TP pool: spending | C | done | Server-validated upgrade packet + stat screen |
| TP cost formula (path weights, soft cap) | C | done | GameTest `tpCostFollowsPath` |
| Class/path choice | B | stubbed | Affects TP cost only; no UI |
| Battle power / level | C | done | Shown in HUD + `/dbz stats` |

## 3.2 Ki & combat
| Feature | Src | Status | Notes |
|---|---|---|---|
| Ki pool regen | B | done | KiTicker; GameTest |
| Ki control spend efficiency | B | done | DamageCalculator.kiCost |
| Charging / power up | B | done | GameTested + seen in-client (HUD chip, stamina drain). First-person aura is subtle by design |
| Ki blast framework | B | done | GameTested + all styles seen rendering in-client |
| Techniques: ki blast, wave beam, finger beam, volley, disk, homing orb | B | done | Seen in-client; beams are true sustained beams (side view verified), homing curves |
| Techniques: grab-throw, ball-drop, explosive wave, teleport, heal, sense | B | todo | |
| Melee combo, knockback, stamina, guard | B | done | GameTests (combo, stamina, guard reduction) |
| Heavy (charged) hit | B | done | GameTest (>2x damage); HUD tag seen in-client after a real H key press |
| Aerial combat feel (air combos, knock-up) | B | todo | Flight + dash exist |
| Flight | B | done | Toggle, ki drain, auto-stop at 0 ki; GameTest |
| Dash / burst movement + afterimage | B | done | GameTest (velocity, cost, cooldown, evasion); seen in-client |
| Central DamageCalculator | B | done | combat.DamageCalculator; GameTests |
| Body pool as real health (DBC-style) | C | done | BodyHealth mirror; GameTests (hit, mirror, lethal) |

## 3.3 Transformations
| Feature | Src | Status | Notes |
|---|---|---|---|
| Form framework (multipliers, drain, aura, unlocks) | B | done | transform package; 8 GameTests |
| Saiyan line (SSJ tiers, ascended, God/Blue-style) | B | done | 7 forms seen in-client; God gated by flag until the Phase 4 ritual |
| Great Ape + false moon | C | done | Moon Orb item + full moon; tail; 3x size; GameTest + seen in-client. Placeholder look (scaled player) |
| Hair/eye/aura visual swap | B | done | Code-built placeholder hair (4 styles), pupils, form aura, lightning; seen in-client |
| Form mastery | B | done | Drain reduction + multiplier bonus; GameTest |
| Kaio-style stackable buff (Overdrive) | B | done | Levels, drain, backlash, form gating; GameTest; seen in-client |

## 3.4 Races & character creation
| Feature | Src | Status | Notes |
|---|---|---|---|
| Race selection (8 races) | B | stubbed | Enum + `/dbz race`; no effects |
| Saiyan Zenkai | B | todo | |
| Half-Saiyan | B | todo | |
| Human | B | todo | |
| Namekian regen / fusion / giant | B | todo | |
| Frost Demon restriction forms | B | todo | |
| Majin absorb / regen / magic | B | todo | |
| Android / Cyborg (no hunger, energy absorb, no fatigue) | B | todo | |
| Character-creation screen | B | todo | `created` flag stored |
| Alignment | V | stubbed | Stored, clamped −100..100 |

## 3.5 Skills & deck
| Feature | Src | Status | Notes |
|---|---|---|---|
| Technique library | B | todo | |
| Deck / loadout + hotkeys | V | todo | |
| Racial skills, status effects | B | todo | |
| Acquisition: training, masters, scrolls, quests | B | todo | |

## 3.6 Progression & training
| Feature | Src | Status | Notes |
|---|---|---|---|
| Physical / spiritual training | B | todo | |
| Gravity chamber | B | todo | |
| Hyperbolic Time Chamber | B | todo | |
| Mastery (forms & techniques) | B | todo | |
| Prestige / God Ki scaling | B | todo | |

## 3.7 World, items & endgame
| Feature | Src | Status | Notes |
|---|---|---|---|
| Senzu Bean | B | done | Full heal + body/ki/stamina refill; GameTest + seen in-game |
| Dragon Balls, radar, dragon, wishes | B | todo | |
| Scouter | B | todo | `StatCalculator.battlePower` ready |
| Capsules | V | todo | |
| Gi/armor sets + set bonuses | B | todo | |
| Training weights | B | todo | |
| NPC enemies + bosses | B | todo | |
| Galactic Patrol faction | V | todo | |
| Planets / dimensions / travel | V | todo | |
| Aging, needs, family, cosmetics | V | todo | ages stored |
| Quest system | B | todo | |

## 3.8 UI/UX
| Feature | Src | Status | Notes |
|---|---|---|---|
| Debug stat overlay | — | done | Config `hud.showDebugOverlay` |
| DBZ HUD (bars, release, status, combo, technique) | B | done | Seen in-client. Form display Phase 2 |
| Aura visuals (seen by others) | B | done | Public state sync + aura glow renderer; seen in third person |
| Stat screen | B | done | Seen in-client (opened via dev hook) |
| Skill/deck screen | V | todo | |
| Dragon Ball radar screen | B | todo | |
| Keybinds | B | done | Real presses observed in the test client (Y, R, H, F5). G charge, Z lower release, V fly, LAlt guard, R fire, Y next, K stats |
| Config for every tunable | B | in-progress | All Phase 0 numbers in config |
| Admin/debug command `/dbz` | — | done | GameTest `commandSetsStats` |

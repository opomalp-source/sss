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
| Derived: defense, evasion, ki control, spirit modifier, melee/ki damage, atk/move speed, ki transfer | B | stubbed | Computed + displayed; not yet applied to gameplay |
| Release % | B | stubbed | Stored, clamped, shown; no gameplay effect yet |
| Physical / mental age | V | stubbed | Stored only |
| TP pool | C | stubbed | Stored, admin grant; no earning/spending yet |
| TP cost formula (path weights, soft cap) | C | done | GameTest `tpCostFollowsPath` |
| Class/path choice | B | stubbed | Affects TP cost only; no UI |
| Battle power / level | C | done | Shown in HUD + `/dbz stats` |

## 3.2 Ki & combat
| Feature | Src | Status | Notes |
|---|---|---|---|
| Ki pool regen | B | todo | |
| Ki control spend efficiency | B | todo | value computed |
| Charging / power up | B | todo | |
| Ki blast framework | B | todo | |
| Techniques: finger beam, wave beam, volley, disk, grab-throw, ball-drop, explosive wave, teleport/afterimage, heal, guard, sense | B | todo | |
| Melee combo, knockback, block, heavy hit | B | todo | |
| Flight, dash, burst | B | todo | |
| Central DamageCalculator | B | todo | |

## 3.3 Transformations
| Feature | Src | Status | Notes |
|---|---|---|---|
| Form framework (multipliers, drain, aura, unlocks) | B | todo | |
| Saiyan line (SSJ tiers, ascended, God/Blue-style) | B | todo | |
| Great Ape + false moon | C | todo | |
| Hair/eye/aura visual swap | B | todo | |
| Form mastery | B | todo | |
| Kaio-style stackable buff | B | todo | |

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
| DBZ HUD (bars, form, charge, aura) | B | todo | |
| Stat screen | B | todo | |
| Skill/deck screen | V | todo | |
| Dragon Ball radar screen | B | todo | |
| Keybinds | B | todo | |
| Config for every tunable | B | in-progress | All Phase 0 numbers in config |
| Admin/debug command `/dbz` | — | done | GameTest `commandSetsStats` |

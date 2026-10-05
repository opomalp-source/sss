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
| Derived: ki transfer | B | done | SPI sets the rate; Ki Transfer technique gives ki to the player you look at. GameTest |
| Release % | B | done | Scales ki damage + ki cost; charging raises, key lowers; GameTests |
| Physical / mental age | V | done | Body ages by race (STR/DEX fade past 60); the mind ages for all, x3 meditating; a Time Chamber day is a year; wisdom: +1% TP per mental year past 20 (max +40%). GameTests |
| TP pool: earning | C | done | Damage dealt, kills, charging; MIND bonus |
| TP pool: spending | C | done | Server-validated upgrade packet + stat screen |
| TP cost formula (path weights, soft cap) | C | done | GameTest `tpCostFollowsPath` |
| Class/path choice | B | done | Chosen at creation (`/dbz path` to change): Fighter cheaper body stats, +5% melee, +10% stamina; Spiritualist cheaper ki stats, +5% ki damage, +10% ki; Hybrid +5% TP. GameTests |
| Battle power / level | C | done | Shown in HUD + `/dbz stats` |

## 3.2 Ki & combat
| Feature | Src | Status | Notes |
|---|---|---|---|
| Ki pool regen | B | done | KiTicker; GameTest |
| Ki control spend efficiency | B | done | DamageCalculator.kiCost |
| Charging / power up | B | done | GameTested + seen in-client (HUD chip, stamina drain). First-person aura is subtle by design |
| Ki blast framework | B | done | GameTested + all styles seen rendering in-client |
| Techniques: ki blast, wave beam, finger beam, volley, disk, homing orb | B | done | Seen in-client; beams are true sustained beams (side view verified), homing curves |
| Techniques: explosive wave, teleport, heal, sense | B | done | Explosive Wave, Instant Step, Ki Heal, Ki Sense |
| Techniques: grab-throw, ball-drop | B | done | Grab & Throw (hold, throw, impact damage; bosses immune), Gathering Sphere (forms overhead, hurled at the crosshair). GameTests |
| Melee combo, knockback, stamina, guard | B | done | GameTests (combo, stamina, guard reduction) |
| Heavy (charged) hit | B | done | GameTest (>2x damage); HUD tag seen in-client after a real H key press |
| Aerial combat feel (air combos, knock-up) | B | done | Air hits +15% and juggle; heavy from the ground launches, heavy from above looking down spikes. GameTest |
| Flight | B | done | Toggle, ki drain, auto-stop at 0 ki; GameTest |
| Player animation (playerAnimator) | B | done | 30 keyframed animations (stances, combo, ki casts, transform, hits, dash), layered and synced to all viewers; screenshotted in the dev client; GameTest for cast mapping |
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
| Race selection (8 races) | B | done | Creation screen + `/dbz race`; traits, start bonuses, TP weights |
| Saiyan Zenkai | B | done | Arms below 15% body, fires on recovery, cooldown; GameTest |
| Half-Saiyan | B | done | Smaller Zenkai, +5% TP, tail, Saiyan line + Ultimate (flag) |
| Human | B | done | +15% TP, -15% ki cost, Full Power/Buffed/Potential Unleashed, Solar Flare |
| Namekian regen / giant | B | done | 3x regen, Giant/Super/Orange forms, Regenerate; GameTest |
| Namekian fusion | B | done | Fuse with a beaten Namekian Warrior (spawn on Namek) or a consenting Namekian player (25% of their attributes + techniques; they start over); max 3. GameTests |
| Frost Demon restriction forms | B | done | Second/Third/Final/Golden, breathless, horns, Supernova Orb |
| Majin regen / magic | B | done | 5x regen, kill heal, Candy Beam; GameTests |
| Majin absorption of other fighters | B | done | Absorb a beaten non-boss: +10% STR/DEX/KI per stack (max 3, 10 min), steals a technique from players; respects PvP. GameTests |
| Android / Cyborg (no hunger, energy absorb, no fatigue) | B | done | Passives + Energy Absorb / Arm Cannon; GameTests |
| Character-creation screen | B | done | Seen in-client with live preview |
| Alignment | V | done | Deeds shift it (monsters +, innocents/masters/good players -); Good 30+: +10% ki regen, Patrol work; Evil -30-: +10% damage; shown on the stat screen. GameTests |

## 3.5 Skills & deck
| Feature | Src | Status | Notes |
|---|---|---|---|
| Technique library | B | done | 18 techniques; GameTests |
| Deck / loadout + hotkeys | V | done | Techniques screen seen in-client; R/Y use the deck |
| Racial skills | B | done | One per race |
| Status effects (custom) | B | done | Stunned (Paralysis Wave, grabs) and Ki Sealed (Seal Orb, Tyrant Lord); HUD chips; GameTests |
| Acquisition: TP + scrolls | B | done | Masters and quests in Phase 4 |

## 3.6 Progression & training
| Feature | Src | Status | Notes |
|---|---|---|---|
| Physical / spiritual training | B | done | Punching Bag, moving under gravity, meditation (sneak still 3 s), ki charging; training multiplier. GameTests |
| Gravity chamber | B | done | Block 1-100g, radius 6; tolerance 1 + (STR+CON) x 0.02; slowdown + body strain above it; x(1 + 0.1/g) training. GameTests + seen in-client |
| Hyperbolic Time Chamber | B | done | Door block, white void dimension, 10g, x4 training, 1-day stay, sends you back. GameTests + seen in-client |
| Mastery (forms & techniques) | B | done | Forms (Phase 2); techniques: +0.5 x SPI modifier per use, at 100: +25% damage, -30% ki, -25% cooldown; shown in the Techniques screen. GameTest |
| Prestige / God Ki scaling | B | done | Prestige at level 2000 (stat screen): attributes + TP reset, +25% TP and +5% power each. God ki (ritual or wish): +25% dealt / -25% taken vs ordinary ki, invisible to scouters and Ki Sense. GameTests |

## 3.7 World, items & endgame
| Feature | Src | Status | Notes |
|---|---|---|---|
| Senzu Bean | B | done | Full heal + body/ki/stamina refill; GameTest + seen in-game |
| Dragon Balls, radar, dragon, wishes | B | done | 7 balls scattered near spawn (SavedData), radar HUD, Eternal Dragon, 8 wishes; stone for 2 days after a wish. GameTests + seen in-client |
| Scouter | B | done | Helmet slot; reads power level/name/distance; shatters above 1,000,000. Seen in-client |
| Capsules | V | done | 27-slot storage in an item, no nesting. GameTest |
| Gi/armor sets + set bonuses | B | done | Turtle gi, Demon gi, Battle Armor: STR/DEX/KI multipliers when the full set (chest, legs, boots) is worn. GameTest |
| Training weights | B | done | Slower movement, x training while worn. GameTest |
| NPC enemies + bosses | B | done | Sproutling, Ki Soldier, Android Unit (scale to the nearest player, ki attacks); Tyrant Lord + Rampage Brute bosses with summon items and loot. GameTests |
| Galactic Patrol faction | V | done | Patrol Officer, 4 repeatable bounties, 5 ranks from reputation (0/50/150/400/1000), good alignment required. GameTests + seen in-client |
| Planets / dimensions / travel | V | done | Namek (1g), Northern Planet (10g), Earth; Space Pod screen, 1-min recharge, lands on dry ground. GameTests + seen in-client |
| Aging, needs, family, cosmetics | V | done | Aging, titles; thirst (drinks, swimming; androids exempt) and temperature (desert sun, Nether, snow; fire/chestplate warm you; Frost Demons exempt) slow stamina recovery; partners via Promise Ring (+10% TP near each other); scars and tattoos (Life screen). Config toggles. GameTests |
| Quest system | B | done | 7-quest master line + bounties; objectives (level, learn, kill, collect, form, flag, visit), rewards, quest screen. GameTests + seen in-client |

## 3.8 UI/UX
| Feature | Src | Status | Notes |
|---|---|---|---|
| Debug stat overlay | — | done | Config `hud.showDebugOverlay` |
| DBZ HUD (bars, release, status, combo, technique) | B | done | Seen in-client. Form display Phase 2 |
| Aura visuals (seen by others) | B | done | Layered flame aura (outer, white-hot core, licks), halo, ground glow, lightning bolts, calm god-ki style, first-person screen-edge flames; screenshotted |
| Impact feel (V2-B) | B | done | Hit flashes, shockwave rings, debris, landing craters, trauma screen shake, FOV kick, hitstop, transformation burst + flash, afterimages; ImpactPacket GameTest; screenshotted |
| Stat screen | B | done | Seen in-client (opened via dev hook) |
| Skill/deck screen | V | done | Techniques screen |
| Dragon Ball radar screen | B | done | HUD radar while holding the Dragon Radar; seen in-client |
| Quest / planet / wish / title UI | B | done | Quest screen (NPC), planet screen (Space Pod), wish screen (dragon), Title button on the stat screen |
| Keybinds | B | done | Real presses observed in the test client (Y, R, H, F5). G charge, Z lower release, V fly, LAlt guard, R fire, Y next, K stats |
| Config for every tunable | B | done | `serverconfig/dbzenith-server.toml`: combat, techniques, forms, races, training, Dragon Balls, gear, enemies, life sim (Phase 5 audit moved the last constants: pod recharge, dragon wait, false moon, scouter range, enemy scaling, boss enrage) |
| Admin/debug command `/dbz` | — | done | GameTest `commandSetsStats` |

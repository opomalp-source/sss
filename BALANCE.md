# Balance

The defaults are tuned with a model, not by feel: the `BalanceReport` GameTest builds reference characters
(points spread evenly over the seven attributes, 100% release, base form, no gear), measures them with the real
formulas against the real enemies, and asserts the targets below (`defaultsMeetTheBalanceTargets`). A change that
moves the balance fails the build. Every run writes the full tables to `run-gametest/balance-report.md`.

## Targets
| What | Target | Now |
|---|---|---|
| Steady fighting to level 150 (first transformations) | 0.7-1.6 h | 1.1 h |
| ... to level 500 | 3-6 h | 4.4 h |
| ... to level 1000 (Divine Ritual, god ki) | 7-14 h | 10.3 h |
| ... to level 2000 (prestige) | 17-32 h | 23.7 h |
| Punches to beat an equal-level Ki Soldier | 8-25 at every level | 15-17 |
| Soldier hits to beat you | 7-25 at every level | 9-14 |
| Boss (Tyrant Lord) hits to beat you | about 5-8 | 5.5-8.2 |
| Gravity x weights x Time Chamber | at most x4 | x4 cap |
| Saiyan Zenkai, per cooldown | at most 60% of fighting in that time | 5-46% |
| Best form by level 2000, every race | at least x4.5 | x4.8-x6.5 |

"Steady fighting" = equal-level soldiers, two punches a second, eight seconds to find the next one, no training
multipliers. Training (punching bag, gravity, Time Chamber) and quests make real play faster than this.

## What the pass changed (balance revision 2)
- **Enemies keep pace**: level = full power / 700 (was a square root, so foes fell hopelessly behind). Levels add
  *toughness* (incoming damage divided by 1 + 0.5 x (level - 1)) instead of max health, because vanilla caps health at 1024.
  Damage +55% per level (was 35%). Max level 200 (was 60). Tyrant Lord / Rampage Brute hit for 6 / 9 (were 10 / 14), Brute 500 HP.
- **TP rewards grow with the square root** of the damage dealt and of the victim's effective health (they grew linearly
  and outran costs ~10x). Keys renamed: `tpPerHit` 0.04, `tpPerKill` 0.5.
- **Attribute costs** rise faster: `tpCostPerPoint` 0.45 (was 0.25).
- **Training**: punching bag 0.08 TP a punch (was 0.5), charging every 20 s (was 5 s), meditation 0.08 TP/s (was 0.2),
  moving under gravity 0.01 TP/s per g (was 0.05). Training multipliers stack to at most x4 (they reached x19).
- **Time Chamber**: one day's rest before you may enter again (you could walk straight back in). Cooldowns now survive relogs.
- **Zenkai**: + 0.25 x sqrt(value) to STR/DEX/CON/KI (Half-Saiyan 0.15) instead of +3%, every 30 minutes (was 10), and only
  a living foe can arm it (gravity strain used to arm it for free).
- **Namekian Warrior fusion**: +5% of your own STR/CON/KI/SPI (at least 3) instead of 3 points per warrior level (worth ~360 levels late).
- **Quests**: master line 100 / 150 / 250 / 600 / 1200 / 2000 / 4000 TP; Patrol bounties 150 / 250 / 1500 / 1500 (repeatable ones paid ~5x the fighting they asked for).
- **Late forms** so no race tops out a thousand levels early: Transcendent (Human x5), Dragon Clan Awakening (Namekian x5),
  Primordial Majin (x5.4/4.8/4.8), Infinite Core (Android x4.8), Omega Frame (Cyborg x4.8), all at level 1500.
  Potential Unleashed x3.4 (was x3).

## Knobs
- `training_points.tpGainMultiplier` (default 1.0): the whole curve at once. 2.0 = twice as fast, 0.5 = half.
- `enemies.powerPerLevel`, `healthPerLevel`, `damagePerLevel`: how hard the world fights back.
- `training.trainingMultiplierCap`, `chamberCooldownTicks`: how much training shortcuts are worth.

## Existing worlds
Server config lives in each world (`serverconfig/dbzenith-server.toml`), so new defaults never reached old worlds.
The file now has `balanceVersion`; on load, a file from an older revision gets the values this pass changed reset to
the new defaults once (`config.BalanceMigration`), and the log says so. Everything else you set stays.

## Pass 2 (balance revision 3)
The `BalanceReport2` GameTest models four more areas, writes `run-gametest/balance-report-2.md` and asserts these
targets (`pass2TargetsHold`):

| What | Target | Now |
|---|---|---|
| Damage per ki, every damaging technique (level 1000) | 0.6-1.6x the median | 5.2-8.2 (median 6.2) |
| A technique spammed vs punching | at most 4x | at most x1.18 (Ki Blast) |
| Overdrive: extra output of one full burst, any level | 30-75 multiplier-seconds | 50 (125 mastered) |
| Best form of each race vs the median race, levels 100-2000 | 0.75-1.33x | 0.80-1.28x |
| Netherite vs no armour, survival against fighters | at most 2x | x1.24 |
| Battle Armor vs no armour, survival against fighters | at least 1.15x | x1.24 (plus +8% damage) |

Changes:
- **Techniques**: Homing Orb x1.2 (was 0.8), Cutter Disk x1.8 (1.5), Explosive Wave x2.2 (1.5), Gathering Sphere x12 (6),
  Supernova Orb x5 (4); Arm Cannon costs 40 ki (30) with a 1 s cooldown (0.75 s). Volleys are modelled at 60% of shots landing;
  Seal Orb and Candy Beam count as utility.
- **Overdrive** drains stamina twice as fast (`overdriveStaminaDrainPercent` 2.0): a burst is now worth half a boss fight, not
  a whole one. **Summoned bosses** match the strongest form you can take (`StatCalculator.peakPower`), so transforming after the
  summons is no shortcut. Ordinary enemies still match your current power.
- **Forms**: Buffed x2.8/1.8/2.4 (was 2.6/1.5/1.8); Third Form unlocks at 250 (200); Final Form x3.2 with 1%/s drain (x3.5, 0.5%/s);
  Golden Form x5.0 (5.5); Super Saiyan Blue x6.0 (6.5); Super Namekian, Upgrade Mk III, Full Conversion and Super Majin unlock at 300
  (350-400), so every race has a second tier by then.
- **Gear**: fighters punch through vanilla armour (`enemies.fighterArmorEffect` 0.25: armour counts a quarter against them;
  it still counts fully against vanilla mobs). Full gi sets take damage off everything: Turtle and Demon gi 5%, Battle Armor 15%
  (shown in the tooltip). v0.32.0 adds two sets: Namekian garb (ki +12%, dexterity +4%, 8% off) for ki fighters, and Majin
  garb (strength +12%, dexterity +3%, 6% off) for brawlers, both between the gis and Battle Armor.
  v0.36.0 adds the zip hoodie set (dexterity +8%, a little of the rest, 3% off: casual wear) and Frost Demon armour (ki +12%,
  strength +4%, 14% off: the Frost Demon counterpart to Battle Armor).

## Still for real play
Feel, not numbers: how fights read with knockback, flight and dashes; whether racial passives (Namekian regeneration,
Android ki absorption, Majin kill-heal) tip close duels; whether the pacing targets match how people actually play.

## Guard and the Ki Creator (V2-E)
- **Guard meter** (0-100). Each blocked hit costs 4, plus enough that blocking 40% of your max body in one go empties the meter (`guardBreakBodyFraction`). Ki costs ×1.5. An empty meter breaks the guard: 1.5 s stun, then 3 s before you can guard again. The meter refills 15/s, starting 1 s after the last blocked hit.
- **Parry**: raising the guard at most 5 ticks before a blow lands (`parryWindowTicks`) cancels it, staggers the attacker for 1.25 s and returns 10 meter. One parry per raise. **Deflect**: 8 ticks before a ki blast sends it back where you look, and it becomes yours.
- **Ki Creator**: costs are not chosen by the player. Ki cost and cooldown come from the median of the built-in damaging techniques (ki per point of damage, cooldown per point of damage), times a tax per modifier (×1.05-1.2). A design is therefore never more efficient than the typical built-in technique. `CombatDepth2Tests.kiCreatorDesignsStayInBalance` builds every legal design at level 1000 and asserts the pass-2 targets: 0.6-1.6× the median damage per ki, and at most 4× punching when spammed. Creating costs 100 + 80·power² + 150 per modifier TP. Three slots, plus one per 300 levels (max 8), from level 10.
- **Beam struggles**: the clash moves 0.014 × (power difference / total) of the gap per tick. Power is the beam's damage per pulse × (1 + mash); a mash adds 0.5 (max 3) for 1% max ki and decays 8% a tick. The winner hits the loser for 6× both beams' per-pulse damage; after 10 s it all explodes where the beams meet.

## Races v2 and forms v2 (CX-2 / CX-3)
- **DBV multipliers are mapped**, not copied: `FormScale.fromDbv(m) = 1 + 1.1 ln m` (2x 1.76, 6x 2.97, 16x 4.05, 22x 4.40, 32x 4.81, 56x 5.43, 76x 5.76). The existing lines already sat on this curve.
- **Ranged forms** (DBV "2x to 6x") grow from the bottom to the top of their range with mastery, instead of taking the generic mastery bonus. The balance model counts them at the middle of the range.
- **Rising forms** (Legendary lines) add up to 20% (Legendary Primal 10%) of their multiplier over 3 minutes of continuous combat (a blow dealt or taken in the last 10 seconds).
- New races have a 1.25x starter form at level 100, so the level-100 race median stays where the pass-2 targets put it.
- New tier-4 forms (Crimson Sovereign, Ultimate Perfect, Tuffle King, Apex Mutation) unlock at 1500, alongside the other races' late tiers.
- `Races2Tests.everyVariantStaysInBalance`: at levels 500, 1000, 1500 and 2000, every variant's best form is within 0.65-1.45x the median variant, and at least x4.5 by level 2000.

## Transform time and God Ki (v0.14.2)
- **Power-up time:** `(transformTimeBase + transformTimePerTier * tier) * (1 - 0.6 * mastery / 75)` ticks, at least 10, and instant at 75% mastery (`instantTransformMastery`). With the defaults (24 + 14 per tier), an unmastered tier 1 takes 38 ticks (1.9 s), tier 3 takes 66 ticks (3.3 s), and tier 5 takes 94 ticks (4.7 s). Creative mode, tests and scripted scenes are instant.
- **Interruption:** a hit for at least 3% of max body (`transformInterruptDamage`) breaks a power-up, and the ki already spent is lost. While powering up, you are slowed (Slowness IV).
- **God Ki:** awakening it (the ritual quest or a wish) is level 1. Experience for level n is `300 * (n - 1)^2` seconds: 5 minutes for level 2, 20 for 3, 80 for 5, and 6.75 hours for 10. You gain 1 a second in god forms (`godKiXpPerSecond`), and half that while meditating with god ki.
- **Per level past the first:** god forms gain +2% power (+18% at level 10) and drain 5% less (-45% at level 10).
- **Between god ki users:** the god ki edge becomes `godKiEdge * 0.08` per level of difference, capped at 5 levels (±10% with the default edge).
- **Required levels:** god forms ask for level 1, those unlocked at 1500 or later for level 2, those grown out of a god form (Blue) for level 3, and god forms unlocked at 1800 or later for level 5.

## Racial skills (CX-4, v0.15.0)
- **Passives:** always-on modifiers stay at +10% or less (most -8% to -15% taken, or +8% to +10% to one stat). Conditional ones stay at +25% or less, and the conditions cost something: below 30% body, at night, while transformed, against a stronger foe, building over 3 minutes of combat.
- **Stacking:** the most stacked case, a Legendary in combat at full ramp, is +8% (Warrior Race) x +15% (Escalating Power) = x1.24 power. That sits beside the forms' x5 to x60, so form balance (Races2Tests) holds.
- **Actives:** the buffs are short, with cooldowns about 6 to 10 times their length.
  - Saiyan's Resolve: -30% taken for 15 s every 90 s.
  - Shattering the Limit: +25% for 12 s, then -15% for 24 s, every 150 s.
  - Mindless Gambit: +30% dealt and +30% taken.
  - Sacred Barrier: -60% for 6 s every 60 s.
- **Instant actives:** their damage uses the ki damage formula, at x0.3 to x0.8 of a ki blast.
- **Cheating death:** Second Wind (Human) restores 35% body and stamina every 10 minutes. Death Regeneration (Majin, level 400) returns you at 20% body every 10 minutes. Reincarnation (Namekian, level 700) returns you at 30% body every 30 minutes.

## Universal skills (CX-5, v0.16.0)
- **Learning:** TP for level n is the base TP x n². Character level gates each skill level (`unlockLevelScale` applies).
  - Ki Sense: 2k TP; levels at 50, 250 and 600.
  - Kaioken: 4k TP; levels at 100, 400, 800 and 1200.
  - Rising Charge: 8k at 150.
  - Spirit Shock: 10k at 250.
  - Echo Strike: 12k; levels at 200, 500 and 900.
  - Ki Barrier: 15k at 300.
  - Desperate Gambit: 20k at 400.
  - Limit Break: 30k at 500.
  - Instant Transmission: 60k at 900.
- **Kaioken:** +10% power a stage up to x2 at stage 10, then +5% a stage to x2.5 at stage 20. Levels cap the stage at 2, 4, 10 and 20.
  - Burn: 0.4% max body per second per stage, times the strain 1 + min(2, (SPI - WIL) / WIL).
  - It gives out below 10% body and cannot start below 15%. It only works in base form or a calm god form, which makes it a base-form and Blue amplifier, far below the forms on its own.
- **Limit Break:** +25% power for 60 s, then -30% for 120 s, every 300 s. Half-Saiyans get +50%, the New Generation +100%.
- **Ki Barrier:** 8 s. Ki damage x0.3 x0.8 (the general -20%), so x0.24; blows x0.8. Every 40 s, for 15% ki.
- **Desperate Gambit:** only below 25% body. Costs half your ki and all your stamina. +40% damage dealt and no death for 10 s, then no ki regeneration for 20 s. Every 180 s.
- **Echo Strike:** a counter of one melee hit (x1, x1.3 or x1.6) after an afterimage dodge, every 6 s.
- **Rising Charge:** a technique fired within 2 s of releasing a charge held at least 2 s gets +10% per second held, up to +50%.
- **Spirit Shock:** a 1.5 s stun in 4.5 blocks that also breaks guards; 10% ki, every 25 s.
- **Instant Transmission:** 25% ki, every 30 s.

## Combat v3 (CX-6, v0.17.0)
Constants are in `CombatMoves`.
- **Z-hit:** a blow within 10 ticks of a dash. x1.5 damage and a 20-tick stun (the longest), once per dash.
- **Directional heavies:** they keep the heavy's charge multiplier.
  - Back (uppercut): a knock-up 1.6x as high.
  - Forward (rush): driven 2.6 blocks/tick along the ground.
  - Sideways (hook): thrown sideways plus a 12-tick stun.
- **Sweep:** guard + heavy, for the heavy's stamina cost. Melee x1.2 in a 3-block frontal arc, unblockable (it lowers the guard). Floors the target for 30 ticks.
- **Chase:** within 30 ticks of a heavy launch; costs a dash. At most 3 per combo (a new target, or 60 ticks without a launch, resets it). The next blow within 20 ticks gets x1.15.
- **Chase counter:** raising the guard within 8 ticks of being chased. The chaser is stunned for 15 ticks.
- **Revenge Counter:** a dash while stunned. Costs 10% of max stamina; every 4 s. 12 ticks of hyper armour (no knockback or stun) and a Z-hit on the last attacker within 10 blocks.
- **Breaker Wave:** Shift + dash while hit within the last second, or while stunned. 2 charges, each back after 30 s. Ki damage x0.3 in 5 blocks, a strong push, and clears your stun and downed state.
- **Snap recovery:** a dash within 30 ticks of being launched, for 15 stamina. Stops your momentum, with 8 ticks of evasion.
- **Ground slide:** a dash while downed. A roll at 0.7x dash strength.
- **Spot dodge:** guard + dash, for half a dash's stamina. 10 ticks of evasion.
- **Side step:** guard + dash with A or D. A 0.55x dash sideways, 8 ticks of evasion.
- **Clash:** two players hitting each other within 3 ticks. The second blow is cancelled and both are pushed apart.
- **Downed:** 30 ticks. Half damage, no knockback, no launches.

## Ki Creator v2 (CX-7, v0.18.0)
- **Shapes:**
  - Laser: x(1.4 + 0.4p), a 0.2-wide beam, very fast.
  - Wave: (7+p) shots of x(0.18 + 0.05p) in a 40° fan, 14-tick range.
  - Nova: a self burst of x(1.2 + 0.5p), radius 4 + 0.6p.
  - Rain: (6+2p) shots of x(0.3 + 0.08p) falling on the spot you aim at.
  - Volleys are costed at 60% of shots landing, as before.
- **Methods:**
  - Charged: x1.5 damage at a tax of 0.9. A windup over the head, or 20 ticks of gathering for beams.
  - Placed: a mine with a tax of 0.95. It waits up to 200 ticks and goes off within 2 blocks, hitting everything in 2.5 blocks (minimum explosion 1.2). Out of reach, it is placed 10 blocks ahead.
- **Origins** change size and speed. Damage per ki does not move, because cost follows damage.
  - Mouth: x1.08 damage.
  - Eyes: 0.6 size, 1.3 speed, x0.9 damage.
  - Finger: 0.6 size, 1.35 speed, +1 pierce, x0.95 damage.
- **Ki types:**
  - Pure: x1.05 damage, tax 1.05.
  - Burning (3 s of fire) and Corrosive (weakness and poison): tax 1.10.
  - Freezing (slowness II and frost) and Shock (30% chance of an 8-tick stun): tax 1.12.
  - Draining (heals 15% of the damage): tax 1.15.
  - Divine: x1.10 damage, tax 1.10; needs god ki.
- **Modifier taxes:**
  - Split 1.15 (three shots of x0.4 on impact).
  - Bounce 1.08 (two ricochets).
  - Guided 1.12 (steers to the crosshair).
  - Chain 1.20 (two jumps at half damage, once per attack).
  - Guard Break 1.12 (-35 guard meter).
  - Stun 1.18 (10 ticks).
  - Knockback 1.06.
  - Rapid: x0.75 damage, x0.6 cooldown.
  - Efficient: x0.85 damage, x0.75 cost.
- **Totals:** the combined tax is capped at 1.55. A third modifier slot opens at level 800. TP cost: +200 for a non-pure type, +150 for a non-fired method.
- **Checked:** `kiCreatorDesignsStayInBalance` covers every shape, power and modifier combination (up to three modifiers at p3), and every shape × method × origin × type at p1/p5 with risky modifier sets. All stay within 0.6-1.6x the median damage per ki of the built-in techniques, and below 4x punching DPS.

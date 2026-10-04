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

## Not modelled yet
Ki-technique damage per ki, Overdrive at high levels, PvP between races, and gear set bonuses are covered by unit
tests but not by pacing targets. They are the next things to watch in real play.

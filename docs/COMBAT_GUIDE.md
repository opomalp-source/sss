# Combat v4: the guide

How the combat system fits together, how to add a move, how to make a transformation change the fight, and how to
balance characters. The player-facing summary of every mechanic is in [COMBAT.md](COMBAT.md); the art, sound and
animation you can provide are in [COMBAT_ASSETS.md](COMBAT_ASSETS.md).

## 1. The map

| Part | Where | What it does |
|---|---|---|
| Input | `client/ClientInput`, `network/*Packet` | Keys become small packets (melee press, Ki Blast down and up, dash, guard, lock-on) |
| Input guard | `network/InputGuard` | Rate limits and sanity checks on every combat packet |
| Moves (data) | `data/dbzenith/combat/moves/*.json`, `combat/engine/Move`, `Moves` | What each blow is: timing, damage, hitbox, launch, animation |
| State machine | `combat/engine/Fighter`, `CombatEngine` | One per fighter (players and NPCs): idle, attacking, guarding, stunned, launched, knocked down; moves, buffering, cancels, combos, hitstop |
| Hit detection | `combat/engine/Hitbox`, `CombatEngine.detect`, `LagComp` | Sphere, cone or box hitboxes, tested against where the victim is and where a lagging attacker saw them |
| Damage | `combat/CombatEvents`, `combat/DamageCalculator`, `combat/PvpBalance` | Stats to damage, defense, guard, evasion, and the PvP balance curve |
| Defense | `combat/GuardRules`, `combat/engine/Evasion` | Guard (front only), perfect guard, vanish, counter, Burst, super dash, chase, rolls |
| Ki | `combat/engine/KiCombat`, `SpecialMeter`, `skill/TechniqueHandler` | Quick and charged blasts, technique tiers, the special meter |
| PvP rules | `combat/PvpRules`, `PvpZones` | PvP mode, safe zones, the duel's arena rules |
| Feedback | `client/fx/ImpactFx`, `DamagePopups`, `CameraFx`, `Cinematics`, `client/ui/*` | Impacts, hitstop, numbers, callouts, HUD, camera |
| Animation | `client/anim/Anims`, `AnimController`, `NpcActions` | The clips and who plays them (players and NPCs) |
| Modes | `duel/*`, `npc/TrainingDummy`, `combat/CombatLog` | Duels, the dummy, the log |

The server decides everything (hits, damage, costs, cooldowns). The client only shows things early: predicted move
starts, dashes and quick blasts, which the server confirms or takes back.

## 2. Adding a move

A move is one JSON file. Any data pack can add or replace them; `/reload` picks up changes without a restart.

**1. Write the file.** `data/<your namespace>/combat/moves/<id>.json`, for example a forward heavy that only a Super
Saiyan can throw:

```json
{
  "button": "heavy",
  "direction": "forward",
  "where": "ground",
  "after": ["start", "light_2", "light_3"],
  "priority": 5,
  "startup": 7, "active": 3, "recovery": 14, "cancel": 10,
  "damage": 1.6, "guard_damage": 1.4, "ki_cost": 0, "stamina_cost": 6,
  "hitstun": 18, "knockback": 0.9, "lift": 0.15,
  "launch": "away", "launch_power": 1.3,
  "hitbox": { "shape": "box", "range": 3.2, "radius": 0.9, "height": 2.0 },
  "lunge": 1.5,
  "anim": "RUSH",
  "impact": "heavy",
  "hitstop": 5,
  "forms": ["super_saiyan", "super_saiyan_2"],
  "form_damage": { "super_saiyan_2": 1.2 }
}
```

Every field and its default is in the table in [COMBAT.md](COMBAT.md#move-files). The ones that shape a move most:

- **When it can be thrown:** `button`, `direction`, `where`, `after`.
  - `after` lists the moves it can follow; `"start"` lets it open a chain.
  - An exact direction beats `any`; `priority` breaks ties.
- **Timing:** `startup`, `active`, `recovery` (ticks, 20 a second), and `cancel` (from when a landed blow may chain on).
- **What it does:** `damage` (times the fighter's melee damage), `hitstun`, `knockback`, `launch` (`away`, `up`, `spike`, `knockdown`).
- **Reach:** `hitbox`. A sphere sits ahead, a cone fans out from the eyes, a box runs along the facing.
- **Looks:**
  - `anim` names a clip (see [COMBAT_ASSETS.md](COMBAT_ASSETS.md#4-animations)). A file in `assets/dbzenith/player_animation/<name>.json` can supply a brand new one.
  - `impact` sets the hit effect and sound (`punch`, `heavy`, `spike`); `hitstop` the freeze.

**2. Give it a name.** Add `"move.dbzenith.<id>": "Golden Rush"` to a language file, or the move list shows the id made readable.

**3. Try it.**
- `/reload`, then `/dbz strike <you> heavy forward` (or the keys).
- Turn on **Settings → Combat → Hitboxes & frame data** (`/dbzhitbox`) to see the hitbox and the frame bar, then hit a **Training Dummy** (it shows damage per string).
- The move appears in **Settings → Controls → Controls & Move List** by itself.

**4. Check it is fair.** Compare its damage per tick with the moves it sits beside:
- light chain 0.8-1.35 over 9-18 ticks;
- heavies 1.3-1.6 over 16-24.

A move that launches or is unblockable should cost more time or stamina.

## 3. Making a transformation change the fight

Forms already change every fight through their stat multipliers: more damage, more health, a higher battle power. The
PvP curve then decides how much that gap counts (section 4). Beyond that there are three levels, from data to code.

**Data: moves of a form.** In a move file:
- `forms` limits it to those forms;
- `form_damage` gives a damage multiplier per form.

With these, a form can get its own finishers, or hit harder with the same ones. The form ids are those of `/dbz form`
(`super_saiyan`, `super_saiyan_blue`, `ultra_instinct`...). Clients predict with the same rule, so a form move starts at once too.

**Data: techniques of a form.** A technique's tier, meter cost and cinematic come from
`data/dbzenith/combat/techniques/<technique>.json` (`tier`, `meter`, `cinematic`). Which techniques a race or form may
learn is in the technique library.

**Code: a special interaction.** When a form should bend a rule rather than a number, hook it where that rule is decided:

| To change | Hook | Example already there |
|---|---|---|
| Whether a blow lands at all | `CombatEvents.onHurt`, the `afterimage` check | `UltraInstinct.evade`: the body dodges on its own |
| Damage taken or dealt | `CombatEvents.onHurt` (`raw *=` before `againstPlayer`) | `godKiFactor`: god ki against mortals |
| A dodge or counter | `Evasion.tryVanish`, `Evasion.counter` | |
| Ki costs | `DamageCalculator.kiCost`, `KiCombat.fire` | racial ki-cost reduction |
| Being knocked out of a transformation | `FormHandler.interrupt` (from `CombatEvents`) | `transformInterruptDamage` |
| Meter gains | `SpecialMeter.gain` callers | |

Example: make a vanish free in Super Saiyan Blue. In `Evasion.tryVanish`, where the ki cost is worked out, multiply the
cost by `0` when `d.getFormId().equals("super_saiyan_blue")`. Then add a GameTest beside `EvasionTests` that sets the
form (`d.setFormId`) and checks the ki is unchanged after a vanish.

## 4. Balancing characters

### The model

Between players, every blow goes through the balance curve (`combat/PvpBalance`):

1. **Power ratio `x`:** how hard the attacker's jab-sized blow lands on the defender, against the defender's own on themselves. Melee for blows, ki for ki. It is 1 between equals, and counts forms, release, stats and gear.
2. **Fight length:** between equals a jab takes 1/`equalJabsToKo` of the foe's health, at every level. Everything about the blow (the move, heavies, Z-hits, criticals, combo scaling, guard) keeps its weight on top.
3. **The curve:** `x` counts as `clamp(x ^ powerExponent, 1 / dominanceCap, dominanceCap)`.
4. **The caps:** one blow takes at most `maxHitFraction` of the foe's health. Past `comboCapFraction` in one combo, blows land at a quarter.

Without the curve, a fighter four times the level wins in a fortieth to an eightieth of the blows. With the defaults:

| Levels (reference characters) | Jabs for A to KO B | Jabs for B to KO A |
|---|---|---|
| 50 vs 50, 200 vs 200, 800 vs 800 | 40 | 40 |
| 50 vs 200 | 85 | 25 |
| 200 vs 800 | 77 | 15 |
| 500 vs 2000 | 73 | 11 |

The stronger clearly wins; the weaker is not helpless. These numbers come from the `BalanceReport` GameTest, which
writes `run-gametest/balance-report.md` each run. The build fails if they leave the targets: equals 30-50 jabs; the
stronger always fewer; the gap at most the cap squared; never under three blows.

### The knobs

| Feel | Setting (server config) | Default | Raise it to... |
|---|---|---|---|
| Fight length | `[pvp] equalJabsToKo` | 40 | make fights longer |
| How much power counts | `[pvp] powerExponent` | 0.5 | make power count more (1 = fully, 0 = not at all) |
| The most power can count | `[pvp] dominanceCap` | 4 | let a big gap count for more |
| One-shot guard | `[pvp] maxHitFraction` | 0.35 | allow bigger single blows |
| Touch-of-death guard | `[pvp] comboCapFraction` | 0.6 | let one combo take more |
| Combo decay | `[combat_engine] comboDamageDecay`, `comboMinDamage`, `hitstunDecay`, `hitstunMin` | 7%, 35%, 5%, 40% | shorten combos |
| Juggles | `[combat_engine] juggleLimit` | 8 | allow longer air combos |
| Hitstop | `[combat_engine] hitstopScale` | 1 | heavier-feeling blows (0 = off) |
| Criticals | `[combat_engine] critBehindBonus`, `critPunishBonus` | 1.2, 1.15 | reward flanking and punishing |
| Defense | `[combat_engine] vanishKiPercent`, `vanishCooldownTicks`, `burstKiPercent`, `burstRechargeTicks`, `guardArcDegrees`; `[combat] parryWindowTicks` | | make dodging dearer, or perfect guards easier |
| Special meter | `[combat_engine] specialPer*`, `specialBars` | | fill it faster, or allow more bars |
| Supers and ultimates | `combat/techniques/*.json` (`meter`) | 100 / 300 | make one dearer or cheaper |
| Ki blasts | `combat/ki/rapid_blast.json`, `charged_blast.json` | | cost, damage, size, stun, homing |
| Moves | `combat/moves/*.json` | | per move |
| Netcode | `[network] lagCompensationTicks`, `inputRateScale` | 6, 1 | forgive more lag, or allow more inputs |
| Duels | `[duel] duelArenaRadius`, `duelTimeLimit`, `duelRingOutSeconds` | 24, 180, 5 | |

### Recipes

- **"Fights end too fast" or "drag on":** `equalJabsToKo` first, then the move damages.
- **"Stronger players stomp everyone":** lower `powerExponent` (0.4) or `dominanceCap` (3).
- **"Leveling doesn't matter in PvP":** raise `powerExponent` (0.6-0.7).
- **"Combos last forever":** raise `comboDamageDecay` or `hitstunDecay`, lower `juggleLimit`, or lower `comboCapFraction`.
- **"Nobody guards":** widen `parryWindowTicks` a tick, or make heavies' `guard_damage` lower.
- **"Vanish spam":** raise `vanishKiPercent` or `vanishCooldownTicks`.
- **"One race is too strong in PvP":** check its plain jab in the report first. The curve already evens out raw power, so the cause is usually a passive (`RacePassives`, `RacialSkills`) that adds damage after the curve's yardstick. Scale that.

### Workflow

1. Change the config or data, then `/reload` (data) or restart (config).
2. Run the GameTests (`./gradlew runGameTestServer`) and read `run-gametest/balance-report.md`.
3. In game:
   - spar with the Training Dummy (perfect guard, dodge and attack modes) with hitboxes on;
   - duel a friend (`/duel <name> 3`) and read the results screen;
   - `/dbz netstats` shows lag compensation at work.

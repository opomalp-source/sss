# Combat v4 (CX-19)

The PvP combat system, built phase by phase. This page grows with each phase; the final guide (adding moves,
transformation interactions, balancing characters) is completed in the balance phase.

## PvP mode (phase 1)

- **Toggling:** PvP mode is off on joining and after death. Toggle it with **P** or `/pvp [on|off]`.
- **Fights:** both players must be in PvP mode. A player in PvP mode who strikes one who isn't pulls them in; that first blow does no harm.
- **Limits:** a cooldown between toggles, and no switching off while in a fight.
- **Safe zones:** around spawn, the other world (not Hell), the tournament grounds (except between the two fighters of a match), and `/dbz pvpzone add|remove|list`.
- **Config:** server config `[pvp]`.

## Melee (phase 2)

Bare-handed melee runs through the combat engine (`combat/engine`), for players and NPCs alike. Weapons and tools keep vanilla's swing.

| Input | What it does |
|---|---|
| Attack (left click) | Light. Chains `light_1` → `light_2` → `light_3` → `light_4` → `light_5` (knocks away) |
| Heavy (H), no direction | `heavy_smash`: knocks away; a wall in the way is a wall slam |
| Heavy + forward | `heavy_rush`: lunging shoulder charge |
| Heavy + back (ground) | `heavy_sweep`: unblockable, floors the foe |
| Heavy + sideways | `heavy_hook`: long stun, start a new chain |
| Heavy looking up | `heavy_uppercut` (ground) / `heavy_launcher` (air): launch for a juggle (Dash still chases) |
| Heavy looking down (air) | `heavy_spike`: drives the foe into the ground (ground slam, then floored) |

**Timing:**
- A press made too early is buffered (8 ticks) and used as soon as it can be.
- A move that **landed** can be cancelled into the next one from its `cancel` tick.
- A whiff must recover fully, and a whiffed move does not continue the chain.
- After a move ends, the chain stays open for 12 ticks.

**Fairness rules (server config `[combat_engine]`):**
- Each hit of a combo does 7% less damage (never below 35%) and stuns 5% shorter (never below 40%).
- A juggled foe floats for 8 air hits, then falls free.
- A floored foe takes half damage and only 2 hits; after getting up they are untouchable for 10 ticks.
- A combo ends after 30 ticks without a hit.
- A blow within half a second of a dash is a Z-hit (x1.5, a longer stun).
- Two fighters striking each other in the same instant clash: both blows cancel and they are thrown apart.

### Move files

`data/dbzenith/combat/moves/<id>.json` (any data pack can add or override them; `/reload` picks up changes). Times are in ticks (20 a second).

| Field | Meaning (default) |
|---|---|
| `button` | `light` or `heavy` |
| `direction` | `any`, `neutral`, `forward`, `back`, `side`, `up` (looking up), `down` (looking down, in the air). Exact matches beat `any` |
| `where` | `any`, `ground`, `air` |
| `after` | Moves it follows; `"start"` means it can open (default `["start"]`) |
| `priority` | Tie-breaker between equally good matches (0) |
| `startup`, `active`, `recovery` | Wind-up, hitting window, recovery (3, 2, 6) |
| `cancel` | Tick (from the start) from which a landed move may cancel into a follow-up (startup + active) |
| `damage` | Multiplier on the fighter's melee damage (1.0) |
| `guard_damage` | Multiplier on guard-meter damage (1.0) |
| `ki_cost`, `stamina_cost` | Costs on use (0, 2) |
| `hitstun` | Ticks the foe is stunned (12), shortened along a combo |
| `knockback`, `lift` | Horizontal push and upward lift (0.2, 0) |
| `launch` | `none`, `away` (wall slams), `up` (juggle), `spike` (ground slam and knockdown), `knockdown` |
| `launch_power` | Strength of the launch (1.0) |
| `unblockable` | Ignores guard (false) |
| `armor` | Ticks from the start during which hits don't interrupt it (0) |
| `hitbox.shape` | `sphere` (a ball `range` x 0.6 ahead), `cone` (`range`, `angle` degrees), `box` (`range` deep, `radius` each side, `height`) |
| `hitbox.max_targets` | How many foes one swing can hit (1) |
| `lunge` | Blocks stepped toward the foe on use (0) |
| `anim` | Animation clip (a name from `Anims`: `JAB_RIGHT`, `CROSS_LEFT`, `HOOK`, `KICK`, `ZHIT`, `HEAVY_PUNCH`, `RUSH`, `UPPERCUT`, `LAUNCHER`, `SPIKE`, `SWEEP`...) |
| `impact` | Hit effect: `punch`, `heavy`, `spike` |

**Dev:**
- `/dbz strike <player> <light|heavy> [neutral|forward|back|side|up|down]` presses a button and prints the fighter's state.
- `/dbz move <player> sweep|knockdown`.

## Guard, dodges and counters (phase 3)

**Guard (Guard key, held):**
- Covers blows from the front (a 200° arc, `guardArcDegrees`); blows from behind get round it.
- Blocked blows empty the guard meter; an empty meter breaks the guard (a stun, and no guarding for a moment).
- **Perfect guard:** raising the guard within 5 ticks before a blow lands parries it: no damage, the attacker staggers, and you get the counter.

**The Dash key, in order of what the moment calls for:**

| Situation | Dash does |
|---|---|
| Floored | Tech roll out (brief invulnerability) |
| Caught in a combo (stunned, or juggled) | **Burst**: a shockwave that throws everyone off you and ends the combo. 25% ki and one of two charges (each comes back after 30 s) |
| Launched but free | Air recovery (stamina) |
| Guarding | Side step (with A/D) or spot dodge (brief invulnerability) |
| Right after launching a foe | Chase: vanish into its flight path (three per combo); the next blow is a Z-hit. A foe who raises guard just as you arrive vanishes behind you instead (a chase counter) |
| Pushing forward at a foe in front (4-40 blocks) | **Super dash**: rush to them (5% ki), arriving with a Z-hit ready |
| Otherwise | An ordinary dash, which opens the **vanish** window: a blow landing within 5 ticks misses and you appear behind the attacker (4% ki, 3 s cooldown) |

**Counter:** after a vanish or a perfect guard (or a chase counter), any press within 12 ticks throws `counter_strike`: fast, unblockable, knocks the foe away. Counters can be vanished in turn, so trading vanishes is a duel of timing.

**Config `[combat_engine]`:** vanishWindowTicks, vanishCooldownTicks, vanishKiPercent, counterWindowTicks, burstKiPercent, burstRechargeTicks, superDashKiPercent, superDashRange, superDashSpeed, superDashMaxTicks, guardArcDegrees. Parry: `parryWindowTicks` and `parryStunTicks` in the combat section.

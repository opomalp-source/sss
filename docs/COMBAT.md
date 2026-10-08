# Combat v4 (CX-19)

The PvP combat system, built phase by phase. This page grows with each phase; the final guide (adding moves,
transformation interactions, balancing characters) is completed in the balance phase.

## PvP mode (phase 1)

- **Toggling:** PvP mode is off on joining and after death. Toggle it with **P** or `/pvp [on|off]`: a plain switch, no sound, light, message or marker (CX-20).
- **Fights (CX-20):** any hit that reaches a player (from a player, an NPC or a mob) tags them into PvP mode for 15 s (`combatTagSeconds`); they can't switch off while tagged, and stay in PvP mode after until they switch it off. Server options `tagForcesPvp` and `hurtOutOfPvp`.
- **The PvP meters (CX-20):** two bars bottom right in PvP mode. The form bar (right) fills from blows dealt and taken and perfect guards; J only reaches a form whose stud it has reached, and the form drains it. The technique bar (left) fills from perfect guards, vanishes, counters, long combos and charging; O raises Kaioken to reached stages, then Ultra Instinct (a technique, never on J) at the top. Outside PvP you transform freely. Numbers in `data/<ns>/combat/meters/*.json`.
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

## Ki blasts, the special meter, supers and ultimates (phase 4)

**Ki Blast key (C):**
- **Tap:** a quick blast in your aura's colour (1.5% ki, as fast as every 3 ticks). It stuns briefly and pushes a little.
- **Hold:** after 8 ticks you start charging (slowed, the focus pose). Release for a charged blast; it is full after 22 more ticks. It is bigger, harder and costlier (4-12% ki), stuns longer and throws the foe (a wall slam if one is behind them).
- Blasts count toward the combo and can be guarded, vanished or dodged like blows.

**The special meter:** three bars of 100 under the stamina bar.

| Fills by | Amount |
|---|---|
| Landing a light blow or a quick blast | 5 |
| Landing a heavy blow or a charged blast | 11 |
| Taking a blow | 4 |
| A perfect guard | 25 |
| A vanish | 15 |

**Technique tiers:**

| Tier | Costs | Techniques |
|---|---|---|
| Super | 1 bar (and its ki) | Kamehameha, Galick Gun, Masenko, Destructo Disc, Mini Spirit Bomb, Tuffle Cannon, Explosive Wave, Death Beam |
| Ultimate | 3 bars (and its ki), plus a cinematic | Spirit Bomb, Death Ball, Hakai |
| Basic | Ki only | Everything else |

- **Ultimate cinematic:** a cut-in with the name, a flash and a shake on the screen of everyone within 64 blocks. The game doesn't pause. Client config `ultimateCinematic` turns it off.
- **Beam clash:** mash the ki key (R) to push. Press **Ki Blast** to spend a bar on a surge that doubles your push for 1.5 seconds.

### Data

- `data/dbzenith/combat/ki/rapid_blast.json` and `charged_blast.json`:
  - Fields: `cost_percent`, `damage`, `size`, `speed`, `explosion`, `life`, `cooldown`, `hitstun`, `knockback`, `spread`, `homing`.
  - For the charged blast, each value also has a `_max`, and two more fields set the charge: `min_charge` (ticks before charging starts) and `charge_ticks` (ticks to full).
- `data/dbzenith/combat/techniques/<technique id>.json`: `tier` (`basic`, `super`, `ultimate`), `meter` (default 100 for a super and 300 for an ultimate), `cinematic` (default true for an ultimate).
  - Making a technique a super is one small file. Deleting the file makes it basic again.

**Config `[combat_engine]`:** `specialMeter` (off: supers and ultimates cost only ki), `specialBars`, `specialPerHit`, `specialPerHeavy`, `specialPerHitTaken`, `specialPerPerfectGuard`, `specialPerVanish`.

**Dev:** `/dbz special <player> <amount>`, `/dbz kiblast <player> <charge>` (-1 for a quick blast, 0-1 for a charged one).

## Hit feedback (phase 5)

- **Hitstop:** when a blow lands, both fighters freeze for a moment (light 2 ticks, heavy 4, perfect guard 6, guard break 5; criticals +1, counters +2). The knockback comes when the freeze ends. A move can set its own `hitstop` (ticks). Server config `hitstopScale` (0 = off); client config `hitstop` only stops the animation freeze.
- **Criticals:** a blow from the back (the 90° behind the foe) does x1.2 (`critBehindBonus`). One that catches the foe winding up their own move does x1.15 (`critPunishBonus`).
- **What you see and hear:**

| Blow | Look | Sound |
|---|---|---|
| Light | White flash and ring | Slap with a whiff of air |
| Heavy | Bigger flash, rings, camera kick | Punch over a sub boom |
| Guarded | Pale blue sparks | Block over a muffled thud |
| Perfect guard | Gold flash, the attacker reels, PERFECT GUARD! | Ringing parry |
| Guard break | Blue burst, GUARD BREAK! | Shatter and a boom |
| Critical | Red-orange burst, speed lines, CRITICAL! | A sharp crack |
| Counter | Gold burst, speed lines, zoom kick, COUNTER! | A rising sting |

- **Damage popups:** each blow's damage over whoever took it. White is a normal blow, yellow a heavy, orange a critical, gold a counter, pale blue a blow through a guard, and ki attacks show in the ki's colour. Client config `damagePopups`.
- **Ultimates:** a cut-in, a flash and a shake for everyone near, and your own swings your camera to the front for a moment. Client config `ultimateCinematic`.
- **Stances:** the server tells clients when a fighter is stunned, launched, knocked down or guarding. Players and NPCs show it: a dazed sway, a tumble, lying down, then getting up. NPCs also play their moves and hit reactions with the same clips as players.

## Lock-on (phase 6)

- **N:** lock onto the best foe in view (nearest the crosshair; players in PvP mode, fighters and monsters first), or let go. **Shift+N:** the next foe round to the right.
- **While locked:**
  - The camera keeps the foe framed, in first or third person, on the ground and in flight. The mouse can look away a little and springs back.
  - Movement is still yours: forward closes in, the sides circle the foe.
  - In third person the view sits over your right shoulder.
- **Marker:** brackets round the foe (red for a foe, gold for anyone else), with its name and distance.
- **Moves:** the super dash goes for the locked foe from any angle, and ki blasts curve toward it (`lock_homing` in the ki blast files).
- **Letting go:** automatic when the foe dies, gets too far, or is out of sight for 3 seconds.
- **Client config:** `lockOnRange` (48), `lockOnCameraSpeed` (1; 0 = marker only), `lockOnFreeLook` (25°).
- **Dev:** `/dbz lockon <player> [target]`.

## Netcode and fairness (phase 7)

- **The server decides:** every hit, cost, cooldown, reach and position.
- **Input limits:** each kind of input has a per-second budget per player (well above human speed). Extra presses are dropped, and malformed packets are rejected. Server config `[network]`: `inputRateScale` (0 = no limit), `inputViolationKick` (0 = never kick).
- **Lag compensation:** your blows are also tested against where you saw your foe, rewound by your ping (at most `lagCompensationTicks`, 6 = 300 ms). Fair at normal latencies; a high-ping player can't reach back further than that.
- **Prediction:** your own blows, quick ki blasts and plain dashes start on your screen at once. The server confirms or corrects them, and a refused one is taken back. Client config `prediction`.
- **Small packets:** the fast-changing pools (body, ki, stamina, the special and guard meters) go in a 40-byte packet; the full state only when something else changes.
- **Dev:** `/dbz netstats <player>`.

## The combat HUD (phase 8)

- **Enemy panel** (top right): your locked target, or whoever you last fought (5 s). It shows their name, form and battle power, health, the distance and what they're caught in. For players it also shows ki, guard and the special meter.
- **Callouts:** VANISH!, PERFECT GUARD!, COUNTER!, CLASH!, BEAM STRUGGLE!, BURST!, GUARD BROKEN! across the middle of the screen.
- **Combo counter:** the hits beside the crosshair, the combo's damage, and a bar until it drops.
- **Special meter, guard and PvP badge:** under your bars, and at the top (phases 1-4).
- **Controls & Move List:** Settings → Controls → Controls & Move List. It shows your combat keys and every move's input and frame data (read from the move files, so new moves appear by themselves), plus dodging, guarding, ki and lock-on.
- **Settings → Combat:** damage numbers, callouts, enemy panel, combo counter, ultimate cinematics, instant response. **Settings → Camera:** the lock-on camera, free look and range.

## Duels, training and tools (phase 9)

**Duels:** `/duel <player> [1|3|5] [full|melee]` challenges; the other clicks Accept.
- 3-2-1-FIGHT, in a ringed arena of 24 blocks round the spot.
- A knockout, a ring out (5 s outside the ring), or the clock (180 s; more health left wins) takes the round.
- Duelists fight only each other, PvP mode or not. Melee rules ban ki.
- At the end: a results screen with each side's numbers and the rating change.
- Other commands:
  - `/duel accept|decline [from]`, `/duel forfeit`;
  - `/duel stats [player]`, `/duel top` (Elo from 1000);
  - `/duel watch <player>`, `/duel leave` (spectate with a camera that frames both).
- Server config `[duel]`: duelArenaRadius, duelTimeLimit, duelRingOutSeconds, duelChallengeSeconds.

**Training dummy:** spawn egg.
- Right-click (Shift: back) to cycle: stand, guard, perfect guard, dodge, counter, attack, random.
- It never dies, heals after 3 s alone and goes back to its spot.
- Its name shows your string: hits, damage, damage per second.

**Hitboxes & frame data** (Settings → Combat, or `/dbzhitbox`):
- Every move's hitbox in the world (yellow startup, red active, blue recovery) and every hurtbox.
- A frame bar for your own moves showing the cancel point.

**Combat log** (Settings → Combat): knockouts, big combos (5+), guard breaks, bursts, ultimates and duels near you.

## Balance (phase 10)

- **Between players**, every blow goes through the balance curve.
  - Fights between equals last about 40 jabs at every level (`[pvp] equalJabsToKo`).
  - A power gap counts, but softened: `powerExponent` 0.5, at most `dominanceCap` 4. A fighter four times the level wins in roughly a fifth of the blows the other needs.
  - No blow takes more than 35% of a player's health, and a combo past 60% lands the rest at a quarter.
- **Forms** can have their own moves (`forms`) and hit harder with any move (`form_damage`).
- **Artists** can replace any combat animation with a playerAnimator file.

How to add moves, make forms matter, and tune it all: **[COMBAT_GUIDE.md](COMBAT_GUIDE.md)**. Art, sounds and
animations you can provide: **[COMBAT_ASSETS.md](COMBAT_ASSETS.md)**.

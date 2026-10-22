# Combat AI for NPC fighters

The progress document for the NPC combat AI (started 2026-10-21, v0.159.0). It records what the codebase had before
this work (verified by reading the code, not assumed), the design, what is built and wired in, how it was tested, and
what is left. Keep it current so the work can be picked up in a later session.

## 1. What existed before (audit, v0.158.0)

### Combat-capable NPCs
All of them are `npc.KiFighter` (a vanilla `Monster`) or subclasses, registered in `npc.ModNpcs`:

| Entity id | Shown as | Class | Techniques (profile) |
|---|---|---|---|
| `sproutling` | Saibaman | KiFighter | none (melee) |
| `ki_soldier` | Frieza Force Soldier | KiFighter | Ki Blast |
| `android_unit` | Red Ribbon Android | KiFighter | Arm Cannon |
| `namekian_warrior` | Namekian Warrior | NeutralFighter | Ki Blast |
| `ogre_guard` | Ogre Guard | NeutralFighter | none |
| `damned_warrior` | Damned Warrior | KiFighter | Ki Blast |
| `tyrant_lord` | Frieza | BossFighter | Finger Beam, Supernova Orb, Ki Blast, Seal Orb |
| `rampage_brute` | Broly | BossFighter | Wave Beam, Rapid Volley |
| tournament roster (7) | Mr. Satan, Spopovich, ... | TournamentFighter | Ki Blast for three of them |

Not combat AI: `TrainingDummy` (scripted training modes on purpose: stand, guard, parry, dodge, counter, attack),
quest givers, style masters and the other-world residents (invulnerable, talk only).

### Their behaviour before
- Goals: vanilla `MeleeAttackGoal` (walk at the target, swing when in reach), `KiAttackGoal` (a fixed timer plus up to
  a second of random delay; fires a **random** technique from the profile when 4-28 blocks away with line of sight),
  random strolling, `HurtByTargetGoal`, `NearestAttackableTargetGoal<Player>`.
- Melee: `CombatEngine.npcAttack` presses a light, and with a 1-in-3 chance a heavy finisher (random direction).
- Bosses: teleport behind a target more than 12 blocks away every 8-12 s; "enrage" at half health (x1.5 damage,
  +30% speed, dust particles).
- No guard, no dodging, no counters, no flight, no stamina or ki pool (ki attacks were limited only by the timer), no
  transformations, no awareness of anything but distance and line of sight. `npc.Animated` (flight and charge hints
  for the motion engine) existed but no entity implemented it.

### Systems the AI can use (all verified)
- **Combat engine** (`combat.engine.CombatEngine`): moves from data (`data/dbzenith/combat/moves`: `light_1..5`,
  `heavy_smash/rush/uppercut/launcher/spike/sweep/hook`, `counter_strike`), presses with direction, chains, cancel
  windows, hitstun, juggles, clashes, criticals for hits from behind and for catching a wind-up (a "punish"), Z-hits.
  `Fighter.state()` reports guarding and charging for players only.
- **Evasion** (`Evasion`): `openVanish(e, ticks)` works for any entity (a blow landing in the window misses and the
  defender appears behind the attacker with a counter window); `counterReady`; the counter is thrown by any press.
- **Guard** (`GuardRules`): players only (meter, parry window, break, lock). The training dummy has its own
  block/parry in `CombatEvents`.
- **Ki**: `TechniqueHandler.spawn(level, caster, technique, damage, scale)` fires any technique for any caster
  (blasts, volleys, beams, disks, placed, rain); `chargeDamage`/`chargeScale` for charged techniques. Players pay ki
  from `PlayerData`; NPCs had no pool.
- **Flight**: `ki.FlightModel` is pure math (momentum, accel/coast/brake, boost) shared by both sides; players fly via
  vanilla abilities. Flight ki drain is `flightKiPercentPerSecond`.
- **Forms**: `transform.Forms` (per race and variant, parent chains, unlock levels scaled by `unlockLevelScale`,
  required flags, ki and stamina drain per second, aura colours, scale) and `FormHandler` (rules for players:
  `transformKiCostPercent`, power-up time, interruption, revert at 0 ki). Everything reads `PlayerData`: NPCs had no
  forms. Auras (`client.aura.AuraSystem`) were drawn for players only, from `PublicStatePacket`.
- **Power**: `StatCalculator.battlePower` (scouter value; god ki unreadable), NPC levels from the nearest player's
  full power (`enemies.powerPerLevel`, 700 by default).
- **Targeting/teams**: player lock-on (`Targeting`) and the enemy panel (`FoeFocus`); vanilla teams via
  `isAlliedTo` (the engine already skips allies). **No factions or alliances existed.**
- **Wounds/knock-out/limbs**: `Wounds` (slower ki and cooldowns when badly hurt), `KnockOut` (downed NPCs get `NoAi`),
  `Limbs.canUseKi`.

### Nemesis system: it does not exist
There was **no Nemesis system** in the code, the docs, the TODO, the devlog or the git history (searched for nemesis,
rival, grudge, personality, memory, relationship): no long-term NPC memories, rivalries, relationships, personalities
or NPC progression. It may have been discussed but it was never built. This work builds a compatible **foundation**
(section 3.10), not a fake Nemesis system: one personality system on the NPC, and a bounded persistent memory of
important NPCs' encounters per opponent that a future Nemesis feature can build on.

## 2. Design

One brain per fighter, in `com.dbzenith.npc.ai`, driven by a single goal (`CombatAiGoal`) that replaces
`MeleeAttackGoal` and `KiAttackGoal` on every `KiFighter`. Server-authoritative; clients only see the results through
existing channels (entity data, the engine's existing move/state packets, ki entities).

```
CombatAiGoal (MOVE+LOOK)  ->  NpcBrain.tick()
   modes (HFSM): IDLE -> ENGAGE <-> RECOVER (retreat / charge up) ; TRANSFORMING (committed power-up)
   every decision tick (staggered): Perception -> ThreatAssessment (target) -> UtilityScorer (actions) -> commit
   every tick: executors (Locomotion, Melee, Defence, Ki, Forms) carry out the committed action
```

- **Determinism.** No `Math.random`. Variation comes from `AiRandom`: a hash of the NPC's UUID, the decision counter
  and a salt, scaled by the NPC's quality (better fighters vary less). The same fight from the same state makes the
  same choices.
- **Utility scoring.** Each candidate action gets a score from considerations (health, ki, stamina, relative power,
  distance, opponent profile, techniques ready, form, personality, experience, environment) and learned bias, plus a
  hysteresis bonus for the action under way. The best wins; a lower-quality NPC sometimes takes the second best (a
  "mistake"), deterministically.
- **Commitment and reaction.** An action holds for its minimum ticks; reactions to events (a transformation seen, a
  blast incoming) are queued with a reaction delay by quality.

## 3. Components (where each requirement lives)

| # | Requirement | Classes |
|---|---|---|
| 1 | Decision architecture | `NpcBrain` (modes, decide, execute), `Action`, `Situation`, `UtilityScorer`, `AiRandom`, `CombatAiGoal` |
| 2 | Targeting, threat | `ThreatAssessment`, `Power` |
| 3 | Melee, guard, counters, styles | `MeleeTactics`, `NpcGuard` (+ `CombatEvents`, `Fighter.state`), reactive layer in `NpcBrain` |
| 4 | Ki management | `KiTactics`, `NpcResources` |
| 5 | Transformations | `NpcForms` (existing `Forms` and their rules), TRANSFORM/REVERT scoring |
| 6 | Movement and flight | `Locomotion` (ground navigation, strafing, `FlightModel` flight, safety probes) |
| 7 | Adaptation | `OpponentProfile` (observed behaviour, moving averages) |
| 8 | Personality | `Personality`, `FightStyle` |
| 9 | Teams | `Faction`, squad roles in `NpcBrain.refreshSquad`, friendly-fire check in `KiTactics` |
| 10 | Learning | `FightLog` (in-fight, everyone), `AiMemory` (persistent, bosses) |
| 11 | Events and pacing | `AiEvents` (hooks), reaction queue and commitment in `NpcBrain` |
| 12 | Performance and config | `[npc_ai]` in `dbzenith-server.toml`, staggering, activation range, light AI |
| 13 | Debug | `/dbz ai ...` (`AiCommands`) |

### 3.1 Decision architecture
- `CombatAiGoal` (MOVE+LOOK, priority 2, ticks every tick) replaces `MeleeAttackGoal` and `KiAttackGoal` on every
  `KiFighter` (and the `NeutralFighter` and `TournamentFighter` overrides). The old goals stay registered as
  `LegacyMelee`/`KiAttackGoal`, active only when `npc_ai.enabled = false`. Target goals (hurt-by, nearest player) are
  unchanged and feed the threat assessment.
- Modes: no foe (idle: power down after the fight, land, recover), powering up (committed, hovering, interruptible),
  fighting (utility choice). `NpcBrain.passiveTick` runs from `KiFighter.aiStep` every tick whatever the mode:
  pools, form drain, flight drain, the fight ending.
- `Situation` is a plain snapshot (`NpcBrain.perceive`); `UtilityScorer.score` is a pure function of it, each action a
  sum of named terms, plus the learned bias and +0.12 for the action under way. `Result.why(action)` gives the four
  largest terms (shown by `/dbz ai inspect`).
- The choice (`NpcBrain.pick`): best score plus a deterministic jitter (`AiRandom`, ±0.06 at most, less for better
  fighters); with a small chance (10% light, 5% normal, 2% elite, times `npc_ai.mistakes`) the second best if it was
  close (a "misjudged" note). A committed action (each has a minimum, `Action.commit`) is only cut short by urgent
  ones: evade, punish, interrupt, guard against a blow coming, retreat near death, protect an ally.
- Actions: idle, approach, pressure (melee), punish (an opening), interrupt (a charging foe), keep distance, strafe,
  guard, evade, ki volley, big technique, charge ki, transform, revert, retreat, pursue, protect ally.

### 3.2 Targeting and threat
`ThreatAssessment.choose` (each decision; the area scan every fourth): candidates are the current target, the last to
hurt it, whoever is beating on a weakened ally, and faction enemies who are fighting nearby (any in 24 blocks with
`factionWars`). Score: +0.35 current, +0.4 if they target it, +0.5 if they hurt it lately, +0.2 x relative standing
(0.2..3), -0.03 per block, +0.6 x honor for an ally's attacker, +0.2 x aggression for a foe below 30%. A newcomer must
beat the current target by a third (no flip-flopping). Never allies, the knocked out (the grip system finishes them),
spectators, creative players, or anyone outside the fighter's bounds or test arena. Tournament fighters keep the
opponent the bracket gives them.

`Power`: players' scouter battle power (form and release in; god ki unreadable, so full power x1.5 is assumed); NPCs'
level power (`powerPerLevel` x level) x fury x form; plain mobs a guess from health and attack. `standing` weighs it
by health left (35% + 65% x health).

### 3.3 Melee
Through the combat engine as a player (moves, chains, cancels, cooldowns, animations). NPCs now pay each move's
stamina (and ki) from their own pool (`KiFighter.aiPayMove`, called from `CombatEngine.start`); winded fighters back
off. Strings run `FightStyle.comboLength` lights (aggressive 5, balanced 4, tactical 3, defensive 2), then a finisher:
spike on a foe below in the air, uppercut (tactical), rush (aggressive), else smash. A guarding foe on the ground gets
the sweep (unblockable) with a chance that grows with how much it turtles. Whiffs against a dodgy foe make it wait a
moment before the next blow (baiting). Punish/interrupt dash in (stamina) and go for the finisher.

Defence (the reactive layer, never faster than the fighter's reaction time: `reactionTicks` x 1.4/1/0.55 by tier,
minus up to 2 for high levels, at least 3): seeing a blow wind up (the engine's visible move state, never inputs)
within reach and facing it, it may raise a fresh guard right before the hit (a parry, chance by quality and style),
just block, or vanish (`Evasion.openVanish`, ki and the vanish cooldown) if evading. Blows faster than its reaction
(a jab is 2 ticks) can only be blocked by a guard already up. After a vanish or a parry it throws the counter
(`counter_strike`). Ki attacks on a collision course (blasts by closest approach, beams by line) are sidestepped
across their path (stamina, safe ground only).

The guard (`NpcGuard`) follows the players' rules and config: front only, parry window, chip damage (15%), meter
cost by the blow's share, ki x1.5, break = stun + lock, sweeps ignore it. Clients see the guard pose through the
existing `FighterStatePacket` (`Fighter.state` now reports NPC guard and charge).

### 3.4 Ki
Only the profile's techniques (`KiFighter.Profile`), through `TechniqueHandler.spawn`. Costs: the technique's own ki
cost from the NPC pool (1,000 units at level 1, +25 a level, at most 3,000); cooldown: the technique's, x `Wounds`
when badly hurt. Damage: `kiDamage()` x sqrt(the technique's damage multiplier) x charge (previously every technique
did the same `kiDamage()` per projectile; a volley did six times a blast's damage). Aim leads a moving target by up to
80% of its motion (quality and a deterministic jitter: never perfect). Holds fire with an ally in the line (shows in
the notes). Chargeable techniques (Finger Beam, Supernova Orb) are charged for real (charge pose and aura via
`AI_CHARGING`, a 4% blow breaks it and half the ki is lost). The cast sends the existing `AnimEventPacket` so NPCs now
play the cast animation (client: `AnimController.onEvent` handles NPC casts). Charging ki (CHARGE_KI) regains 8% a
second, stops when hit, when a foe comes within 6 blocks, or at 85%. Out of combat ki returns 2% a second, in combat
0.6%.

### 3.5 Transformations
`NpcForms`, existing forms only. Lines: Frieza (Tyrant Lord) the Frost Demon line (Second, Third, Final, Golden);
Broly (Rampage Brute) the Legendary Saiyan line (Wrathful, C-Type, Full Power, Controlled); Red Ribbon Androids the
Android upgrades; Namekian Warriors Super and Orange Namekian (not Giant: NPC bodies cannot grow). Rules as in the
class comment: race and variant, manual trigger, no giants, no flags or god ki, unlocked by the equivalent character
level (`Power.equivalentLevel`), one tier at a time, cost `transformKiCostPercent` paid on completion, power-up time
from `transformTimeBase/PerTier` (x0.6 elite, x0.85 others), broken by `transformInterruptDamage`, drain 75% of the
form's per-second drain, falls back to base at 0 ki, powers down 10 s after the fight. Effect: 1 + (mean multiplier -
1) x `formPowerShare` (0.3) on damage and on its power for judging.

When (`UtilityScorer`, TRANSFORM): need = desired ratio (1 + 0.35 caution - 0.3 recklessness) - current ratio, plus
0.3 if the foe just transformed; nothing below 0.12 need at more than half health (escalate only when needed);
pride subtracts up to 0.55 while healthy (arrogant fighters wait until hurt); unaffordable (less than 10% ki left after
cost and ten seconds of drain) -1; too close or a ki attack coming lowers it. Hysteresis: 8 s (160 ticks) between
changes, 10 s after a revert. REVERT only when out of ki (<15%) or, for disciplined fighters, clearly overpowering
(ratio > 2.2). Seen by everyone: synced form (`AI_FORM`) and flags drive the aura (`AuraSystem` now draws NPC fighters
from their synced data), power-up particles and sound, the burst (`ImpactPacket`), and a combat-log line.

### 3.6 Movement and flight
`Locomotion`: ground paths renewed at most every 10 ticks (or when the goal moves 2 blocks), flanking points round
the foe, strafing by the move control with safe-step checks (no drops over 3, no lava, no walls), retreats to loaded
ground. Flight uses `FlightModel.step` (the players' model) with the players' `flightBaseSpeed`, `flightKiPercentPerSecond`
drain (x the planet's flight factor) and `Wounds.flight`. Takes off when the foe is in the air, far above with no
path, when pursuing far, or retreating from close; lands when the fight is on the ground. Every flight tick
`Locomotion.safe` refuses unloaded chunks (look-ahead 3 and 10 ticks), walls and ceilings (climb, slide, else hold)
and keeps an altitude band (20 over ground, 8 over the goal). No flight for forms that forbid it, the stunned, the
ki sealed, under 10% ki, or with bounds (the tournament ring). Landings and saved-in-the-air fighters never take fall
damage. Bosses pursue a far foe by vanishing behind it (their old teleport, now the AI's choice, same cooldown).

### 3.7 Adaptation
`OpponentProfile`, per foe, from what anyone could see: blows started (`CombatEngine.start`), ki fired
(`TechniqueHandler.use`, `KiCombat.fire`, NPC casts), dodges (`Evasion.tryVanish`), transformations
(`FormHandler.enter` and reading the form), and at each decision: guarding, charging, in the air, closing speed, keeping
range. Rates are moving averages (weight 0.06 a decision, about six seconds to follow a change), traits are scaled by
confidence (samples / (samples + 40)). Traits: melee-heavy, blast spam, turtle, dodgy, rusher, kiter, charger,
transformer, flier, and the technique it repeats. They feed the scores (keep distance from brawlers, strafe blast
spam, sweep turtles, homing or volleys against dodgers, pursue kiters, interrupt chargers, transform sooner against
transformers, read a repeated technique by strafing). No input reading and no prediction beyond these habits.

### 3.8 Personality
`Personality` (the only personality system): aggressive, defensive, tactical, arrogant, honorable, cowardly,
balanced, reckless, patient, each eight traits (aggression, caution, patience, pride, honor, courage, recklessness,
discipline) and a melee `FightStyle` (aggressive, defensive, tactical, balanced). Born from the type's list by UUID
(stable), saved on the entity (`dbzAi.personality`), changeable with `/dbz ai personality`. Defaults: Frieza arrogant,
Broly reckless, soldiers aggressive/balanced/arrogant/cowardly, Saibamen reckless/aggressive, androids tactical/patient,
Namekians honorable/defensive/patient, ogres defensive/honorable, the damned aggressive/reckless/arrogant, the
tournament roster one each (Mr. Satan arrogant, Spopovich reckless, Nam honorable, Ranfan tactical, ...).

### 3.9 Teams
`Faction` (there were none): Frieza Force (soldiers, Saibamen, Frieza) vs the Namekians; the damned vs the ogres;
the Red Ribbon Army alone; Broly and tournament fighters with no allies. Scoreboard teams override factions.
`KiFighter.isAlliedTo` reports faction allies, so the combat engine and vanilla targeting skip them. Squads
(`refreshSquad`, every 2 s): allies within 20 blocks; round a shared foe the lowest id leads, the others flank at
±65°, ±130°, every other one with ki takes a ranged support role; a weakened ally under attack makes its attacker a
threat (protect ally); the squad falls back together when an ally on the same foe retreats or their combined
standing is under 35% of the foe's; an ally falling makes the brave furious and the cowardly fall back. Ki is held
when an ally is in the line of fire.

### 3.10 Learning and the Nemesis foundation
`FightLog` (everyone): while an action runs, the share of the foe's health dealt counts for it and 1.2 x the share of
its own health lost against it; the running mean becomes a bias of at most ±0.3, trusted by tries/(tries+3).
`AiMemory` (`data/dbzenith_ai_memory.dat`, overworld storage, schema 1, bosses only, `npc_ai.memory`): per NPC UUID
(type, last seen, fallen) and per opponent UUID (name, fights, wins, losses, fled, grudge, per-action payoff and
tries, stored by action name). A new fight seeds the log from memory (tries capped at 3, so the fight soon decides);
the end of a fight blends what it learned in (60/40). Bounded: `memoryMaxNpcs` (256, longest unseen forgotten) and
16 opponents each. This is the foundation for a Nemesis system: records outlive the NPC, carry a grudge and the
opponent's name. No Nemesis behaviour (returning, promotions, taunts) is built.

### 3.11 Events and pacing
Hooks (`AiEvents`, one line each in the combat code): blows (with guard/dodge outcome), moves started, ki fired,
transformations, vanishes, deaths. Reactions: opponent transformed (reconsiders after its reaction time, TRANSFORM
gets +0.2/+0.3), opponent exhausted (punish opening), a new technique revealed (more caution for 5 s), its own low
health (once, under 30%: cowards run, the reckless go all in, the arrogant drop their pride), an ally falling (fury or
fear for 5 s). Pacing: reaction delays, commitment, staggered decisions, deliberate small mistakes, every resource
finite. Better fighters decide more often, react faster and err less, but still need stamina and ki, can be parried
and guard-broken, and can never react to a jab.

### 3.12 Performance
Decisions every `decisionTicks` (6; elite x0.5, light x2), staggered by entity id. Area scans only at decisions
(threats every fourth decision, squads every 2 s, incoming ki every second tick within 24 blocks). Event-driven
observation. Paths renewed at most twice a second. Beyond `activeRange` (48) from every player and not fighting one,
a fighter thinks at the light level. Entities in unloaded chunks do not tick; flight refuses to enter them. No new
packets: NPC state rides on two synced entity fields and the engine's existing packets (the cast animation is the
existing `AnimEventPacket`, one per cast). Protocol bumped to 54 (entity data layout changed).

Config `[npc_ai]` in `dbzenith-server.toml`: `enabled`, `quality` (0-2 cap), `decisionTicks`, `activeRange`,
`reactionTicks`, `flight`, `transform`, `formPowerShare`, `mistakes`, `factionWars`, `memory`, `memoryMaxNpcs`.

### 3.13 Debug
`/dbz ai inspect [fighter]` (default: nearest within 32): personality, style, tier, reaction and decision times,
faction, fights; action, how long and why (the deciding terms); target, distance, threat, power ratio; ki, stamina,
guard; form, power-up progress, the form ladder it can climb and the reason for the last change; the opponent profile;
what it has learned; the top four scores; squad role; the last six notes (adaptations, reactions, mistakes).
`/dbz ai watch` (action bar line twice a second), `/dbz ai personality <fighter> <name>`, `/dbz ai memory <fighter>`,
`/dbz ai stats`.

## 4. Status (v0.159.0)

All thirteen stages are built and wired in, in one version: decision architecture, targeting, melee/guard/counters,
ki, transformations, movement and flight, adaptation, personality, teams, learning, events and pacing, performance
and config, debug. Every `KiFighter` (all nine NPC kinds plus the tournament roster) runs on it.

Tuning found in the dev client and fixed before release: technique charges capped at 3 s for NPCs (the Supernova's
45 s charge kept Frieza standing still for 17 s); transformations ruled out when far ahead (ratio > 1.5) even when
hurt; the debug readout says why a target was dropped.

## 5. Testing

- **GameTests (`AiTests`, 10; all 331 pass):** deterministic scoring and personality (pressure up close, fire rather
  than walk into a brawler, cowards run and the reckless don't, nothing to fire with means charge, but not with a foe
  in its face); transformations by need, pride and cost (arrogant waits until hurt, exhausted fighters avoid costly
  forms, no form without need); gradual adaptation (one flurry proves little, a minute of brawling reads as a brawler
  and moves a ki fighter to keep away); Frieza outmatched and hurt transforms into a Frost Demon form of his own line,
  shown to clients, paid in ki, strengthening his blows, and stays transformed (hysteresis); a low-ki soldier charges
  visibly instead of firing and its ki comes back; NPCs on enemy sides (scoreboard teams) fight each other; an
  outnumbered coward picks one of three attackers as its target and runs; the NPC guard parries, blocks, ignores
  blows from behind and sweeps, breaks with stun and lock, and a guarded blow through the real damage path does a
  fraction; fire held with an ally in the line; flight stops at unloaded chunks and never enters walls or ceilings;
  mind (personality, ki, form) survives saving and loading, and the memory store round-trips, bounded to 16 opponents
  with the longest unseen forgotten. World tests run in their own batches, inside arenas, and always clean up.
- **Background dev client (scripted, `/z/dev/*.sh`, Dev player):** Frieza against a much stronger Dev on a bedrock
  arena: second form on his first decision ("outmatched +0.95, hurt +0.25, pride -0.21"), third form eight seconds
  later, big techniques, ki volleys, vanishing behind to close the distance, melee pressure; his form aura (pale violet
  flame and ground ring) drawn on the NPC in the screenshots. A Frieza Force Soldier outmatched by Dev took off to
  retreat, lost sight, logged the fight as fled, came down gently and recovered ki; a soldier stronger than a
  hovering Dev flew up to him and fought in the air (screenshot).
- **Not tested:** long play sessions and many NPCs at once (no profiling of dozens of fighters), the tournament under
  the AI in a real bracket (the ring bounds are set but not watched in the client), Broly and the Android and Namekian
  form lines in the client (covered by the same code path as Frieza), a dedicated multiplayer server with two
  clients (sync goes through entity data, which vanilla syncs to every tracker).

## 6. Not done / limits

- **Nemesis system:** only the foundation (one personality system on the NPC, `AiMemory` per NPC and opponent with
  wins, losses, flights, grudge and what worked). No returning nemeses, promotions, taunts or hierarchy.
- NPC forms change the aura, not the body: NPC skins are baked, so no hair colour or shape change, and giant forms
  (Great Ape, Giant Namekian) are excluded. Form speed bonuses are not applied to NPC movement.
- NPCs don't use beam struggles, the Burst, the special meter, Kaioken or Ultra Instinct.
- Ki damage per technique changed: `kiDamage x sqrt(damage multiplier)` (a volley used to do six blasts' damage).
- The training dummy keeps its own scripted modes on purpose (it is a training tool).
- A foe that flies out of sight is dropped after vanilla's unseen-memory time (the fight is then "fled").

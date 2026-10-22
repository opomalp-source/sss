# The Nemesis system

Progress document for the Nemesis system (the user's request of 2026-10-22, inspired by Shadow of Mordor and Shadow
of War). It records the audit, the plan, what is built, how it was tested and what is left, so the work can resume
in a later session. The combat AI it builds on is in `docs/COMBAT_AI.md`.

## 1. What the codebase gives it (audit)

- Minecraft 1.20.1, Forge (NeoForge legacy toolchain), Java 17. Server-authoritative; one `SimpleChannel`
  (`ModNetwork`, protocol versioned); world data as `SavedData` in the overworld's storage (`GalaxyData`, `AiMemory`).
- **NPCs:** `KiFighter` and subclasses (`ModNpcs`); looks are a generated skin (`tools/ArtGen.java`, `NpcArt`) plus 3D
  hair, race parts, tail and armour pieces (`client/render/NpcLooks`, `NpcPartsLayer`). There was no Saiyan NPC
  other than Broly.
- **Combat AI (v0.159.0):** `npc.ai.NpcBrain` with `Personality` (the one personality system), `OpponentProfile`
  (observed habits), `FightLog` + `AiMemory` (what worked, per opponent, bosses only), `Faction`, `NpcForms` (each
  race's existing forms), `ThreatAssessment`.
- **Power:** NPC levels from player full power (`KiFighter.levelFor`, `enemies.powerPerLevel`), `Power.equivalentLevel`
  maps an NPC level to a character level for form unlocks; `Techniques` per race with unlock levels.
- **Defeat:** `combat.KnockOut` downs NPC fighters at zero health; they bleed out after `knockOutNpcSeconds` unless
  finished by a grip (`Grip`, NPCs also finish downed players); death goes through `LivingDeathEvent`.
- **UI:** the character menu (`client/screen/MenuScreen`, tabs Stats/Techniques/Skills/Forms/Styles) with the `Ui`
  kit; request/response packets in the `GalaxyPackets` style.
- **Tournament:** a bracket of canon roster fighters (`tournament.Roster`, `TournamentFighter`, never saved).
- **There was no Nemesis system** (see COMBAT_AI.md 1).

## 2. Plan (built in this order)

| Step | What | Where |
|---|---|---|
| 3 | Persistent identities, memories, per-player relations | `nemesis.NemesisData` (SavedData), `Character`, `PlayerRelation`, `Memory` |
| 4 | Traits on top of `Personality` (one system), rivalries and relationship states, data-driven dialogue | `Trait`, `Temper`, `Relationship`, `Dialogue` (+ `data/dbzenith/nemesis_dialogue/*.json`) |
| 5 | Progression with the existing levels, forms and techniques; a Saiyan Warrior NPC | `Progression`, `ModNpcs.SAIYAN_WARRIOR` |
| 6 | Defeat, survival, retirement, death; returning enemies and special encounters; adaptive seeding | `Fates`, `Encounters`, `OpponentProfile` seeding |
| 7 | NPC-to-NPC relations, reputation and ranks, background simulation | `NpcRelation`, `Rank`, `WorldSim` |
| 8 | Nemesis History GUI | `NemesisPackets`, `client/screen/RivalsScreen` (a tab of the character menu) |
| 9-10 | Tests, multiplayer and performance review | `NemesisTests` |

Details are filled in as each step lands (section 3).

## 3. Built (v0.160.0)

All steps are built and wired in, in one version. Package `com.dbzenith.nemesis` unless noted.

### 3.1 Identities and persistence
- `NamedFighter`: a persistent identity (its own UUID, separate from any entity): name and epithet, entity type, race and
  variant, personality and traits, fighter level and experience, growth rate, battles/wins/defeats, learned
  techniques, forms reached, reputation, status, goal, last place, timestamps, bonds with players and with other
  characters, and a bounded history of memories. `Bond` per player (and per character). `Memory` (30 kinds).
- `NemesisData`: SavedData `dbzenith_nemesis` in the overworld storage, schema 1, per-player index, bounded
  (`maxCharacters` active, the least important retire to make room; 512 records, the oldest gone ones forgotten; 32
  memories each). Unknown fields are ignored and missing ones default.
- Bodies: `KiFighter` carries its character's id (`dbzNemesis` NBT). `Nemesis.bind` gives a body its name (shown over
  its head), level, temperament, traits and learned techniques; `Nemesis.onJoin` refuses a body that is a stale copy
  (the character's recorded body is another entity) or belongs to the dead or retired. Bodies never despawn.
- Who is named: canon characters on sight (Frieza, Broly: one identity per world under their own names; the
  tournament roster), Saiyan Warriors on sight, and ordinary fighters when they knock out a player, or when a player
  leaves them alive (one in four of those survive to become a character). NPCs without AI (statues, tests) never.

### 3.2 Memory, relationships, personality, dialogue
- Events recorded (`Nemesis`): meetings and fights (once per meeting), wins and losses (humiliating ones noted), being
  spared and sparing, the player escaping, retreating, surviving death, seeing the player's forms and techniques,
  being helped, an ally defeated, training, new forms and techniques, relationship changes, duels and tournaments
  between characters, spars, betrayals, retirement, death, interrupted training.
- `Relationship` (stranger, neutral, respectful, competitive / bitter / friendly rival, sworn enemy, ally, training
  partner) read from affinity and rivalry with hysteresis; not a ladder (tested both ways). Deltas depend on the
  character: the honorable respect being spared, the proud take it as humiliation, the loyal take an ally's defeat
  personally, the vengeful hold grudges.
- `Trait` (vengeful, intelligent, competitive, loyal, calm, friendly, ambitious) adds to `Personality` (the one
  personality system); `npc.ai.Temper` is the sum, and the combat AI now fights with it. Traits are born (one or two
  by UUID) and can be gained after major experiences (spared twice: grateful or humiliated; surviving death:
  vengeful).
- `Dialogue`: data-driven (`data/<ns>/nemesis_dialogue/*.json`, `/reload`), 16 situations (first meeting, meeting
  again, revenge return, rival return, found training, ambush, intervention, ally arriving, winning, losing, sparing,
  fleeing, the player transforming, its own transformation, talk, end of a spar), conditions on recorded facts
  (relation, personality, traits, race, rank, fights, wins, losses, last result, spared either way, escaped, ally
  defeated, the player's form, a new form or technique since the last meeting, stronger, days since, has a rival),
  placeholders ({player} {npc} {wins} {losses} {fights} {days} {form} {npcform} {technique} {newtech} {rank} {rival});
  more specific lines likelier; the last eight said to that player skipped. Chat lines to the player and anyone
  within 32 blocks. `core.json` ships 138 original lines.

### 3.3 Progression (`Progression`)
Experience from fights (+25), victories (+40), defeats (+40; Saiyans +70: zenkai), spars, duels, and training while
away (per in-game hour, by discipline, ambition, rivalry, and obsessively when vengeful). Levels on the fighter scale,
capped at `growthPerDay` a day (1.5) and a quarter above the strongest player it has measured itself against, never
past `enemies.maxLevel`. New forms come from the race's existing ladder (`NpcForms`, by the character level its fighter
level equals), new techniques from the existing table (its race's or anyone's, unlocked, not self effects, not the
gods' techniques; at most four). Arrival levels are capped near the player's own (x1.3). Canon bosses keep the
summon's scale.

### 3.4 Fates, returns and encounters
- Knocked out by a player and left: it gets away (`RECOVERING`), back in 0.5-2 in-game days (x1.5 recovering, x0.7
  vengeful, x1.2 calm). Finished by a grip: it may survive (`survivalChance` 0.45, more for canon characters, at most
  three times) and returns scarred, often vengeful; otherwise it is dead for good. Fleeing or done with its business,
  the body vanishes (`AWAY`). Beaten seven times by a player it has come to respect: retired.
- `Encounters` (every 30 s one online player, every 5 s for fights): revenge, rival challenge, ambush (config),
  training (it trains, aura up; it speaks when you come near; strike it and it fights, or spars if it is your friend),
  intervention (a rival who wants you for itself takes on your foe), alliance (a friend comes when you're hurt;
  config). Eligibility: alive, survival, not in a safe zone, duel, tournament, the other world, space or the Time
  Chamber; per-player cooldown (15 real minutes); at most `maxPresent` bodies out and one per player; safe ground in a
  loaded chunk 14-22 blocks off (8-12 behind for an ambush). A callout announces rivals and allies.
- Spars (`Spars`): hit a friend and it spars to 30% and stops; striking again right after is a betrayal.
- Adaptive combat: a character remembers each player's habits (`OpponentProfile` averages, the techniques seen) and
  starts the next fight from them, trusted by its discipline (intelligent ones remember more); the existing combat
  AI then adapts as before (COMBAT_AI.md 3.7).

### 3.5 Characters among themselves, reputation, world simulation
- `WorldSim` (every in-game hour, 16 characters a step, no entities): training for the time away, feelings fading
  apart (not grudges), meetings: enemies (by faction or feeling) duel (level and a deterministic roll), allies train
  together, soured friendships turn to betrayal; every third day the eight best-known hold a tournament. State is
  per world (deterministic, tested).
- `Rank` (Unknown Fighter, Rising Warrior, Experienced Warrior, Elite Fighter, Legendary Warrior) from reputation,
  separate from power: +20 x relative strength for beating a player, +8 a duel, +40 a tournament, +15 a new form;
  -10 a defeat, -5 a retreat, -4 a lost duel.

### 3.6 The Saiyan Warrior
A new NPC (`saiyan_warrior`, Frieza Force, Saiyan, can fly, Ki Blast and Crimson Beam), the rival archetype: an
original skin from `tools/ArtGen.java only saiyan_warrior` (battle armour, scalp roots), wild black 3D hair, a wrapped
tail, 3D pads; spawn egg; rare natural spawns in the overworld (weight 4). Every NPC's 3D hair now follows its form
(`NpcPartsLayer`): a Saiyan in Super Saiyan has gold, spiked hair.

### 3.7 The Nemesis History screen
A "Rivals" tab in the character menu (K): the characters who know you, with a live portrait, race, rank, relationship
(coloured), rivalry bar, your record, mercy shown either way, the power they showed last, the forms you saw them use,
how and where you last saw them, and your story (memories involving you, plus their tournament wins and the forms
you've seen). `NemesisPackets` (on request, rate-limited, 40 characters at most) sends only what the player
discovered. Protocol 55. The social Party/Clans tabs now lay out around any number of menu pages.

### 3.8 Config and commands
`[nemesis]`: enabled, promote, maxCharacters (48), maxPresent (4), encounterCooldownMinutes (15), encounterChance
(0.3), returnMinDays (0.5), returnMaxDays (2), survivalChance (0.45), growthPerDay (1.5), worldSimulation, ambushes,
alliances, dialogue. `/dbz nemesis list|info|promote|encounter|simulate|bond` (operators).

### 3.9 Multiplayer and performance
Per-player bonds (one character can respect one player and hate another; tested), shared progression, every decision
on the server, packets only on request. Event-driven (the combat code's hooks); a 1-second pass over loaded bodies
only, encounters every 30 s (one player) and 5 s (fights), the world simulation 16 characters an in-game hour.
Duplicate bodies refused; disconnects send a player's encounter bodies away; the dead and retired never come back.

## 4. Testing

- **GameTests (`NemesisTests`, 11; all 341 pass):** identities and memories survive saving; relationships move both
  ways without flicker; dialogue only claims what happened, varies, fills placeholders from the record; growth
  bounded per day and by the players met, reaching Super Saiyan from the existing table and only existing
  techniques; promotion binds one body and refuses copies and bodies of the dead; knock-outs, sparing and escapes
  remembered (an honorable character respects being spared; a soldier who beats a player becomes someone); death
  final or survived by chance, never forever; a vengeful loser returns for revenge at a fair level, on loaded ground,
  one at a time, and leaves when done; the world simulation is deterministic and bounded and characters duel
  without the player; separate bonds per player and only that player's story on their screen, through the wire.
- **Dev client (background, scripted):** a Saiyan Warrior named on sight ("Taroh the Challenger") went Super Saiyan
  when outmatched (gold spiked hair, golden aura); greeted Dev in chat ("We meet again, Dev."); knocked out by a
  barely-scratched Dev it was recorded as a humiliating loss and said its Saiyan line; left to bleed out it got away
  (recovering, back in 1.5 days); brought back as a revenge encounter it said a Saiyan revenge line; the Rivals
  screen showed its portrait, record, forms seen and story. Found and fixed: the spared credit (the game forgets the
  last attacker after five seconds), duplicate memories, a stale "fighting" status, the tabs overlapping.
- **Not tested in the client:** ambushes, interventions, alliances and spars (covered by code paths and the
  encounter test, not watched live), several players at once on a dedicated server, long-term play (days of
  returns and growth), natural spawns of Saiyan Warriors.

## 5. Not done / limits

- Dialogue is English only (data files; other languages can add their own files).
- NPC-to-NPC relations are abstract (simulated while away); characters who meet in the world fight by faction as the
  combat AI decides, and their bond records it, but they don't hold conversations with each other.
- Tournament roster characters are named and remember players from matches, but never appear outside the tournament.
- A character's hair follows its form, but its skin and body don't (baked NPC skins).
- No portrait for characters whose entity type was removed by another mod (the screen leaves the frame empty).

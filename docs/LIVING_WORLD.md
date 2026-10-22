# The living world

Progress document for the living-world NPC system (the user's request of 2026-10-22, after Skyrim and Oblivion): NPCs
with homes, jobs, daily routines, moods and goals, who talk, gossip, teach, give quests, travel and can join you. It
extends the two systems before it and shares their data: identities, memories, personalities and relationships come
from the Nemesis system (`docs/NEMESIS.md`), fighting from the combat AI (`docs/COMBAT_AI.md`). Keep this current so the
work can resume in a later session.

## 1. Audit (v0.160.0)

- **Settlements:** none. `world.landmark` has a West City entry that is an empty placeholder (never generated). What
  exists: the spawn dojo and Galactic Patrol outpost (`NpcStructures`, placed once per new world by
  `QuestNpcPlacement`, `/dbz build` for old worlds), Namek dome houses (chunk feature, no residents), the series worlds'
  plazas (`IconicLandmarks`), the tournament grounds, Kami's Lookout, the Cell Games arena.
- **NPCs who live:** none. Quest givers and style masters stand where placed; other-world residents talk. No homes,
  routines, civilians, merchants, guards or travelers.
- **Quests:** `quest.Quest` is static data per giver type (Master, Patrol, the Kais, Beerus, Whis), progress on the
  player (`PlayerData`). No NPC-specific or generated quests.
- **Teaching:** `TechniqueLibrary.learnFree` (teachers and quests: race-checked), TP learning, scrolls; style
  masters (`style.*`) teach styles with affinity and requirements; King Kai teaches Kaioken through his trials.
- **Dialogue UI:** quest boards, the master screen, chat lines from the Nemesis system. No branching conversations.
- **Identity, memory, personality, relationships, growth:** the Nemesis system (built in v0.160.0). **Combat:** the
  combat AI (v0.159.0). Both are used as they are.

## 2. Design

One NPC, three layers, one set of data:

| Layer | What | Where |
|---|---|---|
| Who | identity, personality and traits, memories, bonds, growth, rank | `nemesis.NamedFighter` (+ occupation, home, mood, knowledge, companion state) |
| How it fights | the combat AI | `npc.ai.NpcBrain` (every resident body is a `KiFighter`) |
| How it lives | settlements, schedules, routines, conversations, gossip, teaching, quests, companions, travel | `living.*` |

Every town resident is a named character in `NemesisData` (an identity with a body), so a student you meet in town can
become your rival, your friend, your companion, with the same memories. Civilians are `KiFighter`s too, but their
occupation keeps them out of fights: they flee and hide.

Simulation layers: a player within `living.activeRange` → full routines; loaded but farther → routines decided four
times less often; unloaded → nothing ticks, and a resident's place is worked out from its schedule when its chunk loads
(and travelers move abstractly).

## 3. Plan

Stage 1 (this release): settlements and points of interest, the Ginger Town builder, residents (entity, looks,
identities), occupations and data-driven schedules, routines and movement, moods and goals, social life between
residents, conversations (data-driven, branching, server-validated) with their screen, rumors from real events, world
events (attacks, fame). Stage 2: companions and their commands, masters and lessons, dynamic quests, travelers.

## 4. Built (stage 1, v0.161.0)

All in `com.dbzenith.living` unless named otherwise.

- **Settlements** (`Settlement`, `Poi`, `LivingData` = SavedData `dbzenith_living`, schema 1): name, world, centre,
  radius, points of interest (home, shop, market, tavern, dojo, plaza, patrol, gate; each a floor a resident can stand
  on), places nobody could reach (dropped from use), an alert until a game time, wanted players (not saved: it blows
  over). `LivingData` also keeps rumors (256 at most), each player's fame and fame tier, the placed flag and the day news
  last arrived.
- **Ginger Town** (`TownBuilder`): a round levelled clearing blending into the land at the edge, two crossing roads
  with gates, lamps and hedges, a plaza with a fountain, benches and two market stalls, seven dome houses (two beds
  each, flower beds by the door), a shop with a counter and shelves, a restaurant with tables and a kitchen, the dojo
  (`NpcStructures`), and the game's own oaks and birches round the edge. Every place is registered
  as a `Poi`. A new world gets it once, on the first login, on dry, fairly flat land 64-184 blocks from spawn
  (`Living.placeIfNew`; the site is read from the terrain generator without making chunks). Existing worlds never get
  it on their own: `/dbz settlement build [name]` builds one where you stand. Config `living.autoBuildTown`.
- **Residents** (`Resident`, a `KiFighter` registered as `dbzenith:resident`): each is the body of a Nemesis character
  (`NamedFighter` with `occupation`, `settlement`, `home`, `look`, `mood`, `knowledge`); there is no second identity
  system. `Residents` makes them (a temperament fit for the trade, born traits incl. the new curious, shy and greedy, a
  level by trade, a goal, the trade's techniques) and peoples a town (master, students, guards, merchant, cook,
  citizens; `living.townPopulation`, default 10). Townsfolk are a faction (`Faction.TOWNSFOLK`) allied with each other
  and against the Frieza Force, the Red Ribbon Army and the damned. Civilians never fight (`KiFighter.aiMayFight`);
  fighters use the combat AI as it is (`KiFighter.aiProfile` gives the AI the resident's own profile). Their looks:
  eleven original townsfolk skins made by `tools/ArtGen.java` (`only townsfolk`) with 3D hair and caps from `NpcLooks`,
  drawn per entity by `client.render.VariantRenderer` (synced skin). A knocked-out or "dead but survived" resident
  recovers at home (at most `living.recoverMinutes`); a body lost with its chunk is replaced once its place has been
  ticking a while; a dead resident is mourned and, once a day, a newcomer moves into a town below its limit. A resident
  summoned by command or egg becomes a citizen of the nearest town with a free bed (or of no town: it strolls).
- **The Nemesis system, adjusted:** residents don't count toward `nemesis.maxCharacters`, are never retired to make
  room, are skipped by encounters and the world simulation (they have their own life), never leave the world after a
  fight, and civilians don't start spars when struck. `NamedFighter.onMemory` lets the living world hear every memory.
  `Bond` gained `talks` and `told`.
- **Schedules** (`Schedules`, data `data/<ns>/schedules/<occupation>.json`, hours 0-24 with sunrise at 6): blocks of
  sleep, home, work, train, social, eat, wander, patrol, shop, travel. Each resident lives it its own way: an hour
  earlier or later (from its id), a day off one day in seven (not guards), shy residents skip the crowds, competitive or
  ambitious fighters train instead of socialising, curious ones wander, tired ones go home early, worried ones stay in,
  and an alert sends civilians home and fighters out.
- **Routines** (`RoutineGoal`): where the activity takes it (its home, its workplace, the dojo's master spot or floor,
  tavern tables, the plaza or the tavern in the evening, market spots, the guards' round in turn, a wander between
  public places), walking there, and once there: sleeping in its own bed (beds shared out by who moved in first),
  drilling at the dojo (the Nemesis training aura and greeting), working its counter, moving on along the round.
  Layered: near a player (`living.activeRange`) it decides every second, farther every four; a body that loads far
  from where its day has it, with nobody near, is simply there. Places it fails to reach three times are dropped.
  `FleeGoal`: civilians who are hurt, or near a threat while the town is on alert, run home, then away.
  `TalkGoal`: it stops and faces a player it is talking to.
- **Moods** (`Mood`: happy, relaxed, excited, worried, annoyed, angry, tired): set by events for a while, else from
  temperament (tired at night). They change what it says, whether it chats, and its schedule.
- **Goals** (`NamedFighter.Goal` + strongest, impress the master, business, explore, find a student, protect the town):
  from trade and traits; conversations speak of them.
- **Social life** (`Social`, every two seconds for loaded residents): idle residents near each other may chat
  (likelier for the friendly and curious, rarer for the shy, never the angry or worried; at most `living.maxChatsPerTick`
  started per tick): they face each other, their bond moves by how well their temperaments suit (the greedy and the
  proud grate), each passes on the newest news the other hasn't heard, and a player within 10 blocks overhears a line
  (at most every 30 s). Residents at the dojo pair off for drills (strikes and guards, no harm) and both gain; more with
  the master watching. Residents greet a famous player or a friend (once a day each) and warn off a wanted one.
- **Conversations** (`Conversations`, data `data/<ns>/conversations/*.json`, `ConversationPackets`,
  `client.screen.DialogueScreen`): branching nodes; lines and options with conditions (trade, mood, traits,
  personality, goal, relation, affinity, first meeting, talks, fame, hour, alert, wanted, has news, bond flags,
  fighter, stronger than you, fought before, activity) and actions (flags, affinity, rivalry, mood, tell news, remember,
  spar, close); placeholders incl. `{rumor}`, `{master}` and `{where:<place>}` (real directions). The most specific
  line that holds is said; situations that must win (wanted, an alert, a first meeting, being woken) weigh most. The
  server sends only the line and the options that apply and checks every pick (the session, the same body, still in
  reach and not fighting, the option offered, its conditions still true). Conversations end when you walk off, after
  two minutes idle, on logout, or when the resident is attacked. Number keys pick; the world stays in view.
- **Rumors** (`Rumor`, `Rumors`, lines in `data/<ns>/rumors/*.json`): made from real Nemesis memories (a character beat
  or lost to a player, was spared, survived death, reached a form, won a tournament, beat another character, rose in
  rank, died) and town events (an attack, a death, a newcomer, a famous player). Heard first by the town where it
  happened (within 160 blocks), passed on in chats, and once a day a little news from elsewhere reaches each town
  through those whose trade brings it (merchants everything; guards threats; the master and students fighters;
  citizens and cooks town life). Told to the player it is about, a rumor uses its "_you" lines.
- **World events** (`Living`): a blow to a resident from outside puts its town on alert (`settlement alarm`), sets
  moods, is news once per attack; a player who did it is wanted (guards and, during the alert, every fighter of the
  town go for them; witnesses think less of them); killing a resident is mourned and remembered by the whole town.
  Guards always, and other fighters during an alert, take on monsters and invaders inside the town. A player who kills
  an attacker during an alert gains fame and thanks. **Fame** (`Fame`: unknown, heard of 30, known 100, famous 300,
  legend 800) grows by beating known fighters and defending towns; a new tier is news.
- **Commands** (`LivingCommands`): `/dbz settlement build [name]` (where you stand), `build nearby` (the best dry,
  flat site 64-184 blocks away), `list`, `info`, `alarm`; `/dbz living rumors`, `fame <player> <n>`, and
  `talk <resident>` / `choose <n>` (a conversation as if you right-clicked, for debugging and screenshots).
- **Config** `[living]`: enabled, autoBuildTown, activeRange, townPopulation, socialLife, rumors, maxChatsPerTick,
  recoverMinutes. The living world needs the Nemesis system on.
- **Network:** protocol 56 (three conversation packets).

## 4b. Built (stage 2, v0.162.0)

- **Companions** (`Companions`, `CompanionGoal`): ask a fighter resident "Will you travel with me?". Whether it will is
  its own decision (`Companions.refusal`): civilians aren't fighters, nobody goes with a stranger (affinity under 15),
  someone it dislikes, or someone its town wants; a master won't leave its students below 45 affinity, a guard its post
  below 30, the cowardly the safety of town below 50; the arrogant won't follow anyone weaker; nobody leaves while the
  town is on alert; one companion can't be shared; `living.maxCompanions` (2). Each refusal has its own lines. Its
  orders, in conversation: follow, wait here, defend me (it goes for whoever attacks you), fight alongside me (also what
  you attack, and threats round you), stay out of fights, go home for now, let's train (a spar), let's talk, let's part
  ways. It keeps up with you (walks, runs, and appears beside you when left far behind or across a wall), never attacks
  you or your other companions (a stray blow from you costs a little goodwill, not a spar or an alarm), fights with the
  combat AI by its orders, and leaves if it comes to dislike you. When you log out, when it is hurt badly enough to go
  recover, or when its town is attacked (guards and masters), it goes home; talk to it to bring it along again. All of
  it is on its character (`companionOf`, `companionMode`, `waitPos`), so it survives saving.
- **Masters' lessons** (`Lessons`): a master teaches existing techniques only (its school's curriculum: Ki Blast, Ki
  Sense, Rapid Volley, Wave Beam, Solar Flare, Explosive Wave; and anything its character learned), the first one you
  don't know, can use (race, level) and haven't been taught by it. Only someone it trusts (affinity 15). Three steps,
  kept on your bond with it so they survive saving: it demonstrates the technique (fired harmlessly into the sky, or its
  aura for a self technique), you hold your ki charged beside it for five seconds in all, then a test spar with it;
  when that spar ends you have the technique (`TechniqueLibrary.learnFree`), it is never taught twice, and the master
  is glad of a student. You can give a lesson up.
- **Errands** (`Errand`, `Errands`, kept in `LivingData`): quests made from the town's real state, up to three offered
  per town, one per resident: a master wants two of its students sparred; a student wants to train with you; a guard
  names a real threat (an active named fighter of the Frieza Force, the Red Ribbon or the damned within 600 blocks), or
  else wants five monsters near town killed; someone wants a message taken to someone (in another town when there is
  one); the cook wants ingredients, the merchant goods; someone at odds with a neighbour (their bond says so) asks you to
  talk to them; the curious want to hear about a real landmark nearby (Kami's Lookout, the tournament grounds, the Cell
  Games arena, the Frypan Mountains: read from the terrain without making chunks). Progress is read off the world
  (spars counted on bonds, the threat's defeat in its own memory, kills near town, who you talked to, where you went,
  what is in your bag); turned in to whoever asked, once (it is removed before the reward is given): training points,
  their goodwill, a little fame. Untaken errands lapse after three days, taken ones after five (with a little
  disappointment). At most three at a time per player.
- **Travelers** (`Travelers`, `Occupation.TRAVELER`): `living.travelers` (2) fighters of the road. On the road they are
  abstract (Nemesis status AWAY, where they head and when they get there; a quarter day per 500 blocks); they arrive at
  a town (seen coming in at a gate when the town is loaded), lodge at the restaurant for a day or two as residents (they
  chat, train, can be talked to and recruited), then walk out through a gate if someone is about to see it, or are
  simply gone. They hear the world's newest news on the road and tell each town up to three things on arrival, taking
  its news with them. A companion traveler stops travelling while it is one. A stale body of a townsperson who is away
  (on the road, recovering) is refused when its chunk loads.
- **Town lines for fights:** townsfolk have their own words when a spar starts and when they defend their town (data
  barks "spar_start" and "defend"), instead of the Nemesis rival lines.
- **Fixes from play:** residents crowding one table or the kitchen no longer mark it unreachable (close enough counts,
  and places get another try each day); a companion left behind with no clear spot beside you appears where you stand.
- **Conversations:** new conditions (companion, companion_mode, refusal, lesson, lesson_refusal, errand, traveler),
  actions (recruit, command, dismiss, lesson_start, lesson_test, lesson_quit, errand_accept, errand_turn_in,
  errand_abandon) and placeholders ({lesson}, {errand}, {errand_progress}, {reward}); the stage-2 dialogue is in
  `data/dbzenith/conversations/living_stage2.json`. Up to twelve answers (the key 0 picks the tenth).
- **Commands:** `/dbz living errands`, `/dbz living travelers`; `talk` and `choose` print the options offered.
- **Config:** `living.travelers`, `living.maxCompanions`.

## 4c. Companions and parties (v0.163.0)

- A companion counts its player's party (the Party tab's group, `social.SocialData`) as friends
  (`Companions.friendOf`, `Resident.isFriendOf`): it never targets a party-mate or a party-mate's companion, a
  party-mate's stray blow at it is forgiven like its own player's (a little goodwill lost; no spar, no town alarm,
  never wanted for it), and in "defend me" and "fight alongside me" it also goes for whoever attacks a party-mate within
  16 blocks.
- The Party tab lists each member's companions under them (name, trade, orders: following, waiting, defending,
  fighting alongside, staying out of fights, at home), not selectable; the header counts them. Without a party it lists
  your own companions. The server adds them to the party snapshot (`SocialService.companions`).
- It still travels with, and takes orders from, its own player only.
- GameTest `companionsCountYourPartyAsFriends`; 355 GameTests pass, twice in a row. Dev client: the Party tab alone
  ("Your companions: Tarrag · Town guard · at home") and in a party ("1 companion", Tarrag defending under Dev).
  Devshot names `party_...` open the Party tab, `partyc_...` create a party first (dev only).

## 5. Testing

- **GameTests** (`gametest.LivingTests`, 8): the living data and the residents' new fields survive saving; schedules
  shaped by trade, temperament, mood, days off and an alert; residents are townsfolk characters (faction, civilians
  don't fight, skins by trade, not counted against the Nemesis limit); a resident walks to where its day says;
  conversations branch and every pick is checked on the server (unoffered options, another body, walking away; a
  wanted player gets other options; choices are remembered); a real defeat becomes a rumor heard by the town where it
  happened, makes the player's name, is told differently to the player it is about, and passes on in a chat; an attack
  alerts the town (wanted, the civilian runs, news, guards go for the attacker, witnesses think less of them, fear and
  anger); the town builder's places all have a floor and room to stand, and two beds a home. 349 GameTests pass,
  twice in a row.
- **Made robust on the way** (they depended on where the test framework placed them, which the new batches changed):
  the planet-transmission test now has dry land under its landing chunk (it could be open sea); the enemy-factions
  test fights in a closed pen of its own (the Namekian flew out of reach, or the Saibaman wandered off into water).
- **Dev client:** Ginger Town built near the dev world's coast (`build`) and in a forest (`build nearby`, a round
  clearing); residents at lunch in the restaurant, students chatting (overheard: a real line naming their master),
  everyone in their own bed at night except the two guards on patrol, the conversation screen (the first meeting, a
  famous player's welcome, news told about you), and an attack: the town on alert, the rumor known by all ten, the
  guards, students and master coming for the attacker. Fixed from what the screenshots showed: the painted anatomy's
  outer layer over the townsfolk's clothes and faces (`ArtGen` `dress()` and `hatless()`; the Saiyan Warrior had the
  same hidden face and is regenerated), a square slab of land on the sea (the levelling is round now), bare grounds
  (trees, hedges, flower beds), sleepers woken by their own routine, two towns of identical people (the town's id is
  in every resident's seed), names running out (a pool of fifty; the dead give theirs back), a conversation panel that
  hid the person you talk to (two columns, lower).

- **Stage 2 tests** (`LivingTests`, 5 more): a companion decides for itself (a cook isn't a fighter; nobody follows a
  stranger; a guard who trusts you does), joins through the conversation, takes orders (wait where told), never turns
  on you, catches up when you go 38 blocks away, goes home when you log out and is still your companion; a master
  teaches an existing technique you don't know, step by step (demonstration, ki held beside it, the test spar), and
  never the same one twice; the cook's errand is taken, refused without the goods, paid once and remembered, and a
  message is delivered by talking to its recipient; a traveler arrives, brings news from the road and takes the town's,
  then moves on; a traveler due to leave walks out through the gate and is on the road. 354 GameTests pass, twice in a
  row.
- **Made robust on the way** (the new batches moved where other tests are placed): the duel ring-out test's staying
  player can no longer be knocked out by a neighbouring test, the Nemesis promotion test clears earlier tests'
  leftover characters first, travelers ignore rumors dated in the future (tests make some).
- **Dev client (stage 2):** errands from the real town (the cook's raw cod, a guard naming a real Nemesis Saiyan
  Warrior about, messages, a student's spar) offered, taken, turned in (the cod taken from the bag, 150 training points,
  the cook's opinion up); a refusal (an arrogant student stronger than Dev); a guard who joined, the orders menu, the
  guard catching up in the forest; a master's lesson (Ki Sense: the offer and the demonstration); two travelers on the
  road, arrived, one in town telling Dev its news.

## 6. Not done / limits

- Lessons' ki-control step and the test spar were checked by GameTests, not by hand in the dev client (it can't hold a
  key); the walk to a gate likewise.
- Errands have no journal screen yet: what you took is said in chat when you take it and by whoever asked.
- The enemy-factions combat test still fails now and then (its fighters' outcome varies), as before this release.
- One town is built (Ginger Town); other settlements come from the same builder by command. West City stays a
  landmark placeholder.
- Drills between residents are visual (strikes and guards, no damage); real NPC-against-NPC spars with the combat AI
  would need the AI to accept an allied sparring partner as a target.
- Doorways are open (no doors), so routines need no door handling.
- Iron golems would attack residents (they are `Monster`s to vanilla, as all the mod's fighters are); towns have none.

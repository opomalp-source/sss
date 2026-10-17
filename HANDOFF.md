# Handoff: where Dragon Block Zenith stands (v0.144.0, 2026-10-17)

## The prompt to paste into a new session

> You're continuing work on **Dragon Block Zenith**, my Minecraft 1.20.1 Forge mod (Java 17), a Dragon Ball game
> with races, forms, ki attacks, combat, auras, flight, quests and an other world. The repo is attached (or cloned
> from https://github.com/opomalp-source/sss, branch `main`).
>
> Before doing anything, read `CLAUDE.md` (how to build, test, release and check visuals), then `HANDOFF.md` (where
> things stand), the last few entries of `DEVLOG.md` and the open items in `TODO.md`. Follow their workflow: each
> finished version gets a DEVLOG entry, TODO and docs updates, a `mod_version` bump, all GameTests passing, and a
> release with `tools/release.sh` (direct install to `.minecraft/mods`, no updater). Check how things look with the
> dev-client screenshots before releasing, because I want it all to look great. Use the official Dragon Ball names in
> anything players read, keep every asset original, and leave story mode for last.
>
> The latest version is v0.144.0. When you've read up, tell me in a few lines what you see as the state of the mod
> and what you'd do next, then wait for me.

## Recent work (newest last; details in DEVLOG.md)

- **v0.127-128:**
  - Knock-out, carry, grip finishers.
  - Limbs and blood: race-coloured blood, crawling on a lost leg, the left hand taking over for a lost right arm.
  - Stumps that must be closed (bandage, sear with ki, Senzu); Namekian and Majin regrowth with an animation.
  - Race-tinted battle damage in three layouts; earned scars that fade from red to pale; no knock-out text.
  - Guide: `docs/KNOCKOUT.md`.
- **v0.129.0:**
  - Ultra Instinct and Mastered Ultra Instinct are techniques over any form (anyone taught by Whis).
  - New auras after the user's pictures: UI wispy blue-violet with dashed light streaks; Super Saiyan God a fire.
- **v0.130.0:**
  - Great Ape: only at a full moon, after looking at it six seconds.
  - A new ape body (`ApeShape`, `GreatApeModel`, `tools/GreatApeGen.java`) with a roar and chest beats.
  - Guide: `docs/GREAT_APE.md`.
- **v0.131.0:**
  - The transformation burst: a pillar of light, an energy dome, the ground answering.
  - Aura settings: own aura, intensity, ground rings, aura light. This finishes the aura rework (CX-24).
- **v0.132.0:**
  - Clothes and armour: richer painted textures, thick parts standing off the body, layered Saiyan pads and the
    Frieza Force's sweeping pads.
  - Head clothing items: Piccolo's turban, bandana, headband.
  - Guide: `docs/CLOTHES.md`.
- **v0.133.0:** feel pass. Scars come from 15% blows and from being knocked out; an open stump slows healing to a
  third.
- **v0.134.0:** the Great Ape after the user's pictures.
  - The player's own figure grown giant (ten times, 18 blocks) under fur, their hairstyle as the mane, an ape's
    face with red eyes and fangs (`ApeLayer`, `ApeHeadModel`, `ApeShape`, `tools/GreatApeGen.java`).
  - Every footfall and landing shakes the view, with dust (`GiantSteps`); a giant's step, reach, stride, trampling
    (`GiantBody`).
  - The change tears cloth; battle armour stretches; metal armour bursts off.
- **v0.135.0:** Great Ape growth and camera, the first priority in the user's next Great Ape pass.
  - Hunching, straining breaths, growth from normal size over about three seconds; fur arrives part by part;
    the muzzle and ears grow out, the mane grows and changes colour, the original face strains then fades.
  - Full-sized roar, sound, dust, shake and title cut-in together; chest beats and recovery. About eight seconds.
  - The camera rises and retreats with the visible body, frames the torso, eases farther out for the roar and
    returns; camera-corner collision checks. First person rises with the growth too.
  - A witnessed transition starts the sequence; seeing an existing ape or switching ape variants does not replay it.
    Reverting interrupts it and cancels the pending roar. The server still applies its form size and rules immediately.
  - 246 GameTests; front/rear growth recordings, restored face, mane framing, battle armour, first-person rise and
    wall collision checked in devshots. Guide: `docs/GREAT_APE.md`.

- **v0.136.0:** user feedback on the Great Ape addressed: ground-stomp offense only (primary/heavy attack), 20-tick
  wind-up, one contact, recovery/cooldown, support/wall/elevation/PvP/limb checks; planted feet during growth and
  attacks. Rewritten transformation choreography and original ape idle/walk motion clips. Armour depth fixed so
  fur cannot cover surviving battle armour during growth. White pupil-less eyes while staring at the moon, synced
  to observers and restored immediately when looking away. 248 GameTests. Protocol 41.

## Known gaps and ideas

- NPC skins dressed in the outfits (soldiers, the brute) are baked images and keep the old outfit look. Re-baking
  them needs a full `ArtGen` run, which rewrites many other images too: check its diff carefully.
- The bandana is still a boxy shape on the square head.
- Scars are drawn on the body layer, so a shirt painted into a plain Minecraft skin doesn't cover them (a gi or a
  jacket layer does).
- Regrowth isn't animated in your own first-person view.
- The Great Ape deliberately attacks only by ground stomps now, as requested. Seek play feedback on the wind-up,
  impact, recovery, new locomotion and giant camera; do not add a mouth blast without a new request.
- A true duel simulation (player against NPC, every race) for pacing hasn't been built: the feel pass used the
  balance reports (`BalanceReport`, `BalanceReport2`, written to run-gametest/*.md).
- Open TODO items: more content (bosses, planets, a space dimension), the original sound set, Animation v5 (combat
  actions as data, CX-18b), the CX-17 originals, and story mode last.

## Tools on the user's Windows PCs

- The first PC (user `sunam`): JDK 17 at `C:\Users\sunam\.jdks\jdk-17.0.20.1+1`; Python 3.12 and ffmpeg are
  installed (needed for the `.py` generators; old shells may not have them on PATH).
- The second PC (user `musta`, from v0.134.0): JDK 17 at `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` (set
  `JAVA_HOME`); no Python on PATH. Windows long paths are off, so keep the repo at a short path (or map one with
  `subst`): deep folders break the Minecraft toolchain. The current checkout is
  `C:\Users\musta\Documents\Codex\2026-10-16\you-re-continuing-work-on-dragon\repo`, mapped to `Z:`. Its dev server is
  set up (`run-server`, RCON password in `server.properties`; do not print it). The scripted client needs
  `pauseOnLostFocus:false`. Leave `server-ip` empty for its `localhost` IPv6 connection.
- User override (2026-10-16): direct installation into `C:\Users\musta\AppData\Roaming\.minecraft\mods`; remove old DBZ jars. No updater publication. This overrides older release notes above.

## v0.137.0 camera follow-up
F5 front/rear cameras pivot at the eyes for every scaled form, with visible-growth smoothing for Great Ape.
The transformation cinematic also targets eye height. Giant distance and collision checks remain.

## v0.138.0 Great Ape eye visibility follow-up
The v0.137 camera change alone left the chin raised: idle/walk head offsets compounded the rig's head counter.
Offsets now balance that counter for a forward gaze. Local front F5 renders neutral look pitch independently
of camera pitch; gameplay aim, observers, rear/first-person views and authored action poses keep their behavior.

## Galaxy / space request — Phase 1 (v0.139.0)
User approved the eight-phase design after reviewing existing code. Phase 1 is deterministic catalogue + sparse
SavedData + server-fed atlas + commands; no runtime procedural dimensions/space flight yet. See docs/GALAXY.md
for architecture, commands, compatibility addresses and the remaining phase sequence. Preserve v1 generation
output (fingerprint tested). Network protocol 42. Existing Earth/Namek/Northern/Beerus dimensions remain intact.
The user explicitly asked for a commit and push after each space phase; direct installation only, no updater.
Phase 2 is now complete below. Story mode remains last.
## Galaxy / space request — Phase 2 (v0.140.0)
Procedural worlds now instantiate on demand using bundled MIT Infiniverse 1.20.1-1.0.0.5. One serializable,
stateless terrain generator covers the landable archetypes, caves/ores, flora and floating asteroid clusters.
Landing is asynchronous, capped, server-authoritative and integrated into the atlas/pod flow. Original static
worlds remain untouched. Independent finite borders with edge haze; full save/close/unregister on idle unload;
region files/builds retained. Vanilla beds survive unload/death cloning; respawn/Other World return reloads and
validates an unloaded bed world. Location checkpoints recover reconnects. See docs/GALAXY.md and THIRD_PARTY.md.

Physical gravity uses Forge's synced living attribute plus item/arrow/thrown arcs and scaled fall damage;
existing ki flight drain and training/strain consume planet gravity. Hostile atmospheres consume air after grace;
existing breathless traits, barriers and pods protect, as does the new full four-piece craftable Space Suit.
Protocol 43 adds a dimension-bound environment HUD packet. Config is dbzenith-planets.toml: maxLoaded4,
idleTicks1200, landingGraceTicks300, physicalGravity, atmosphereHazards. Full slots reject requests safely.
All 257 required GameTests passed, including real landing, independent border, saved build unload/reload and
vanilla respawn into an unloaded world. Dev screenshots reviewed crystal/jungle/volcanic/barren/ocean terrain,
HUD and the original suit; its opaque helmet outer layer was fixed to reveal the visor.

Next is Phase 3: real Space dimension, spheres/starfield, ascent/re-entry, zero-g and sound. Current planet skies
are archetype colors, not orbital skies. Gas giants have no landing surface until upper-cloud travel exists.
Keep galaxy catalogue v1 and terrain v1 stable after release. Story last; direct install only, no updater.

## Galaxy / space request — Phase 3 (v0.141.0, 2026-10-17)
Runtime per-system orbital space now connects supported planets at altitude512. Local orbital coordinates,
independent entity lists/borders and a separate active-orbit cap avoid precision and multiplayer collisions.
Surface/orbit handoffs preload chunks asynchronously, preserve bounded momentum and map re-entry to the
matching surface position (departure anchor for close returns). Space Pods have a continuous launch option.
Original static/surface worlds and galaxy/terrain-v1 catalogue contracts remain unchanged. Protocol44.

Client: cached star/nebula VBO; original async procedural planetary material maps, angular-size mesh/texture
LOD, lit spheres/terminators, rotating cloud shells, atmosphere rims, moons/rings, colored stars/corona/glare.
Materials use public identity only, never server terrain seeds; a48-material cache closes on logout. Physical
asteroid clusters are generated only in visited chunks of the inner belt. Stars burn/blind; gas upper clouds
repel deep entry. Zero-g preserves drift; V enables existing ki-powered thrust/braking, Sprint boosts. Existing
vacuum races, suits, barriers and pods protect. Original synthesized drone/breath/heartbeat/entry/music loops,
wind fades and muffled one-shots. Arrival plasma/rim glow, rumble and shader heat shimmer use existing settings.

All260 GameTests pass: three new space tests cover geometry/inertia, signed addresses/belt determinism/codecs,
actual asynchronous ascent and entry, zero gravity, map identity, packet bounds and matching-location return.
Sound fixture now validates both string and streamed-object JSON entries. An existing charged-blast hit fixture
was isolated into its own batch after repeated concurrent interference; assertions unchanged. Dev screenshots
and motion clips reviewed; ascent horizon and clip input duration corrected. Development-only profiling at
1280x720/renderDistance7/max60fps showed approximately58-60fps in space,60fps on the planet. This is a local
capped smoke check, not a broad performance guarantee. Dedicated-server and dev-client restart also exercised.

Config dbzenith-planets.toml adds maxLoadedOrbits4 and orbitalTravel. Operator previews /dbz galaxy orbit
<players> and /dbz galaxy approach <players> <planet index>. Guide: docs/GALAXY.md. Orbital vertical volume is
bounded; interstellar travel is Phase5. Next: Phase4 authored orbital skies/weather/hazards, landmarks/NPC tables,
cinematic landing discovery and scouter navigation. Story last. Direct install only; no updater/deploy.

## Galaxy Phase 4 (v0.142.0, 2026-10-17)
PlanetExperience/PlanetActivity add dimension-bound climate, landing cards and scouter data (protocol45).
PlanetConditions is a pure seeded weather calendar; shelters, existing protection and grace prevent damage.
Hazards never load remote chunks or edit blocks. Meteors have a40-tick warning and8-pending/world cap.
PlanetLandmarks decorates only new chunks, five clipped block templates; terrain/catalogue-v1 output is stable.
Loaded marked sites get existing NPCs once, recorded sparsely in separate SavedData; peaceful suppresses hostiles.
Discovery waits for ground contact; prior ownership remains; /galaxy namehere and clickable first-owner prompt.
Scouter uses loaded surface entities only, existing god-ki rules, nearest-body data and atlas waypoint projection.
Non-Earth surface sky shares system geometry, day length/tilt, phases/eclipses, colored stars and ring arc.
Trinary stars spread in a shared30k orbital triangle and diffuse light prevents full darkness. Sky lightmap
adapts visually; vanilla server light/crop clocks remain intact. Earth/Great Ape moon remain vanilla.
Three new GameTests cover calendar/projection/eclipse bounds, persistent site claims, real generated structure,
ground-contact discovery and real roof protection against server ki drain. All263 required tests pass. The
existing Needs system consumes planetary temperatures/protection; thirst fixture isolated from biome cold.
False-moon fixture isolated into the active test arena; older landing fixture explicitly simulates ground contact.
Next: Phase5 hyperspeed, cross-dimensional Instant Transmission and remaining iconic systems. Story last.

## Galaxy Phase 5 (v0.143.0, 2026-10-17)
Interstellar owns capped per-player hyperspeed jobs: orbit only, actual piloted pod or near-full ki flight,
10-20s nominal + bounded preload budget (30s total), cancellation/damage/death/realm checks, separate orbit
cap retained. No destination created during the charge. Existing SpaceRuntime async transfer preserves pods;
arrival commits ki/cooldown in the transfer callback. Sneak disengages. Protocol46 adds bounded JourneyPacket.
JourneyClient draws original calm/quality-aware star streaks, progress and a soft arrival tint; original drone
is mixed/pitched as a drive. Existing protection/flight drain still apply. Atlas Jump/Transmit + Places charts.

Instant Transmission reuses RacialSkillEffects validation/cost/cooldown via an action callback. Personal
arrivals are optional additive GalaxyData NBT, keyed by player UUID; ground contact backfills old visits.
Planet casts preload surrounding chunks and revalidate on completion; collision-based non-destructive safe
landing handles snow/slabs and actual body size. No ki spent if preparation fails. Known players are recorded
on StartTracking; cross-world sensing respects god ki and living realms. Existing local/home/lock-on remain.
Picker bounded/paged; atlas allows any personally visited planet. Beerus's god-ki gate remains.

New iconic addresses are 27:-6:14:8/0 (Planet Vegeta,10g red desert/ruins) and -34:9:-21:8/0 (Frieza Planet79,
breathable imperial outpost). Slot8 is valid only at these two previously invalid addresses; all previous
v1 inputs, catalogue fingerprints and terrain remain stable. Original block templates, no downloaded assets.
Namek biome tint now has blue grass/water; legacy structures/terrain unchanged. King Kai/Earth/Beerus retained.
Three new GameTests exercise additive identities/arrival persistence, contact/god-ki/cost/cooldown checks,
actual hyperspeed/cancellation and asynchronous personal Earth return. See DEVLOG for final verification.
All266 required tests pass. Dev-client Survival landings/personal picker/actual Earth return verified;
hyperspeed clips, supported Vegeta ruins and Namek tint checked. Frieza outpost is above sea level.
Next: Phase6 extends protected batched craters, then staged planetary destruction and polish. Story last.


## Galaxy Phase 6 (v0.144.0, 2026-10-17)
Destruction now streams successive candidate shells instead of building/sorting a huge block list on impact.
All ki work is queued, including small ki hits. Dig, cracks, scorch, raised lips and optional restoration share
global candidate/edit budgets; neighbour/shape reconciliation is deferred (128 positions/tick). Vanilla's
queued light checks remain supported; no synchronous light flush or unsafe lighting internals. Small melee
impacts retain a bounded immediate response. Jobs are capped, nearby same-owner impacts coalesce and unloaded
chunks are skipped. No dimension pinning; unload/stop clears remaining session work.

Per-block protection/hardness/fluids/block entities/border checks are repeated when editing. Forge break/place
listeners can refuse or replace a block before writing. No physical falling-block debris can cross a claim:
existing original block particles replace it. Substrate-aware scorch melts sand/red sand to glass and leaves
blackstone/basalt with sparse magma after powerful rock hits. Original dust now lingers 8-16s, quality-aware.

Restoration snapshots coalesce per position and preserve later player edits. Pending updates + snapshots have
a shared maxTrackedChanges cap; due restoration is budgeted and session-local. Legacy kiBlastsBreakBlocks
now requests the same protected crater path; vanilla explosions cannot remove terrain behind the queue.
Native damage blast power is bounded16; malformed values refused. Protocol47.

Operator /dbz crater hit <players> <force> runs the real terrain path at a looked-at block, and status reports
last tick's bounded work. Guide: docs/CRATERS.md. Four new tests cover malformed values, material mapping,
legacy blast bypass and actual queue caps/budgets/edge protection/late hardness/Forge veto/glass/restoration.
See DEVLOG for final validation. Next: Phase7 staged planet destruction/remnants/Dragon Ball restoration.
All270 required tests pass. Final before/impact/settled devshots checked with Crop.java; real Supernova impact
and jobs0/tracked0 completion verified. Seeded scorch breaks up stripes; compacted ki rim material prevents
introduced sand from falling away before restoration. Local capped smoke check59-60fps, not broad profiling.
Story remains last. Install directly to the authorized mods folder, remove old DBZ jar; no updater.


## Galaxy Phase 7 (v0.145.0, 2026-10-17)
PlanetDestruction + DoomData own durable 30-90s core events and a sparse, bounded original-state fissure
journal. Large ball/beam impacts use current battle power/permission; operators share protection/caps.
Earth is always protected; configured worlds, recorded bases/spawns and forced chunks veto/cancel events.
Player placement/spawn markers persist; crater synthetic place events are excluded. Old unmarked builds
must be explicitly protected; there is no whole-save block scan. See docs/PLANET_DESTRUCTION.md.

Detonation seals surfaces in GalaxyData, evacuates through existing async SpaceRuntime (Earth cap fallback),
unregisters empty procedural worlds, blocks pending/legacy/bed/transmission entry and reconnects safely.
Region directories are NEVER deleted: retained storage is the snapshot. Static legacy Namek stays registered.
Restore a planet is appended to Wish (old ordinals intact); actual dragon refuses without consuming a wish
when no target/slot is available. Waypoint selects the destroyed target, then lexical fallback. Restoration
pins its surface, preloads one journal chunk per event, repairs64 entries/tick, preserving later edits/builds.

Remnants uses24 deterministic real block clusters/body. Immutable generator descriptors, only visited chunks;
old empty chunks fill with global2048 scans/128 writes, occupied orbital chunks preserved, player placement
cancels pending fills. Sparse materialization records prevent regeneration of mined fragments. Restoration
stops new generation but retains existing orbital debris/builds. Historical destroyed records leave tick work.

DoomPacket protocol48 sends only current-system summaries, max12 bodies, once20ticks. DoomClient adds warm
sky/fog, accessibility-aware quakes/lifting block particles, compact scouter-style warning, original drone mix,
orbital glowing seams, warm expanding flash/shockwave and distant crust silhouettes blending to real clusters.
Atlas shows remains and disables landing; restored status uses destroyed&&!restored. Hyperspeed still reaches
remnants. Warning suppresses scouter lens/readout and orbital tutorial text, retaining health/air HUD.

Four new GameTests exercise saved clocks/journals/materialization/schema preservation, identity/base/restored
flags, fragment bounds/materials, actual evacuation/sealing/mineable generation/no-regrowth/wish restoration
with retained chest contents, and a refused wish followed by a valid wish on the same dragon. Cap fixture counts
already-loaded dimensions; lifecycle uses an unvisited body so mined regions from a prior test remain mined.
Final validation/devshots below. Next: Phase8 polish, profiling, multiplayer/config/balance. Story last.
All274 required GameTests pass on final sources (phase145-testsfinal.log,50s), offline release build succeeds.
Devshots/two detonation clips checked with Crop.java; connected cracks, warm shockwave, real hot block clusters,
compact warning, stable ground/ruins, sky warming and intact/cancelled sphere. Actual cancellation repaired36
fissure entries and retired the event (saved NBT events empty). Local capped smoke checks59-60fps; broad
profiling remains Phase8. Snapshots are retained world storage, not copied backups; already-placed orbital
remnants remain after restoration. Direct install/no updater. Next: finish Phase8, story last.

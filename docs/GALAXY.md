# Galaxy and space

## Phase 1 — Galaxy atlas (v0.139.0)

Open **`/galaxy`** (any player), or use **Galaxy map** below the Space Pod destination menu.
The existing Space Pod destinations still work exactly as before.

- **Galaxy** shows all eight systems in each of the 25 sectors around the selected sector at the current Z slice.
  It is a local window into a much larger address space, not a precomputed/stored galaxy.
- **Sector** shows its eight systems. Click a star to select it; click it again to enter its system chart.
- Click a planet to see its survey model, gravity, air, radius, temperature, day length, tilt, rings, moons,
  palette, hazards, landmark and population archetype. **Back** returns to the system.
- Drag the chart to rotate it and scroll to zoom. Zooming into a selected star enters its next chart level;
  zooming out returns to the previous level. Scroll over the detail panel to read longer descriptions.
- **Home** returns to your current planet's sector. The cyan ring marks its system.
- Enter `x:y:z:system` in the coordinate box and press **Go**. Sector coordinates are -1024 through 1024 inclusive;
  the system index is 0 through 7. Each system has 3–9 planets, numbered from **0** in commands/addresses.
- **Set waypoint** saves the selected planet for your player, across logouts/restarts. With no planet selected,
  it chooses planet index 0. This is a map waypoint; the flight/scouter marker is planned for the later phases.
- **Find** searches discovered planet names (including player names and generated names) or their addresses.
  It never searches/generates the whole galaxy. Results are paginated, 32 at a time; scroll within each page.
- `/galaxy name "x:y:z:system/planet" My Planet` names a discovered procedural planet. Only its first discoverer
  may name it; names use 2–32 letters, numbers, spaces, apostrophes, dots or hyphens. Existing named Dragon Ball
  destinations keep their official names.

**The atlas remains a computed survey.** Phase 2 below adds landing, terrain, gravity and atmosphere; orbital
flight, authored orbital skies, weather hazards, landmarks and destruction remain later phases. Merely browsing never creates chunks or dimensions,
never grants a discovery and never gives the client the world/terrain seed.

Visiting the existing Earth, Namek, King Kai's Planet or Beerus's Planet records discovery of that existing world.
The first visitor's name is retained. Procedural landing also records discoveries through this same index.

## Commands (operators)

The existing `/dbz` root requires permission level 2. Quote addresses in commands because Brigadier's unquoted
word parser does not accept colons/slashes. In the map coordinate box do not include quotes.

```
/dbz galaxy                             # open your map
/dbz galaxy map Dev                     # open a player's map (also supports selectors)
/dbz galaxy system "0:0:0:0"            # inspect a system from player or console
/dbz galaxy discover "3:-4:5:2" 0        # admin survey: records a discovery for yourself, without terrain
/dbz galaxy name "3:-4:5:2/0" Quiet Horizon
```

The admin discovery command is a testing tool. Normal players cannot mark unvisited planets discovered from the map.

## Architecture and save contract

- `galaxy/GalaxyCatalog`: pure, order-independent generation from world seed + persisted galaxy salt + integer
  sector address + system index. Planets have independent derived seeds; moon seeds derive from their parent.
  Every numeric/string field is bounded. The version-1 fingerprint is tested: **do not change v1 outputs after
  release**. New generation behavior must use a new version and explicit migration/preservation strategy.
- `galaxy/GalaxyData`: sparse overworld `SavedData` (`data/dbzenith_galaxy.dat`), schema/generator version and salt;
  discoveries (owner UUID/name, first visit time, generated name, optional chosen name), per-player waypoints,
  and reserved destroyed/restored/base/snapshot fields. Unsupported versions fail rather than silently resetting
  the galaxy. Terrain seed and world seed never appear in map packets.
- No runtime-dimension dependency is needed for Phase 1. Existing dimensions and region files are not replaced.
  Earth system `0:0:0:0`, Namek `12:-3:6:1`, King Kai's Planet `-9:4:2:0`, Beerus `40:8:-17:3` reserve stable
  compatibility addresses; their planet index 0 refers to the existing dimension. Other dimensions use Earth as
  the chart origin. Their richly authored orbital systems are a later phase, not a migration of existing terrain.
- `galaxy/GalaxyService`: server-only survey generation. At most 200 system summaries, 8 sector summaries,
  9 detailed planets (at most 5 moons each), or 32 search results per reply. Searching uses only the discovery
  index; descriptor generation is limited to returned results. Known-system membership is indexed per request.
- `network/GalaxyPackets`: bounded request fields on the existing SimpleChannel, protocol **43**. Dedicated
  `InputGuard.Kind.GALAXY` bucket (burst 3, refill 2/s), server validation of coordinates and waypoint targets;
  only the sender's waypoint can be changed. UI replies have request IDs; closed/replaced screens ignore stale
  replies, and timed-out requests can be retried. The client never authorizes discovery or destruction.
- `client/screen/GalaxyScreen`: original code-drawn stars, orbit lines and shaded survey spheres, clipped chart
  and detail panels, local view rotation and eased zoom. No third-party textures or runtime galaxy generation.
- `gametest/GalaxyTests`: deterministic fingerprint/order/seed checks, coordinate boundaries, archetype coverage,
  sparse save/load/ownership/naming, preservation of future base/restore data, bounded packet round trips,
  surveys without discoveries/dimensions, invalid requests, and authoritative waypoint storage.

## Remaining phases (approved design order)

2. Runtime planets, parameterized chunk generation, gravity and atmosphere. Validate the MIT-licensed Infiniverse
   1.20.1 implementation against this NeoForge version before adding a dependency; cap/unload empty dimensions.
3. Space dimension, lit planet spheres/starfield, atmosphere ascent and re-entry, zero-g and sound. Use system-local
   coordinates and preload/fade dimension handoffs while preserving motion; a completely stall-free handoff is
   not guaranteed by Minecraft. Flat terrain uses a spherical orbital representation.
4. Orbital skies, hazards/weather, landmarks, NPC tables, first-landing presentation/naming, scouter navigation.
5. Hyperspeed, cross-dimension Instant Transmission and iconic systems (official names, original assets).
6. Extend the existing crater queue: large craters with capped tick work and protection checks.
7. Staged planetary destruction, orbital explosion/remnants and Dragon Ball restoration; Earth/base protection;
   restore player builds only when a valid snapshot exists, with an explicit policy otherwise.
8. Visual/audio polish, multiplayer and save/restart checks, profiling, LOD, settings and balance.

Each phase: appropriate new GameTests + complete existing suite, dev-client visual review, version/docs/devlog,
commit/tag, push main and its release tags as requested for this project, direct install to
`C:\Users\musta\AppData\Roaming\.minecraft\mods`, remove old DBZ jars. **Do not publish to the updater.**

## Phase 2 — Living procedural planets (v0.140.0)

Hold a **Space Pod** in either hand, open `/galaxy`, select a planet and press **Land**. Creative players can
preview without a pod. Existing Space Pod travel cooldown, living-world restrictions and legacy destinations
still apply. Stay within eight blocks of departure while landing terrain prepares. A ridden pod travels with
you and remains sealed. `/galaxy home` returns to Earth with the same transport/cooldown rules.

- Only a landing request creates a planet dimension. Terrain is generated from that planet's seed and archetype:
  temperate, ocean, desert, ice, volcanic, jungle, barren, crystal, toxic swamp, training and floating asteroids.
  Gas giants correctly refuse solid landing; upper-cloud travel belongs to the space phase.
- One codec-backed chunk generator supplies independent terrain, caves, ore clusters, palettes, seas/lava,
  compact trees and crystal growth. World borders have diameter **twice the catalogue radius**; border haze
  increases near the edge. These are finite flat Minecraft worlds, not spherical terrain or wrap-around.
- Living entities use Forge's synchronized gravity attribute. Items and ordinary arrows/thrown projectiles also
  change their arcs; existing no-gravity ki projectiles keep their own movement. Fall damage scales with gravity.
  Existing ki flight uses a bounded square-root gravity cost factor, and existing training/strain rules consume
  the planet's gravity. Legacy Earth/Namek/King Kai/Beerus/Time Chamber behavior is preserved.
- Vacuum, thin, toxic and scorching air consume a 300-point air reserve after a configurable landing grace.
  Empty reserve causes periodic damage through existing combat/body rules. Breathless race traits, active ki
  barriers, a ridden pod or a complete space suit protect you. The environment HUD displays name, gravity, air
  type, reserve and protection. Heat/cold/weather-specific effects remain Phase 4.
- Craft each **Space Suit** piece from its corresponding iron armor piece plus glass, redstone and leather
  (shapeless). All four pieces are required to seal the suit. These use original generated textures/icons.
- First successful landing records discovery; naming/search/waypoints use the Phase 1 store. Cinematic title
  cards and scouter navigation remain Phase 4. Survey day length, orbital positions and hazard descriptions
  are future rendering/content parameters; this phase does not claim custom orbital skies.

Operators can use `/dbz galaxy land <players> "x:y:z:system/planet"`,
`/dbz galaxy sample <kind> <players>` (e.g. `crystal`, `jungle`, `barren`, `volcanic`), and
`/dbz galaxy unload "x:y:z:system/planet"`. Landing safety/cooldown rules still apply to targets.

Server config **`dbzenith-planets.toml`** controls `maxLoaded` (default 4), `idleTicks` (1200),
`landingGraceTicks` (300), `physicalGravity` and `atmosphereHazards`. Occupied worlds, forced chunks and pending
landings prevent unload. New requests fail safely at the cap; occupied planets are never evicted. At most 16
landings prepare concurrently. Chunk requests run asynchronously; no server-thread wait for generated terrain.
Oceans, lava and void worlds receive a small safe landing pad when no suitable nearby dry surface exists.

Unloading explicitly saves and closes region storage, removes the runtime dimension, and keeps region files for
builds. Revisiting reopens those files; it never regenerates over player construction. A per-player location
checkpoint supports reconnecting after their planet unloaded. Vanilla bed references survive unload and death
cloning; returning from Other World or ordinary respawn can reload and validate the bed asynchronously. Missing
or obstructed beds fall back safely. A full planet cap can delay a reconnect/bed return; free a slot and retry.
No old dimensions are replaced. No migration of existing region files or galaxy-v1 descriptor outputs is made.

The MIT-licensed **Infiniverse 1.20.1-1.0.0.5** is bundled in the mod jar, with its license. It has been exercised
on this checkout's NeoForge 47.1.106 dedicated server and client. See [third-party provenance](THIRD_PARTY.md).
Generation and discoveries stay server-authoritative. Protocol 43 adds a small dimension-bound environment
packet to the existing channel; both sides need this version. Browsing unvisited systems still creates no world.

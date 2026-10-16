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
- **Home** returns to your current legacy planet's sector. The cyan ring marks its system.
- Enter `x:y:z:system` in the coordinate box and press **Go**. Sector coordinates are -1024 through 1024 inclusive;
  the system index is 0 through 7. Each system has 3–9 planets, numbered from **0** in commands/addresses.
- **Set waypoint** saves the selected planet for your player, across logouts/restarts. With no planet selected,
  it chooses planet index 0. This is a map waypoint; the flight/scouter marker is planned for the later phases.
- **Find** searches discovered planet names (including player names and generated names) or their addresses.
  It never searches/generates the whole galaxy. Results are paginated, 32 at a time; scroll within each page.
- `/galaxy name "x:y:z:system/planet" My Planet` names a discovered procedural planet. Only its first discoverer
  may name it; names use 2–32 letters, numbers, spaces, apostrophes, dots or hyphens. Existing named Dragon Ball
  destinations keep their official names.

**This phase is a chart and data foundation.** Procedural terrain, landing, space flight, environment hazards,
planet destruction and restoration are not active yet. Survey descriptions are generation parameters, not a
claim that the corresponding runtime worlds already exist. Merely browsing never creates chunks or dimensions,
never grants a discovery and never gives the client the world/terrain seed.

Visiting the existing Earth, Namek, King Kai's Planet or Beerus's Planet records discovery of that existing world.
The first visitor's name is retained. Procedural landing/discovery will use the same record in later phases.

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
- `network/GalaxyPackets`: bounded request fields on the existing SimpleChannel, protocol **42**. Dedicated
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

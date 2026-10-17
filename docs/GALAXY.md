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

## Phase 3 — Orbital space (v0.141.0)

**Leaving a planet:** fly up with the existing ki flight control (**V**) to altitude **512**, or board a parked
Space Pod and choose **Launch into orbit**. The pod climbs continuously. Sky/fog darken from altitude180,
clouds disappear above260, stars emerge and flight wind falls away. A curved distant horizon fills the edge
beyond the real terrain. At the boundary, the server preloads an orbital arrival chunk and fades the handoff.

**Movement:** W/S thrust along your view, A/D strafe, Space rises, Sneak brakes, Sprint boosts. Ki flight must be
active to thrust or brake unless creative; without it you keep drifting. Existing ki costs/seals still apply.
The Space Pod supplies its own thrust. Normal dropped items and projectiles float with zero gravity; real
asteroid clusters in the inner belt can be landed on and mined. Boost is capped at5.5 blocks/tick; interstellar
hyperspeed is Phase5. Gentle camera roll uses the existing flight cinematic camera option.

**Re-entry:** fly toward a solid planet's atmosphere. Its surface chunks preload before the transfer; a close
return uses the departure anchor, while wider orbital travel maps the radial position back to the finite
surface. Arrival is at altitude340 with downward momentum, plasma/fire particles, warm edge glow, rumble,
shader heat shimmer and a rising atmospheric roar. Clouds/terrain appear below. The existing heat-haze,
particle-quality, camera and reduce-flashing settings also govern these effects. Exact stall-free dimension
switches cannot be guaranteed by Minecraft; this uses a short fade and asynchronous chunk preparation, not a
new loading-screen UI. A full planet cap refuses preparation safely; free a surface slot and retry.

**The orbital view:** original procedural surface maps match the catalogue palette, with polar ice/continents,
land/ocean detail, slowly rotating cloud shells, a stellar day/night terminator, atmosphere rims, rings, nearby
moons, colored stars/corona/glare, a dense starfield and soft galactic-band/nebula meshes. These are render-only
spheres over flat surface worlds, not voxel spheres. Parent orbital positions use stable catalogue phases;
moons animate. Fully authored planetary skies/eclipses remain Phase4. Upper gas-giant clouds can be approached
from orbit but have no solid surface; pressure repels deep entry. Stars burn/blind at close range.

Space is a **runtime dimension instance per visited system**, with local coordinates and its own player/entity
list. This avoids coordinate precision problems and prevents players in different systems sharing the same
physical location. Protocol44 syncs bounded server survey descriptors through the existing channel, without
terrain/world seeds. A rate-limited 12-byte velocity hint is validated for finite/capped values and proximity;
it never authorizes destination or world creation. The server owns crossings, caps, discovery and preloads.

No system instance/chunks are created by browsing. Orbital worlds save/close/unregister when empty and idle;
occupied/forced/pending worlds remain loaded. `maxLoadedOrbits` defaults4 separately from surface slots.
`orbitalTravel` can disable automatic surface departure. Restart and reconnect use persistent orbital location
checkpoints and independent borders. Existing surface regions and galaxy/terrain-v1 outputs are unchanged.
Orbital vertical travel is bounded inside the Minecraft dimension's -1024..1024 volume; horizontal border
radius120000 contains all system bodies. Interstellar transitions are a later phase.

The original synthesized Ogg loops are generated by `tools/gen_space_audio.py` (numpy + soundfile), with no
samples or recordings: low drone, sealed-suit breathing, heartbeat, atmospheric roar and swelling harmonies.
New world one-shots attenuate and lower in pitch toward vacuum; UI sounds and lifecycle-managed ki loops
retain their behavior. Wind fades continuously with altitude. Music respects the Music volume slider and
breathing/ambience the Ambient slider. This is a tailored sound mix, not a replacement audio engine.

Material synthesis runs on a dedicated background worker. Meshes use angular-size LOD; far bodies use low
resolution maps. A bounded48-material cache releases textures on logout. The star/nebula mesh uploads once
and is drawn as a VBO; rendering allocates no planet dimensions or chunks. Final profiling is Phase8.

Operator previews: `/dbz galaxy orbit <players>` leaves the current supported planet, and
`/dbz galaxy approach <players> <planet index>` frames a body's daylight side without creating its surface.
These are operator tools, not player interstellar travel. `/galaxy home` remains the transport-based Earth return.

## Phase 4 — living planets (v0.142.0)

Non-Earth surface skies project the orbital system through a rotating frame using the catalogue day length,
axial tilt and position. Stars retain their catalogue color/size; other planets and orbiting moons use the same
compressed orbital geometry as space. Moon illumination produces phases; aligned moons occlude solar disks.
Ringed worlds have a translucent equatorial sky arc. Three-star systems use widely separated barycentric
positions shared by both renderers and a diffuse daylight floor. These are stylized render distances, not an
astronomical simulation. Sky-lightmap adaptation affects presentation; vanilla server lighting, crop rules and
save clocks retain their behavior. Earth retains its vanilla Sun/Moon and Great Ape gaze contract.

Procedural planets have a deterministic 9600-tick weather calendar, including 1800-tick events with gentle
onset/decay. Archetypes select dust, acid, meteors, ion storms, cold or quakes; hot/cold worlds retain a thermal
hazard between events. Heat/cold feed the existing Needs temperature and stamina-recovery system. Dust slows
exposed players, acid/temperature hurt, and ion storms drain ki. Roofs, the
existing sealed suit/pod/barrier/vacuum adaptations and landing grace protect. Weather has bounded local
particles, fog and a quiet original storm mix; quakes respect the cinematic-camera setting. Meteors warn for
40 ticks, then affect a small loaded area without terrain griefing; at most eight impacts are pending per world.
No climate work requests a new chunk. Config: `planetWeather`, `weatherDamage`, `landmarkInhabitants`.

Newly generated procedural chunks can contain rare ruins, crashed ships, crystal clusters, master's shelters
and abandoned outposts, on seeded 512-block cells. Each piece is clipped to its own chunk and graded onto the
terrain. Terrain-v1 noise and existing generated chunks/builds are unchanged; new decorative content appears
only as new chunks reach the surface-generation stage. Asteroid/gas archetypes skip surface landmarks.
Nearby loaded marked sites populate once, with existing wildlife, soldiers, Namekian settlers or a martial arts
master. Sparse `dbzenith_planet_sites` SavedData prevents duplicate inhabitants after unload/restart; defeated
inhabitants do not continually respawn. Peaceful difficulty suppresses hostile inhabitants.

Discovery now waits for ground contact. A player's first landing shows the planet name, type, gravity and first
discoverer. The first discoverer gets a clickable naming suggestion: `/galaxy namehere New Name`. Existing
`/galaxy name "x:y:z:slot/planet" New Name` still works. Named iconic worlds remain protected; old discovery
ownership is preserved. Individual landing-card flags survive reconnect/death through existing player data.

Wear the existing Scouter in orbit for nearest-body type, distance, gravity, atmosphere and a surface signal
summary. It scans only a currently loaded surface (bounded to 2048 entities), omits god ki via the existing
scouter rules, and explicitly reports an unloaded surface as unscanned. This is a loaded-region reading, not
a promise to detect entities in unloaded chunks. An atlas waypoint in the current system gets a projected
marker or direction arrow. No distant world is loaded by scanning or setting a waypoint. Updates are server
bound to the current dimension and sent every 100 ticks; protocol45 adds the small S2C activity packet.

Operator checks: `/dbz galaxy weather dust <players>` (also clear/acid/meteors/ion/cold/heat/quake) previews a
weather event for 1200 ticks; it is transient and does not change the calendar/save. `/dbz galaxy landmark
<players>` previews the nearest candidate within five cells. Previously generated chunks can lack a candidate's
new structure. Interstellar travel/Instant Transmission and the remaining iconic worlds are Phase5; story last.

## Phase 5 — courses and familiar ki (v0.143.0)

Open `/galaxy` in orbit, inspect a system/planet and press **Jump**. A piloted Space Pod provides its drive;
otherwise use existing ki flight with at least98% ki. A completed foot jump costs20% maximum ki and navigation
recharges for200 ticks. Pods retain their pilot and vessel across the transfer. Existing legacy pod routes
remain available; procedural Land now expects your current star system (creative operator previews exempt).
The nominal trip lasts10-20 seconds by sector distance, with up to10 more seconds for preload. At30 seconds
an unfinished trip aborts safely. Arrival waits for the existing async orbital preloader; no world is opened
during charge, and normal active-world caps still apply. A failed/cancelled jump spends no jump cost.

Star streaks expand into an original blue-violet tunnel, with a rising pitch/gain mix of the original orbital
drone and a soft arrival fade. Low quality trims rays; calm/reduced-flash settings reduce motion and tint.
Sneak disengages. Damage, death, losing the pod/ki flight, leaving the departure world or moving away interrupt
the course. Ki flight drain, vacuum and stellar hazards continue during travel. Surface arrival is through
ordinary planetary re-entry or Land in the same system; Jump does not instantiate the surface.

The existing learned **Instant Transmission** skill retains its ki cost, cooldown and ki-seal checks. Its
picker now includes familiar players across living worlds and a bounded list of personally visited planets;
Previous/Next prevent destinations falling off the screen. A player becomes familiar when actually tracked
nearby. Divine targets remain unreadable without god ki. Dead/Other World/Time Chamber cross-world targets
are excluded; existing local/Home behavior remains. Cross-world person arrival checks loaded collision space.

The atlas **Transmit** button works for any personally landed planet once the skill is learned; the same action
is `/galaxy transmit "x:y:z:slot/planet"`. Planet casts require standing still while chunks preload. Learned
skill, ki, cooldown and living state are checked again at completion, then the existing skill pays once. No
charge for failed preparation or a blocked safe landing. Saved arrival regions survive reconnect/death via
optional sparse GalaxyData records; old visited flags work and get an anchor the next time you touch ground.
Landing respects collision shapes and the current form's headroom; it never clears a player's build.

**Places**, at the top of the atlas, opens the six iconic charts. These reveal charts rather than claiming
discovery or loading terrain. Earth, Namek (blue grass/water under a green three-sun sky), King Kai's10g planet
and Beerus's Planet keep their existing dimensions and structures. Beerus's system still needs god ki.
Two additive reserved charts introduce:

| World | Address | Identity |
|---|---|---|
| Planet Vegeta | `27:-6:14:8/0` |10g, red desert, a ruined arrival courtyard |
| Frieza Planet 79 | `-34:9:-21:8/0` |Breathable imperial world, metal outpost above sea level and existing soldier inhabitants |

Only these two chart locations accept slot8. All previously valid catalogue-v1 addresses and terrain noise
outputs are unchanged; new iconic generation cannot overwrite an old procedural planet. First landing records
discovery; the two iconic primary names are protected. All art uses original code/block construction.

Commands: `/galaxy jump "ref"`; operator `/dbz galaxy jump <players> "ref"` uses the same travel rules.
`hyperspeedTravel` can disable jumps. Requests use existing rate limits; protocol46 adds a small S2C journey
clock/name packet. Personal destination data is bounded and requested explicitly, with no terrain seeds sent.
Crater/destruction work is Phase6/7; full performance/balance polish is Phase8. Story last.


## Phase 6 — craters (v0.144.0)

Real ki crater terrain now streams across bounded scan/edit/reconciliation stages, with per-block protection,
substrate-aware glass and hot rock, original lingering dust/rubble, safe optional restoration and no vanilla
explosion terrain bypass. Existing ball, beam and clash systems use it automatically. See [Craters](CRATERS.md)
for config limits, lifecycle tradeoffs and operator checks. Planet-level destruction is Phase7, polish Phase8.


## Phase 7 — planet destruction (v0.145.0)

Powerful ki impacts can destabilize an unprotected planet. A persistent countdown leads to evacuation and an
orbital explosion, followed by real mineable debris. The atlas seals the landing surface until a dragon
restores it. Existing region files and builds are retained. See [Planet destruction](PLANET_DESTRUCTION.md)
for safeguards, config, commands, snapshots and restoration limits. Phase8 is the final polish pass.


## Phase 8 — polish and bounded work (v0.146.0)

Orbital rendering uses shared meshes, distant impostors, conservative culling, a smooth atmospheric rim and
a bounded48-entry LRU material cache with4 generation jobs and paced uploads. Space detail and sound mixing
are independent settings. Settings tabs page inside the window. Atlas searches scan at most512 discoveries
per page, return32 matches and continue using Next; known-system checks use a derived index. Search cursor
semantics use protocol49. Existing saves, v1 catalogue identities and terrain output stay unchanged.

See [Space polish](SPACE_POLISH.md) for measured local frame/sky timings, exact limits, controls, regression
coverage and remaining hardware/human-soak validation. Motion hints are isolated by player and dimension;
menus/focus loss clear thrust while retaining coast. All eight planned galaxy phases are implemented.

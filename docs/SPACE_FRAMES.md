# Space frames and scales

How the places in Dragon Block Zenith's space relate to each other, and the rules every system follows when it carries
a player, a view or a destination between them. Code: `galaxy/space/SpaceFrames` (all surface/orbit transforms),
`SpaceMath` (orbital layout), `GalaxyCatalog` (galactic positions). This is the contract for the space rework
(v0.151.0 onward); later phases extend it rather than invent private conversions.

## The six spaces

| Space | Units and precision | Origin and axes | Where it lives |
|---|---|---|---|
| **Galactic** | catalogue units (double); a sector is 1000 units | sector `(0,0,0)` corner; axes are the orbital dimension's axes | `GalaxyCatalog.SystemSpec.x/y/z` (catalogue v1, never changes) |
| **Star system** | orbit units (double); planet *i* orbits at `2 + 1.7 i` | the primary star; the orbital plane is horizontal | `GalaxyCatalog.Body.orbit/phase` |
| **Orbital (local)** | blocks (double), one runtime dimension per system | star at `(0,128,0)`, planet *i* at `orbit × 6000` blocks, `y = 128` | `SpaceMath.center/star/radius`, `orbit/v1/x_y_z_slot` dimensions |
| **Planet-centred** | orbital blocks | the planet's centre; the sphere has `SpaceMath.radius` blocks | `SpaceFrames.Planet` |
| **Surface** | blocks; east `+x`, south `+z`, up `+y`; altitude from the datum `y = 64` | the surface world's own origin | `planet/v1/...` and the legacy Earth/Namek/King Kai/Beerus dimensions |
| **Camera-relative** | float, relative to the camera | the camera | the renderers (`SpaceSky`), which subtract the camera position in double before any float conversion |

Galactic and star-system axes are the orbital dimension's axes, so a star's direction in the orbital sky is simply
`normalize(target - here)` in galactic coordinates (used from phase 2). Sky frames (phase 2, `StarSky`): orbit draws
galactic directions as they are; Earth turns them on vanilla's celestial sphere (`Ry(-90°)·Rx(time of day)`, the
same turn as its Sun and Moon); other planets turn them with `PlanetSkyMath.local` (their day, tilt and the
ground under you), the same turn as their suns. From phase 3 a planet surface's sky is the galaxy seen through
that planet's spin and the ground frame under you (see "Planet spin"), so the stars stay put across an ascent.
The orbital dimension keeps every
coordinate within ±120,000 blocks horizontally and ±1,024 vertically, small enough for Minecraft's double positions
and float rendering offsets; nothing uses one huge absolute float space.

## Three different distances

- **Logical astronomy** (galactic units, orbit units): what the map shows and what decides travel times.
- **Playable distance and speed** (blocks, blocks per tick): what you fly. Orbital space compresses a system to
  `6000` blocks per orbit unit; ki flight and pods move at most `5.5` blocks per tick (Minecraft's movement checks
  reject faster client motion). Faster travel inside a system is a later phase (a server-tracked cruise), never
  faster physical movement.
- **Render scale and apparent size**: the renderers draw distant bodies at their true direction and angular size
  (`radius / distance`), not at their block distance.

## Surface and orbit

A planet's surface world is a flat finite square (procedural and legacy worlds) or effectively endless (Earth). The
orbital sphere stands for it:

- **Mapping.** Finite worlds use the equirectangular map they always used: `x` runs round the equator
  (longitude), `z` towards the poles (latitude). The north and south border edges collapse to the poles, and
  north–south distances on the sphere are compressed about 2× against east–west. Earth maps a disc of
  `EARTH_DISC = 32,768` blocks with an azimuthal equidistant projection centred on **your own departure point**
  (or world spawn before you have one): an Earth return lands where you left, entering a few degrees away lands a
  proportional distance away (5° is about 910 blocks), and points beyond the disc wrap round the sphere. This is
  approximate on purpose: there is no circumnavigation, the borders are not crossed, and Earth's sphere is a
  personal frame rather than a fixed globe.
- **Scale.** One orbital block of altitude stands for `scale = size / (π × orbital radius)` surface blocks, the
  same ratio the surface wraps round the sphere's equator with (about 3.8 for a 2,048-block world, 8.7 for 16,384,
  17.4 for Earth's disc). Speeds are not scaled: you keep flying at the same blocks per tick.
- **Crossings.** Ascent at `y = 512` on the surface (`ascentAltitude` in orbit). Re-entry where an inbound flight
  crosses the orbital altitude that maps to `y = 364` (`entryAltitude`), arriving there. The 148-block dead band
  means hovering at either boundary cannot bounce you back and forth.
- **The local ground frame.** Under each surface point, `SpaceFrames.basis` gives east, up (outward) and south on
  the sphere. Velocity and view cross a boundary component by component in this frame, so you keep moving and
  looking the same way relative to the ground. Leaving at a standstill still gives a gentle outward push (0.18
  blocks/tick) so you don't fall straight back; arriving always descends at least gently (0.3).

## Planet spin (phase 3)

Every planet turns, and every view of it uses the same turn (`SpaceFrames.Spin`, a body-fixed → orbital rotation):
the orbital sphere's texture, the surface sky, the ground frame of each crossing and the space camera.

- **Procedural and legacy planets:** `Rx(tilt) · Rz(2π · gameTime / dayTicks)`, the world's game time (so every
  player and the server agree, and it survives restarts).
- **Earth:** turned by vanilla's own day, so the catalogue Sun stands exactly where vanilla draws its Sun:
  `earthSkyMatrix = Ry(-90°) · Rx(timeOfDay · 360°) · Aᵀ`, where `A` turns `+Y` to the real direction of the
  system's star. A stopped day cycle stops Earth.
- **Co-rotation.** Within two planet radii you turn with the ground (`SpaceClient`: each tick a stopped player is
  carried by the spin, faded out from two to five radii), so stopping over a place keeps you over it, and the
  space camera turns with you (`SpaceCamera.carry`).

## The space camera (phase 3)

In orbit "up" is a pure function of where you are: the outward normal of the nearest planet within two radii,
easing to the galaxy's `+Y` by five radii (`SpaceCamera.up`). The mouse turns the view freely in that frame (no
pitch limit, no flips at the poles) and the roll that keeps the horizon level is applied to the camera
(`ComputeCameraAngles`) and to the player model (`SpaceCamera.orient`). No mixins: Minecraft turns the player
before the render tick starts, so the camera parks the vanilla pitch between frames and reads the mouse itself.
Thrust follows the camera's own forward, left and up.

## Transfers (phase 3)

One state machine for every crossing (`SpaceRuntime` for ascents, `PlanetRuntime` for entries):

1. **Prepare ahead.** An ascent starts preparing orbit when the climb will reach `y = 512` within its lead
   (`clamp(climb rate × 80, 32, 200)` blocks). An entry forecasts where the dive will cross the entry altitude
   (`SpaceRuntime.entryForecast`): the arrival time counts the dive's measured acceleration up to the 3-block cap
   (`arrival`), the sideways drift is carried that long, and the crossing lands on the ground that will be under it
   then (the spin a forecast's worth of ticks ahead). Preparation starts about six seconds before the crossing and
   loads 5×5 chunks under it (the landing ticket covers all of them).
2. **Follow the course.** Every ten ticks the forecast is renewed; if it moves more than a chunk from the prepared
   area the preparation moves with it.
3. **Cross only at the crossing.** The transfer commits when the player actually reaches the boundary, from their
   state at that moment. An entry lands at the true crossing when it is on the prepared ground (within 36 blocks of
   the prepared chunk's middle), at the prepared point otherwise.
4. **Hold if not ready.** Reaching the atmosphere before its ground is ready holds you just inside it (no deeper,
   no drift: you hover over the same ground) with "Holding at the atmosphere: preparing the ground below..."; the
   crossing goes the moment it is ready and descends gently.
5. **Give up.** Turning well away (twice the entry altitude plus a margin, no longer inbound) or diving away from
   an ascent releases the preparation; nothing transfers later. Timeouts: 600 ticks.
6. **Settle.** No other crossing starts for 40 ticks after one (stamped with the world's game time, which survives
   restarts).

No loading screen: the destination is prepared, so `ReceivingLevelScreen` is suppressed during a transfer and a
light veil and status line stand in. A pod and its rider cross together and it keeps its name, health and
state (`SpacePodEntity.carry`).

## Planet maps (phase 4)

What you see of a planet from space is drawn from the ground it generates (`galaxy/space/PlanetMaps`, server side;
the client never receives a seed):

- **Whole-planet maps** (512 × 256, equirectangular in the sphere's own texture layout). Texel (i, j) shows the
  surface point the sphere renderer puts there: mesh direction → body frame `(-v.z, -v.x, v.y)` →
  `SpaceFrames.surface`. Procedural worlds sample `PlanetTerrain` itself (heights, seas or lava, top blocks, forests,
  relief lit from the north-west); Earth and Namek sample their own climate and biome noise at the estimated ground
  height (vanilla's `depth` is `offset + 1 - y/128`, zero at the ground), so coasts, oceans and their depth, deserts,
  badlands, snow and forests are where the world has them; rivers read as their banks. The small flat worlds are
  their biome's colour. Each texel averages its area (more samples along texels the poles stretch), and rows near
  the map's poles are evened out along their length (the mesh pinches them into small circles).
- **Regional maps** (256², 32 blocks a texel, 8,192 blocks square on a 2,048-block grid) round the point under you:
  the ground far below you on a surface, and the approach terrain within 1.3 radii in orbit. Same ground functions,
  absolute surface coordinates (Earth's are the same for everyone).
- **Transport.** The client asks (`PlanetMapPacket.Request`) the first time it draws a planet or needs a region;
  the server renders off its thread (one low-priority worker), caches 24 maps, replies with deflated RGB (a full map
  ~100–250 KB); requests only for planets of the system you are in, at most 12 per ten seconds. Until a map
  arrives the old material shows. Protocol 52.
- **Earth.** Its whole-planet map is the disc round your departure point (the scene carries the anchor, rounded to
  64 blocks). Seen from far below on Earth's surface the frame is centred on you, so Earth's day there is vanilla's;
  the regional map gives the ground, and the departure disc is used only within half its radius.

**Clouds** are vanilla's own: a planet with air shows, at each surface point, the cloud cell vanilla draws there
(12-block cells of `clouds.png`, drifting 0.03 blocks a tick with the client's cloud clock), averaged over each
texel; the contrast fades at coarse scales so the 3,072-block cloud tile doesn't stripe the planet. The layer sits
at vanilla's cloud height (128 blocks over the datum) at the altitude scale, at most 2% of the radius.

**The view from far below** (surfaces above y 180). The ground is drawn as the world is: flat out to the edge of the
loaded chunks, then curving away with the planet's radius (`scale × orbital radius` blocks) to the true horizon,
every point placed by its ground distance from you, so the map lines up with the blocks; air thickens towards the
horizon from the edge of the loaded ground; above y 260 (where vanilla stops drawing clouds) the same clouds from
above, only where the ground is behind them. **Close orbit** (within 1.6 radii): a fine grid round the point under
you instead of the whole sphere, with the regional map within 1.3 radii.

**The terminator** is the same everywhere: the sphere is lit by the true star direction in the planet's turning
frame, which is exactly where the Sun stands on the ground (`TransitionTests`, `PlanetMapTests`).

## Travel (phase 5)

- **Units.** Between stars, light years (the catalogue's units). Within a system, AU: Earth's orbit is 1 AU,
  12,000 orbital blocks (planet *i* orbits at `(2 + 1.7 i) / 2` AU). Speeds in blocks a second.
- **Cruise (`Cruise`).** From orbit to a planet of the same system: the server keeps you in place, the client draws
  the sky from a point moving along the course (`CruiseClient.view`, smoothstep easing, the same on both sides) and
  the server sets you down at its end, 700 blocks over the planet on your side of it (or over its top when that
  line is blocked), drifting towards it. The course must clear every star by 1,500 blocks and every other body by
  twice its radius; otherwise it is refused. 3–15 s (`40 + 0.8 √distance` ticks). Cancelled, you stop where the
  view had got to. Nothing moves faster than flight: there is one same-dimension teleport at the end.
- **Hyperspeed (`Interstellar`, `SpaceRuntime.jump`).** The course is the catalogue's own vector between the two
  systems (`SpaceRuntime.course`). After a spool-up (up to 2.5 s) the client flies the real star field along it
  (`StarSky.voyage`): each star where and as bright as it is from the moving point, drawn out into a streak; your
  own system recedes behind you. Courses longer than 5,000 ly fly 8,000 ly of the field at a pace the eye can follow
  (the field wraps in a 12,000-ly cube) and draw the destination star ahead, as bright as the true distance still to
  go. You arrive on the line you came along, 900 blocks short of the target planet and still heading for it (the
  sunward approach when that line is in a star's glare or beside another body), keeping your view, and ease to a
  stop over about two seconds unless you steer. The arrival flash covers the change to the destination's own sky.
- **Rules.** One course at a time (a second is refused; disengage first). Disengaging before the transfer leaves you
  where you started at no cost. A dead or disconnected traveller is never moved. Only a pod's pilot sets its course;
  a passenger sees the same trip and crosses with the pilot (`SpacePodEntity.carry` brings every rider).

## Continuity telemetry

Every committed transfer records both sides and its errors against this mapping (`SpaceTelemetry`, last 64 per
server, nothing saved): position error in blocks, view-direction and pitch change in degrees, velocity direction
change and speed change. `/dbz galaxy transitions [player]` lists the last eight. Set the JVM property
`-Ddbzenith.spaceTrace=true` to log each one.

Reading the numbers: an intentional rule shows up honestly (a standstill ascent reports a 180° velocity direction
change because it is pushed outward; an entry's position error is mostly how far the player moved during the
preload, at the orbital-to-surface altitude scale).

## Acceptance tolerances (phase 1 onward)

| Check | Tolerance | Where |
|---|---|---|
| Surface → orbit → surface | exact to 1e-4 blocks inside the mapped area | `SpaceFrameTests` |
| View carried and back | under 0.01° | `SpaceFrameTests` |
| Real ascent | position under 0.01 blocks, view under 0.5° | `SpaceTests`, telemetry |
| Real re-entry | velocity direction under 5°, speed within 10%, view under 0.5° (pitch 2°) | `SpaceTests`, telemetry |
| Earth return 5° off the departure | lands 910 blocks away (was 833,333) | `SpaceFrameTests` |
| Ten ascent/entry cycles | no drift (under 1e-4 blocks per cycle) | `SpaceFrameTests` |
| Dead band | at least 128 blocks between entry arrival and ascent | `SpaceFrameTests` |
| Spins | proper rotations; a turning planet still maps both ways to 1e-4 blocks; Earth's star where vanilla's Sun is to 1e-9 | `TransitionTests` |
| Co-rotation | a stopped player stays over the same ground to 1e-6 blocks a tick | `TransitionTests` |
| Dive forecast | arrival within two ticks of the flight model | `TransitionTests` |
| Real dive into Earth (dev client) | lands 0.00 blocks from the true crossing, view and velocity unchanged | telemetry |
| Map texel ↔ surface point | within 1.5 texels both ways (equirectangular and Earth's disc) | `PlanetMapTests` |
| Procedural seas on the map | over 90% of texels agree with the terrain's own sea level | `PlanetMapTests` |
| Map on the wire | a full 512 × 256 map under 400 KB, texel for texel | `PlanetMapTests` |
| Cruise | 3–15 s; ends exactly 700 blocks over the planet, clear of stars (1,500) and bodies (2 radii); a cancelled one stops on the way | `TravelTests` |
| Hyperspeed arrival | 900 blocks short of the planet, heading for it, along the course unless that is unsafe; the course is the catalogue's light years | `TravelTests` |
| Real hyperspeed (dev client) | 0.00 blocks, view and velocity unchanged on arrival | telemetry |

Later phases add the transition, camera, star-identity and performance tolerances from the rework plan
(`DEVLOG.md`, 2026-10-20).

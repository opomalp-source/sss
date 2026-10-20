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
`normalize(target - here)` in galactic coordinates (used from phase 2). The orbital dimension keeps every
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

**Known limit until phase 3:** a player's "up" is always the world's vertical. Departing from the middle of a
world arrives on top of the sphere, but far from the middle the planet is beside or behind you in orbit. Phase 3
adds a free-orientation space camera whose "down" eases towards the nearest planet.

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

Later phases add the transition, camera, star-identity and performance tolerances from the rework plan
(`DEVLOG.md`, 2026-10-20).

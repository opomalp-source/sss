# Galaxy Phase 8 — v0.146.0

Space now has independent detail and sound controls, shared sphere meshes, distant impostors, a soft
atmospheric rim, and bounded texture work. The atlas uses a derived index for known systems and examines
at most512 discovery records per search page, returning at most32 matches. Sparse searches can have empty
pages: **Next** continues; **Previous** returns to the actual previous cursor. Surveying never creates terrain.

## Settings and balance

Open Zenith Settings → Effects. Use the page arrows to reach **Space detail**, **Space ambience** and
**Space music**. Long settings tabs now page within the window, including the minimum GUI size.

| Setting | Default | Scope |
|---|---|---|
| Space detail |Balanced |Low:24 segments near a planet; Balanced:40; High:56. Far objects use small impostors. |
| Space ambience |80% |Original drone, drive and weather mix; breathing and warning sounds retain their existing levels. |
| Space music |70% |Original orbital music; vanilla music is suppressed in vacuum to avoid overlapping tracks. |

The same controls are `[space] quality` (0–2), `ambienceVolume` and `musicVolume` in
`dbzenith-client.toml`. Low caps large material maps at128 pixels; Balanced/High use512-pixel surfaces and
256-pixel clouds. Small materials use64 pixels. Quality never affects server simulation or destinations.
Flight tips fade after ten seconds, keeping familiar orbital views clear.
Existing flash/shake accessibility controls still apply; re-entry rumble is reduced in calm mode.

The survival/travel balance is retained: landing air grace, race/suit/barrier/pod protection, gravity-scaled
flight costs, capped hyperspeed duration/cost, and planetary destruction protections/power thresholds. This
pass changes presentation and removes unbounded work rather than changing progression or making hazards free.

## Limits and compatibility

- Shared immutable unit meshes reuse their normals/UVs. Textured spheres no longer calculate unused procedural
  colors every vertex. Fully hidden bodies are culled conservatively; large moon/ring/explosion bounds prevent
  visible silhouettes popping out. The atmospheric halo uses a small gradient annulus.
- Material cache:48 LRU entries. At most4 generation jobs queued/running; deferred visible materials retry.
  At most1 completed material uploads per frame, also limited to one every16ms. Eviction/logout releases textures
  and closes orphaned images when their jobs finish. Stable texture IDs avoid a growing dynamic-name counter.
- Remnant layouts are cached for at most12 body descriptors. Height queries inspect only intersecting belt/
  fragment height ranges; negative-height and high orbital clusters retain exactly the same block output.
- Motion hints are finite, speed-bounded and tied to one player and dimension; a transfer clears old hints.
  Menus and focus loss clear thrust input, preserving coast without letting a held key keep accelerating.
- Known-system and search indices rebuild from existing discovery records. Save schema, catalogue-v1
  fingerprints, dimension keys and terrain generation remain unchanged. No data for undiscovered systems.
  Discovery saves retain their existing sorted order without sorting a second copy.
- Protocol49 reflects search cursor semantics. Update both server and clients together. Existing packet
  request limits, planet/orbit caps, permissions and protection policies remain active.

Earth remains protected. Older unmarked builds still need `/dbz planet protect "ref"`, as explained in
[Planet destruction](PLANET_DESTRUCTION.md). This version does not delete or migrate region directories.

## Local performance check

Measured on2026-10-17: Windows, Java17, Ryzen7 5800X (16 logical processors), RTX3060 Ti, driver
32.0.16.1088,1280×720, render distance7, ordinary graphics, no shader pack. Same stationary daylight approach
to Earth; VSync/frame limiting disabled for the comparison. Baseline disables **only** the custom sky in that
same orbital scene. It is not a benchmark against a separately loaded vanilla world.

The diagnostics record600-frame rolling windows after120 warm-up frames. Table entries are medians of
reported windows in each settled section. Frame intervals include presentation/driver work; sky duration is
CPU/driver submission time, not a GPU timer query. Mesh counts cover sphere/halo/impostor submissions only.

| Scene | Frame mean ms | Frame p95 ms | Sky mean ms | Mesh vertices / draws |
|---|---:|---:|---:|---:|
| Earth, sky disabled |0.336 |0.397 |0.000 |0 /0 |
| Earth, Low |0.656 |0.764 |0.316 |1968 /5 |
| Earth, Balanced |0.840 |1.046 |0.465 |4040 /5 |
| Earth, High |1.050 |1.280 |0.693 |7400 /5 |
| Namek system, Balanced |1.084 |1.283 |0.733 |5104 /10 |

With VSync and the60 FPS cap restored, Earth held60 FPS (16.678ms mean frame interval in the recorded
section); measured sky work was about0.484ms. Texture generation peaked at4 jobs across the two systems and
settled to zero pending/running. The early Phase8 Earth pass used6632 mesh vertices/16 draws on Balanced;
the final culling/halo pass used4040/5. These are local scene measurements, not a zero-cost claim or a promise
for every GPU, shader pack, base, combat scene or multiplayer server.

Reproduction uses `tools/dev/winshots.sh` and the devshot name flags:

```
first_hidegui_spaceuncapped_spacebaseline_spaceprofile_baseline
first_hidegui_spacerender_spacequality1_spaceprofile_balanced
first_hidegui_spacequality0_spaceprofile_low
first_hidegui_spacequality2_spaceprofile_high
first_hidegui_spacecapped_spacequality1_spaceprofile_capped
```

Keep the camera/location fixed and wait at least15 seconds per setting. `spacebaseline` bypasses the sky
only in the dev automation client; `spacerender` restores it. Logs contain `[space-profile]` samples. The
release jar does not enable profiling or sky bypass in normal play. Capturing many sequence PNGs changes
performance, so the comparison uses single screenshots with read-only waits instead of continuous capture.

## Validation and in-game checks

Five added GameTests exercise shared/bounded meshes and conservative culling,512-record sparse searches
through1800 discoveries and index reload, exact remnant height parity, independent/dimension-bound finite
momentum, and concurrent server-player transfers where only one player's trip is cancelled. The existing
suite also exercises actual ascent/re-entry, pods, transmission, destruction/restoration and catalogue hashes.

Try a normal ascent and re-entry, open the map while coasting, change Space detail, and visit Namek.
Use the settings page arrows at a large GUI scale; reset affects the entire selected tab. Two-player server
regressions use real ServerPlayers with embedded connections. Human multiplayer soak testing, shader-pack
compatibility and performance across other hardware remain useful follow-up validation; this local pass
does not claim to cover those environments. Story mode remains last.

# Planet destruction — v0.145.0

A large ki impact (force at least2) by a fighter above the configured battle-power threshold can trigger
core instability on an unprotected, landable planet. It reuses the actual ki-impact and protection systems;
small blasts, melee craters and attacks in a safe zone cannot start it. Operators can start the same event.

The default countdown is60 seconds, configurable30–90. Players in the system receive a warning. On the
surface the sky/fog reddens, the ground rumbles, natural terrain develops hot magma fissures, block particles
lift and gravity fluctuates. From orbit, glowing seams spread across the sphere. Detonation produces a brief
swelling flash, expanding shockwave, muffled original explosion and drifting crust silhouettes. Existing
calm/flash controls apply. The countdown is a server clock, persists over restarts and pauses while offline.

Survivors are thrown into orbital space by default. The existing async transit preserves Space Pods and
respects orbital caps; Earth is the emergency fallback if orbit is unavailable. An optional lethal policy
uses ordinary damage; immortals receive the evacuation fallback. Reconnecting players on destroyed surfaces
are evacuated too. Pending landings, beds, pod travel and Instant Transmission cannot enter a sealed surface.
Hyperspeed can still reach its star system to explore the remains.

## Protection and configuration

`dbzenith-planets.toml`, `[planets.destruction]`:

| Key | Default | Effect |
|---|---|---|
| enabled | true | Enable planetary events; disabling cancels active countdowns and repairs their fissures |
| powerThreshold |10000000 | Minimum current battle power for an impact-triggered event |
| attackPermissionLevel |0 | Permission level required for an attack to start it |
| countdownSeconds |60 |30–90 seconds of evacuation time |
| maxConcurrent |4 |1–8 simultaneous active/restoring events |
| protectPlayerBases |true | Recorded base planets cannot be destroyed |
| protectSpawnPlanets |true | Forced chunks and player spawn planets protect the surface |
| ejectSurvivors |true | Evacuate; false attempts lethal damage first |
| protectedPlanets |Earth, King Kai, Beerus | Protected body addresses |

Earth always remains protected as the safe server return world. Player block placements and setting a
spawn persistently mark a planet as a base. Protection engages even during a countdown, cancelling it.
Previously built worlds must be marked with the operator `protect` command before enabling destruction;
there is no reliable way to infer every old build from blocks. Player spawns set after this update persist
through the same base marker, including offline owners. Forced chunks are checked on loaded surfaces.
Existing crater safe zones and Forge block-break listeners also protect each fissure candidate.

Operator commands (permission2), quote the address:

```
/dbz planet status "27:-6:14:8/0"
/dbz planet destroy "27:-6:14:8/0"
/dbz planet restore "27:-6:14:8/0"
/dbz planet protect "27:-6:14:8/0"
/dbz planet unprotect "27:-6:14:8/0"
/dbz planet remnant Dev 0
```

`destroy` respects protections, event caps and enabled settings; it bypasses the attack-power requirement
only. Console-triggered events use server ownership: fissures require an online player actor, so use
`execute as Dev at Dev run dbz planet destroy "ref"` to exercise per-block protection in a dev world.
`remnant` moves an operator-selected player in the matching destroyed system near its first mineable cluster.
`unprotect` clears the base marker only; it does not bypass configured/forced-chunk protection.

## Snapshots, remnants and wishes

Destruction **never deletes dimension directories or region files**. The sealed region storage is the
surface snapshot. Empty procedural surfaces save and unregister through the existing runtime. Legacy static
dimensions such as Namek remain registered but their normal travel is sealed. The sparse SavedData records
the event and up to1024 original fissure states. Non-flowing magma avoids lava spreading beyond that journal.
Fissures touch only natural blocks in already-loaded chunks: no inventories, fluids or whole-world scans.

**Restore a planet** is a new Earth Dragon Ball wish, also available to Super Shenron. Set the atlas waypoint
to the destroyed planet; without a destroyed waypoint it chooses the first destroyed address in lexical
order. No target means the dragon refuses the wish without consuming it. Operator `restore` uses the same
recovery. The preserved world reopens after its fissure journal is repaired; builds/inventories and ordinary
pre-destruction crater damage remain as they were. Later player edits take precedence over fissure repairs.
Unvisited worlds have no region files to retain: they generate deterministically when first visited after
restoration. Names, ownership, personal visits and catalogue-v1 generation remain unchanged.

Twenty-four deterministic small clusters become real blocks only in requested orbital chunks. Existing
occupied orbital chunks are preserved. Loaded empty chunks fill incrementally (2048 candidates/128 writes
per server tick globally); a new placement cancels pending fill in its chunk. Sparse materialization records
prevent refresh/relog from regenerating mined fragments. New chunk generation uses the same bounded shapes.
Large distant crust silhouettes blend down toward the real clusters as you approach. Restoring a planet
stops future remnant generation, but keeps already-placed debris and orbital builds to avoid deleting player
work. Surface chunks are preloaded one at a time during repair, at most64 journal entries per event/tick.

Only current-system event summaries (at most12 bodies, once per second) reach clients. No snapshot, world
seed or block journal is sent. Historical destroyed planets do not enter per-tick countdown iteration.
Terrain generation, rendering caches and active event count remain bounded. Broad profiling and multiplayer
soak testing remain Phase8; this release's checks are GameTests and local dev-client visual checks.

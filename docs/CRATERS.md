# Ki craters (Galaxy Phase 6, v0.144.0)

Destructive ki balls, beams and clashes use the existing Destruction system. Impacts dig a ragged bowl,
crack its edge, scorch exposed rock and raise a lip. Sandstone/sand melt into glass, red desert material
into orange glass, and powerful impacts can leave patches of magma among blackstone and basalt.
Seeded, irregular variation breaks up the scorch, with melted sandy patches around the rim. Loose rim
material introduced by ki damage is compacted into stable blocks rather than spawning falling sand.
Earth scorches into coarse dirt and tuff. Inventories and arbitrary building materials are not used as
scorch material. Wall impacts scorch the exposed wall rather than an unrelated horizontal floor.

Rubble is client-side block debris; it cannot land on a player's base. The original world-lit dust puffs
linger for roughly 8-16 seconds, rising and thinning. Low effect quality reduces their number. Existing
camera shake/accessibility settings and original impact/rumble sounds remain in use. No downloaded assets.

## Bounded server work

Large ki impacts enqueue in constant space/time, without scanning terrain on the impact tick. Each job
walks outward through successive shells, then processes exposed surfaces, lip columns and neighbour updates.
Jobs share a round-robin budget across dimensions. All queued direct edits, including cracks, scorch,
lip placement and restoration, count against blocksPerTick. Small fighter/slam craters retain their
immediate bounded response; their remaining neighbour work uses the queue.

| Setting under destruction | Default | Meaning |
|---|---:|---|
| kiCraterMaxRadius | 20 | Maximum ki bowl radius; rim work extends to about 1.45 times this |
| kiCraterMaxBlocks | 30000 | Maximum blocks removed per job; finishing changes are also capped |
| blocksPerTick | 1500 | Shared direct edit budget for queued work |
| scansPerTick | 12000 | Shared candidate inspection budget, including empty/protected positions |
| maxJobs | 16 | Pending jobs; excess impacts still deal their normal combat damage |
| maxTrackedChanges | 200000 | Pending neighbour records plus temporary restoration snapshots |
| debris | true | Client rubble particles |
| regrowSeconds | 0 | Optional temporary crater restoration; zero leaves terrain altered |

Vanilla's light engine receives queued light checks from the batched block writes. Neighbour and shape
reconciliation is deferred and limited to 128 positions per tick, with a loaded chunk buffer; the code
does not force a synchronous light-engine flush or disable lighting. This uses supported 1.20.1 APIs
instead of modifying lighting internals. Very large craters can take several seconds to finish.

No crater scan or finishing pass loads chunks. Terrain at an unloaded boundary is skipped. Jobs do not
pin idle dimensions, and unload/stop discards their remaining work. Restoration snapshots are bounded
and remain session-local, as in the previous implementation; they are not a server-restart backup.

## Protection and restoration

Every direct edit checks the actual target block: spawn/safe zones, tournament ground, Other World,
world border, loaded chunk, block entity, fluid and current hardness. Player edits additionally pass
Forge break/place protection events, and listener changes are rechecked before writing. NPC jobs obey
mobGriefing. A safe region overlapping only the edge of a crater remains protected.

Disabling destruction or ki craters, removing an owner, or disabling mob griefing for an NPC stops further
carving; bounded reconciliation handles existing changes. The legacy kiBlastsBreakBlocks option now requests
this same protected terrain path for explosive attacks. Vanilla explosions retain damage/knockback but
cannot bypass the queue to remove blocks. Malformed blast values are refused; native damage blast power
is bounded to 16, independently of configurable crater size.

Temporary craters remember original and expected final states. Restoration is bottom-up, shares the
edit budget, skips unloaded/protected terrain, and restores only a matching expected state. A later
player block of a different state is preserved. Restoration checks block state, not provenance.
Repeated edits to one position keep one original snapshot.

## In-game checks

Fire a destructive attack at open ground away from spawn. Check the bowl, ragged rim, substrate changes,
flying particles and dust after the flash fades. Compare rock and red desert terrain. A protected area
intersecting the rim should keep its blocks.

Operators can aim at a block within 64 blocks and use:

- /dbz crater hit <players> <force> (0.35-8): real protected crater path, no separate preview generator.
- /dbz crater status: last tick's inspections, direct changes, notifications, jobs and tracked records.

These are terrain-editing commands. They use the normal protection/config rules and live in the existing
operator-only /dbz command tree. Planet destruction and Dragon Ball restoration remain Phase 7; story last.

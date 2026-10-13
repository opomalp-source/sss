# Flying Nimbus

Nimbus is a reusable flying mount restricted to Good alignment (30 or higher). The server checks eligibility both when mounting and during flight. Losing Good alignment dismounts the rider.

## Obtaining and riding

Find Flying Nimbus in Creative > Tools & Utilities, or use `/give @s dbzenith:flying_nimbus`.

Craft it in this layout:

```
Yellow wool | Feather      | Yellow wool
Feather     | Golden apple | Feather
Yellow wool | Feather      | Yellow wool
```

Use the item in open space to summon and mount it. It is not consumed. Empty clouds disappear after one minute. Summoning another replaces your nearby empty cloud.

- WASD and view direction: steer; look down while moving forward to descend.
- Space: rise.
- Sprint: fly faster.
- M: switch between standing and sitting (remappable in Controls).
- Use the item again while mounted: also switches posture.
- Shift: dismount.

The cloud uses one closed sculpted surface with a scalloped silhouette, a broad billowing top, recessed golden spirals, and a swept rear tip. Its fading wake is a continuous tapered mesh with shared rings, not separate spheres or Minecraft particles. The body mesh and its surface normals are cached once. Motion and eligibility are server-authoritative; a missing input stream brakes to a hover. Both client and server need this version for multiplayer.

## Build and compatibility

The source checkout predates the user's installed 0.88.0 release. Build the donor with Gradle, then use `tools/PackageHairUpdate.ps1` against the untouched original 0.88.0 JAR. Never install the whole 0.72.0 donor. The packager preserves the prior hair and transformation changes, adds only Nimbus classes/assets, and verifies unrelated original entries byte-for-byte. Nimbus registration is self-contained and does not replace the baseline entity/item/network registries.

The `NimbusTests` server GameTests cover alignment boundaries, bypass rejection, alignment loss, posture persistence, non-finite input rejection, movement and stale input braking. `tools/nimbus-review.init.gradle` launches an isolated client review. Development tests/review classes are excluded from the packaged release.

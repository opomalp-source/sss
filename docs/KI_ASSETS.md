# Ki attack rework: assets

Every ki attack works with **original placeholders**:
- shaders and meshes written in code (`client/fx/KiEnergy`, `BigSphere`);
- one generated noise texture;
- sounds synthesised by `tools/SfxGen.java`.

To replace one with hand-made work, put a file at the same path, in the mod's resources or in a resource pack. No code
changes are needed. Keep the size, format and (for sounds) roughly the length.

**Generated files:** if you replace a file a generator writes, remove its line from the generator, or the next run
overwrites yours.
- `ki_noise.png` comes from `tools/KiNoiseGen.java`.
- The sounds come from `tools/SfxGen.java`.

## 1. Shaders

Under `assets/dbzenith/shaders/core/`.

| File | What it is |
|---|---|
| `ki_energy.json`, `.vsh`, `.fsh` | The energy shader. It is additive and goes into the bloom. The mode arrives in the overlay UV (`UV1.x`): 0 shell, 1 core, 2 plasma strands, 3 halo, 4 ribbon, 5 tint. It uses `GameTime` and samples `Sampler0` (the noise). |
| `ki_energy_tint.json` | The same programs, blended normally rather than added. It draws the see-through coloured body under the glow, so attacks keep their colour against a bright sky. |

With an Oculus or Iris shader pack on, neither is used and the older plain look is drawn.

## 2. Textures

PNG, under `assets/dbzenith/textures/entity/`.

| File | Size | What it is | Now |
|---|---|---|---|
| `ki_noise.png` | 128x128 RGB, **tileable** both ways | The energy's movement. Each channel is read separately: **red**, soft billows (the shell's plasma, ribbon flow); **green**, thin ridges (the crackling strands, used through a threshold); **blue**, a second billow at another scale. | `KiNoiseGen` |
| `ki_glow.png` | 32x32, white radial glow on transparent | The ground light under charges and big spheres, and the plain fallback blast | AssetGen |
| `impact_star.png` | 64x64, white on transparent | The spinning flare across small blasts | ArtGen |
| `spirit_bomb.png` | 128x128 | The Spirit Bomb when no energy shader is available (a shader pack) | ArtGen |
| `ground_cracks.png` | 128x128, cracks on transparent | The ground cracking under a long charge | ArtGen |

## 3. Sounds

OGG Vorbis, mono, 44.1 kHz, under `assets/dbzenith/sounds/`. Events are in `sounds.json`.

| Event | Files | Length | What it is |
|---|---|---|---|
| `ki_release` | `ki_release_1..3` | about 1.4 s | Every blast let go: a punch transient, a sub-bass thump, a falling zap and a sizzle |
| `ki_travel` | `ki_travel` (**loops**) | 1.3 s, seamless | A blast in flight: a hum and a fluttering rush, pitched by doppler. Big spheres play it low (about 0.6 pitch) as a rumble, even while hanging still. |
| `impact_debris` | `impact_debris_1..2` | about 2.5 s | Stones pattering down after an explosion |
| `impact_echo` | `impact_echo_1..2` | about 4.7 s | A dull boom in a long tail, delayed by distance, for big explosions |
| `ki_charge` | `ki_charge` (**loops**) | 2.25 s, seamless | The charge-up: a deep hum, crackling electricity and a faint whine. It is pitched 0.6 to 1.55 with the charge. |

Each attack's data file can name its own sound events in `look.sounds` (see [KI_ATTACKS.md](KI_ATTACKS.md)), so a
resource pack can give one attack a sound of its own without replacing the shared ones.

## 4. Wanted for the later phases

None of these exist yet. Each would get a placeholder when its phase lands.

| Phase | Asset | Spec |
|---|---|---|
| 5 | `disc_whine` loop | A thin rising whine for spinning discs (Destructo Disc), 1 to 2 s, seamless |
| 5 | `barrage_rattle` | A rapid rattle for volleys, 0.5 s, several variants |
| 6 | `crater_scorch.png` | 128x128, a dark scorched ring fading out, for crater floors |
| 6 | `impact_plume.png` | 64x256 strip of 4 frames: a rising dust and smoke plume, alpha |
| 6 | `impact_big` | A huge layered explosion (crack, boom, roar), 3 to 5 s, 2 variants |
| 8 | `beam_clash` loop | Two beams grinding against each other: a roar and crackle, 2 s, seamless |
| 9 | `heat_distort.png` | 64x64 normal-map-style noise for heat shimmer (RG channels), tileable |

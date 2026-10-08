# Assets

All art is **original** and drawn in code. No Toei/Shueisha/Dragon Block C art is bundled.

- `tools/ArtGen.java` draws the art: items, blocks, armor layers, NPC skins, race skins, the Great Ape and the Eternal Dragon. Run it with `java tools/ArtGen.java`.
- `tools/AssetGen.java` draws effect textures (ki glow and beam, hair strands, status icons, scars and tattoos) and the GameTest structure.
- `tools/ContactSheet.java <folder> <out.png> [cell]` tiles a folder of PNGs into one review sheet.

Edit the generators, never the PNGs: every run rebuilds them. To use hand-drawn art instead, replace a PNG at the same path and size and remove its line from the generator.

Style: 16x16 items and blocks; selective dark outlines; light from the top left; hue-shifted ramps (cool shadows, warm highlights); few colours per object.

Reference material (for drawing originals, or for adding art yourself for **private use only**): the Dragon Block V wiki (https://dragonblockv.wiki.gg/), the Dragon Block C CurseForge page and JinGames wiki, and Dragon Ball fan wikis.

## Status

| Asset | What | Status | Notes |
|---|---|---|---|
| `textures/item/*` (21 items) | Senzu Bean, Moon Orb, Scroll, weights, radar, scouter, capsule, Space Pod, sigil, totem, ring, 3 gi/armor sets | **drawn** (ArtGen) | Shaded, outlined, seen in item frames in-game |
| `textures/block/dragon_ball_*`, `dragon_ball_shell` + `models/block/dragon_ball_template.json` | Dragon Balls as small orbs (three crossed boxes), stars on the four front faces, 3D in the inventory | **drawn** | |
| `textures/block/punching_bag` + model | Hanging bag: rope, caps, stitching, tape bands | **drawn** | |
| `textures/block/gravity_chamber_{top,side,bottom}` | Machine console with a "10G" screen, dial, vents, status strip | **drawn** | |
| `textures/block/time_chamber_door` | Marble with a gold-trimmed arch onto a starry void | **drawn** | |
| `textures/models/armor/*` | Turtle and Demon gi (V-neck, lapels, belt, wristbands, back emblem), Battle Armor, scouter, weights | **drawn** | |
| `textures/entity/fighter/*` (8) | Master, Patrol Officer, Ki Soldier, Android Unit, Sproutling, Tyrant Lord, Rampage Brute, Namekian Warrior; full 64x64 skins with outer layers | **drawn** | NPCs now use the player model so outer layers (capes, helmets) show |
| `textures/entity/race/{namekian,frost_demon,majin}` | Optional full race looks for players (Life screen) | **drawn** | |
| `textures/entity/great_ape` + `client/render/GreatApeModel` | Real ape model: hunched body, long arms, muzzle, brow, tail; walk and swipe animation | **drawn** | |
| `textures/entity/eternal_dragon` + `client/render/DragonModel` | Scaled serpent coil with dorsal spikes; head with snout, jaw, horns, whiskers, glowing eyes | **drawn** | |
| `textures/entity/form_hair` + `client/render/FormHairModel` | Hair per style (spiky, tall, long, slim): tapered three-tier spikes, dark roots to bright tips, strand texture | **drawn** | |
| `client/render/RaceFeatureModel` | Antennae, horns, tentacle, tail | good placeholder | |
| `textures/entity/ki_glow`, `ki_beam` | Ki projectiles and beams, tinted at render time | final for now | |
| `textures/mob_effect/*`, `entity/cosmetics/*` | Status icons, scars, tattoos | final for now | |
| `textures/gui/hud.png` + `client/DbzHud` | Gold-trimmed glass panel (nine-slice), bar icons, bevelled tracks, glossy fills | **drawn** | |
| `textures/entity/space_pod` + `client/render/SpacePodRenderer` | Space Pod entity: place it, climb in, it launches with flames and a pod lands at the destination | **drawn** | |
| Namek terrain | Namek trees (pale log, round teal leaves) and white dome houses (world feature) in new chunks; grass and water tinted by the biome | **drawn** | The Northern Planet is still plain grass |
| Spawn eggs | Vanilla template, tinted | fine | |

## Combat v4 (CX-19)

The combat textures, sounds, particles and animations, with exact specs and what to make first: [docs/COMBAT_ASSETS.md](docs/COMBAT_ASSETS.md).

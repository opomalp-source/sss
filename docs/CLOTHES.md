# Fighting clothes and head clothing

## Outfits

Each outfit is three pieces (top, trousers, boots), worn in the chest, legs and feet slots. A full set gives its bonus
(shown on the tooltip). Clothes torn in battle stay torn until mended (see `KNOCKOUT.md`).

| Outfit | Look |
|---|---|
| Turtle gi | orange wrap gi with raised lapels and obi knot, navy undershirt, sash and wristbands; hand-drawn Turtle School emblems on chest/back |
| Demon gi | purple gi, red sash and wristbands |
| Piccolo's gi | sleeveless purple gi, blue obi, low brown shoes; ivory mantle/collar and long flared cape with modeled pleats and a finished lower edge |
| Saiyan Saga battle armor | white raised chest shell and straps over a navy bodysuit; ribbed gold abdomen, shoulder guards and hip guards; white gloves and boots |
| Cell Saga battle armor | distinct streamlined white shell over a blue bodysuit, flexible gold abdomen; no shoulder/hip guards; white gloves and boots |
| Frieza Force armour | white plate with purple trim over a black bodysuit; pads sweeping up and out |
| Majin | open dark vest, white baggy trousers, a belt with a buckle, gold shoes |
| Fusion outfit | open dark vest with padded gold collar and arm openings, sky-blue sash, white loose trousers and dark shoes |
| Hoodie | red zip hoodie with its hood down, joggers, sneakers |

What is thick stands off the body: armor shells, ribs, collars, lapels and knots are modeled pieces. Gloves, bands,
boots and cloth volume follow the existing segmented, bending limbs. Local cube-surface expansion covers the tops
of shoes and gloves as well as their sides, fixing exposed toes from scaling about the leg joint.

Both armor cuts stretch through Great Ape growth. Existing `battle_armor_*` items keep their IDs and become the
Saiyan Saga set; the new items are `cell_armor_top`, `cell_armor_pants`, `cell_armor_boots`. Craft the new cut with
the existing armor recipe patterns, substituting **blue wool** for black wool. Both sets have the same combat
bonuses, but mixed cuts do not grant a full-set bonus. Existing saves need no migration.

The cape retains the movement-driven spring chain: its overlapping panels now have continuous cloth backing,
raised pleats, a widening silhouette and a closed lower edge. It sways while walking and streams behind flight. The
weighted shoulder mantle follows the trunk rather than lifting with every arm swing. Piccolo's turban is a low
ivory wrap around a purple crown; it remains a separate head item.

## Visual references

The DBZ anime palette is the baseline. References guide silhouette, garment construction and color; no downloaded
textures, game models, screenshots or traced art are bundled. Every material, emblem stroke, icon and mesh is
drawn in the repository's generators/model code.

- [Official Goku costume guide](https://en.dragon-ball-official.com/news/01_313.html)
- [Official Vegeta costume guide](https://en.dragon-ball-official.com/news/01_155.html): early armor versus Bulma's later cut, and its ability to expand in Great Ape form.
- [Official Piccolo character reference](https://en.dragon-ball-official.com/news/01_1178.html)
- [Piccolo clothing description](https://dragonball.fandom.com/wiki/Piccolo): anime purple gi/blue obi versus manga palette.
- [Bandai Namco's Gotenks reference](https://www.bandainamcoent.com/news/gotenks-kid-buu-and-gohan-join-the-battle): dark blue/yellow Metamoran vest.

## Head clothing

Worn in the head slot (instead of a scouter):

| Item | Recipe | Notes |
|---|---|---|
| Piccolo's Turban | 5 white wool, 1 purple wool (`AAA`, `ABA`) | domed white turban with its purple band; covers the hair and a Namekian's antennae |
| Bandana | 3 orange wool over 2 string | Pan's orange bandana, knotted at the back; covers the hair |
| Headband | string, red wool, string | a red headband, its tails swinging behind |

## For developers

- Outfit textures: `tools/ArtGen.java` (`java tools/ArtGen.java only gear`), the `Gear` class. Each top also has a
  `_top_shell` texture holding only its thick parts, drawn by `GearLayer.thick`.
- Wearable UV sheets are512x512 (the same64-unit UV layout); icons64x64; neutral detail atlas512x512. The gear-only
  generator also refreshes the nine Classic/NPC fallback armor sheets. Other body/face generation stays at its
  existing resolution. Do not run the full art generator just to update clothes.
- `LimbSegments.clothingShell` temporarily expands each segmented cube around its own center during clothing
  passes; `GearLayer` restores both this state and all model scales in `finally`. Skin/fur passes keep zero expansion.
- 3D pieces (pads, cape, sash tails, head clothing): `client/render/GearModel.java`, tinted greyscale materials from
  `gear_parts.png`.
- Head clothing icons: `tools/HeadwearIcons.java`.

Clothing cap UVs are handled separately: a cap's vertical coordinate measures depth rather than distance down a limb. Boot maps leave thigh caps transparent; trouser maps cover those caps, and glove regions stay off the shoulders. Half-torso caps keep the original body cap UV islands instead of sampling chest paint. The cape is a cached continuous trapezoid mesh with closed pleats, rather than a stack of rectangular boxes.

Headwear also has distinct generated Classic/NPC fallback sheets; its item texture paths no longer reference a missing shared material. For Great Ape visual QA, freeze a full moon at18000 or allow a false moon at least40 ticks to rise before forcing the form.

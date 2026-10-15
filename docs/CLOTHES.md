# Fighting clothes and head clothing

## Outfits

Each outfit is three pieces (top, trousers, boots), worn in the chest, legs and feet slots. A full set gives its bonus
(shown on the tooltip). Clothes torn in battle stay torn until mended (see `KNOCKOUT.md`).

| Outfit | Look |
|---|---|
| Turtle gi | orange gi, blue undershirt, sash and wristbands, the school emblem on the back |
| Demon gi | purple gi, red sash and wristbands |
| Namekian gi | purple gi, blue sash; a white cape and wide square shoulder pads |
| Saiyan battle armour | white chest plate with gold straps over a navy bodysuit; layered pads flaring down and out; gloves and boots |
| Frieza Force armour | white plate with purple trim over a black bodysuit; pads sweeping up and out |
| Majin | open dark vest, white baggy trousers, a belt with a buckle, gold shoes |
| Fusion outfit | open teal vest, gold quilted pads, wide sash, white baggy trousers |
| Hoodie | red zip hoodie with its hood down, joggers, sneakers |

What is thick stands off the body: armour plates, gloves, wristbands, sashes and boots are drawn a little larger than
the body, following every bend of the limbs.

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
- 3D pieces (pads, cape, sash tails, head clothing): `client/render/GearModel.java`, tinted greyscale materials from
  `gear_parts.png`.
- Head clothing icons: `tools/HeadwearIcons.java`.

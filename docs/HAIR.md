# Hair (CX-35g: model hair)

Every stock hairstyle is sculpted per look and drawn in blocks, like the rest of Minecraft (and like the block-hair
of the Dragon Block mods the user showed as references), and it moves.

## Where things are (`client/hair`)

| Class | What it does |
|---|---|
| `HairLock` | One lock at rest: 7 knots (x, y, z, width, thickness), stiffness, weight |
| `HairShape` | A head of hair (its locks) and the change from one look to another |
| `HairLook` | Which look a form shows: base, SSJ, SSJ2, SSJ3, SSJ4, God, Beast |
| `Sculpt` | The authoring tools: `lock`, `pair`, `fan`, `toward`, `knots`, `crown`, `buzz`, `feel` |
| `HairStyles` | Every preset in every look |
| `HairCodes` | From a hair code to a shape (stock styles, or editor hair strand by strand) |
| `HairRender` | Lays each lock down as a chain of cubes on a half-pixel grid |
| `HairSim` | The physics |

Head pixels: x is the wearer's left, y is down (the top of the head is -8), z is back (the face is at -4).
A lock leaves the scalp `el` degrees above the horizontal and `az` degrees round from straight back (+90 left,
180 over the face) and bends `curl` degrees along its length.

## Drawing

`HairRender` draws each lock as a chain of boxes, one per segment, turned along the lock and a step thinner each time,
ending in a small square tip, textured with `hair_block.png` (a 16 x 16 strand tile at the model's own pixel size, made
by `tools/HairTextureGen.java`). (CX-35f laid tiny cubes on a half-pixel grid, which looked like Teardown:)
(62% of the lock's width there, in whole grid steps), shrinking to a single half-pixel at the tip, so a slanting lock
steps like a staircase. Cubes take Minecraft's own face lighting, vary a little cube to cube, and are darker toward
the root; dark hair is lifted to a dark grey so its blocks read. There is no scalp layer: the locks cover the head,
and every style except the buzz cut and the mohawk gets a few flat locks over the crown (`Sculpt.crown`) so no skin
shows between the roots from above. The buzz cut is short locks lying close all over (`Sculpt.buzz`).

## Adding or changing a look

1. In `HairStyles`, edit the preset's function (`goku`, `vegeta`, ...) or the look's case in `ssj`, `ssj3`, `ssj4`, `beast`.
2. Render the sheets: `gradlew -I tools/hair-sheet.init.gradle hairSheet -PhairOut=<folder>`, optionally
   `-PhairArgs="only=PRINCE looks=BASE,SSJ views=4 big pitch=-0.75"`, or `grow` for the transformation step by step.
3. Run the checks: `gradlew -I tools/hair-preview.init.gradle hairPreview`.

## Transformations

`HairShape.morph`: each lock of the new look takes over the old lock rooted nearest it and swings round its root;
new locks grow out of the scalp along their own path; locks the new look lacks draw back in. All locks move together,
following the transformation bar.

## Physics

`HairSim` steps the hair once per world frame (other draws of the same fighter, like the HUD portrait, show it as it
is): the knots lag a little behind the head's motion (14%), are strongly damped, spring back firmly to the style,
never swing far from it, hang with gravity, stream back a little in moving air, lift in an aura, and keep out of the
head and the back. Off with `hairPhysics`, and past 48 blocks.

## No bald spots (CX-35g)

`Sculpt.build` (and `HairStyles.author` again, for looks made by slimming or scaling another) walks the scalp in
0.55 px steps over the top and down the brow, sides and back to the style's hairline; wherever no lock reaches well
over a point, it adds a short flat lock there (back over the crown, up from the hairline elsewhere), underneath the
others. The mohawk opts out (`cover = false`). `HairPreview` checks 15,000+ points over the crown of every look.

## Volume and flow (CX-35h)

- Every lock is drawn 1.2x as wide as authored (`Sculpt.VOLUME`), at least 70% as thick as it is wide, and tapers
  from full to a point over its length (Goku's hand-placed proportions); straight cuts end blunt.
- Fan locks arc: the outer ones bend out and over further (`curl - |theta| * .32`), and each curves round its own
  way a little (a fixed twist per lock), so the hair flows instead of standing in rows.
- `HairRender` draws a lock as three long boxes (two knots each), each a step thinner, like the Dragon Block hair
  models, not six short ones (which read as staircases).
- Bare-spot fillers (`Sculpt.fill`) are flat, blunt slabs (`Sculpt.flat`) so they don't form steps on the forehead.

## CX-35i: transformation wave, hairlines, no bare scalp anywhere, fuller manes

- **Transformation:** the change sweeps from the brow to the nape (a lock starts when the wave reaches its root), and
  each lock eases out a little past its new shape and settles back, so the hair whips into place
  (`HairShape.local`). The in-betweens get their bare spots closed too (`HairStyles.closed`).
- **No bare scalp:** `Sculpt.fill` also walks the rim where the top of the head meets the sides (the corners seen
  from three-quarter views); `Sculpt.covers` measures a lock by its thickness (what actually lies over the scalp),
  not its width. `HairPreview` checks the top, the rim, and the brow, sides and back down to each hairline, for every
  look and for every transformation at a quarter, half and three quarters.
- **Hairlines:** Vegeta has his widow's peak as its own locks.
- **Manes** (Raditz, Super Saiyan 3, Super Saiyan 4): four layers of eleven strands each, thinner, lengths varying a
  little strand to strand and crossing as they fall, two strands either side behind the ears. The Super Saiyan 3 bang
  is a long lock curving down past the eye.

# Hair (CX-35f: block hair)

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

`HairRender` walks each lock in 0.35 px steps and puts down a cube on a 0.5 px grid at every step: big near the root
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

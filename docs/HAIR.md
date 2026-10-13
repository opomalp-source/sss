# Hair (CX-35)

Every stock hairstyle is sculpted per look, drawn as smooth tapering locks over a fitted scalp, and moves.

## Where things are (`client/hair`)

| Class | What it does |
|---|---|
| `HairLock` | One lock at rest: 7 knots (x, y, z, width, thickness), stiffness, weight |
| `HairCap` | The scalp under the locks, cut to a hairline: fringe, widow's peak, temples, sides, nape |
| `HairShape` | A head of hair (cap + locks) and the morph from one look to another |
| `HairLook` | Which look a form shows: base, SSJ, SSJ2, SSJ3, SSJ4, God, Beast |
| `Sculpt` | The authoring tools: `lock`, `pair`, `fan`, `knots`, `cap`, `feel` |
| `HairStyles` | Every preset in every look |
| `HairCodes` | From a hair code to a shape (stock styles, or editor hair strand by strand) |
| `HairRender` | Draws a shape (or its simulated knots) as tapering bands |
| `HairSim` | The physics |

Head pixels: x is the wearer's left, y is down (the top of the head is -8), z is back (the face is at -4).
A lock leaves the scalp `el` degrees above the horizontal and `az` degrees round from straight back (+90 left,
180 over the face) and bends `curl` degrees along its length.

## Adding or changing a look

1. In `HairStyles`, edit the preset's function (`goku`, `vegeta`, ...) or the look's case in `ssj`, `ssj3`, `ssj4`, `beast`.
2. Render the sheets: `gradlew -I tools/hair-sheet.init.gradle hairSheet -PhairOut=<folder>`, optionally
   `-PhairArgs="only=PRINCE looks=BASE,SSJ views=4 big"`, or `grow` for the transformation step by step.
3. Run the checks: `gradlew -I tools/hair-preview.init.gradle hairPreview` (every look and change draws whole, the
   physics are stable, old saves upgrade).

A new preset is a new `HairCode.Preset` entry (its strand code for the editor) and a case in `HairStyles.base`.

## Transformations

`HairShape.morph`: each lock of the new look takes over the old lock rooted nearest it and swings round its root;
a lock that must grow much longer turns onto its new path first and then grows along it; new locks grow out of the
scalp along their own path; locks the new look lacks draw back in; each lock moves a moment after the last. The
progress follows the transformation bar (`HairTransformation`).

## Physics

`HairSim` keeps each fighter's knots in head space and steps them every frame (in sub-steps of a third of a tick):
- last frame's knots are carried into this frame's head frame, so turning, running, stopping and jumping make the hair
  lag, swing and settle on its own;
- gravity pulls toward the real down however the head tilts; moving air (the fighter's own speed) streams it back;
  an aura lifts it (powering up fully, a transformed form a little);
- each knot springs back toward its place in the style, firm at the root and looser to the tip, by the lock's
  stiffness (Super Saiyan hair 0.85-0.9, short hair 0.5-0.6, long hair and tails 0.12-0.3);
- segments keep their length; knots are pushed out of the head and the back.

Off with the `hairPhysics` setting, and past 48 blocks (the hair is then drawn at rest).

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
- last frame's knots are carried part of the way (30%) into this frame's head frame, so turning, running, stopping and jumping make the hair
  lag, swing and settle on its own;
- gravity pulls toward the real down however the head tilts; moving air (the fighter's own speed) streams it back;
  an aura lifts it (powering up fully, a transformed form a little);
- each knot springs back toward its place in the style, firm at the root and looser to the tip, by the lock's
  stiffness (Super Saiyan hair 0.85-0.9, short hair 0.5-0.6, long hair and tails 0.12-0.3);
- segments keep their length; knots are pushed out of the head and the back.

Off with the `hairPhysics` setting, and past 48 blocks (the hair is then drawn at rest).

## Shading (CX-35b)

Hair is painted like anime hair rather than lit like a block:
- `textures/entity/hair_lock.png` (made by `tools/HairTextureGen.java`, 32 x 64): the left half is one lock, u across
  and v from root to tip, in greys the hair colour tints (dark edges that outline each lock, faint strand lines, a
  darker root, a lighter tip); the right half is the gloss, a broken saw-edged highlight streak, clear elsewhere.
- Every face takes one of three cel tones by how it faces a key light above and in front of the head: lit (the
  colour, dark hair lifted with a blue-grey sheen), mid and shadow. Shadows shift hue: warm colours toward red-orange
  (gold goes amber, never olive), cool ones toward blue.
- The gloss is laid just over the lit broad side of each lock, a third of the way along, tinted toward white.
- Roots and the scalp are darker; each lock differs a little in value.
- Vertices face up, so Minecraft's soft entity lighting doesn't dull the tones; the world's light level still darkens
  hair in shade (transformed hair glows).

The sheets (`hairSheet`) sample the texture through the emitted UVs, so they show what the game shows.

The hair steps once per world frame; drawing the same fighter again that frame (the HUD portrait, the inventory)
shows it as it is. Damping is strong and every knot stays within a swing of its place (`.18 + .42 * (1 - stiff)` of
its distance along the lock), so it sways and settles rather than whipping about.

## Fullness (CX-35d)

`Sculpt.build` fills every style out so it reads as a full head of hair with nothing showing through: between every two
neighbouring locks that flow the same way (roots within 4.6 px, directions within 60 degrees, lengths within about
double), a lock blended from both, tucked a little under them; under every lock a shorter, wider underlayer near the
roots. The hand-placed locks stay on top and keep the style's shape. Set `dense = false` on a sculpt to keep only the
underlayer (Goku's own hand-placed layout does this).

Hair that rests inside the head or the back (tucked locks, hair lying on the scalp) is not pushed out of it, so it
doesn't shiver between the spring and the collision.

## Lock shape and the hair mass (CX-35e)

- Locks have a diamond cross-section: an edge either side across the scalp and a ridge away from it, so each spike is
  lit on one side of its ridge and shadowed on the other, as the S.H.Figuarts sculpts and the anime draw them. The
  outline sits on the outer edges; the gloss lies on the lit face.
- Spiky locks are leaves: full near the root, then to a point. Straight cuts (`Sculpt.blunt`: Future Trunks, the
  parted, long, swept, slicked and bowl cuts) end blunt and rounded.
- The scalp is a hair mass (the crown 1 px thick, the sides and back half a pixel out from the head) the locks grow out
  of; the underlayers are bodies rather than flat bands and sit well under the locks above. Seen from below or the
  side, nothing shows through. `tools/HairSheet.java` takes `pitch=-0.75` to check from below.

References used: the official S.H.Figuarts product photos on dragon-ball-official.com and tamashiiweb.com (Vegeta in
base and Super Saiyan, Future Trunks, Super Saiyan Gohan), and toy galleries on toyark.com.

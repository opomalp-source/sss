# The Great Ape

A Saiyan or half-Saiyan with a tail can turn into a Great Ape.

## How the change comes

- **Only under a full moon.** Minecraft's moon goes through eight phases, one a night; only the full one works (the
  very first night of a new world is always a full moon). A false moon (the Moon Orb) works any time, for as long as
  it shines.
- **You have to look at it.** Out under the open sky, look at the moon. Your heart starts to pound (you hear it, and
  the edges of your screen pulse red), harder and faster each second. After **six seconds** of staring, the change
  takes you. Look away and it calms again.
- **What stops it:** no tail, a roof or anything solid over your head, or already being in a form.

## As a Great Ape

- **You, grown giant.** The ape is your own character ten times over (about 18 blocks tall; the legendary ape a
  little taller): a heavy build under a coat of fur, a bare chest, your own hairstyle grown into a wild mane in the
  fur's colour, an ape's brow, muzzle and fangs, pointed ears, glowing red eyes and your tail.
- **Clothes tear.** The change rips apart whatever cloth you wear (gi, outfits, turbans, bandanas, headbands, leather).
  The battle armour of the Saiyans and the Frieza Force is made to stretch and stays on; metal armour bursts off into
  your inventory (or onto the ground when it's full). Item tooltips say which is which.
- **The growth.** You hunch and strain as your body grows over about three seconds. Fur spreads over the shoulders,
  trunk and legs, then the face; your muzzle and ears grow out, and your hairstyle becomes the mane. Your own face
  keeps its strained expression until the ape face takes over.
- **The roar.** At full size you rise up, throw your head back and roar with your arms flung wide (the ground
  trembles), then pound your chest. The whole sequence lasts about eight seconds.
- **Every step shakes the ground**, for you and everyone near you: a thud, dust and earth kicked up at the foot, and
  the view shaking. Landing from a jump or a drop is a stomp with both feet, harder the further you fell.
- **A giant's body:** you step up over what a person would climb, reach as far as your arms, stride faster, can't
  suffocate with your head in a cliff and feel a fall only from a height in proportion. Trees and plants you walk
  into give way (leaves and plants are crushed, logs drop as wood); never in safe zones, and protection mods are
  asked first. Server config `destruction.giantTrample` turns it off.
- Enormous strength; no flight, no ki techniques. The camera stands back so you can see the whole ape in third
  person. It rises and retreats with the visible growth, frames your torso, eases further out for the roar and
  returns afterwards. First person rises with the growth too; third person checks the camera's corners against walls.
- **It ends** when the moon is gone (the night ends, the false moon fades, something covers you) or your tail is cut.
  You collapse back, exhausted.
- The golden ape and the legendary ape are the same in their own fur (gold, a green-gold).

## For developers

- Looks: `client/render/ApeLook` (textures and colours), `ApeLayer` (the fur, the glowing eyes, the face on the head
  and its jaw), `ApeHeadModel` with its shape as data in `ApeShape`; other body layers step aside for the ape, the
  hair layer draws your hairstyle in the fur's colour. The size comes from the form's scale (`FormScaleRenderer`).
- Animation: the motion engine's heavy idle and walk (`motion/sets/fighter.json`), the change's roar
  `Anims.APE_ROAR`. Footfalls follow the gait: `client/fx/GiantSteps`.
- Change timing: `transform/ApeChange` (growth 8-68 ticks, roar 72-108, chest beats and recovery to 160, camera
  recovery to 180). `client/render/ApeTransformation` starts on a witnessed non-ape-to-ape change, never a render:
  joining beside an existing ape, rendering a portrait, and switching between ape variants do not replay growth.
  The visual body and camera interpolate together; the server's form size, reach and clothing rules still apply
  immediately when the form changes. `ApeLayer` blends the coat part by part, `FaceLayer` fades the old expression,
  and `FormHairLayer` blends the mane. The roar sound, dust, shake and optional title cut-in land together.
- Server: `transform/GiantBody` (step, reach, stride, trampling, no suffocation, falls, tearing clothes).
- Textures: `tools/GreatApeGen.java` paints the fur bodies (player skin layout), the face parts (the `ApeShape`
  layout) and the eyes; run `java -cp build/classes/java/main tools/GreatApeGen.java` after compiling.

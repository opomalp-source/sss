# The Great Ape

A Saiyan or half-Saiyan with a tail can turn into a Great Ape.

## How the change comes

- **Only under a full moon.** Minecraft's moon goes through eight phases, one a night; only the full one works (the
  very first night of a new world is always a full moon). A false moon (the Moon Orb) works any time, for as long as
  it shines.
- **You have to look at it.** Out under the open sky, look at the moon. Your heart starts to pound (you hear it, and
  the edges of your screen pulse red), harder and faster each second. After **six seconds** of staring, the change
  takes you. Look away and it calms again.
- **White eyes.** While you stare at the full moon, your eyes turn completely white, without pupils. Other players
  see it too. Looking away restores them immediately; during the change they give way to the ape's red eyes.
- **What stops it:** no tail, a roof or anything solid over your head, or already being in a form.

## As a Great Ape

- **You, grown giant.** The ape is your own character ten times over (about 18 blocks tall; the legendary ape a
  little taller): a heavy build under a coat of fur, a bare chest, your own hairstyle grown into a wild mane in the
  fur's colour, an ape's brow, muzzle and fangs, pointed ears, glowing red eyes and your tail.
- **Clothes tear.** The change rips apart whatever cloth you wear (gi, outfits, turbans, bandanas, headbands, leather).
  The battle armour of the Saiyans and the Frieza Force is made to stretch and stays on; metal armour bursts off into
  your inventory (or onto the ground when it's full). Item tooltips say which is which.
- **Armour stays visible.** Surviving battle armour stretches with you throughout the change, including the suit,
  gloves, boots and shoulder pads. Growing fur stays underneath it.
- **The growth.** You clutch your head, brace your legs and arch your back through surges of growth over about four seconds. Fur spreads over the shoulders,
  trunk and legs, then the face; your muzzle and ears grow out, and your hairstyle becomes the mane. Your own face
  keeps its strained expression until the ape face takes over.
- **The roar.** At full size you rise into a heavy, low-armed roar, then two deliberate chest beats and a hunched
  stance. The whole sequence lasts about nine and a half seconds. Your feet stay planted until it finishes.
- **Attack: Ground Stomp.** Press attack (left mouse by default), or the heavy-attack key. You shift your weight,
  lift one knee and drive the foot into the ground. Damage lands after a one-second wind-up: a shockwave around
  that foot reaches nearby grounded foes (about 5.5 blocks), with dust, cracks, a deep impact and camera shake.
  Each target can be hit once per stomp. The other foot goes next; an injured leg is avoided.
- **Commit to the stomp.** Your feet stay planted through its 1.4-second recovery. Stomps are at least 2.8 seconds
  apart and cost 6% of maximum stamina in survival. You need solid ground beneath the striking foot. Jumping,
  being stunned or knocked out, or being moved away during the wind-up cancels it. It cannot hit through walls,
  reach fliers overhead, or hurt anyone in safe zones; normal PvP and ally rules still apply.
- **Stomps are your attacks.** No human punches, combos, weapon hits, ki attacks or dashes while an ape. Holding
  a weapon still uses the stomp. Ordinary walking and landing effects do not deal stomp damage.
- **Every step shakes the ground**, for you and everyone near you: a thud, dust and earth kicked up at the foot, and
  the view shaking. Landing from a jump or a drop is a stomp with both feet, harder the further you fell.
- **A giant's body:** you step up over what a person would climb, reach as far as your arms, stride faster, can't
  suffocate with your head in a cliff and feel a fall only from a height in proportion. Trees and plants you walk
  into give way (leaves and plants are crushed, logs drop as wood); never in safe zones, and protection mods are
  asked first. Server config `destruction.giantTrample` turns it off.
- Enormous strength; no flight, no ki techniques. The camera stands back so you can see the whole ape in third
  person. It rises and retreats with the visible growth, pivots at your eyes, eases further out for the roar and
  returns afterwards. First person rises with the growth too; third person checks the camera's corners against walls.
- **It ends** when the moon is gone (the night ends, the false moon fades, something covers you) or your tail is cut.
  You collapse back, exhausted.
- The golden ape and the legendary ape are the same in their own fur (gold, a green-gold).

## For developers

- Looks: `client/render/ApeLook` (textures and colours), `ApeLayer` (the fur, the glowing eyes, the face on the head
  and its jaw), `ApeHeadModel` with its shape as data in `ApeShape`; other body layers step aside for the ape, the
  hair layer draws your hairstyle in the fur's colour. The size comes from the form's scale (`FormScaleRenderer`).
- Animation: original `idle_ape`/`walk_ape` clips inherit the heavy motion data, with a hunched trunk and low arms;
  the change is `Anims.APE_ROAR`, attacks `APE_STOMP_LEFT/RIGHT`. Footfalls follow `client/fx/GiantSteps`.
- Change timing: `transform/ApeChange` (overlapping growth surges 16-92 ticks, roar 106-142, recovery to 190, camera
  recovery to 210). `client/render/ApeTransformation` starts on a witnessed non-ape-to-ape change, never a render:
  joining beside an existing ape, rendering a portrait, and switching between ape variants do not replay growth.
  The visual body and camera interpolate together; the server's form size, reach and clothing rules still apply
  immediately when the form changes. `ApeLayer` blends the coat part by part, `FaceLayer` fades the old expression,
  and `FormHairLayer` blends the mane. The roar sound, dust, shake and optional title cut-in land together.
- Server: `transform/GiantBody` (step, reach, stride, trampling, no suffocation, falls, tearing clothes).
- Combat: `transform/ApeStomp`; primary/heavy inputs route here before humanoid combat. Server-tick timing:
  contact 20, end 48, next stomp 56. Checks support, range, elevation, line of sight, allies, PvP, safe zones and
  limbs. Growth and stomps use a transient movement modifier, removed on recovery or reversion. A server damage
  gate blocks other outgoing attacks. `/dbz apestomp <players>` uses the same validation for dev checks.
- White eyes: `PublicStatePacket.MOON_EYES`, computed from actual moon visibility and gaze each server tick.
  `FaceLayer` fills the iris masks white without pupils (including over ordinary Minecraft skins); it fades into the
  ape face during growth. Packet protocol 41 includes the new animation/effect meanings.
- Armour depth: fur grows just above each body part; clothing is drawn farther above it on all axes. Keep this
  ordering when editing either layer, including pants and gloves, to avoid the v0.135 transition hiding armour.
- Textures: `tools/GreatApeGen.java` paints the fur bodies (player skin layout), the face parts (the `ApeShape`
  layout) and the eyes; run `java -cp build/classes/java/main tools/GreatApeGen.java` after compiling.

### Front F5 face view (v0.138.0)
The resting head faces forward instead of tipping the chin upward. In your front F5 view, camera pitch does not
change the local head's look pitch, so you can raise the view to inspect the eyes. Gameplay aim and the head
movement seen by other players still follow your actual look. Roar and stomp animations keep their head poses.

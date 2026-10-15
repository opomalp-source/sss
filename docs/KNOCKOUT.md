# Knocked out, carrying, gripping, limbs and clothes

## Knocked out

At zero health a fighter (a player, or an NPC fighter) goes down instead of dying.

- **On screen:** nothing in words: no banner, timer or tag. The downed player's screen darkens at the edges with a
  slow, faint heartbeat, deeper as their time runs out.
- **Moving:** strong fighters (Strength + Constitution at least `knockOutCrawlStrength`) can crawl; the rest just lie
  there.
- **Only two things kill someone who is down:**
  - **Their time runs out:** `knockOutSeconds` for players (tougher ones last up to half again longer),
    `knockOutNpcSeconds` for NPC fighters. A player can give up by holding Sneak for three seconds.
  - **Someone grips them** (below).
- **Blows don't kill them.** Each blow costs them time instead (a twelfth of their total, at least two seconds), and
  draws blood.
- **Saving them:**
  - Right-click them with a **Senzu Bean**: up at full strength, broken limbs set.
  - **Heal** them past a fifth of their health (Ki Heal, a Namekian's healing).
  - **Help them up:** hold right-click on them with an empty hand for five seconds. They are up with a quarter of
    their health.

## Carrying

- **Pick up:** sneak and right-click a downed fighter with an empty hand. They go over your shoulder.
- **While carrying:** you walk 30% slower and can't sprint, attack or use ki. You can fly, more slowly.
- **Letting go:** hold Sneak for a second to put them down gently, or attack to throw them.
- **Being knocked down, stunned, or teleporting away** drops them.
- **Their bleeding continues:** carrying doesn't stop it, so get them to someone who can save them.
- The carried player sees it all from their own body.

## Gripping (finishing off)

Press **F** (the grip key; it takes the place of swapping hands only while there is someone to grip) next to a downed
fighter. The direction you hold picks the finisher:

| Hold | Finisher |
|---|---|
| nothing or Forward | **Ki Execution:** hauled up by the collar, a ki blast point-blank to the chest |
| Left | **Skull Slam:** lifted high by the head and slammed into the ground |
| Right | **Bear Hug:** lifted into a crushing hug |
| Back | **Stomp:** a stomp to the chest that cracks the ground |
| Jump | **Elbow Drop:** a leap and an elbow driven down from above |
| Sneak | **Ki Spear:** a hand of ki driven clean through |

- **Breaking a grip:** anyone hurting the gripper hard (6% of their health in one blow), stunning them or knocking them
  down breaks it, and the downed fighter lives on.
- **NPC fighters grip downed players** they were fighting: they walk up, pause three seconds (time for a friend to
  step in), and grip.

## Limbs and blood

- **Fresh cuts bleed:**
  - A limb cut off by a blade bleeds hard for twenty seconds, then seeps until the wound is closed (below). One cut by
    ki is seared shut and bleeds for six.
  - The stump drips and spurts with the heartbeat, and drains 1% of health a second per wound (which can knock you
    out).
  - The limb flies off and lies on the ground for a minute, in the fighter's skin tone.
- **Hard blows** (an eighth of your health or more) draw blood too.
- **Blood colours:**

| Race | Blood |
|---|---|
| Earthling, Saiyan, half-Saiyan, Tuffle | red |
| Namekian | violet, as Piccolo's |
| Frieza's race | purple |
| Majin | pink goo, in fat slow blobs |
| Android | dark oil, with sparks |
| Cyborg | red, with sparks |
| Bio-Android | Cell's purple |
| Vampire | near black |
| Generic alien | green |
| Core Person | amber |

- **Stopping it:** a **Bandage** (3 paper + 1 string makes 2) stops the bleeding and tends the most hurt limb. Use it
  on yourself or on someone else, even someone down. Senzu Beans and healing stop it too.

## Living without a limb

- **A leg gone** (with no prosthetic) puts you on the ground: you crawl, slowly, low to the ground, and can't jump.
  You can still fly. A prosthetic leg stands you back up.
- **The right arm gone:** the left takes over. It becomes your main hand (what you hold is in it, and it swings), and
  your stances, blows and ki poses are mirrored so the left arm does what the right would. The off hand holds
  nothing: what was in it goes back to your inventory. A blow thrown with one arm is weaker; with none, no ki.
- **The stump** is in your own skin's colour (a Namekian's is green), and its end shows how the wound is:
  - **open:** raw, in your blood's colour, the bone showing;
  - **bandaged:** wrapped, the blood soaking through;
  - **seared:** burnt shut, black at the middle;
  - **healed:** skin drawn over, with a pale, puckered seam. A bandaged or seared stump heals over in five minutes.

## Closing a wound

Only Namekians and Majins close and regrow a lost limb on their own. Anyone else has to close it, or it keeps seeping
(0.4% of health a second) after the first bleeding is over, and while it is open your body heals at only a third of
its usual pace:

- a **Bandage** binds it;
- **Sneak + Ki Blast** sears it shut with your own ki: a tenth of your ki, and it hurts;
- a **Senzu Bean**, Ki Heal or a Namekian's healing closes it at once (healed over).

## Growing it back

Namekians (and Majins, faster) grow lost limbs back, or at once with Regenerate. A bud of slime (pink goo for a
Majin) swells at the stump, then the limb pushes out of it, thin at first and filling out, wet and dripping. They
hunch over it, straining, then stand tall and thrust the new arm out (or stamp the new leg down).

## Battle damage and scars

- **Battle damage** follows the fighter: the rips show their own skin, cuts are in their own blood's colour, and
  bruises are made from their skin. Each fighter has one of three layouts, so it doesn't look the same on everyone.
- **Scars are earned:** a heavy wound (15% of your health or more in one blow) can leave one, more likely the harder
  it was and for a blade, and going down (knocked out) leaves one most of the time. At most one a minute, fourteen in
  all.
  - It goes where the blow landed: the side it came from, high or low; a hard blow high from the front can scar the
    face.
  - What made it shows: a blade or ki disc leaves a fine line (long ones sometimes with stitch marks), a ki blast or
    beam a mottled burn, a heavy blow a short jagged split, a thrust a small puncture.
  - Fresh scars are angry and red; over three days they settle to pale scar tissue, in your own skin's tones.
  - They stay for life (a Senzu Bean heals wounds, not scars) and through death. A wish to be made whole
    (Restoration) clears them. The scars picked on the Life screen are separate and stay.

## Clothes

Clothes torn in battle stay torn until they are mended.

- **A gi:** the pieces take hard blows (4% of health or more) in their durability. They are torn, ripped, then in
  tatters by how worn the most worn piece is. A gi never wears right out: it stays on, in rags.
- **Your own clothes** (no gi on, and NPC fighters' uniforms) wear the same way, kept with the fighter.
- **Mending:**
  - A **Sewing Kit** (an iron nugget over string, leather and string; six uses) mends your own clothes and patches each
    gi piece halfway.
  - An **anvil** with the gi's material finishes the job.
  - Senzu Beans heal the body, not the clothes.
- Bruises, scratches and grime still come and go with health.

## Commands (cheats)

- `/dbz knockout <targets>`: put fighters down.
- `/dbz carry <carrier> [target]`: pick a downed fighter up (no target: let go).
- `/dbz grip <gripper> <target> <finisher>`: finish them with `ki_execution`, `skull_slam`, `stomp`, `bear_hug`,
  `elbow_drop` or `ki_spear`.
- `/dbz limb <targets> <limb> <action>`: `break`, `sever` (it bleeds), `bandage`, `sear`, `close` (healed over),
  `regrow` (with the regrowth), `prosthetic` or `heal`.
- `/dbz scars <players> add <count>`, `age <days>`, `clear`: give, age or clear battle scars.

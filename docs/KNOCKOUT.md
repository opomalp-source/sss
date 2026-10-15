# Knocked out, carrying, gripping, limbs and clothes

## Knocked out

At zero health a fighter (a player, or an NPC fighter) goes down instead of dying.

- **On screen:** a timer shows over their head. The downed player's own screen pulses red, with the time left and what
  can save them.
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
- The carried player sees it all from their own body, with "Carried by ..." on their screen.

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
  - A limb cut off by a blade bleeds for twenty seconds. One cut by ki is seared and bleeds for six.
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
- `/dbz limb <targets> <limb> sever`: cut a limb off (it bleeds).

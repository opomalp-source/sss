# Handoff: where Dragon Block Zenith stands (v0.136.0, 2026-10-16)

## The prompt to paste into a new session

> You're continuing work on **Dragon Block Zenith**, my Minecraft 1.20.1 Forge mod (Java 17), a Dragon Ball game
> with races, forms, ki attacks, combat, auras, flight, quests and an other world. The repo is attached (or cloned
> from https://github.com/opomalp-source/sss, branch `main`).
>
> Before doing anything, read `CLAUDE.md` (how to build, test, release and check visuals), then `HANDOFF.md` (where
> things stand), the last few entries of `DEVLOG.md` and the open items in `TODO.md`. Follow their workflow: each
> finished version gets a DEVLOG entry, TODO and docs updates, a `mod_version` bump, all GameTests passing, and a
> release with `tools/release.sh` (which also publishes the jar for my auto-updater). Check how things look with the
> dev-client screenshots before releasing, because I want it all to look great. Use the official Dragon Ball names in
> anything players read, keep every asset original, and leave story mode for last.
>
> The latest version is v0.136.0. When you've read up, tell me in a few lines what you see as the state of the mod
> and what you'd do next, then wait for me.

## Recent work (newest last; details in DEVLOG.md)

- **v0.127-128:**
  - Knock-out, carry, grip finishers.
  - Limbs and blood: race-coloured blood, crawling on a lost leg, the left hand taking over for a lost right arm.
  - Stumps that must be closed (bandage, sear with ki, Senzu); Namekian and Majin regrowth with an animation.
  - Race-tinted battle damage in three layouts; earned scars that fade from red to pale; no knock-out text.
  - Guide: `docs/KNOCKOUT.md`.
- **v0.129.0:**
  - Ultra Instinct and Mastered Ultra Instinct are techniques over any form (anyone taught by Whis).
  - New auras after the user's pictures: UI wispy blue-violet with dashed light streaks; Super Saiyan God a fire.
- **v0.130.0:**
  - Great Ape: only at a full moon, after looking at it six seconds.
  - A new ape body (`ApeShape`, `GreatApeModel`, `tools/GreatApeGen.java`) with a roar and chest beats.
  - Guide: `docs/GREAT_APE.md`.
- **v0.131.0:**
  - The transformation burst: a pillar of light, an energy dome, the ground answering.
  - Aura settings: own aura, intensity, ground rings, aura light. This finishes the aura rework (CX-24).
- **v0.132.0:**
  - Clothes and armour: richer painted textures, thick parts standing off the body, layered Saiyan pads and the
    Frieza Force's sweeping pads.
  - Head clothing items: Piccolo's turban, bandana, headband.
  - Guide: `docs/CLOTHES.md`.
- **v0.133.0:** feel pass. Scars come from 15% blows and from being knocked out; an open stump slows healing to a
  third.
- **v0.134.0:** the Great Ape after the user's pictures.
  - The player's own figure grown giant (ten times, 18 blocks) under fur, their hairstyle as the mane, an ape's
    face with red eyes and fangs (`ApeLayer`, `ApeHeadModel`, `ApeShape`, `tools/GreatApeGen.java`).
  - Every footfall and landing shakes the view, with dust (`GiantSteps`); a giant's step, reach, stride, trampling
    (`GiantBody`).
  - The change tears cloth; battle armour stretches; metal armour bursts off.
- **v0.135.0:** Great Ape growth and camera, the first priority in the user's next Great Ape pass.
  - Hunching, straining breaths, growth from normal size over about three seconds; fur arrives part by part;
    the muzzle and ears grow out, the mane grows and changes colour, the original face strains then fades.
  - Full-sized roar, sound, dust, shake and title cut-in together; chest beats and recovery. About eight seconds.
  - The camera rises and retreats with the visible body, frames the torso, eases farther out for the roar and
    returns; camera-corner collision checks. First person rises with the growth too.
  - A witnessed transition starts the sequence; seeing an existing ape or switching ape variants does not replay it.
    Reverting interrupts it and cancels the pending roar. The server still applies its form size and rules immediately.
  - 246 GameTests; front/rear growth recordings, restored face, mane framing, battle armour, first-person rise and
    wall collision checked in devshots. Guide: `docs/GREAT_APE.md`.

- **v0.136.0:** user feedback on the Great Ape addressed: ground-stomp offense only (primary/heavy attack), 20-tick
  wind-up, one contact, recovery/cooldown, support/wall/elevation/PvP/limb checks; planted feet during growth and
  attacks. Rewritten transformation choreography and original ape idle/walk motion clips. Armour depth fixed so
  fur cannot cover surviving battle armour during growth. White pupil-less eyes while staring at the moon, synced
  to observers and restored immediately when looking away. 248 GameTests. Protocol 41.

## Known gaps and ideas

- NPC skins dressed in the outfits (soldiers, the brute) are baked images and keep the old outfit look. Re-baking
  them needs a full `ArtGen` run, which rewrites many other images too: check its diff carefully.
- The bandana is still a boxy shape on the square head.
- Scars are drawn on the body layer, so a shirt painted into a plain Minecraft skin doesn't cover them (a gi or a
  jacket layer does).
- Regrowth isn't animated in your own first-person view.
- The Great Ape deliberately attacks only by ground stomps now, as requested. Seek play feedback on the wind-up,
  impact, recovery, new locomotion and giant camera; do not add a mouth blast without a new request.
- A true duel simulation (player against NPC, every race) for pacing hasn't been built: the feel pass used the
  balance reports (`BalanceReport`, `BalanceReport2`, written to run-gametest/*.md).
- Open TODO items: more content (bosses, planets, a space dimension), the original sound set, Animation v5 (combat
  actions as data, CX-18b), the CX-17 originals, and story mode last.

## Tools on the user's Windows PCs

- The first PC (user `sunam`): JDK 17 at `C:\Users\sunam\.jdks\jdk-17.0.20.1+1`; Python 3.12 and ffmpeg are
  installed (needed for the `.py` generators; old shells may not have them on PATH).
- The second PC (user `musta`, from v0.134.0): JDK 17 at `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` (set
  `JAVA_HOME`); no Python on PATH. Windows long paths are off, so keep the repo at a short path (or map one with
  `subst`): deep folders break the Minecraft toolchain. The current checkout is
  `C:\Users\musta\Documents\Codex\2026-10-16\you-re-continuing-work-on-dragon\repo`, mapped to `Z:`. Its dev server is
  set up (`run-server`, RCON password in `server.properties`; do not print it). The scripted client needs
  `pauseOnLostFocus:false`. Leave `server-ip` empty for its `localhost` IPv6 connection.
- The user's PC auto-installs released jars.

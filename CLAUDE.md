# Dragon Block Zenith: notes for Claude sessions

Minecraft 1.20.1 Forge mod (Java 17), a Dragon Ball game. Start with `HANDOFF.md` (where things stand), then
DEVLOG.md (newest entries at the bottom) and TODO.md.

- **Building and testing:** JDK 17 (`JAVA_HOME`); on Windows keep the repo at a short path (long paths break the
  toolchain; `subst` a drive if needed). Tests are GameTests:
  `rm -f run-gametest/world/serverconfig/dbzenith-server.toml; ./gradlew --offline build runGameTestServer`
  (all must pass; a few are timing-sensitive, rerun once before digging in). Then `./gradlew --offline build -x test`
  for the jar. In tests, schedule delayed steps with `Delays.after(helper, ticks, ...)`, never
  `helper.runAfterDelay` (nested delays crash the test server).
- **Releasing a version:** update DEVLOG.md (append), TODO.md and the docs, bump `mod_version` in gradle.properties,
  build and test, then `tools/release.sh <version> "<summary>"`. It commits (with the Co-Authored-By trailer), tags,
  installs the jar into the user's mods folders on Windows, and runs `tools/deploy.sh --no-build`, which publishes
  the jar on the `builds` branch. The user's PC runs `tools/windows/dbz-update.ps1` every 10 minutes, swapping the new
  jar in. Never skip deploying a finished version. Push `main` and the tags when the user asks to update the repo.
- **Checking visuals:** devshots, i.e. screenshots taken by the dev client on `/dbz devshot`.
  - On Windows: `tools/dev/winshots.sh "<setup;cmds>" <shot> "@cmd|cmd" <shot> ...`, then look at them with
    `tools/dev/Crop.java`.
  - In a Linux cloud container: `tools/devshots.sh <shot list> <log dir>` (see the script's header and DEVLOG
    "verification recipe").
  - Shot names: `front_`/`third_` camera, `hidegui`, `aurapv.<aura>.<state>`, `auratech.kaioken.<stage>`,
    `auraset.<path-with-dashes>+<value>`, `aurareload`, `seq<N>` for an N-frame clip.
  - Over RCON `~` is world spawn, which has a sky lookout high above it: teleport away (e.g. `tp Dev ~70 ~ ~`) to test
    things that need the open sky.
- **Generators (re-run after editing their tables):**
  - Auras are data (docs/AURA_GUIDE.md). Form auras come from `tools/gen_auras.py`; hand-made ones (Blue, God, Ultra
    Instinct) are in its `KEEP` set.
  - Movement animations: `tools/gen_motion.py` and `tools/gen_styles.py` (run from tools/) write
    motion/clips/*.json (docs/ANIMATION.md "Generated clips"). Combat clips live in `client/anim/Anims.java`.
  - Art: `tools/ArtGen.java` (`java tools/ArtGen.java only gear` for the clothes alone; a full run rewrites everything);
    the Great Ape's textures `java -cp build/classes/java/main tools/GreatApeGen.java`; battle damage
    `tools/BattleDamageGen.java`; blood and item icons `tools/BloodGen.java`, `tools/HeadwearIcons.java`.
- **Conventions:** official Dragon Ball names in player-facing text (ids may stay as they are); every asset original;
  the user wants things to look great, so check visuals in the dev client before releasing. Story mode comes last.

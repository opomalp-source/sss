# Dragon Block Zenith: notes for Claude sessions

- Read DEVLOG.md (newest entries at the bottom) and TODO.md before starting.
- **Deploying:** whenever you finish a version (bump `mod_version` in gradle.properties), run `tools/deploy.sh`. It
  builds the jar and publishes it on the `builds` branch; the user's PC runs `tools/windows/dbz-update.ps1` every 10
  minutes, which swaps the new jar into their mods folder and deletes the old one. Never skip this step.
- **Checking visuals in a cloud container:** `tools/devshots.sh <shot list> <log dir>` starts the dev server and an
  Xvfb client and takes `/dbz devshot` screenshots (see the script's header and DEVLOG "verification recipe").
  Devshot names: `front_`/`third_` camera, `hidegui`, `aurapv.<aura>.<state>`, `auratech.kaioken.<stage>`,
  `auraset.<path-with-dashes>+<value>`, `aurareload`, `seq<N>` for an N-frame clip.
- **Auras** are data (docs/AURA_GUIDE.md). Form auras come from `tools/gen_auras.py`; re-run it after editing its tables.

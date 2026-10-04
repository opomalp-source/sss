# Dragon Block Zenith

A Dragon Ball-inspired total-conversion mod for **Minecraft 1.20.1 / NeoForge 47.1.106**. Mod id `dbzenith`.
Status, roadmap and decisions: [TODO.md](TODO.md), [FEATURE_MATRIX.md](FEATURE_MATRIX.md), [DEVLOG.md](DEVLOG.md), [BRIEF.md](BRIEF.md).

## Build
Requires JDK 17 (`JAVA_HOME`). On this machine: `C:\Users\sunam\.jdks\jdk-17.0.20.1+1`.

```bash
./gradlew build                # jar -> build/libs/dbzenith-<version>.jar
./gradlew runGameTestServer    # automated in-game tests, non-zero exit on failure
./gradlew runClient            # dev client (username Dev)
./gradlew runServer            # dev dedicated server in run-server/
```

## Play
Install **NeoForge 1.20.1-47.1.106** into a dedicated game directory, then put `dbzenith-<version>.jar` in that profile's `mods` folder.

## In-game (Phase 0)
- Creative tab **Dragon Block Zenith** → Senzu Bean (full heal + ki/stamina refill).
- Top-left debug overlay shows your synced stats (toggle `hud.showDebugOverlay` in `config/dbzenith-client.toml`).
- `/dbz stats`, `/dbz set <player> <field> <value>` (fields: strength, dexterity, constitution, ki_power, willpower, mind, spirit, tp, body, ki, stamina, release, alignment, physical_age, mental_age), `/dbz tp add`, `/dbz race`, `/dbz path`, `/dbz refill`, `/dbz reset`.
- Balance numbers: `<world>/serverconfig/dbzenith-server.toml`.

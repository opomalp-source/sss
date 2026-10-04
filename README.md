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

## Controls (rebind under Controls > Dragon Block Zenith)
| Key | Action |
|---|---|
| G (hold) | Charge ki: fills ki, raises release %, drains stamina, aura |
| Z | Lower release % by 10 |
| V | Toggle flight (drains ki) |
| Left Alt (hold) | Guard: blocks 60% of damage, costs stamina |
| R | Use selected technique |
| Y | Next technique in your deck |
| H (hold, release) | Charge a heavy strike: your next punch hits 1.5x-3x harder |
| B | Dash in your movement direction (brief afterimage: attacks miss) |
| J | Transform to your next form (or your chosen target). Shift+J: drop one form |
| N | Overdrive: stackable power multiplier that burns your body. Shift+N: end |
| K | Training screen: spend TP on attributes (Shift+click = +10). **Forms >** shows your form tree; **Techniques >** lets you learn techniques with TP and equip your deck (4 slots, up to 8 with level) |

## In-game
- First join opens **Create your character**: race, path, body, hair, eyes, alignment ("Decide later" reopens it next time).
- Races: Human, Saiyan, Half-Saiyan, Namekian, Frost Demon, Majin, Android, Cyborg. Each has its own form line, passives and racial technique.
- Creative tab **Dragon Block Zenith**: Senzu Bean (full heal), Moon Orb (false moon), Technique Scrolls (teach a technique).
- Top-left HUD: power level, body/ki/stamina, release %, status. Earn TP by fighting and charging; spend it with K.
- `/dbz stats`, `/dbz set <player> <field> <value>` (fields: strength, dexterity, constitution, ki_power, willpower, mind, spirit, tp, body, ki, stamina, release, alignment, physical_age, mental_age), `/dbz tp add`, `/dbz race`, `/dbz path`, `/dbz refill`, `/dbz reset`.
- Balance numbers: `<world>/serverconfig/dbzenith-server.toml`.

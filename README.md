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
| O | Kaioken: one stage up per press (learn it first, Shift+I). Shift+O: release |
| K | Training screen: spend TP on attributes (Shift+click = +10). **Forms >** shows your form tree; **Techniques >** lets you learn techniques with TP and equip your deck (4 slots, up to 8 with level) |

## In-game
- First join opens **Create your character**: race, path, body, hair, eyes, alignment ("Decide later" reopens it next time).
- Races: Human, Saiyan, Half-Saiyan, Namekian, Frieza Race, Majin, Android, Cyborg. Each has its own form line, passives and racial technique.
- Creative tab **Dragon Block Zenith**: everything below plus Senzu Bean (full heal), Moon Orb (false moon), Technique Scrolls (teach a technique). Gear, training blocks, radar, pod and boss items are also craftable (see the recipe book).
- Top-left HUD: power level, body/ki/stamina, release %, status. Earn TP by fighting and charging; spend it with K.
- **Training**: Gravity Chamber block (right-click to raise the g, sneak + right-click to lower; too much g slows and hurts you), Punching Bag, meditation (sneak and stand still), Training Weights (chest slot). All of them multiply TP gains.
- **Hyperbolic Time Chamber**: place and use the Time Chamber Door. 10g, x4 training, one day's stay, then it sends you back where you came in.
- **Dragon Balls**: seven balls are scattered near world spawn. Hold the Dragon Radar to track them. Place all seven within 4 blocks of each other and right-click one to call Shenron and pick a wish. Afterwards the balls turn to stone for two days.
- **Gear**: Scouter (helmet; reads power levels and breaks on huge ones), Capsule (27 slots in your pocket), Turtle / Demon gi and Battle Armor (full-set bonus), Training Weights.
- **Enemies**: Saibamen, Frieza Force Soldiers and Red Ribbon Androids spawn in the world and scale to the nearest player. Bosses: use a Frieza Force Emblem (Frieza) or a Rage Totem (Broly) on the ground.
- **Quests**: in a new world Master Roshi waits in a dojo and a Galactic Patrolman in an outpost, both about 12 blocks from spawn (in an older world they stand near spawn; `/dbz build dojo|outpost <pos>` builds the houses). Right-click them. The master's questline ends with the Divine Ritual (god ki). The Galactic Patrol hands out repeatable bounties; reputation raises your rank.
- **Space**: set the Space Pod down on the ground, climb in (right-click) and pick Earth, Namek or King Kai's Planet (10g): it launches and lands you at the destination, where it stays for the trip back (punch it to pack it up). Using the pod in the air still travels instantly. It needs a minute to recharge between flights. Namek has its own round-canopy trees and white dome houses (in newly explored land).
- **Life sim**: characters age (Androids and Majins never do), and STR/DEX fade in old age; the mind ages too and brings wisdom (more TP). Earned titles are shown before your name (Title button on the stat screen). **Life** (stat screen) shows thirst, temperature and your partner, and lets you pick a scar and a tattoo. Namekians, Frost Demons and Majins get a full race look (green, white-and-violet, pink) that can be switched off there. Drink (water bottles, milk, soups, melon) or swim when THIRSTY; deserts by day and the Nether are HOT, snow is COLD (a chestplate or a fire helps). Give someone a **Promise Ring** to become partners (+TP when training together). Toggles: `life_sim.*`.
- **Advanced combat**: hits on airborne foes juggle them; a heavy hit (H) from the ground launches, from above while looking down it spikes. New techniques: Ki Transfer, Grab & Throw (use twice), Paralysis Wave (stun), Seal Orb (seals ki), Gathering Sphere (huge ball formed overhead). Techniques gain **mastery** with use (cheaper, stronger, faster).
- **Race extras**: Namekians learn **Namekian Fusion** (fuse with a beaten Namekian Warrior on Namek, or a Namekian friend who accepts); Majins learn **Absorption** (absorb beaten foes for temporary power). Saiyan tails can be cut by blades and grow back in 3 days.
- **Alignment** (stat screen): slaying monsters is good, killing villagers, masters or good players is evil. Good: faster ki. Evil: harder hits. Paths: Fighter (melee, stamina), Spiritualist (ki), Hybrid (TP).
- **Prestige**: at level 2000 the stat screen offers to start your attributes over for a permanent bonus. **God ki** hits ordinary ki harder and cannot be read by scouters.
- `/dbz stats`, `/dbz set <player> <field> <value>` (fields: strength, dexterity, constitution, ki_power, willpower, mind, spirit, tp, body, ki, stamina, release, alignment, physical_age, mental_age, prestige, thirst, scar, tattoo), `/dbz tp add`, `/dbz race`, `/dbz path`, `/dbz refill`, `/dbz reset`.
- Balance numbers: `<world>/serverconfig/dbzenith-server.toml`. Too slow or too fast? Change `tpGainMultiplier` (2.0 = twice as fast). See [BALANCE.md](BALANCE.md) for the targets and what each value does.

## Libraries
Bundled inside the mod jar (Jar-in-Jar), so there is nothing extra to install:
- [playerAnimator](https://github.com/KosmX/minecraftPlayerAnimator) (MIT): player animations.
- [bendy-lib](https://github.com/KosmX/bendy-lib) (MIT): bending elbows and knees in those animations.

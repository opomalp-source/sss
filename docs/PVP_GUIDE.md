# PvP, the meters and fighting styles (CX-20)

How PvP mode works, how the two bars fill and gate forms and techniques, and how fighting styles and their masters are built. It also covers how to add a master, a style or a form threshold. The textures and animations are listed in [PVP_ASSETS.md](PVP_ASSETS.md).

## PvP mode

| | PvP off (default) | PvP on |
|---|---|---|
| Melee | Vanilla's punch, vanilla damage | The combat engine: combos, heavies, guard, dashes, ki blasts, lock-on |
| Body | A relaxed idle | The fighting stance (blended in over 5 ticks) |
| Camera | Your own | Third person over the right shoulder (`pvpCamera`) |
| Crosshair | A small grey four-pointed star | A small grey dot at the real aim point |
| Forms and techniques | Free | Gated by the two bars |
| Bars | Hidden | Bottom right |

- **P** switches PvP mode. It is silent: no effects, sounds or messages.
- **Auto-tag:** any hit that reaches you (from a player, an NPC or a mob) tags you for `combatTagSeconds`, during which P can't switch PvP off.
  - With `tagForcesPvp`, the tag also switches you into PvP.
  - With `hurtOutOfPvp`, players can hurt players who are out of PvP; the blow tags them in.

Server config `[pvp]`: `tagForcesPvp`, `hurtOutOfPvp`, `combatTagSeconds`, `toggleCooldownSeconds`, `meterGate`. Client config: `pvpCamera`, `shoulderOffset`, `shoulderHeight`, and `crosshairStyle`, `crosshairDotMode`, `crosshairSize`, `crosshairColor`, `crosshairOpacity` (also in Settings → Style and → Camera).

## The meters

Two upright bars sit bottom right, shown only in PvP mode. Both run 0–100.

- **Form bar (right):** in your aura colour. Its studs are your unlocked forms, in the order J climbs them, following your target form.
- **Technique bar (left):** in the technique's colour. Its studs are the Kaioken stages you know, then Ultra Instinct. Ultra Instinct is a technique (key O), never a form on J.

What fills them (PvP mode only, against anything):

| | Form bar | Technique bar |
|---|---|---|
| Each 1% of the foe's health you take off | +1 | — |
| Each 1% of your own health lost | +1.5 | — |
| Perfect guard | +5 | +10 |
| Vanish | — | +6 |
| Counter that lands | — | +8 |
| Every fifth hit of a combo | — | +4 |
| Charging (per second) | — | +2 |

How the bars gate forms and techniques in PvP (`meterGate`):

- **J** goes to a form only once the bar reaches its stud. If the next form is out of reach, J goes to the highest reached form above yours.
- **O** raises Kaioken only to stages whose stud is reached. With a full technique bar, O goes straight to Ultra Instinct.
- **Outside PvP** everything is free, as before.

How the bars drain:

- **While powered up:** the form bar drains while you hold a form (1%/s, plus 0.25%/s per tier). The technique bar drains 2%/s while Kaioken or Ultra Instinct is on.
- **Without fighting:** after 10 s, both bars decay 3%/s.
- **Dropping back:** a form holds while the bar stays above the stud below it. At that stud you drop one form, and at 0 you return to base. Kaioken drops back by bands the same way; Ultra Instinct holds until the bar is empty.
- **Switching PvP:** going into PvP already transformed fills the bars to what you hold. Leaving PvP empties them.

### The rules file

Every number above lives in `data/<namespace>/combat/meters/*.json`.

- **Merging:** files merge in name order, `default` first. A later file overrides only the fields it gives.
- **Clients:** they receive the merged rules on joining and after `/reload`.

The mod's own file is `data/dbzenith/combat/meters/default.json`:

```json
{
  "form": {
    "per_percent_dealt": 1.0, "per_percent_taken": 1.5, "perfect_guard": 5,
    "drain_per_second": 1.0, "drain_per_tier": 0.25,
    "decay_after_seconds": 10, "decay_per_second": 3,
    "steps": {}
  },
  "technique": {
    "per_percent_dealt": 0.0, "per_percent_taken": 0.0,
    "perfect_guard": 10, "vanish": 6, "counter": 8, "combo_every": 5, "combo": 4,
    "charging_per_second": 2, "drain_per_second": 2,
    "decay_after_seconds": 10, "decay_per_second": 3,
    "kaioken": [{"stage": 2, "at": 25}, {"stage": 4, "at": 40}, {"stage": 10, "at": 60}, {"stage": 20, "at": 80}],
    "ultra_instinct_at": 100,
    "colors": {"kaioken": "#FF2A1E", "ultra_instinct_sign": "#A9B1BD", "ultra_instinct": "#F4F8FF"}
  }
}
```

Both bars take the same fields (`vanish`, `counter`, `combo_every`, `combo`, `charging_per_second` also work on the form bar).

### Adding a form threshold

By default, a player's unlocked forms are spread evenly up the form bar: with two forms, at 50 and 100. To pin a form to a fixed place, give it in `form.steps` in a datapack file, for example `data/mypack/combat/meters/thresholds.json`:

```json
{ "form": { "steps": { "super_saiyan": 30, "super_saiyan_2": 60, "super_saiyan_blue": 95 } } }
```

- Forms not listed keep their even place.
- Keep the places rising along a ladder; a later form below an earlier one would be reachable first.
- The ids are the form ids (`transform/Forms.java`).

**A technique step:** add a Kaioken stage to `technique.kaioken` (`{"stage": 3, "at": 33}`), or move Ultra Instinct with `ultra_instinct_at`. A stage only appears once the player knows it (the Kaioken skill level). New colours go in `technique.colors`.

**Debug:**
- `/dbz meter <player> form|tech|both <0-100>` sets a bar.
- `/dbz unlockform <player> <form>` unlocks a form outright, so its stud appears.

## Fighting styles

**Purely cosmetic.** A style only changes how you move: never your stats, damage, speed or anything else. No buffs, no debuffs; a test checks it.

A style changes some of nine animation slots, and the player picks each slot on its own in K → Styles:

| Slot id | What it plays | Set |
|---|---|---|
| `idle` | Standing still | normal |
| `walk` | Walking | normal |
| `sprint` | Sprinting | normal (and PvP) |
| `hover` | Hovering in flight | normal (and PvP) |
| `flight` | Cruising flight | normal (and PvP) |
| `fast_flight` | Flying flat out | normal (and PvP) |
| `charge` | Charging ki | normal (and PvP) |
| `fight_stance` | Standing in PvP mode | fighting |
| `fight_steps` | Moving in PvP mode | fighting |

In PvP mode, the slots without a fighting version (sprint, flight and the rest) keep the style chosen for them.

### Adding a style (data and resources only)

1. **The style:** `data/<ns>/styles/<id>.json`:

```json
{
  "master": "goku",
  "color": "#F07818",
  "slots": {"idle": "my_idle", "walk": "my_walk", "fight_stance": "my_stance"},
  "requirements": {
    "battle_power": 6000, "affinity": 4, "training_minutes": 10,
    "items": [{"item": "minecraft:cooked_beef", "count": 16}],
    "quest": "dbzenith:some_quest", "races": ["saiyan", "half_saiyan"], "flag": "some_flag"
  },
  "npcs": ["dbzenith:ki_soldier"]
}
```

   - **master:** the master who teaches it. A master may teach several styles.
   - **slots:** clip ids; leave out the slots it doesn't change.
   - **requirements:** every field is optional. The items are handed over when the style is learned.
   - **npcs:** NPC types that move this way.

2. **The clips:** `assets/<ns>/motion/clips/<clip>.json` (see below).
3. **The names:** `style.<ns>.<id>` and `style.<ns>.<id>.desc` in the lang file.

Then `/reload` for the data and F3+T for the clips. Debug commands:

- `/dbz style grant|revoke <player> <id|all>`
- `/dbz style equip <player> <slot> <id|default>`
- `/dbz style affinity <player> <master> <n>`
- `/dbz style training <player> <master> <minutes>`

### Clips

A clip keys bones at normalised times (0–1):

- **Bones:** `head`, `torso`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`, `body`.
- **Channels:** `rot` [pitch, yaw, roll] in degrees, `pos` [x, y, z] in pixels (for `body`, y down is positive), and `bend` (elbow or knee, degrees).
- **Timing:** `"sync": "time"` with a `length` in ticks, or `"sync": "stride"` with a `stride` in blocks per cycle (gaits).
- **Symmetric gaits:** `"symmetric": true` mirrors the right limbs half a cycle later.

Rules of thumb on the player model:

| Pose | Values |
|---|---|
| Arm forward or up | Negative pitch (`-90` straight ahead, `-170` overhead) |
| Arm outward | Positive roll for the right arm, negative for the left |
| Arms crossed | right `[-72, -48, 0]` bend 112, left `[-66, 52, 0]` bend 108 |
| Hands behind the back | right `[24, 28, 6]` bend 70, left `[24, -28, -6]` bend 70 |
| Hands in the pockets | right `[8, -8, 6]` bend 30, left `[8, 8, -6]` bend 30 |
| Crouch | `body` pos y positive (2 is a deep crouch), knees bent 30–60 |

**Inheritance** keeps style clips short:

```json
{"base": "walk", "speed": 1.3,
 "bones": {"rightArm": [{"t": 0.0, "rot": [24, 28, 6], "bend": 70}], "leftArm": [{"t": 0.0, "rot": [24, -28, -6], "bend": 70}]},
 "offset": {"torso": {"rot": [18, 0, 0]}}}
```

- `base` copies a clip, which may itself inherit.
- Each bone in `bones` replaces the base's keys for that bone.
- `offset` adds to every key of the bones kept from the base.
- `speed` shortens a time clip's length or a gait's stride.

## Masters

A master is an NPC who teaches styles. Each is its own entity type, because entity types are registered at start-up, so adding one takes a few lines of code:

1. **The code:** add an entry to `style/MasterRoster.java`: `MY_MASTER(scale, hitboxHeight, eggColour, eggSpotColour)`. That entry registers the entity (`dbzenith:my_master`), its spawn egg and its renderer.
2. **The skin:** `assets/dbzenith/textures/entity/fighter/my_master.png`. It is 128x128: the player skin layout at twice the resolution, both layers. You can draw it by hand or add a painter to `tools/ArtGen.java` (`NpcArt.masters()`) and run `java tools/ArtGen.java only masters`.
3. **The look:** an entry in `client/render/NpcLooks.java` for the 3D hair preset and colour, race parts (antennae, horns, ears, a tail) and extras (cape, shell, pads...).
4. **The data:** `data/<ns>/masters/my_master.json`:

```json
{"uses": "my_style", "color": "#C87A30", "likes": ["minecraft:cooked_chicken"]}
```

   - **uses:** the style the master moves in.
   - **likes:** the gifts that raise affinity.
   - **color:** the master screen's glow.

5. **The lang:** `entity.dbzenith.my_master`, `item.dbzenith.my_master_spawn_egg`, `master.dbzenith.my_master.greet`.
6. **The egg model:** `assets/dbzenith/models/item/my_master_spawn_egg.json` with `{"parent": "minecraft:item/template_spawn_egg"}`.
7. **The styles:** at least one style naming the master (`"master": "my_master"`).

`StyleTests` checks that every master has data, a style, a skin, names and an egg name, and that every style's clips exist.

**How players learn** (`style/StyleLogic`):

- **The master's screen:** right-click a master to open it, unless you're holding a liked gift, which you give instead.
- **Affinity (0–10 per master):**
  - +1 for the first talk each day.
  - +2 per liked gift (three a day).
  - +1 for every five minutes of training together.
- **Training:** "Train together" counts every second spent within 10 blocks of the master.
- **Learn:** puts the style into every slot still on the default.

## All the debug commands (CX-20)

| Command | Does |
|---|---|
| `/dbz pvp <player> [true\|false]` | Switches PvP mode |
| `/dbz pvptag <player>` | Forces a combat tag |
| `/dbz meter <player> form\|tech\|both <0-100>` | Sets a bar |
| `/dbz unlockform <player> <form> [true\|false]` | Unlocks a form outright (its stud appears) |
| `/dbz style grant\|revoke <player> <style\|all>` | Gives or takes a style |
| `/dbz style equip <player> <slot> <style\|default>` | Sets one animation slot |
| `/dbz style affinity <player> <master> <0-10>` | Sets affinity |
| `/dbz style training <player> <master> <minutes>` | Sets minutes trained together |

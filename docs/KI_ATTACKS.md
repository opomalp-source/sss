# Ki attacks

Every ki attack is a data file: `data/<namespace>/ki_attacks/<id>.json`. The 34 attacks the mod ships are in
`src/main/resources/data/dbzenith/ki_attacks/`.

- **Change one:** a file with an existing attack's id changes that attack. Anything the file leaves out keeps its
  built-in value.
- **Add one:** a file with a new id adds a new attack.
- **Loading:** files load with the datapacks, so `/reload` picks up changes, and are sent to every client.
- **Starting point:** `/dbz kiattacks export` writes every attack as it currently stands to
  `<server folder>/ki_attacks_export/`.

## Adding an attack

1. Copy the file closest to what you want (for example `ki_blast.json`) to `data/dbzenith/ki_attacks/my_blast.json`.
   You can also use a datapack of your own, in its own namespace.
2. Change the fields (below).
3. Add its name to a language file: `"technique.dbzenith.my_blast": "My Blast"` and
   `"technique.dbzenith.my_blast.desc": "..."`.
4. `/reload`, then `/dbz learn @s my_blast` to try it.

What an attack *does* beyond damage comes from its `style` and `effect`. Those are built into the mod; files choose
among them.

## Fields

| Field | Meaning |
|---|---|
| `style` | `ball` (a blast; with `count` above 1 a volley), `disk` (a cutting disc), `beam`, `self` (an effect on yourself or around you) |
| `effect` | `none`, or a special: `heal_self`, `heal_ally`, `blind_area`, `explosive_wave`, `teleport`, `ki_sense`, `energy_absorb`, `candy`, `ki_transfer`, `stun_area`, `ki_seal`, `grab`, `fuse`, `absorb`, `life_drain`, `fusion_dance`, `spirit_bomb`, `hakai` |
| `effect_power` | The effect's strength (a heal share, a radius, a duration...; it depends on the effect) |
| `ki_cost` | Base ki cost |
| `damage` | Damage multiplier on the caster's ki damage |
| `speed` | Blocks a tick (beams: how fast they extend) |
| `size` | Diameter in blocks (beams: width) |
| `cooldown` | Ticks before it can be used again |
| `count`, `spread` | Projectiles a use (volleys) and their spread in degrees |
| `pierce` | Targets a projectile passes through |
| `homing` | Seeks the nearest target |
| `explosion` | Explosion power where it lands (0: none) |
| `destructive` | Whether it leaves craters |
| `color` | `"#RRGGBB"`, or `"aura"` for the fighter's aura colour |
| `life` | Ticks it flies (beams: how long they last) |
| `drop` | Ticks it forms over the head before being thrown (large spheres) |
| `learn_tp`, `unlock_level` | TP to learn it and the level it unlocks at (0: starting or racial) |
| `races` | Who can learn it (leave it out for everyone), e.g. `["saiyan"]` |
| `ki_type` | `pure`, `burning`, `freezing`, `shock`, `corrosive`, `draining`, `divine` |
| `flags` | Any of `split`, `bounce`, `guided`, `chain`, `guard_break`, `stun`, `knockback`, `placed`, `rain`, `charged` |
| `charge_seconds`, `charge_power` | How long holding the key can charge it (0: fires at once), and its damage multiplier at a full charge |
| `look` | How it looks and sounds (below) |

## `look`

| Field | Meaning |
|---|---|
| `aura_mix` | 0 to 1: how far its colour leans toward the fighter's own aura colour. A Kamehameha in Super Saiyan Blue comes out bluer than one in Super Saiyan. |
| `core` | The white-hot core's colour |
| `glow`, `plasma`, `trail`, `sparks`, `light`, `flicker` | Multipliers: the halo, the moving plasma on its surface, the trail, the sparks it sheds, the glow it throws on the ground, how much it flickers |
| `sounds.charge`, `.release`, `.travel`, `.impact` | Sound event ids (e.g. `"dbzenith:ki_fire"`); empty: chosen by its kind |
| `impact`, `shake`, `crater` | Multipliers: the impact's flash, rings and sparks; the camera shake; the crater |

The rework phases fill these in as they land. A field the current version doesn't use yet is kept and simply has no
effect.

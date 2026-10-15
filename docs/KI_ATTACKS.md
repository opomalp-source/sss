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
| `sphere` | How a big sphere looks: `"auto"` (any ball of size 2 or more is drawn big, with orbiting sparks), `"gather"` (energy streams in while it hangs still, like the Spirit Bomb), `"nova"` (flares loop off its surface, like the Supernova), `"plain"` (drawn big at any size), `"none"` (never drawn big) |

The rework phases fill these in as they land. A field the current version doesn't use yet is kept and simply has no
effect.

## How they're drawn

Attacks glow through the mod's own `ki_energy` shader, which draws smooth layered meshes (shell, plasma strands,
white-hot core, halo) into the bloom, over a see-through body in the attack's colour that keeps it
coloured against a bright sky. With an Oculus or Iris shader pack on, the shader is skipped and the older, plainer
look is drawn instead. The client setting `kiQuality` (Settings → Effects) trades mesh detail and particle counts for
speed.

## Trying them in game (phases 1 to 8)

The commands below need cheats. `/dbz charge <player> <id>` starts a charge as if the key were held, skipping the deck
and the meter; `/dbz charge <player> release` lets it go. `/dbz cast <entity> <id> <damage>` makes any entity fire an
attack.

- **Basic blast:** fire Ki Blast (`ki_blast`) in daylight and at night.
  - You should see a glowing sphere with a white-hot core, a coloured rim with plasma moving over it, and a halo.
  - Behind it runs a smooth trail; motes peel off it, and its hum rises and falls as it passes you.
  - On impact: sparks, the boom, stones pattering down, and an echo for big ones.
  - Set `kiQuality` in Settings → Effects to compare detail levels.
- **Charge-up:** hold a Kamehameha (`/dbz charge @s wave_beam`) for a few seconds, then `/dbz charge @s release`.
  - The orb at your cupped hands grows. Motes are drawn in, arcs snap round it past half, and its light spreads on the
    ground.
  - The ground cracks at half and near full charge, chunks lift, and the hum rises.
  - You can't walk or jump, but you can turn, and the view doesn't zoom.
  - A heavy hit (a tenth of your health) breaks the charge.
- **Beams:** the release fires a tube that widens as it goes.
  - It has flowing energy, a white-hot core, spirals winding down it, lightning off its edges, and a churning sphere at
    its head.
  - You get a flash and a kick when it fires. Watch it side-on from a few blocks away.
- **Big spheres:**
  - **Supernova:** `/dbz charge @s supernova_orb` (a long charge) grows an orange sphere over your head, with flares
    looping off it and sparks orbiting.
  - **Spirit Bomb:** `/dbz charge @s spirit_bomb` raises a blue-white sphere that energy streams into. Cast it again
    to throw it.
  - Both rumble low, light the ground below, and lift dust and stones near the ground.
  - The Spirit Bomb has a 60-second cooldown. For repeated tries, copy its file into a datapack under a new id with
    `"cooldown": 0`.
- **Barrages, discs, homing, waves (phase 5):**
  - **Rapid Volley** (`/dbz charge @s rapid_volley`): the shots stream out one every 2 ticks, two from each palm, each with a
    flash and a zap. Sweep your aim while it fires.
  - **Cutter Disk:** a flat spinning blade with a bright rim, teeth racing round the edge and arms swirling inside. It
    sheds sparks and whines as it passes.
  - **Homing Orb:** fire it at a mob. A ring spins round the orb, a dashed ring and brackets turn round its target, and
    it warbles as it seeks.
  - **Explosive Wave:** a dome of energy swells round you, a ring races out across the ground, earth is thrown out and
    the ground cracks.
  - **Paralysis Wave:** a fan of yellow lightning thrown forward.
  - **Solar Flare:** a burst of light with rays; anyone facing it is whited out.
- **Beam clash (phase 8):** two fighters fire beams at each other head-on. For example, two NPCs with
  `/dbz cast <npc> wave_beam 1` while they face each other. An orb swells where the beams meet, tinted toward the one
  winning, with lightning, shock rings, sparks and a grinding roar. Mash the ki key to push; the Ki Blast key spends a
  bar for a surge. A draw explodes bigger the longer it held; a winner's beam pours on through.
- **Your own attack:** make a datapack with `data/<ns>/ki_attacks/<id>.json` (start from `/dbz kiattacks export`), then
  `/reload`.
  - A slow test ball: `{"style":"ball","speed":0.012,"size":3.0,"life":400,"color":"#FF7A30","look":{"sphere":"nova"}}`.
  - Fire it with `/dbz cast @s <id> 1`.

## Craters

A destructive attack (`"destructive": true`) tears the land up where it lands, and its explosion sets how big.

- **Size:** ki craters are huge. They are `kiCraterScale` times a fighter's impact crater, up to `kiCraterMaxRadius`
  blocks. A ball's `look.crater` multiplies its own crater.
- **Carving:** big craters are carved outward over a few ticks.
- **Finishing:** the floor is scorched (blackstone, coarse dirt, tuff), a lip of thrown-out earth rings the edge, and
  real blocks are flung out to land where they fall.
- **Settings:** server config `[destruction]`:
  - `kiCraters`: on or off;
  - `kiCraterScale`, `kiCraterMaxRadius`, `kiCraterMaxBlocks`: size;
  - `blocksPerTick`: carving speed;
  - `debris`: flung blocks;
  - `regrowSeconds`: grow the land back after that long; 0 keeps it torn up.
- **Where not:** never in safe zones (PvP zones, the spawn's safe radius, the tournament grounds, the Otherworld).
  NPCs dig only with `mobGriefing` on, and a player's crater respects protection mods.
- **The explosion itself:** it deals vanilla explosion damage and knockback, but its look and sound are the mod's own.
  Blocks are only broken by the explosion with `kiBlastsBreakBlocks` on.

To try it: fire a destructive attack at open ground away from spawn. For example, a datapack attack with
`"explosion": 5.0, "destructive": true`, fired with `/dbz cast @s <id> 1` while looking down.

## Camera and accessibility

- **Your own attacks:** the view is drawn in a little while you charge, rushes out with a jolt when you let a big charge
  go, and rumbles while you hold a beam. Turn these off with Settings → Camera → FOV effects.
- **Screen shake:** scales every shake.
- **Screen flash:** scales every full-screen flash (0% turns them off).
- **Reduce flashing:** keeps flashes faint and slows lightning, arcs and flicker to a quarter, for players sensitive to
  flashing light.

## Post-processing

With ki bloom on (and no shader pack or Fabulous graphics), Settings → Effects offers three post-processing effects:
- **Heat shimmer:** the air ripples round ki light.
- **Colour split:** red and blue pull apart at the screen's edges after big blows and blasts close by.
- **Impact frames:** a frame or two of stark black and white for the biggest impacts. These are never shown with reduce
  flashing on.

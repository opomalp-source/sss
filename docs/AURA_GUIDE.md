# Aura guide

Auras are data. Each one is a JSON file in `src/main/resources/assets/dbzenith/auras/` (a resource pack can add or
replace them at `assets/<namespace>/auras/`). Sub-folders only sort files; the file name is the aura's id. Adding a
form's aura means adding a file, not Java.

Sizes are multiples of the fighter's height; "shares" are of the shell's radius. Anything you leave out takes the
default shown in `client/aura/AuraDef.java`.

## Trying things out in game

| Command | What it does |
|---|---|
| `/dbzaura <id> [stage]` | Wear that aura yourself, whatever your form (a technique's, like `kaioken 20`, goes over it). F5 to look at it. |
| `/dbzaura off` | Stop. |
| `/dbzaura state idle\|charge\|fly\|hit\|burst` | Hold your aura in a state. `hit` and `burst` repeat; `hithold`, `bursthold` freeze them. |
| `/dbzaura set <path> <value>` | Change one value live, e.g. `/dbzaura set layers.2.spikes.size 0.2` or `/dbzaura set shape.peak 0.3`. Colours as `#RRGGBB`. |
| `/dbzaura dump` | Save the aura you are looking at, with your changes, to `aura_tweaks/<id>.json` in the game folder. Copy it over the real file when you like it. |
| `/dbzaura reload` | Read the aura files again (F3+T also works, but reloads everything). |

## Where the files are

| Folder | What |
|---|---|
| `auras/families/` | The ten family templates (flame, roar, divine, wisp, dark, tech, majin, regal, ssj4, base). |
| `auras/forms/` | One file per form, made by `tools/gen_auras.py` (re-run it after changing its tables). |
| `auras/techniques/` | Technique auras worn over the form's: `kaioken.json`. |
| `auras/base.json`, `super_saiyan_blue.json` | The base form's charging aura; Blue, made by hand. |

## Colours from a tint

Any colour can be a `$` token made from the file's `"tint"`: `$c` (the tint), `$edge` (more saturated), `$mid`,
`$core`, `$rim`, `$glow` (paler), `$deep`, `$dark` (darker), `$white`, `$black`. Families use them, so a form only sets
its `tint`. With `"followFighter": true` the tint is the fighter's own aura colour instead (the base aura).
`"idle": false` shows the aura only while charging.

## Adding a technique's aura (or a Kaioken tier)

A technique's file has `"technique": "<id>"` instead of `forms`, and is worn over the form's aura:

```json
{
  "technique": "kaioken", "tint": "#FF2A1E",
  "wrap": { "scale": 1.35, "height": 1.5 },
  "grow": { "scale": 0.025, "height": 0.03, "wild": 0.06 },
  "tiers": [ { "from": 1 }, { "from": 11, "wrap": { "scale": 1.55, "height": 1.8 }, "shape": { "peak": 0.7 } } ]
}
```

`wrap` is its size against the form's aura (alone, it uses its own `shape`). Each `tiers` entry applies from its stage
up: its values replace the file's (a `layers` list replaces the whole list). `grow` adds that much size, height and
wildness for each stage past the tier's first. To add a tier, add an entry with a higher `from`.
Preview: `/dbzaura kaioken 20` (over whatever aura you wear).

## Lightning

`"lightning": { "rate": 3, "size": 1.0, "color": "#F4FBFF" }`: about `rate` jagged bolts at a time flashing over the
aura, more while charging.

## Adding a form's aura

Make `auras/<id>.json` and list the forms that wear it:

```json
{
  "forms": ["super_saiyan_blue"],
  "silhouette": "lobed",
  "shape": { "width": 1.5, "height": 1.95, "bottom": -0.06, "widest": 0.32, "taper": 1.4, "tip": 0.7, "peak": 0.08, "flare": 0.06 },
  "lobes": { "count": 3, "size": 0.16, "rise": 0.3, "rows": 2.6, "billow": 0.75 },
  "motion": { "pulse": 0.025, "pulseSpeed": 2.4, "sway": 0.03, "speed": 1.0 },
  "layers": [ ... ],
  "particles": { "type": "sparkle", "rate": 1.0, "size": 1.0, "color": "#E8FFFF" },
  "motes": { "rate": 1.0, "size": 1.0, "color": "#BFF4FF" },
  "react": { ... }
}
```

To start from another aura and change a few things, use `"extends": "<other id>"`. Objects merge key by key; lists
(like `layers` and `forms`) are replaced whole. A child never inherits `forms`.

### The body: `shape`, `lobes`, `motion`

| Key | Meaning |
|---|---|
| `shape.width`, `shape.height` | Size against the fighter's height (Blue: 1.5 wide, 1.95 tall). |
| `shape.bottom` | Where it starts, below (negative) or above the feet. |
| `shape.widest` | How far up it is widest (0..1 of its height). |
| `shape.taper`, `shape.tip` | How it narrows to the top: higher taper keeps it wide longer, lower tip makes it pointier. |
| `shape.peak` | One tall flame above the head: extra height, as a share of the height (SSJ1 style: 0.3+). |
| `shape.flare` | A wider base round the feet (share of the radius). |
| `lobes.count` | Bulges round the shell. |
| `lobes.rows` | Bulges stacked up the height. |
| `lobes.size` | How far they push out. |
| `lobes.billow` | 0 smooth waves .. 1 round puffs with creases between (Blue's cloudy look). |
| `lobes.rise` | How fast the bulges climb. |
| `motion.pulse`, `pulseSpeed` | Breathing in size. |
| `motion.sway` | Slow lean of the top. |
| `motion.speed` | Pace of everything. Each fighter also runs a little faster or slower, so two auras never match. |

### `layers`

Drawn in order: put the outermost first. One to six layers.

| Key | Meaning |
|---|---|
| `kind` | `shell` (the flame body), `glow` (a bright band just outside another layer), `haze` (a soft inner fill, no hard outline) or `tongues` (flame licks on the outline that rise and break off). |
| `blend` | `normal` keeps colour in daylight; `add` adds light (glows, white-hot cores). Glows default to `add`. |
| `scale`, `heightScale`, `lift` | Size and height against the aura's shape, and how far up it sits. A glow's scale is against the layer it wraps (1.03..1.1). |
| `wraps` | For a glow: the index of the layer whose outline it follows (default: the next shell). |
| `colors.core/mid/edge/rim` | Middle to outline, and a tint along the outline. For a glow, `rim` is its colour. |
| `alpha.core/edge` | Opacity in the middle and at the edge. `opacity` scales the whole layer. |
| `rim.width/strength` | The bright band at the edge, and how strongly the rim tint shows. |
| `silhouette` | `lobed` or `jagged`, for this layer's spike defaults. |
| `spikes.count/size/sharpness/lean` | The outline cut into flames: how many round, how deep, 0 soft .. 1 sharp, how much they lean upward. |
| `motion.scroll/flicker/streaks/speed` | Flames rising, spikes flickering, light streaks, and this layer's pace. |
| `lobes`, `sway`, `seed` | How much of the body's bulges and sway it takes, and its own seed so it moves differently. |
| `motion.warp/tallFlames/stretch/streakSpeed` | How much the flames twist, how much taller they are near the top, how long the noise is drawn out upward, how fast the light streaks climb. |
| `tongues.count/length/width/life/top/rise/wave/inset/low` | For a `tongues` layer: how many at once, length (share of the height), width (share of their length), seconds each lives, share off the top, how far they climb, how much they wave, how far in from the outline they start, how low they start. |

### `particles` and `motes`

`particles.type`: `none`, `sparkle` (star dust rising inside) or `ember` (sparks drifting up and out past the top).
`motes` rise from the ground round a charging fighter; set `rate` to 0 for none.

### `react`: what the aura does when the fighter does something

All shares added on top of the calm aura.

| Key | Default | Meaning |
|---|---|---|
| `charge.scale`, `charge.height` | 0.22, 0.3 | Bigger and taller while charging or transforming. |
| `charge.wild` | 1.2 | Faster flicker and scroll, deeper spikes, bigger bulges. |
| `charge.glow` | 0.35 | Brighter. |
| `charge.shake` | 0.025 | Shaking. |
| `move.trail`, `move.max` | 1.6, 0.9 | Flames stream back when moving fast or flying, up to `max` body heights. |
| `hit.scale`, `hit.bright` | 0.1, 0.7 | A short flare on a hit or swing. |
| `burst.scale`, `burst.bright` | 0.45, 1.2 | A new aura bursting out. |

## Shader packs

With an Oculus/Iris shader pack on, auras fall back to Minecraft's own shaders (Settings → Effects → Aura shader picks
auto, own or plain). Shape, layers, colours, motion and reactions stay; the fine spike cut in the outline does not.
Packs with strong bloom (Complementary, BSL) can make auras look brighter.

## Coming in later phases

Ultra Instinct's afterimages, the transformation sequence, dynamic light, NPC auras, and the quality settings.

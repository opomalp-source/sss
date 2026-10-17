# Custom colors

All existing editable color channels use the same RGB picker in v0.150.0:

- Character creation: body skin, eyes, hair, hair highlights, base aura, racial skin/markings and horns/tail colors where that race supports them.
- Life: body skin; Barber: hair; Face: eyes, hair highlights and base aura; Race Look: racial skin, markings and parts.
- Ki Creator: custom technique color.
- Settings → Style → Crosshair color: any RGB, with the old preset retained as Default.

Click a color button to open the picker. Drag the large square for saturation/brightness and the rainbow rail for
hue. Enter or paste `#RRGGBB` (with or without #, upper/lowercase accepted); three-digit shorthand such as `#0F8`
is also accepted. The swatch and selector update as you edit. Full opaque 24-bit RGB is available, including exact
black and white. Invalid/incomplete input is red and cannot be applied. Enter applies valid input; Escape/Cancel
returns without changing the parent choice. Apply returns the new value to the page; use that page's normal
Save/Done/Confirm to commit it. Life skin and crosshair settings retain their existing immediate-save behavior.

Quick presets remain inside the picker. Where supported, Default/None/Own skin retains the existing -1 meaning:
no highlight, race aura/eyes, original racial colors, or the player's Minecraft skin. Creation's body picker still
uses a generated body rather than offering Own skin. The aura channel customizes the existing base aura; fixed
transformation/Kaioken colors retain their existing behavior. This adds color range to existing channels rather
than introducing garment dyes or new form customization rules.

No art is downloaded or generated: the picker is native GUI geometry. `ColorValues` owns parse/format and HSV/RGB
math; `ColorControl` and `ColorPickerScreen` are shared by every page. Appearance fields/packets already preserve
24-bit RGB, so no wire/save schema change is required. A new client-only `crosshairCustomColor=-1` setting keeps
old `crosshairColor` indices valid and unchanged on upgrade; resetting Style clears the override.

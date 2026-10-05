# Content expansion (CX) — plan

Requested 2026-10-05: everything Dragon Block V has (wiki: dragonblockv.wiki.gg plus the design notes the user pasted), done
better, plus original ideas; race variants; better hair presets; much better animation, VFX and SFX; a settings menu;
"everything more dynamic". This file is the plan and the research record. Progress is tracked in TODO.md (CX section).

## Ground rules
- **Original assets only** (code-drawn art, synthesized sound). The wiki and notes are design references, never copied text.
- **Balance stays ours.** DBV multipliers run 1.5x-76x on a different stat model. Our curve (BALANCE.md) ends at x4.5-6.5 by
  level 2000. Every DBV multiplier is mapped onto our scale with `FormScale.fromDbv(m) = 1 + 1.1 * ln(m)` (2x→1.76, 6x→2.97,
  16x→4.05, 22x→4.40, 32x→4.81, 56x→5.43, 76x→5.76). This keeps the *order* and the *feel* of the DBV tiers, keeps the
  balance tests meaningful, and the multiplier shown in the UI is the real one.
- Every feature ships with a GameTest where logic is testable and with a screenshot check where it is visual.

## Research summary (what DBV has)

**Races and sub-races.**
- Saiyan:
  - Legendary (5% at creation or by wish).
  - Primal: tail permanent, SSJ4 line.
  - Legendary Primal.
- Half-Saiyan, with paths chosen at a milestone: New Generation, Future Lineage, Awakened Evolution (Golden Ape / SSJ4).
- Frost Demon: Mutant (5%); Metal (user list).
- Namekian clans: Warrior / Dragon / Demon (user list).
- Human paths: Ancient Hermit (Surge, mentor), Peak Human (No Ego / Godly Ego), Triclops Descendant (Inner / Awakened / Godly Eye).
- Majin: Corrupted (5%, sanity, Pure form).
- Cyborg (organic 1-49%) and Android (0%), both with Power Tech forms.
- New (user list): Vampire, Gen Alien, Bio Android, Tuffle, Core Person (Kai / Demon).

**Racial skills** (Racial_Skills page): roughly 60 skills across the races. Human: Persistence, Second Wind, Sheer Willpower.
Saiyan: Zenkai, Seasoned Warrior, Warrior Race, Saiyan's Resolve, Prideful, False Advantage. Legendary: Overflowing Power,
Venting, Unstoppable Force, Escalating Power. Primal: Primal Zenkai, Primal Evolution, Unmatched Ferocity, Limitless Power,
Shattering the Limit, Roaring Evolution. Frost Demon: Vacuum Breathing, Perfect Control, Extreme Tenacity, Unrestrained Wrath,
Revitalizing Metamorphosis; Mutant: Long-Awaited 100%, Ruthless Mind, Sadistic Nature. Half-Saiyan: Prodigy, Immeasurable
Potential, Hidden Talent, Blazing Spirit. Namekian: Vitality Restoration, Limb Regeneration, Warrior's Vitality, Spirit
Disruption, Namekian Resilience, Reincarnation (egg). Majin: Gum Body, Elastic Monster, Candy Beam, Remote Absorb, Death
Regeneration (gloops); Corrupted: Mindless Gambit, Sycophantic Rage, Inner Madness.

**Forms** (DBV numbers):
- Saiyan: Great Ape 1.5, SSJ 2-6, Grade 2 2.5, Grade 3 3, SSJ2 4-10, SSJ3 16, SSG 22, Blue/Rosé 32, Blue Evolved/Rosé 2 56.
- Legendary: Wrathful 1.5-10, C-Type 12-20, Full Power 25-40, Controlled 50, plus a rising +0-20% over 3 minutes in combat.
- Primal: + Golden Ape 18, SSJ4 20-40, SSJ4 Full Power 32-50, SSJ4 Limit Breaker 56.
- Legendary Primal: LSSJ 6-9, LSSJ2 9-12, LSSJ3 18, Legendary Great Ape 20, LSSJ4 22-44, LSSJ4FP 34-52, LSSJ4LB 60.
- Human: Max Power 1.5 physical, High Tension 1.5, Extreme Tension 6-12, Godly Tension 32.
- Frost Demon: created forms 1.1-20; suppression forms 0.01-0.99 with ki regen.
- Namekian: Giant 1.5 physical (15 blocks), Namekian Fusion 1.1-20.
- Majin: absorption forms 1.1-20; Corrupted Pure form 56 sane / 76 insane.
- Android and Cyborg: Power Tech 1.1-20.
- God Ki: levels 1/2/3 add +2/+12/+36 for infusing races.
- Mastery: 75% makes the transformation instant; unmastered forms debuff Discipline; full mastery halves active drain.

**Skills:** Limit Break (125% for 1 min, then sealed to 70%; Half-Saiyan 150%, New Gen 300%), Echo Strike (Light / Weightless /
Phantom), Desperate Gambit, Spirit Shock, Rising Charge, Kaioken (stages, HP drain when Discipline lags Spirit), Ki Sense
(incoming-ki indicator, strong-ki warning, active scan), Instant Transmission.

**Combat:**
- Melee: light; heavy 1.25x (charged 1.5 / 1.75x); heavy directionals (WASD) that launch; Z-hit 1.5x (charged 1.75 / 2x, longest stun);
  sweep (block + heavy, unblockable).
- Defence and escapes: blocking 50%; Revenge Counter (10% stamina, hyper-armour Z-hit); Breaker Wave (2 charges, 30 s each).
- Mobility: chase (snap-vanish behind a knocked foe, 3 times) and chase counter; snap recovery; ground slide recovery; spot dodge and side step.
- Clashes and grabs: melee clashes; grabs with slam, drag and throw.
- Downed state: 2 minute timer (Frost Demon 4), carrying, finishing; Otherworld and Limbo.

**Character creator:** body types A-D; skin, iris, hair, aura and highlight colours; eyes, pupils, scars, ears, nose, eyebrows, mouth,
face extra; height 1-2.24 (forms up to 3, giants 15); tails.

**Systems:** attributes STR / END / AGI / DIS / SPI / VIT; Ki release penalties under 50% ki; age (1 day = 1 year, peak age per race);
fusion (Metamoran dance QTE, Potara); family (children with 5% of each parent's stats); planets and sectors; claiming; metals and alloys.

## Milestones
| # | Milestone | Contents |
|---|---|---|
| CX-1 | Settings menu | In-game Settings screen (HUD, effects, camera, accessibility, audio sliders, keybinds link), from the pause menu, K screen and wheel |
| CX-2 | Races v2 | Variant framework (sub-races, rarity rolls, wish to reroll); Saiyan Legendary / Primal / Legendary Primal; Half-Saiyan paths; Frost Demon Mutant / Metal; Namekian clans; Human paths; Corrupted Majin; new races Vampire, Bio Android, Tuffle, Gen Alien, Core Person (Kai / Demon); appearance variants per race |
| CX-3 | Forms v2 | Full DBV form trees per race and variant on the mapped scale; mastery 75% instant; transform time; rising multipliers; god ki levels |
| CX-4 | Racial skills | Active racial skills on a Racial key and wheel ring; passives as hooks; around 60 skills |
| CX-5 | Universal skills | Kaioken, Limit Break, Ki Sense (indicators), Instant Transmission, Ki Barrier, Echo Strike, Rising Charge, Spirit Shock, Desperate Gambit |
| CX-6 | Combat v3 | Z-hits, sweeps, directional heavies, revenge counter, breaker wave, snap vanish / chase / counters, side step / spot dodge, snap and ground recovery, melee clashes, downed state with carry and finish |
| CX-7 | Ki Creator v2 | DBV framework: methods, origins, shapes, types, 14 modifiers, charge levels |
| CX-8 | Creator v2 | Face parts (eyes, pupils, brows, mouth, nose, ears, face extra), highlight colour, aura colour, body types A-D, height 1-2.24, many more and better hair presets, hair physics |
| CX-9 | Animation v3 | Idle breathing, walk / run / sprint, flight ascend / descend / turn banking, combat stances, Z-hit, sweep, vanish, chase, knockdown and get-up, downed, victory, per-race flourishes; procedural secondary motion |
| CX-10 | Sound | Full synthesized SFX set (ffmpeg-encoded OGG): charge loops per tier, aura loops, punches and whooshes, blasts and beams, impacts, guard / parry / break, transformation bursts, vanish, UI |
| CX-11 | VFX v3 | Bloom post-pass, heat haze, kaioken aura, form-specific aura shapes, lightning arcs, ground cracks and debris chunks, speed lines, vanish trails |
| CX-12 | World | Otherworld and Limbo, God Ki pools, fusion (dance QTE and earrings), black star / super dragon ball wishes (variant reroll, immortality), metals and alloys tiers |

Original additions planned along the way: a Hakai-style "Destroyer" path, an evasion "Instinct" path, a tournament arena
with brackets, a spirit bomb that draws energy from nearby players, a dojo of training robots (agility drill), and form cut-ins
with per-form palettes.

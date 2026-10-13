# First transformations and gold flicker — 0.88.0-hair.10

Each form has a persistent form_experienced:<id> flag. Successful entry records it; merely starting or cancelling a charge does not. A form with existing mastery, or the player's currently active form, counts as experienced to accommodate existing saves. A previously used zero-mastery form cannot be inferred from old saves until it is entered once with this update.

Experienced forms retain hair.9 timing (twice the baseline unmastered calculation, with mastery reducing it). A first transformation takes at least 300 + 40 * tier ticks (tier contribution capped at eight), or four times the repeat duration if that is longer. Default first SSJ is 340 ticks / 17 seconds, versus 76 ticks / 3.8 seconds for repeats at zero mastery. Mastered instant timing, held-key minimums, cancellation, costs, requirements and interruption rules remain intact. Creative/admin instant entry still follows the existing bypass.

SSJ, Grade 2, Grade 3, SS2 and SS3 now explicitly flash between the player's normal selected hair colour and gold while held-transforming, including when the current form is already gold. The 0.8-second cycle holds gold for longer as progress grows, but both colours remain visible throughout. God, Blue and other later forms retain their existing colours. Shape rises independently through the charge, using the progressive geometry from hair.9.

Local shape progress follows the synced transformation bar. Remote progress remains estimated because public appearance packets do not contain private mastery/history. Named presets support continuous path interpolation; custom editor codes retain their shape until completion. Movement physics remains unchanged.

## Verification and packaging

Build/reobfuscation, first versus repeat duration, separate form history, save/load persistence at zero mastery, mastery reduction and mastered instant timing passed. Both gold and actual normal colour were checked at early, middle and late progress. Geometry, interpolation, dynamics, 4,296 coverage rays and 18 legacy migrations also passed. The isolated development client captured 27 nine-angle sheets, including four mid-transformation stages.

The source repository is 0.72.0; the production baseline is 0.88.0. Packaging preserves 2,633 unrelated entries byte-for-byte, installs 32 hair/helper entries and narrowly patches the original FormHandler. Its transformTime returns delegate to TransformationExperience.duration, and its successful enter return records the per-form flag. Expected method signatures and hook counts are checked. The old repository's entire FormHandler is never installed. Version metadata and seven preset labels are also updated.

The final hybrid JAR has not been launched through the normal launcher; multiplayer has not been manually playtested. A multiplayer server needs this update for the server-controlled longer first charges. Restart Minecraft after installation. The predecessor is removed from mods, with its backup retained in outputs.

## Reproduction

```powershell
.\gradlew.bat --gradle-user-home ..\work\gradle-home --no-configuration-cache -I tools\hair-preview.init.gradle -I tools\hair-client-review.init.gradle hairPreview build runClientTest
.\tools\PackageHairUpdate.ps1 -OriginalJar ..\outputs\dbzenith-0.88.0-original.jar -BuiltJar .\build\libs\dbzenith-0.72.0.jar -OutputJar '<new output path.jar>'
```

Source overlay: hair-source-update-v10.zip. Java and cached ASM 9.8 are used by PatchTransformTiming.java to patch only the original timing/entry hooks.

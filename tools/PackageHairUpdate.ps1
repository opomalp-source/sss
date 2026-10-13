param(
    [Parameter(Mandatory=$true)][string]$OriginalJar,
    [Parameter(Mandatory=$true)][string]$BuiltJar,
    [Parameter(Mandatory=$true)][string]$OutputJar,
    [string]$Version = '0.88.0-social.1'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$originalPath = (Resolve-Path -LiteralPath $OriginalJar).Path
$builtPath = (Resolve-Path -LiteralPath $BuiltJar).Path
$outputPath = [IO.Path]::GetFullPath($OutputJar)
if ($outputPath -eq $originalPath -or $outputPath -eq $builtPath) { throw 'Output must be a separate file.' }
$hairClasses = '^com/dbzenith/(transform/(?:FormHandler|TransformationExperience)|appearance/HairCode(?:\$[^/]+)?|client/render/(?:HairMesh|HairWind|HairTransformation|HairGeometry|HairPresetLibrary|SculptedHair|FormHairLayer|FormHairModel|NpcPartsLayer)(?:\$[^/]+)?)\.class$'
$migration = 'assets/dbzenith/hair/legacy-presets.properties'
$nimbusClasses = '^com/dbzenith/(?:nimbus|social)/[^/]+\.class$'
$nimbusResources = @('assets/dbzenith/models/item/flying_nimbus.json', 'assets/dbzenith/textures/item/flying_nimbus.png', 'data/dbzenith/recipes/flying_nimbus.json')
$material = 'assets/dbzenith/textures/entity/hair_strands.png'
$metadata = @('META-INF/MANIFEST.MF', 'META-INF/mods.toml', 'assets/dbzenith/lang/en_us.json')
$names = @{spiky='Goku'; prince='Vegeta'; teen='Teen Gohan'; gohan_adult='Adult Gohan'; wild='Broly'; mane='Raditz'; curtains='Trunks'}
function Read-EntryText($entry) {
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
function Copy-Entry($entry, $archive) {
    $copy = $archive.CreateEntry($entry.FullName, [IO.Compression.CompressionLevel]::Optimal)
    $copy.LastWriteTime = $entry.LastWriteTime
    $source = $entry.Open(); $target = $copy.Open()
    try { $source.CopyTo($target) } finally { $source.Dispose(); $target.Dispose() }
}
function Write-EntryText($archive, [string]$name, [string]$content) {
    $entry = $archive.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal)
    $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
    try { $writer.Write($content) } finally { $writer.Dispose() }
}
function Entry-Hash($entry) {
    $sha = [Security.Cryptography.SHA256]::Create(); $stream = $entry.Open()
    try { return [BitConverter]::ToString($sha.ComputeHash($stream)) } finally { $stream.Dispose(); $sha.Dispose() }
}
# The repository is older than the installed baseline: never replace the whole server handler with donor source.
$asm = @(Get-ChildItem (Join-Path $PSScriptRoot '../../work/gradle-home/wrapper/dists') -Filter 'asm-9.8.jar' -Recurse -File)[0].FullName
if (!$asm) { throw 'ASM runtime missing for surgical baseline patch.' }
$timingPath=Join-Path (Split-Path $outputPath -Parent) 'FormHandler-hair10.class'
& java --class-path $asm (Join-Path $PSScriptRoot 'PatchTransformTiming.java') $originalPath $timingPath
if ($LASTEXITCODE -ne 0) { throw 'Transformation timing patch failed.' }
$original = [IO.Compression.ZipFile]::OpenRead($originalPath)
$built = [IO.Compression.ZipFile]::OpenRead($builtPath)
try {
    $modText = Read-EntryText $original.GetEntry('META-INF/mods.toml')
    if ($modText -notmatch '(?m)^version="0\.88\.0"\s*$') { throw 'Expected the original dbzenith 0.88.0 baseline.' }
    if (@($original.Entries | Where-Object { $_.FullName -match '^META-INF/.*\.(SF|RSA|DSA)$' }).Count -gt 0) { throw 'Refusing to alter a signed jar.' }
    $white='assets/dbzenith/textures/entity/aura_white.png'
    if (!$original.GetEntry($white) -or !$built.GetEntry($white) -or (Entry-Hash $original.GetEntry($white)) -ne (Entry-Hash $built.GetEntry($white))) { throw 'Baseline flat hair material differs from tested donor.' }
    $replacements = @($built.Entries | Where-Object { ($_.FullName -match $hairClasses -or $_.FullName -match $nimbusClasses -or $nimbusResources -contains $_.FullName -or $_.FullName -eq $migration) -and $_.FullName -ne 'com/dbzenith/transform/FormHandler.class' })
    foreach ($required in @('NimbusContent','NimbusEntity','NimbusItem','NimbusNetwork','NimbusClient','NimbusRenderer')) {
        if (!$built.GetEntry("com/dbzenith/nimbus/$required.class")) { throw "Missing Nimbus class: $required" }
    }
    foreach ($required in @('SocialData','SocialService','SocialState','SocialNetwork','SocialClient','SocialScreen')) {
        if (!$built.GetEntry("com/dbzenith/social/$required.class")) { throw "Missing social class: $required" }
    }
    foreach ($required in $nimbusResources) { if (!$built.GetEntry($required)) { throw "Missing Nimbus resource: $required" } }

    if ($replacements.Count -lt 10 -or !$built.GetEntry($migration)) { throw 'Hair classes or migration resource missing from built jar.' }
    foreach ($required in @('HairMesh','HairGeometry','HairPresetLibrary','SculptedHair')) {
        if (!$built.GetEntry("com/dbzenith/client/render/$required.class")) { throw "Missing rebuilt hair renderer: $required" }
    }
    $result = [IO.Compression.ZipFile]::Open($outputPath, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $original.Entries) {
            if ($entry.FullName -match $hairClasses -or $entry.FullName -eq $material -or $entry.FullName -eq $migration) { continue }
            if ($entry.FullName -eq 'META-INF/mods.toml') {
                Write-EntryText $result $entry.FullName ($modText -replace '(?m)^version="0\.88\.0"', ('version="' + $Version + '"'))
            } elseif ($entry.FullName -eq 'META-INF/MANIFEST.MF') {
                $manifest = Read-EntryText $entry
                Write-EntryText $result $entry.FullName ($manifest -replace 'Implementation-Version: 0\.88\.0', ('Implementation-Version: ' + $Version))
            } elseif ($entry.FullName -eq 'assets/dbzenith/lang/en_us.json') {
                $language = Read-EntryText $entry
                foreach ($key in $names.Keys) {
                    $pattern = '("hair\.dbzenith\.' + $key + '"\s*:\s*)"[^"\r\n]*"'
                    if ([regex]::IsMatch($language, $pattern)) {
                        $language = [regex]::Replace($language, $pattern, '$1"' + $names[$key] + '"')
                    } else {
                        $end = $language.LastIndexOf('}')
                        $language = $language.Substring(0,$end).TrimEnd() + ",`n  `"hair.dbzenith.$key`": `"$($names[$key])`"`n}" + $language.Substring($end+1)
                    }
                }
                $merged = $language | ConvertFrom-Json
                $donorLanguage = (Read-EntryText $built.GetEntry('assets/dbzenith/lang/en_us.json')) | ConvertFrom-Json
                foreach ($property in $donorLanguage.PSObject.Properties) {
                    if ($property.Name -match 'nimbus') { $merged | Add-Member -NotePropertyName $property.Name -NotePropertyValue $property.Value -Force }
                }
                Write-EntryText $result $entry.FullName ($merged | ConvertTo-Json -Depth 10)
            } else { Copy-Entry $entry $result }
        }
        foreach ($entry in $replacements) { Copy-Entry $entry $result }
        $timingEntry=$result.CreateEntry('com/dbzenith/transform/FormHandler.class')
        $timingStream=$timingEntry.Open()
        try { $bytes=[IO.File]::ReadAllBytes($timingPath);$timingStream.Write($bytes,0,$bytes.Length) } finally { $timingStream.Dispose() }
    } finally { $result.Dispose() }
    $verified = [IO.Compression.ZipFile]::OpenRead($outputPath)
    try {
        if ((Entry-Hash $verified.GetEntry('com/dbzenith/transform/FormHandler.class')).Replace('-','') -ne (Get-FileHash -LiteralPath $timingPath).Hash) { throw 'Timing patch hash mismatch.' }
        $unchanged = 0
        foreach ($entry in $original.Entries) {
            if ($entry.FullName -match $hairClasses -or $entry.FullName -eq $migration -or $entry.FullName -eq $material -or $metadata -contains $entry.FullName) { continue }
            $copy = $verified.GetEntry($entry.FullName)
            if (!$copy -or (Entry-Hash $copy) -ne (Entry-Hash $entry)) { throw ('Unexpected change: ' + $entry.FullName) }
            $unchanged++
        }
        foreach ($entry in $replacements) {
            if ((Entry-Hash $entry) -ne (Entry-Hash $verified.GetEntry($entry.FullName))) { throw ('Replacement mismatch: ' + $entry.FullName) }
        }
        Write-Output "PASS: $unchanged original entries preserved byte-for-byte; $($replacements.Count) hair/Nimbus/social entries and surgical timing patch installed; version metadata and English preset/Nimbus labels updated."
        Write-Output "Output: $outputPath"
    } finally { $verified.Dispose() }
} finally { $original.Dispose(); $built.Dispose() }

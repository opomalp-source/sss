# Dragon Block Zenith updater: puts the newest build into your mods folder and removes the old jar.
# Installed by "Install DBZ auto-update.bat", which runs it every 10 minutes. Run it by hand with -Force to reinstall.
param([switch]$Force)
$ErrorActionPreference = 'Stop'
$base = 'https://raw.githubusercontent.com/opomalp-source/sss/builds'
$home_ = Join-Path $env:APPDATA '.minecraft-dbz'
New-Item -ItemType Directory -Force $home_ | Out-Null
$log = Join-Path $home_ 'dbz-update.log'
function Note($m) { Add-Content $log ("{0}  {1}" -f (Get-Date -Format s), $m) }

try {
    $remote = (Invoke-WebRequest "$base/version.txt?r=$(Get-Random)" -UseBasicParsing -Headers @{ 'Cache-Control' = 'no-cache' }).Content.Trim()
} catch { Note "offline: $($_.Exception.Message)"; exit 0 }
$verFile = Join-Path $home_ 'installed-version.txt'
$installed = if (Test-Path $verFile) { (Get-Content $verFile -Raw).Trim() } else { '' }
if ($remote -eq $installed -and -not $Force) { exit 0 }
$version = $remote.Split(' ')[0]

# Where the mod goes: the DBZ mods folder (the switch scripts copy from it) and the game's live mods folder when
# Dragon Block Zenith is in it. With neither, the game's mods folder.
$dbzMods = Join-Path $home_ 'mods'
$liveMods = Join-Path $env:APPDATA '.minecraft\mods'
$targets = @()
if (Test-Path $dbzMods) { $targets += $dbzMods }
if ((Test-Path $liveMods) -and (Get-ChildItem $liveMods -Filter 'dbzenith-*.jar' -ErrorAction SilentlyContinue)) { $targets += $liveMods }
if ($targets.Count -eq 0) { New-Item -ItemType Directory -Force $liveMods | Out-Null; $targets += $liveMods }

$tmp = Join-Path $env:TEMP "dbzenith-$version.jar"
Invoke-WebRequest "$base/dbzenith.jar?r=$(Get-Random)" -OutFile $tmp -UseBasicParsing
$ok = $true
foreach ($t in $targets) {
    try {
        Get-ChildItem $t -Filter 'dbzenith-*.jar' | Where-Object { $_.Name -ne "dbzenith-$version.jar" } | Remove-Item -Force
        Copy-Item $tmp (Join-Path $t "dbzenith-$version.jar") -Force
        Note "installed $version into $t"
    } catch {
        $ok = $false
        Note "could not update $t (is Minecraft open?): $($_.Exception.Message)"
    }
}
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
if ($ok) {
    Set-Content $verFile $remote
    # keep the updater itself current
    try { Invoke-WebRequest "$base/dbz-update.ps1?r=$(Get-Random)" -OutFile (Join-Path $home_ 'dbz-update.ps1') -UseBasicParsing } catch {}
}

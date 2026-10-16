#!/bin/bash
# Releases a version (after bumping mod_version and building): commits, tags, installs the jar into the local mods
# folder (only if Minecraft isn't running). No updater publication, per user request.
# usage: tools/release.sh <version> "<commit subject>"
# Build and test first:
#   rm -f run-gametest/world/serverconfig/dbzenith-server.toml; ./gradlew --offline build runGameTestServer
#   ./gradlew --offline build -x test
set -euo pipefail
cd "$(dirname "$0")/.."
V=$1; MSG=$2
[ -f build/libs/dbzenith-$V.jar ] || { echo "no jar for $V"; exit 1; }
git add -A src tools docs README.md DEVLOG.md TODO.md gradle.properties build.gradle CLAUDE.md HANDOFF.md
git commit -q -F - <<MSGEOF
$MSG

Co-Authored-By: Codex <noreply@openai.com>
MSGEOF
git tag v$V
if command -v powershell >/dev/null; then                                   # Windows: the user's requested mods folder
  powershell -NoProfile -Command "\$mc = Get-CimInstance Win32_Process -Filter \"name like 'java%'\" | Where-Object { \$_.CommandLine -match 'net\.minecraft|--gameDir|bootstraplauncher' }; if (\$mc) { 'MINECRAFT RUNNING: not installed' } else { foreach (\$d in \"\$env:APPDATA\.minecraft\mods\") { if (Test-Path \$d) { Copy-Item -LiteralPath build\libs\dbzenith-$V.jar -Destination \$d -Force; Get-ChildItem -LiteralPath \$d -File -Filter 'dbzenith-*.jar' | Where-Object { \$_.Name -ne 'dbzenith-$V.jar' } | ForEach-Object { Remove-Item -LiteralPath \$_.FullName -Force } } }; 'installed $V' }"
fi

git log --oneline -1 | cut -c1-90

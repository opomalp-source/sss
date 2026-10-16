#!/bin/bash
# Releases a version (after bumping mod_version and building): commits, tags, installs the jar into the local mods
# folders (only if Minecraft isn't running), and publishes it on the builds branch (tools/deploy.sh).
# usage: tools/release.sh <version> "<commit subject>"
# Build and test first:
#   rm -f run-gametest/world/serverconfig/dbzenith-server.toml; ./gradlew --offline build runGameTestServer
#   ./gradlew --offline build -x test
set -e
cd "$(dirname "$0")/.."
V=$1; MSG=$2
[ -f build/libs/dbzenith-$V.jar ] || { echo "no jar for $V"; exit 1; }
git add -A src tools docs DEVLOG.md TODO.md gradle.properties build.gradle CLAUDE.md HANDOFF.md 2>/dev/null || true
git commit -q -F - <<MSGEOF
$MSG

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSGEOF
git tag v$V
if command -v powershell >/dev/null; then                                   # Windows: the user's two mods folders
  powershell -NoProfile -Command "\$mc = Get-CimInstance Win32_Process -Filter \"name like 'java%'\" | Where-Object { \$_.CommandLine -match 'net\.minecraft|--gameDir|bootstraplauncher' }; if (\$mc) { 'MINECRAFT RUNNING: not installed' } else { foreach (\$d in \"\$env:APPDATA\.minecraft-dbz\mods\", \"\$env:APPDATA\.minecraft\mods\") { if (Test-Path \$d) { Get-ChildItem \$d -Filter 'dbzenith-*.jar' | Remove-Item -Force; Copy-Item build\libs\dbzenith-$V.jar \$d } }; 'installed $V' }"
fi
bash tools/deploy.sh --no-build 2>&1 | tail -1
git log --oneline -1 | cut -c1-90

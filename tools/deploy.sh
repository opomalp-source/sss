#!/bin/bash
# Builds the mod and publishes the jar on the "builds" branch, where the Windows updater (tools/windows) picks it up.
# The branch holds a single commit that is replaced each time, so it never grows.
#   tools/deploy.sh            build and publish
#   tools/deploy.sh --no-build publish build/libs as it is
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
[ "${1:-}" = "--no-build" ] || ./gradlew assemble --console=plain -q
VERSION=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
JAR="build/libs/dbzenith-$VERSION.jar"
[ -f "$JAR" ] || { echo "no $JAR"; exit 1; }
REMOTE=$(git remote get-url origin)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp "$JAR" "$WORK/dbzenith.jar"
cp tools/windows/dbz-update.ps1 "$WORK/dbz-update.ps1"
cp "tools/windows/Install DBZ auto-update.bat" "$WORK/Install DBZ auto-update.bat"
echo "$VERSION $(git rev-parse --short HEAD) $(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$WORK/version.txt"
cat > "$WORK/README.md" <<TXT
# Dragon Block Zenith builds
The latest build, published by tools/deploy.sh: **$VERSION** (from $(git rev-parse --short HEAD)).
Run *Install DBZ auto-update.bat* once on Windows: it installs this jar into your mods folder now and every
10 minutes checks for a newer one, swapping it in and removing the old jar.
TXT
cd "$WORK"
git init -q
git checkout -q -b builds
git add -A
git -c user.name="$(git -C "$ROOT" config user.name || echo dbz)" -c user.email="$(git -C "$ROOT" config user.email || echo dbz@localhost)" \
    commit -q -m "Build $VERSION"
for i in 1 2 3 4; do
    git push -q -f "$REMOTE" builds && break
    sleep $((2 ** i))
done
echo "published $VERSION"

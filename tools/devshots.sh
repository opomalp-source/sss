#!/bin/bash
# Drives the dev server + scripted client (under Xvfb on Linux) and takes devshots, reusing a running client.
# Usage: tools/devshots.sh <shotlist-file> [log-dir]
#   each line: an RCON command (e.g. "dbz devshot Dev front_hidegui_aurapv.super_saiyan_blue.charge_1 40"),
#   "wait N" (seconds) or "# comment". Shots land in run-clienttest/screenshots/dbz_*.png.
# Needs run-server/server.properties with RCON on 25575, password dbzdev (see DEVLOG "verification recipe").
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
S=${2:-/tmp/dbz-devshots}; mkdir -p "$S"; touch "$S/server.log"
LIST=$1
rcon() { java "$ROOT/tools/Rcon.java" 25575 dbzdev "$@" >/dev/null 2>&1; }

cd "$ROOT"
mkdir -p run-clienttest/screenshots; rm -f run-clienttest/screenshots/dbz_*.png
if ! pgrep -f serverRunProgramArgs >/dev/null; then
  ./gradlew runServer --console=plain > "$S/server.log" 2>&1 &
  for i in $(seq 1 600); do grep -q "Done (" "$S/server.log" && break; sleep 1; done
fi
grep -q "Done (" "$S/server.log" || { echo "SERVER FAILED"; tail -30 "$S/server.log"; exit 1; }
echo "server up"

if pgrep -f clientTestRunProgramArgs >/dev/null; then echo "client reused"; else
J0=$(grep -c "Dev joined the game" "$S/server.log")
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientTest --console=plain -PdevScreenshots=99999 -PdevQuitAfter=99999 > "$S/client.log" 2>&1 &
for i in $(seq 1 900); do [ $(grep -c "Dev joined the game" "$S/server.log") -gt $J0 ] && break; sleep 1; done
[ $(grep -c "Dev joined the game" "$S/server.log") -gt $J0 ] || { echo "CLIENT FAILED TO JOIN"; tail -40 "$S/client.log"; exit 1; }
echo "client joined"
sleep 15
fi
rcon "op Dev" "time set noon" "weather clear" "gamerule doDaylightCycle false" "gamerule doWeatherCycle false"

while IFS= read -r line; do
  [ -z "$line" ] && continue
  case "$line" in
    wait*) sleep "${line#wait }";;
    \#*) ;;
    *) rcon "$line";;
  esac
done < "$LIST"
sleep 3
ls "$ROOT/run-clienttest/screenshots/" | grep dbz_

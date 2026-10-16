#!/bin/bash
# Windows (Git Bash) dev screenshots: starts the dev server and the scripted client, runs setup commands and shots
# over RCON, stops both, and copies 1280-wide versions of the named shots to $TEMP/x/.
# usage: tools/dev/winshots.sh "<setup cmd;cmd;...>" shot1 "@cmd|cmd|..." shot2 ...
#   a plain argument is a devshot name (taken 6 ticks later; see CLAUDE.md for name codes, e.g. front_hidegui_x,
#   aurapv.<aura>.<state>, seqN for an N-frame clip); an argument starting with @ is a |-separated list of commands.
#   Note: "~" in RCON commands is relative to world spawn; use "execute as Dev at Dev run ..." for Dev's position.
# Needs JAVA_HOME (JDK 17) and run-server/server.properties with enable-rcon=true, rcon.port=25575 and a rcon.password.
cd "$(dirname "$0")/../.."
J=${JAVA_HOME:-/c/Users/sunam/.jdks/jdk-17.0.20.1+1}/bin/java
P=$(grep '^rcon.password=' run-server/server.properties | cut -d= -f2-)
OUT=${TEMP:-/tmp}; mkdir -p "$OUT/x"
./gradlew --offline runServer > "$OUT/dbz-server.log" 2>&1 &
for i in $(seq 1 120); do grep -q "Done (" "$OUT/dbz-server.log" && break; sleep 2; done
./gradlew --offline runClientTest -PdevScreenshots=0 -PdevQuitAfter=0 > "$OUT/dbz-client.log" 2>&1 &
for i in $(seq 1 150); do grep -q "Dev joined the game" "$OUT/dbz-server.log" && break; sleep 2; done; sleep 8
SETUP="$1"; shift
if [ -n "$SETUP" ]; then IFS=';' read -ra CMDS <<< "$SETUP"; "$J" tools/Rcon.java 25575 "$P" "${CMDS[@]}" >/dev/null; sleep 3; fi
for s in "$@"; do
  if [ "${s:0:1}" = "@" ]; then IFS="|" read -ra CC <<< "${s:1}"; "$J" tools/Rcon.java 25575 "$P" "${CC[@]}" >/dev/null; sleep 4
  else "$J" tools/Rcon.java 25575 "$P" "dbz devshot Dev $s 6" >/dev/null; sleep 4; fi
done
"$J" tools/Rcon.java 25575 "$P" "stop" >/dev/null; sleep 8
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"name like 'java%'\" | Where-Object { \$_.CommandLine -match 'devlaunch|clienttest|ClientTest' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
echo shots-done

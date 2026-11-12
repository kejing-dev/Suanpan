#!/usr/bin/env bash
# Package smoke test (v0.1.2, package io.embodit.suanpan): zh + en cold start (default view + settings first screen),
# reset-progress confirm, real [Quit] -> process and Recents. Evidence -> apps/suanpan-abacus/build/rename-smoke/ and the
# gate files in apps/suanpan-abacus/build/. DEV / AVD env as for the harness.
set -u
HERE="$(cd "$(dirname "$0")" && pwd -W)"
export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
PKG=io.embodit.suanpan
B="C:/Users/Admin/Downloads/spatial-app-factory/apps/suanpan-abacus/build"
OUT="$B/rename-smoke"; mkdir -p "$OUT"
SHOTS="$HERE/../.scratch/shots"
PY=~/.factory-venv/Scripts/python.exe
SP="$HERE/sp.sh"
log() { echo "[$(date +%H:%M:%S)] $*"; }
cold() {
  adb -s "$DEV" shell am force-stop $PKG
  adb -s "$DEV" logcat -c
  adb -s "$DEV" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  for i in $(seq 1 40); do adb -s "$DEV" logcat -d -s Suanpan:I | grep -q "SCENE beads built" && break; sleep 3; done
  sleep 2
}
crop() { $PY -c "from PIL import Image; im=Image.open(r'$1'); im.crop(tuple(int(v*im.width/2160) for v in ($3))).save(r'$2')"; }

adb -s "$DEV" shell pm clear $PKG >/dev/null
for L in zh en; do
  log "cold start $L"
  cold; "$SP" "lang $L" >/dev/null; sleep 1; cold    # language saved, then a real cold start in it
  adb -s "$DEV" logcat -d -s Suanpan:I AndroidRuntime:E > "$OUT/coldstart-$L-logcat.txt"
  WAIT=7 "$HERE/cap.sh" smoke-default-$L 0 0 9999 9999 0.5 >/dev/null; cp "$SHOTS/smoke-default-$L.png" "$OUT/default-$L.png"
  "$SP" "tab settings" >/dev/null; sleep 2
  "$HERE/harness/emu.sh" key d -HoldMs 500 >/dev/null; sleep 2     # the wide volume pushes the window's right edge out of the emulator view
  WAIT=6 "$HERE/cap.sh" smoke-settings-$L 0 0 9999 9999 0.5 >/dev/null; cp "$SHOTS/smoke-settings-$L.png" "$OUT/settings-$L.png"
done
log "gate evidence (zh)"
"$SP" "lang zh" >/dev/null; cold
WAIT=7 "$HERE/cap.sh" default-view 0 0 9999 9999 0.5 >/dev/null; cp "$SHOTS/default-view.png" "$B/default-view.png"
"$SP" "lesson 1" >/dev/null; sleep 2
WAIT=4 "$HERE/cap.sh" first-screen 0 0 9999 9999 0.5 >/dev/null; cp "$SHOTS/first-screen.png" "$B/first-screen.png"
"$SP" "tab settings" >/dev/null; sleep 2; "$HERE/harness/emu.sh" key d -HoldMs 500 >/dev/null; sleep 2
WAIT=6 "$HERE/cap.sh" set-ev 0 0 9999 9999 1.0 >/dev/null
"$SP" "press clear_progress" >/dev/null; sleep 2
WAIT=6 "$HERE/cap.sh" conf-ev 0 0 9999 9999 1.0 >/dev/null
"$SP" "press cancel" >/dev/null; sleep 1
log "exit through the real buttons"
"$SP" "press exit" >/dev/null; sleep 1; "$SP" "press exit_yes" >/dev/null; sleep 4
adb -s "$DEV" shell dumpsys activity recents > "$B/exit-recents.txt"; cp "$B/exit-recents.txt" "$OUT/exit-recents.txt"
if adb -s "$DEV" shell pidof $PKG >/dev/null; then log "PROCESS STILL ALIVE"; else log "process gone"; fi
grep -c "Recent #.*$PKG" "$B/exit-recents.txt" | sed 's/^/recents entries for the app: /'
log "done"

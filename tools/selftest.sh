#!/usr/bin/env bash
# Build-station self-test on the emulator (DEV, default emulator-5554 = AVD Pico_Emulator_6_1_c on kj-home-pc):
# install, cold start, default view, both language tours, the gate evidence (settings first screen, reset-progress
# confirm, exit + recents), launcher icon. Evidence goes to .scratch/shots and the factory's apps/suanpan-abacus/build.
set -u
HERE="$(cd "$(dirname "$0")" && pwd -W)"
export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
DEV="${DEV:-emulator-5554}"; export DEV
PKG=io.embodit.suanpan
APK="$HERE/../app/build/outputs/apk/debug/app-debug.apk"
EVI="C:/Users/Admin/Downloads/spatial-app-factory/apps/suanpan-abacus/build"
SHOTS="$HERE/../.scratch/shots"
PY=~/.factory-venv/Scripts/python.exe
mkdir -p "$EVI" "$SHOTS"
SP="$HERE/sp.sh"
log() { echo "[$(date +%H:%M:%S)] $*"; }
crop_win() { $PY -c "from PIL import Image; im=Image.open(r'$1'); k=im.width/2160; im.crop(tuple(int(v*k) for v in (1440,760,2080,1440))).save(r'$2')"; }
cold() {
  adb -s "$DEV" shell am force-stop $PKG
  [ "${1:-}" = clear ] && adb -s "$DEV" shell pm clear $PKG >/dev/null
  adb -s "$DEV" logcat -c
  adb -s "$DEV" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  for i in $(seq 1 40); do adb -s "$DEV" logcat -d -s Suanpan:I | grep -q "SCENE beads built" && break; sleep 3; done
  sleep 2
}

log "install"; adb -s "$DEV" install -r "$APK" | tail -1
log "cold start (first run)"; cold clear
adb -s "$DEV" logcat -v time -s Suanpan:I AndroidRuntime:E > "$SHOTS/selftest-logcat.txt" &
LOGPID=$!
WAIT=8 "$HERE/cap.sh" default-view 0 0 9999 9999 0.5 >/dev/null && cp "$SHOTS/default-view.png" "$EVI/default-view.png"
"$SP" "lesson 1" >/dev/null; sleep 2
WAIT=5 "$HERE/cap.sh" first-screen 0 0 9999 9999 0.5 >/dev/null && cp "$SHOTS/first-screen.png" "$EVI/first-screen.png"

[ -z "${SKIP_TOURS:-}" ] && for L in zh en; do log "tour $L"; rm -rf "$SHOTS/tour-$L"; "$HERE/tour.sh" $L > "$SHOTS/tour-$L.log" 2>&1; done

log "settings first screen + reset-progress confirm (zh)"
"$SP" "lang zh" >/dev/null; "$SP" "lesson 3 problem 4" >/dev/null; sleep 1; "$SP" "tap 0 lower 1" >/dev/null; sleep 1
"$SP" "tab settings" >/dev/null; sleep 2
WAIT=7 "$HERE/cap.sh" settings 0 0 9999 9999 0.5 >/dev/null; crop_win "$SHOTS/settings_full.png" "$EVI/settings-firstscreen.png"
"$SP" "press clear_progress" >/dev/null; sleep 2
WAIT=7 "$HERE/cap.sh" confirm 0 0 9999 9999 0.5 >/dev/null; crop_win "$SHOTS/confirm_full.png" "$EVI/newgame-confirm.png"
"$SP" "press cancel" >/dev/null; sleep 1

log "S16 resume: half-done problem, force-stop, cold start"
"$SP" "lesson 3 problem 4" >/dev/null; sleep 2; "$SP" "tap 0 lower 1" >/dev/null; sleep 2
kill $LOGPID 2>/dev/null
cold
adb -s "$DEV" logcat -v time -s Suanpan:I AndroidRuntime:E >> "$SHOTS/selftest-logcat.txt" &
LOGPID=$!
WAIT=7 "$HERE/cap.sh" S16 0 0 9999 9999 0.5 >/dev/null

log "3 cold starts, ANR check"
for n in ${COLD:-1 2 3}; do
  adb -s "$DEV" shell am force-stop $PKG; sleep 1
  adb -s "$DEV" logcat -b events -c
  adb -s "$DEV" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 60
  anr=$(adb -s "$DEV" logcat -b events -d | grep -c "am_anr.*$PKG")
  dlg=$(adb -s "$DEV" shell dumpsys window windows | grep -c "Application Not Responding")
  log "cold start $n: am_anr=$anr anr_dialog=$dlg"
done
log "exit through the real buttons, then recents"
"$SP" "tab settings" >/dev/null; sleep 1; "$SP" "press exit" >/dev/null; sleep 1
WAIT=6 "$HERE/cap.sh" S14exit_zh 0 0 9999 9999 0.5 >/dev/null
"$SP" "press exit_yes" >/dev/null; sleep 4
adb -s "$DEV" shell dumpsys activity recents > "$EVI/exit-recents.txt"
adb -s "$DEV" shell pidof $PKG && log "PROCESS STILL ALIVE" || log "process gone"
kill $LOGPID 2>/dev/null
log "launcher: HOME"
adb -s "$DEV" shell input keyevent 3; sleep 4
adb -s "$DEV" exec-out screencap -p > "$SHOTS/launcher_raw.png"
log "done"

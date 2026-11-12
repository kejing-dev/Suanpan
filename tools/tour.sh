#!/usr/bin/env bash
# State tour for the self-test / ui-audit: every named state of the design (S00–S16) in one language, a console
# screenshot each, control-window crop + whitespace. Usage: tools/tour.sh zh|en [names...]
# Output: .scratch/shots/tour-<lang>/<state>.png (full frame, 0.5) + <state>_win.png (window crop) + whitespace.jsonl
set -u
HERE="$(cd "$(dirname "$0")" && pwd -W)"
export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
LANG_="${1:-zh}"; shift || true
OUT="$HERE/../.scratch/shots/tour-$LANG_"; mkdir -p "$OUT"
SP="$HERE/sp.sh"
PY=~/.factory-venv/Scripts/python.exe
shoot() {   # shoot NAME
  WAIT="${WAIT:-7}" "$HERE/cap.sh" "tour_$1" 0 0 9999 9999 0.5 >/dev/null
  mv "$HERE/../.scratch/shots/tour_$1.png" "$OUT/$1.png"
  mv "$HERE/../.scratch/shots/tour_$1_full.png" "$OUT/$1_full.png"
  $PY -c "from PIL import Image; im=Image.open(r'$OUT/$1_full.png'); k=im.width/2160; im.crop(tuple(int(v*k) for v in (1440,760,2080,1440))).save(r'$OUT/$1_win.png')"
  $PY "$HERE/whitespace.py" "$OUT/$1_full.png" | sed "s/^{/{\"state\": \"$1\", \"lang\": \"$LANG_\", /" >> "$OUT/whitespace.jsonl"
}
s() { "$SP" "$@" >/dev/null; sleep "${GAP:-1.5}"; }
STATES="${*:-S01 S02 S03 S03off S03why S05off S06 S07 S08 S09 S10 S11 S13 S14exit S14clear S15}"
s "lang $LANG_"; s "near off"
for st in $STATES; do
  case $st in
    S01) s "goto S01" ;;
    S02) s "goto S02" ;;
    S03) s "lesson 7 problem 5"; s "hint on" ;;
    S03off) s "lesson 7 problem 5"; s "hint off" ;;
    S03why) s "lesson 7 problem 5"; s "hint on"; s "why" ;;
    S05off) s "lesson 7 problem 5"; s "hint on"; s "tap 5 lower 1" ;;
    S06) s "lesson 7 problem 5"; s "freeze 0.1"; s "demo"; sleep 6 ;;
    S07) s "freeze 1"; s "lesson 7 problem 5"; s "tap 1 lower 4"; s "tap 0 upper 1"; s "freeze 0.05"; s "tap 0 lower 2" ;;
    S08) s "freeze 1"; s "lessondone 4" ;;
    S09) s "lesson 7 problem 5"; s "stuck" ;;
    S10) s "freeze 1"; s "seed 7"; s "practice mix 2" ;;
    S11) s "free set 4072" ;;
    S13) s "tab settings" ;;
    S14exit) s "goto S14exit" ;;
    S14clear) s "goto S14clear" ;;
    S15) s "tab learn"; s "goto S15"; sleep 3 ;;
  esac
  shoot "$st"
  case $st in S06|S07) s "freeze 1"; s "stop" ;; S14exit|S14clear) s "press cancel" ;; S15) s "reopen"; sleep 6 ;; esac
done
cat "$OUT/whitespace.jsonl"

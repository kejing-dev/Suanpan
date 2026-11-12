#!/usr/bin/env bash
# cap.sh NAME [x0 y0 x1 y1] [scale]: 60 fps for a moment, console screenshot of emulator DEV, optional crop + scale,
# saved as .scratch/shots/NAME.png (full frame kept as NAME_full.png)
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
"$HERE/harness/emu.sh" fast on >/dev/null
sleep "${WAIT:-6}"
"$HERE/harness/emu.sh" shot "$1" >/dev/null
OUT="$HERE/../.scratch/shots"
mkdir -p "$OUT"
mv "$HERE/../.scratch/shots/harness/$1.png" "$OUT/$1_full.png"
~/.factory-venv/Scripts/python.exe - "$OUT/$1_full.png" "$OUT/$1.png" "${2:-0}" "${3:-0}" "${4:-2160}" "${5:-2158}" "${6:-0.5}" <<'PY'
import sys
from PIL import Image
src, dst, x0, y0, x1, y1, k = sys.argv[1], sys.argv[2], *map(int, sys.argv[3:7]), float(sys.argv[7])
im = Image.open(src).convert('RGB'); im = im.crop((x0, y0, min(x1, im.width), min(y1, im.height)))
im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
im.save(dst)
print(dst, im.size)
PY

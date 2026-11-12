"""Whitespace of the control window in a console screenshot (build.md 空白的量法, profile D44):
(content top − window top + window bottom − content bottom) ÷ window height, plus the largest blank band inside the
content (> 20 % of the window = tighten). Content = rows with text / icons / buttons: dark pixels (labels) or saturated
pixels (the interaction-blue button). The window box is found from its light glass against the room.

  ~/.factory-venv/Scripts/python.exe tools/whitespace.py <shot.png> [x0 y0 x1 y1 search box] -> JSON line
"""
import json
import sys

import os
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import winbox
from PIL import Image


WINDOW = (1525, 905, 1995, 1375)   # search box for window_box (not used by default)
FIXED = (1530, 920, 1990, 1344)    # the control window after a cold start on emulator-5554 (from S14 / S03 edges), not dragged


def window_box(im, box=WINDOW):
    """The window's own edges: the longest run of light, grey glass along the centre column / row of the search box."""
    x0, y0, x1, y1 = box
    def run(line):
        lum = (line[:, 0] * 299 + line[:, 1] * 587 + line[:, 2] * 114) // 1000
        sat = line.max(axis=1) - line.min(axis=1)
        g = (lum >= 145) & (sat < 25)
        best, cur, start, bs = 0, 0, 0, 0
        for i, v in enumerate(g):
            if v:
                if cur == 0: start = i
                cur += 1
                if cur > best: best, bs = cur, start
            else:
                cur = 0
        return bs, bs + best
    cx = (x0 + x1) // 2
    # sample three columns left of centre (text rows can break the glass run) and keep the widest
    tops = [run(im[y0 - 60:y1 + 60, c]) for c in (cx - 150, cx - 175, cx - 200)]
    t0, t1 = max(tops, key=lambda r: r[1] - r[0])
    top, bottom = y0 - 60 + t0, y0 - 60 + t1
    return x0, top, x1, bottom


def measure(path, win=None):
    im = np.asarray(Image.open(path).convert('RGB')).astype(np.int32)
    x0, y0, x1, y1 = win or winbox.find(im)
    a = im[y0 + 12:y1 - 12, x0 + 4:x1 - 4]
    lum = (a[..., 0] * 299 + a[..., 1] * 587 + a[..., 2] * 114) // 1000
    sat = a.max(axis=2) - a.min(axis=2)
    # local contrast: text / icons / buttons are much darker (or more saturated) than their blurred surroundings; the
    # translucent glass's own brightness follows the room behind it and is ignored
    from PIL import ImageFilter
    blur = np.asarray(Image.fromarray(lum.astype(np.uint8)).filter(ImageFilter.BoxBlur(12))).astype(np.int32)
    ink = (lum < blur - 28) | (sat > 70)
    rows = np.where(ink.sum(axis=1) >= 3)[0]
    h = y1 - y0
    if len(rows) == 0:
        return dict(file=path.replace(chr(92), '/').split('/')[-1], error='no content')
    c0, c1 = rows.min() + 12, rows.max() + 12
    gaps = np.diff(rows)
    return dict(file=path.replace(chr(92), '/').split('/')[-1], window=[int(x0), int(y0), int(x1), int(y1)],
                outside=round(float((c0 + h - 1 - c1) / h), 3),
                largest_band=round(float((gaps.max() - 1) / h), 3) if len(gaps) else 0.0)


if __name__ == '__main__':
    win = tuple(map(int, sys.argv[2:6])) if len(sys.argv) >= 6 else None
    print(json.dumps(measure(sys.argv[1], win), ensure_ascii=False))

"""Measured contrast of every text line in the control window (D13), from console screenshots.

For each text line (a run of rows with ink): text = mean of the darkest 3 % pixels, background = median of the light
glass pixels in the same rows; a line inside a blue (interaction) button is measured white-on-blue instead (brightest
3 % vs the blue median). WCAG relative luminance ratio. The screenshot is the rendered result (glass over the room), so
the numbers include what the room does to the glass.

  ~/.factory-venv/Scripts/python.exe tools/contrast.py <shot_full.png>... -> one JSON line per file (min + per line)
"""
import json
import sys

import os
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import winbox
from PIL import Image

BOX = tuple(map(int, os.environ.get('WINBOX', '1536,877,1984,1281').split(',')))  # the window this run (inset)


def rel(c):
    c = np.asarray(c, dtype=float) / 255
    c = np.where(c <= 0.03928, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return 0.2126 * c[..., 0] + 0.7152 * c[..., 1] + 0.0722 * c[..., 2]


def ratio(a, b):
    la, lb = rel(a), rel(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def lines(path):
    im = np.asarray(Image.open(path).convert('RGB')).astype(int)
    x0, y0, x1, y1 = BOX
    a = im[y0:y1, x0:x1]
    lum = (a[..., 0] * 299 + a[..., 1] * 587 + a[..., 2] * 114) // 1000
    sat = a.max(axis=2) - a.min(axis=2)
    blue = (a[..., 2] > 150) & (a[..., 0] < 90) & (sat > 90)
    from PIL import ImageFilter
    blur = np.asarray(Image.fromarray(lum.astype(np.uint8)).filter(ImageFilter.BoxBlur(12))).astype(np.int32)
    ink = (lum < blur - 28) | blue
    rows = np.where(ink.sum(axis=1) >= 3)[0]
    out = []
    if len(rows) == 0:
        return out
    groups, start, prev = [], rows[0], rows[0]
    for r in rows[1:]:
        if r > prev + 2:
            groups.append((start, prev)); start = r
        prev = r
    groups.append((start, prev))
    for g0, g1 in groups:
        band = a[g0:g1 + 1].reshape(-1, 3)
        bl = blue[g0:g1 + 1].reshape(-1)
        lb = lum[g0:g1 + 1].reshape(-1)
        if bl.mean() > 0.15:   # a blue button: white label on blue
            bg = np.median(band[bl], axis=0)
            txt = band[np.argsort(lb)[-max(3, len(lb) // 140):]].mean(axis=0)
            kind = 'on-blue'
        else:
            inkb = ink[g0:g1 + 1].reshape(-1)
            glass = band[~inkb]
            if len(glass) < 20:
                continue
            bg = np.median(glass, axis=0)
            dark = band[np.argsort(lb)[:max(3, len(lb) // 140)]]
            txt = dark.mean(axis=0)
            kind = 'on-glass'
        out.append(dict(rows=[int(g0 + y0), int(g1 + y0)], kind=kind, ratio=round(float(ratio(txt, bg)), 2),
                        text=[int(v) for v in txt], bg=[int(v) for v in bg]))
    return out


for p in sys.argv[1:]:
    ls = [l for l in lines(p) if l["rows"][1] - l["rows"][0] >= 8]   # text lines, not 1–4 px edges
    print(json.dumps(dict(file=p.replace(chr(92), '/').split('/')[-1], min=min((l['ratio'] for l in ls), default=None),
                          lines=len(ls), worst=sorted(ls, key=lambda l: l["ratio"]))))

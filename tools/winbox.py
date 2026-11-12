"""Finds the control window in a console screenshot. Its bottom edge = the last row (scanning up from the cabinet)
whose median brightness across x 1560..1990 is glass (>= 150) for 10 rows running; its height is constant at this
distance (424 px = 600 dp, measured on clean cold-start frames), so top = bottom - 424."""
import numpy as np

HEIGHT = 424


def find(im, x0=1560, x1=1990, y0=760, y1=1480):
    a = im[y0:y1, x0:x1]
    lum = (a[..., 0] * 299 + a[..., 1] * 587 + a[..., 2] * 114) // 1000
    g = np.median(lum, axis=1) >= 150
    run = 0
    for i in range(len(g) - 1, -1, -1):
        run = run + 1 if g[i] else 0
        if run >= 10:
            bottom = y0 + i + run
            return (1530, bottom - HEIGHT, 1990, bottom)
    return (1530, 920, 1990, 1344)

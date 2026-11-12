"""Builds Suanpan's layered spatial app icon (PICO OS 6) with Pillow (design appIcon).

  ~/.factory-venv/Scripts/python.exe tools/build_icon.py [--preview out.png] [--png512 path]

Layers (1024 px, inside the inscribed circle, no baked shadows):
  0  background: 青花蓝 #1F3F99, full circle
  1  middle: 米白 #F3E9D2 abacus face — rounded frame, a thick beam, three rods
  2  top: 黄杨 #E2B46E bicone beads, 2 above / 5 below the beam on each rod (上二下五); the middle rod reads 7
     (one top bead + two bottom beads at the beam, a shade brighter than the beads away from it)
Also writes each layer's SDF, the flattened round icon (mipmap) and the adaptive-icon foreground.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
S = 1024
SS = 4

BLUE = (31, 63, 153)
IVORY = (243, 233, 210)
BEAD = (226, 180, 110)
BEAD_LIT = (246, 206, 140)
BEAD_DIM = (196, 150, 86)


def mask(size, draw_fn):
    big = Image.new("L", (size * SS, size * SS), 0)
    draw_fn(ImageDraw.Draw(big), SS)
    return big.resize((size, size), Image.LANCZOS)


def solid(size, color, m):
    layer = Image.new("RGBA", (size, size), color + (0,))
    layer.putalpha(m)
    return layer


def background(size, round_canvas):
    if round_canvas:
        return solid(size, BLUE, mask(size, lambda d, k: d.ellipse((0, 0, size * k - 1, size * k - 1), fill=255)))
    return Image.new("RGBA", (size, size), BLUE + (255,))


# geometry in a 1024 frame, scaled by `sc` around the centre
FX0, FY0, FX1, FY1 = 232, 262, 792, 762      # frame outer
BAR = 34                                       # frame bar width
BEAM_Y0, BEAM_Y1 = 400, 436                    # beam
RODS_X = [372, 512, 652]
BEAD_W, BEAD_H = 104, 50


def T(v, sc, size):
    c = size / 2
    return c + (v - 512) * sc


def face(size, sc):
    def draw(d, k):
        t = lambda v: T(v, sc, size) * k
        d.rounded_rectangle((t(FX0), t(FY0), t(FX1), t(FY1)), radius=38 * sc * k, fill=255)
        d.rounded_rectangle((t(FX0 + BAR), t(FY0 + BAR), t(FX1 - BAR), t(FY1 - BAR)), radius=10 * sc * k, fill=0)
        d.rectangle((t(FX0 + BAR), t(BEAM_Y0), t(FX1 - BAR), t(BEAM_Y1)), fill=255)
        for x in RODS_X:
            d.rectangle((t(x - 7), t(FY0 + BAR), t(x + 7), t(FY1 - BAR)), fill=255)
    return solid(size, IVORY, mask(size, draw))


def bead_poly(cx, cy, w, h):
    # bicone seen from the front: a hexagon with rounded ends
    return [(cx - w / 2, cy), (cx - w / 2 + h * 0.45, cy - h / 2), (cx + w / 2 - h * 0.45, cy - h / 2),
            (cx + w / 2, cy), (cx + w / 2 - h * 0.45, cy + h / 2), (cx - w / 2 + h * 0.45, cy + h / 2)]


def beads(size, sc):
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    top_free = (FY0 + BAR, BEAM_Y0)
    bot_free = (BEAM_Y1, FY1 - BAR)
    gap = 4
    for ri, x in enumerate(RODS_X):
        at_beam_u = 1 if ri == 1 else 0
        at_beam_l = 2 if ri == 1 else 0
        for i in range(2):  # upper beads, i = 1 nearest the beam
            j = i + 1
            if j <= at_beam_u:
                cy = BEAM_Y0 - (j - 0.5) * (BEAD_H + gap)
                col = BEAD_LIT
            else:
                cy = top_free[0] + (2 - j + 0.5) * (BEAD_H + gap)
                col = BEAD_DIM
            m = mask(size, lambda d, k, cy=cy: d.polygon([(T(px, sc, size) * k, T(py, sc, size) * k) for px, py in bead_poly(x, cy, BEAD_W, BEAD_H)], fill=255))
            out.alpha_composite(solid(size, col, m.filter(ImageFilter.GaussianBlur(0.6))))
        for i in range(5):
            j = i + 1
            if j <= at_beam_l:
                cy = BEAM_Y1 + (j - 0.5) * (BEAD_H + gap)
                col = BEAD_LIT
            else:
                cy = bot_free[1] - (5 - j + 0.5) * (BEAD_H + gap)
                col = BEAD_DIM
            m = mask(size, lambda d, k, cy=cy: d.polygon([(T(px, sc, size) * k, T(py, sc, size) * k) for px, py in bead_poly(x, cy, BEAD_W, BEAD_H)], fill=255))
            out.alpha_composite(solid(size, col, m.filter(ImageFilter.GaussianBlur(0.6))))
    return out


def sdf(layer):
    inside = layer.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
    dist = Image.new("L", inside.size, 0)
    cur = inside
    for i in range(1, 33):
        dist.paste(min(255, i * 8), mask=cur)
        cur = cur.filter(ImageFilter.MinFilter(3) if i % 2 else ImageFilter.Kernel((3, 3), (0, 1, 0, 1, 1, 1, 0, 1, 0), 5))
        cur = cur.point(lambda v: 255 if v >= 255 else 0)
    dist = Image.composite(dist, Image.new("L", inside.size, 0), inside)
    return Image.merge("RGBA", (dist, dist, dist, dist))


def flatten(layers):
    out = Image.new("RGBA", layers[0].size, (0, 0, 0, 0))
    for layer in layers:
        out.alpha_composite(layer)
    return out


def main():
    for sub in ("drawable", "mipmap-xxxhdpi"):
        os.makedirs(os.path.join(RES, sub), exist_ok=True)
    layers = [background(S, True), face(S, 1.0), beads(S, 1.0)]
    for i, layer in enumerate(layers):
        layer.save(os.path.join(RES, "drawable", f"icon_3d_layer_{i}.png"))
        sdf(layer).save(os.path.join(RES, "drawable", f"icon_3d_sdf_{i}.png"))
    flat = flatten(layers)
    flat.resize((432, 432), Image.LANCZOS).save(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_round.png"))
    a = 432
    sq = flatten([background(a, False), face(a, 0.78 * a / S), beads(a, 0.78 * a / S)])
    sq.convert("RGB").save(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher.png"))
    if "--png512" in sys.argv:
        flat.resize((512, 512), Image.LANCZOS).save(sys.argv[sys.argv.index("--png512") + 1])
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        w = 300
        tiles = layers + [flat, sq.resize((S, S))]
        sheet = Image.new("RGB", (w * len(tiles), w), (60, 64, 70))
        for i, t in enumerate(tiles):
            t2 = t.resize((w, w), Image.LANCZOS)
            sheet.paste(t2, (i * w, 0), t2)
        sheet.save(out)
    print("ICON layers", len(layers))


main()

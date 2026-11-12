"""Calibrates the decimated Tripo GLBs for the scene (profile D25: centre, facing, size), with the factory venv:

  ~/.factory-venv/Scripts/python.exe tools/prep_models.py <decimated dir> app/src/main/assets/models

Input: bead.glb / frame.glb / beam.glb / stand.glb from bin/glb_decimate.py. Output (baked into the vertices, no node
transforms), placed by the app like the procedural parts:
  bead   symmetry axis (shortest extent) along +Y, centred, Ø 0.055 × 0.040 m (design entities3d.beads)
  frame  stood up wider than tall, bottom edge centre at the origin; outer 0.600 × 0.448 × 0.030 and, by a piecewise
         linear warp per axis, the inner opening exactly x ±0.270, y 0.030 … 0.418 (so beads never cut into the bars)
  beam   centred, 0.540 × 0.020 × 0.026 (sits at face y 0.274 … 0.294)
  stand  the board only (faces below the struts), bottom centre at the origin, 0.60 × 0.025 × 0.24
  brace  one of the stand's struts, cut out, long axis along +Y, centred, 0.03 × 0.22 × 0.02; the app props the abacus
         face with two of them from behind (leaning like an easel leg)
Prints the box before / after; writes <decimated dir>/calibration.json (asset list of the hand-over).
"""
import json
import os
import sys

import numpy as np
import trimesh

# v0.1.1 (QA #1 q1-1, D17 >= 2°): the whole abacus is the design's size × 1.5 (AbacusLayout.K); every model is calibrated
# to the design size below, then scaled uniformly about its calibrated origin.
SCALE = 1.5


def rot_axis(a_from, a_to):
    if a_from == a_to:
        return np.eye(4)
    v = np.zeros(3); v[a_from] = 1
    w = np.zeros(3); w[a_to] = 1
    return trimesh.transformations.rotation_matrix(np.pi / 2, np.cross(v, w))


def fit_box(m, size, origin):
    lo, hi = m.bounds
    ext = hi - lo
    S = np.array(size) / ext
    c = (lo + hi) / 2
    M = np.eye(4); M[:3, :3] = np.diag(S)
    M[:3, 3] = -(c if origin == 'centre' else np.array([c[0], lo[1], c[2]])) * S
    m.apply_transform(M)


def warp(values, src_knots, dst_knots):
    return np.interp(values, src_knots, dst_knots)


def sub(m, mask):
    s = m.copy()
    s.update_faces(mask)
    s.remove_unreferenced_vertices()
    return s


def report(role, m, ext0, dst):
    lo, hi = m.bounds
    r = dict(role=role, faces=int(len(m.faces)), source_box=[round(float(x), 4) for x in ext0],
             min=[round(float(x), 4) for x in lo], max=[round(float(x), 4) for x in hi], bytes=os.path.getsize(dst))
    print(json.dumps(r))
    return r


def main():
    src, dst = sys.argv[1], sys.argv[2]
    os.makedirs(dst, exist_ok=True)
    out = []

    # bead
    m = trimesh.load(os.path.join(src, 'bead.glb'), force='mesh', process=False)
    ext0 = m.extents.copy()
    m.apply_transform(rot_axis(int(np.argmin(ext0)), 1))
    fit_box(m, (0.055, 0.040, 0.055), 'centre')
    m.apply_scale(SCALE); m.export(os.path.join(dst, 'bead.glb')); out.append(report('bead', m, ext0, os.path.join(dst, 'bead.glb')))

    # frame: thinnest axis -> Z, wider than tall, then warp outer + inner edges onto the design
    m = trimesh.load(os.path.join(src, 'frame.glb'), force='mesh', process=False)
    ext0 = m.extents.copy()
    m.apply_transform(rot_axis(int(np.argmin(ext0)), 2))
    if m.extents[0] < m.extents[1]:
        m.apply_transform(rot_axis(0, 1))
    lo, hi = m.bounds; c = (lo + hi) / 2; e = hi - lo
    tc = m.triangles_center
    mid_y = np.abs(tc[:, 1] - c[1]) < 0.15 * e[1]
    mid_x = np.abs(tc[:, 0] - c[0]) < 0.15 * e[0]
    inner_x = np.min(np.abs(tc[mid_y, 0] - c[0]))     # half width of the opening
    inner_y = np.min(np.abs(tc[mid_x, 1] - c[1]))     # half height of the opening
    v = m.vertices.copy()
    v[:, 0] = warp(v[:, 0], [lo[0], c[0] - inner_x, c[0] + inner_x, hi[0]], [-0.300, -0.270, 0.270, 0.300])
    v[:, 1] = warp(v[:, 1], [lo[1], c[1] - inner_y, c[1] + inner_y, hi[1]], [0.000, 0.030, 0.418, 0.448])
    v[:, 2] = (v[:, 2] - c[2]) * (0.030 / e[2])
    m.vertices = v
    print('frame opening half', round(float(inner_x), 4), round(float(inner_y), 4), 'of outer half', (e[:2] / 2).round(4))
    m.apply_scale(SCALE); m.export(os.path.join(dst, 'frame.glb')); out.append(report('frame', m, ext0, os.path.join(dst, 'frame.glb')))

    # beam
    m = trimesh.load(os.path.join(src, 'beam.glb'), force='mesh', process=False)
    ext0 = m.extents.copy()
    m.apply_transform(rot_axis(int(np.argmax(ext0)), 0))
    fit_box(m, (0.540, 0.020, 0.026), 'centre')
    m.apply_scale(SCALE); m.export(os.path.join(dst, 'beam.glb')); out.append(report('beam', m, ext0, os.path.join(dst, 'beam.glb')))

    # stand board + one strut
    m = trimesh.load(os.path.join(src, 'stand.glb'), force='mesh', process=False)
    ext0 = m.extents.copy()
    lo, hi = m.bounds
    cut = lo[1] + 0.34 * (hi[1] - lo[1])
    tc = m.triangles_center
    board = sub(m, tc[:, 1] < cut)
    strut = sub(m, (tc[:, 1] >= cut) & (tc[:, 0] < (lo[0] + hi[0]) / 2))
    fit_box(board, (0.600, 0.025, 0.240), 'bottom_centre')
    board.apply_scale(SCALE); board.export(os.path.join(dst, 'stand.glb')); out.append(report('stand', board, ext0, os.path.join(dst, 'stand.glb')))
    # strut: principal axis -> Y
    pts = strut.vertices - strut.vertices.mean(axis=0)
    _, _, vt = np.linalg.svd(pts, full_matrices=False)
    axis = vt[0] / np.linalg.norm(vt[0])
    if axis[1] < 0:
        axis = -axis
    R = trimesh.geometry.align_vectors(axis, [0, 1, 0])
    strut.apply_transform(R)
    fit_box(strut, (0.030, 0.220, 0.020), 'centre')
    strut.apply_scale(SCALE); strut.export(os.path.join(dst, 'brace.glb')); out.append(report('brace', strut, ext0, os.path.join(dst, 'brace.glb')))

    json.dump(out, open(os.path.join(src, 'calibration.json'), 'w'), indent=1)


main()

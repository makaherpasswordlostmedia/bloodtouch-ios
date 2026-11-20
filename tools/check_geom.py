"""Measure drawn widget art on a device screenshot and compare with the predicted geometry.

libtouchcontrols squeezes art horizontally by K=(26/16)/(W/H), so a widget whose tap rect is
tw x th px must paint a (tw*K) x th blob. If the measured extent disagrees the squeeze factor -
and therefore every rect in make_layouts.py - is wrong.
"""
import re
import sys
from PIL import Image

W, H = 2340.0, 1080.0
K = (26.0 / 16.0) / (W / H)
shot, layout = sys.argv[1], sys.argv[2]
tags = sys.argv[3:] or ["attack", "use", "jump"]

im = Image.open(shot).convert("L")
px = im.load()
txt = open(layout).read()
for m in re.finditer(r"<(\w+)\s+([^>]*?)/>", txt):
    tag = m.group(1)
    if tag not in tags:
        continue
    a = {k: float(v) for k, v in re.findall(r'(\w+)="(-?[\d.]+)"', m.group(2))}
    if a["hidden"]:
        continue
    x0, x1, y0, y1 = a["left"] * W, a["right"] * W, a["top"] * H, a["bottom"] * H
    dw, dh = (x1 - x0) * K, y1 - y0
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    pad = 60
    bx0, bx1 = int(cx - dw / 2 - pad), int(cx + dw / 2 + pad)
    by0, by1 = int(cy - dh / 2 - pad), int(cy + dh / 2 + pad)
    win = [px[x, y] for y in range(max(by0, 0), min(by1, H)) for x in range(max(bx0, 0), min(bx1, W))]
    if not win:
        continue
    lo, hi = sorted(win)[int(len(win) * 0.55)], max(win)
    pts = [(x, y) for y in range(max(by0, 0), min(by1, H)) for x in range(max(bx0, 0), min(bx1, W))
           if px[x, y] > lo + (hi - lo) * 0.25]
    if not pts:
        print("%-8s predicted %.0fx%.0f  measured: nothing bright enough" % (tag, dw, dh))
        continue
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    mw, mh = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1
    print("%-8s predicted %.0fx%.0f @(%d,%d)  measured %dx%d @(%d,%d)  w/pred=%.3f -> K~%.3f" % (
        tag, dw, dh, cx - dw / 2, cy - dh / 2, mw, mh, min(xs), min(ys),
        mw / dw, (mw / (x1 - x0))))

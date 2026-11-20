"""Is the screen actually moving?  dumpsys gfxinfo counts the Java view hierarchy, not the engine's
own GL swaps, so a live cutscene reads as 0 fps there - compare two screencaps instead."""
import sys

from PIL import Image

a = Image.open(sys.argv[1]).convert("RGB")
b = Image.open(sys.argv[2]).convert("RGB")
if a.size != b.size:
    print("size changed %s -> %s (the screen really moved)" % (a.size, b.size))
    sys.exit(0)

pa, pb = a.load(), b.load()
changed = 0
for y in range(0, a.height, 3):
    for x in range(0, a.width, 3):
        if abs(pa[x, y][0] - pb[x, y][0]) > 6 or abs(pa[x, y][1] - pb[x, y][1]) > 6 \
           or abs(pa[x, y][2] - pb[x, y][2]) > 6:
            changed += 1

total = len(range(0, a.width, 3)) * len(range(0, a.height, 3))
print("changed %d of %d sampled px (%.3f%%)" % (changed, total, 100.0 * changed / total))

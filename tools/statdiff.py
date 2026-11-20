"""Which touch-layout files the engine rewrote while we were cycling the pause menu.

libtouchcontrols writes a <file>.default snapshot every time it points a widget group at a layout,
so a per-cycle rewrite count tells us the pause path really does reload the groups - the only place
left where a /sdcard write can stall the render thread.
"""
import sys


def load(path):
    out = {}
    for line in open(path):
        parts = line.split()
        if len(parts) == 2:
            out[parts[0]] = parts[1]
    return out


b, a = load(sys.argv[1]), load(sys.argv[2])
changed = sorted(k.rsplit("/", 1)[-1] for k in a if k in b and a[k] != b[k])
print("layout files: %d before, %d after, rewritten during the pause cycles: %d %s" % (
    len(b), len(a), len(changed), " ".join(changed)))

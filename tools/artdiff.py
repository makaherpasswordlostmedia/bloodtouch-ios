import struct, sys

FS = r"G:\Blood_gog\Blood - Fresh Supply"
RS = r"G:\Blood_gog\blood_refreshed_supply"
PACK = r"G:\blood_port\pack\BLOOD"


def head(p):
    with open(p, "rb") as f:
        d = f.read(64)
    return d


def art_info(p):
    d = head(p)
    # Blood ART header: char id[12]; short version; short artsize; int numTiles; int dataOffset; int numSamples
    ident = d[:12]
    ver, artsize = struct.unpack("<hh", d[12:16])
    numtiles, offset, numsamples = struct.unpack("<iii", d[16:28])
    return ident, ver, artsize, numtiles, offset, numsamples


for name in ("tiles003.art", "tiles018.art"):
    print("=== " + name)
    for tag, root in (("FS  ", FS), ("RS  ", RS), ("pack", PACK)):
        import os
        p = os.path.join(root, name)
        if not os.path.isfile(p):
            print("  %s absent" % tag)
            continue
        print("  %s %s  size=%d" % (tag, art_info(p), os.path.getsize(p)))

print()
print("=== byte diff tiles003.art FS vs RS")
a = open(FS + "\\tiles003.art", "rb").read()
b = open(RS + "\\tiles003.art", "rb").read()
n = min(len(a), len(b))
diff = [i for i in range(n) if a[i] != b[i]]
print("  len %d vs %d, differing bytes %d (%.3f%%)" % (len(a), len(b), len(diff), 100.0 * len(diff) / n))
if diff:
    print("  first diff at %d, last at %d" % (diff[0], diff[-1]))
    print("  FS @%d: %s" % (diff[0], a[diff[0] - 8:diff[0] + 24].hex()))
    print("  RS @%d: %s" % (diff[0], b[diff[0] - 8:diff[0] + 24].hex()))

import os, struct

FS = r"G:\Blood_gog\Blood - Fresh Supply\surface.dat"
RS = r"G:\Blood_gog\blood_refreshed_supply\surface.dat"
PK = r"G:\blood_port\pack\BLOOD\surface.dat"

for tag, p in (("FS", FS), ("RS", RS), ("pack", PK)):
    d = open(p, "rb").read()
    # Build surface.dat header: int numSurfs; int numTiles; int version;  (then tile records)
    a, b, c = struct.unpack("<iii", d[:12])
    print("%-5s size=%6d  numSurfs=%d numTiles=%d version=%d" % (tag, len(d), a, b, c))
    print("      first16=%s" % d[:16].hex())

fs = open(FS, "rb").read()
rs = open(RS, "rb").read()
n = min(len(fs), len(rs))
print()
print("RS contains FS as a prefix:", rs[:len(fs)] == fs)
diff = sum(1 for i in range(n) if fs[i] != rs[i])
print("differing bytes in common range: %d of %d" % (diff, n))
if diff:
    first = next(i for i in range(n) if fs[i] != rs[i])
    print("first diff at %d" % first)
    print("  FS: %s" % fs[first:first + 32].hex())
    print("  RS: %s" % rs[first:first + 32].hex())

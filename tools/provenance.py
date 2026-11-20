import hashlib, os

PACK = r"G:\blood_port\pack\BLOOD"
FS = r"G:\Blood_gog\Blood - Fresh Supply"
RS = r"G:\Blood_gog\blood_refreshed_supply"


def md5(p, limit=None):
    if not os.path.isfile(p):
        return None
    h = hashlib.md5()
    with open(p, "rb") as f:
        while True:
            b = f.read(1 << 20)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


names = sorted(n for n in os.listdir(PACK) if n.lower().endswith((".rff", ".ini", ".art", ".dat")))
print("%-16s %10s  %-8s %-8s %s" % ("file", "pack size", "vs FS", "vs RS", "note"))
for n in names:
    p = os.path.join(PACK, n)
    a = md5(p)
    b = md5(os.path.join(FS, n))
    c = md5(os.path.join(RS, n))
    if n.lower().endswith(".art") or n.lower().endswith(".dat"):
        note = "asset"
    else:
        note = ""
    print("%-16s %10d  %-8s %-8s %s" % (
        n, os.path.getsize(p),
        "same" if a == b else ("-" if b is None else "DIFF"),
        "same" if a == c else ("-" if c is None else "DIFF"),
        note))

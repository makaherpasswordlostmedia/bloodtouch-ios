import os
import sys


def listing(root):
    out = {}
    for dirpath, dirnames, filenames in os.walk(root):
        for f in filenames:
            p = os.path.join(dirpath, f)
            rel = os.path.relpath(p, root).replace(os.sep, "/")
            try:
                sz = os.path.getsize(p)
            except OSError:
                sz = -1
            out[rel.lower()] = sz
    return out


steam = listing(sys.argv[1])
pack = listing(sys.argv[2])
print("steam files:", len(steam), " pack files:", len(pack))

skip_prefix = ("user/", "save", "screenshots", "steam_appid.txt", "default.ini")
missing = []
for k, s in steam.items():
    if k in pack:
        continue
    if "/addons/" in k or k.startswith(skip_prefix):
        continue
    missing.append((k, s))

print("\n== in reference install but NOT in our pack (by size)")
for k, s in sorted(missing, key=lambda x: -x[1])[:40]:
    print("  %-55s %12d" % (k, s))

print("\n== only in our pack")
only = [k for k in pack if k not in steam and "/addons/" not in k]
for k in sorted(only)[:40]:
    print("  ", k)

print("\n== size mismatch on shared files")
bad = 0
for k in steam:
    if k in pack and steam[k] != pack[k] and "/addons/" not in k:
        bad += 1
        if bad < 15:
            print("  %-45s ref=%d pack=%d" % (k, steam[k], pack[k]))
print("total mismatched:", bad)

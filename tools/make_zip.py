"""Packs the Android port into one archive: APK + game data + instructions + checksums.

Level 1 deflate rather than STORED on purpose: the media is already compressed, but the .art
tiles, voxel.dat and the RFF archives still give up a few percent, and level 1 costs seconds.
"""
import hashlib
import os
import zipfile

ROOT = "G:/blood_port"
OUT = ROOT + "/dist/BloodTouch_Android.zip"
FILES = [
    (ROOT + "/bloodtouch.apk", "bloodtouch.apk"),
    (ROOT + "/dist/INSTALL.txt", "INSTALL.txt"),
]
GAME = ROOT + "/pack/BLOOD"

entries = list(FILES)
for base, dirs, names in os.walk(GAME):
    dirs.sort()
    for name in sorted(names):
        path = os.path.join(base, name)
        entries.append((path, "BLOOD/" + os.path.relpath(path, GAME).replace("\\", "/")))

missing = [src for src, _ in entries if not os.path.isfile(src)]
if missing:
    raise SystemExit("missing: " + ", ".join(missing))

total = sum(os.path.getsize(src) for src, _ in entries)
print("files: %d, bytes: %d" % (len(entries), total))

digests = []
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=1) as zf:
    for src, name in entries:
        md5 = hashlib.md5()
        with open(src, "rb") as fh:
            while True:
                chunk = fh.read(1 << 20)
                if not chunk:
                    break
                md5.update(chunk)
        digests.append("%s  %s" % (md5.hexdigest(), name))
        zf.write(src, name)

info = zipfile.ZipInfo("checksums.txt", date_time=(2026, 10, 9, 17, 50, 0))
info.compress_type = zipfile.ZIP_DEFLATED
with zipfile.ZipFile(OUT, "a") as zf:
    zf.writestr(info, ("\n".join(digests) + "\n").encode("utf-8"))

print("archive: %d bytes" % os.path.getsize(OUT))

"""Zip a directory into dist/ with a checksums.txt alongside, so a copy can be verified later."""
import hashlib
import os
import sys
import zipfile

SRC = sys.argv[1].rstrip("/")
OUT = sys.argv[2]
PREFIX = sys.argv[3] if len(sys.argv) > 3 else ""

entries = []
for base, dirs, names in os.walk(SRC):
    dirs.sort()
    for name in sorted(names):
        path = os.path.join(base, name)
        rel = os.path.relpath(path, SRC).replace("\\", "/")
        entries.append((path, (PREFIX + "/" if PREFIX else "") + rel))

entries.sort(key=lambda e: e[1])
print("files: %d, bytes: %d" % (len(entries), sum(os.path.getsize(p) for p, _ in entries)))

digests = []
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
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

info = zipfile.ZipInfo((PREFIX + "/" if PREFIX else "") + "checksums.txt",
                       date_time=(2026, 10, 9, 18, 30, 0))
info.compress_type = zipfile.ZIP_DEFLATED
with zipfile.ZipFile(OUT, "a") as zf:
    zf.writestr(info, ("\n".join(digests) + "\n").encode("utf-8"))

print("archive: %s, %d bytes" % (OUT, os.path.getsize(OUT)))

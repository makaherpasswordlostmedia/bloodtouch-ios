"""Rewrite the aapt2-linked APK with '/' path separators (aapt2 on Windows emits
backslashes apksigner rejects), then add classes.dex and the native libraries."""
import os
import sys
import zipfile

apk = sys.argv[1]
dex = sys.argv[2]
libdir = sys.argv[3]

src = zipfile.ZipFile(apk, "r")
tmp = apk + ".tmp"
out = zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED)

for info in src.infolist():
    name = info.filename.replace("\\", "/")
    meta = zipfile.ZipInfo(name, date_time=info.date_time)
    meta.compress_type = info.compress_type
    meta.external_attr = info.external_attr
    meta.create_system = info.create_system
    out.writestr(meta, src.read(info))
src.close()

out.writestr("classes.dex", open(dex, "rb").read(), zipfile.ZIP_STORED)

for fn in sorted(os.listdir(libdir)):
    if fn.endswith(".so"):
        out.writestr("lib/arm64-v8a/" + fn,
                     open(os.path.join(libdir, fn), "rb").read(),
                     zipfile.ZIP_DEFLATED)

out.close()
os.replace(tmp, apk)
print("entries:", len(zipfile.ZipFile(apk).namelist()))

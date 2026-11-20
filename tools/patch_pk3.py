"""Comment the brightness/gamma controls out of menudef.txt inside raze_dev.pk3.

The engine pk3 is the only place the in-game menus are defined, and menudef has no 'remove item'
directive, so the entries have to go where they are written.  Commenting (not deleting) mirrors how
the file itself already disables the menu-volume slider, and it stays idempotent: an entry that is
already commented is left alone.
"""
import os
import re
import shutil
import sys
import zipfile

PK3 = sys.argv[1] if len(sys.argv) > 1 else "G:/blood_port/bloodtouch/assets/raze_dev.pk3"
TMP = PK3 + ".tmp"

# Every surface that adjusts how bright the picture is: the two sliders in Display Options and the
# interactive gamma/brightness screen the Controls menu opens.
TARGETS = {
    "brightness slider": re.compile(r'^\s*Slider\b.*"vid_brightness"', re.I),
    "gamma slider": re.compile(r'^\s*Slider\b.*"vid_gamma"', re.I),
    "gamma screen control": re.compile(r'^\s*Control\b.*,\s*"bumpgamma"', re.I),
}

src = zipfile.ZipFile(PK3)
text = src.read("menudef.txt").decode("utf-8", "replace")
lines = text.split("\n")

for label, pattern in TARGETS.items():
    live = [i for i, l in enumerate(lines) if pattern.match(l)]
    if len(live) != 1:
        sys.exit("expected exactly one %s, found %d" % (label, len(live)))
    i = live[0]
    lines[i] = "//" + lines[i]
    print("patched %-22s line %d: %s" % (label, i + 1, lines[i].strip()[:88]))

new = zipfile.ZipFile(TMP, "w")
for info in src.infolist():
    data = src.read(info.filename)
    if info.filename == "menudef.txt":
        data = "\n".join(lines).encode("utf-8")
    meta = zipfile.ZipInfo(info.filename, date_time=info.date_time)
    meta.compress_type = info.compress_type
    meta.external_attr = info.external_attr
    meta.create_system = info.create_system
    new.writestr(meta, data)
src.close()
new.close()
shutil.move(TMP, PK3)

body = zipfile.ZipFile(PK3).read("menudef.txt").decode("utf-8", "replace")
print("entries:", len(zipfile.ZipFile(PK3).namelist()))
for label, pattern in TARGETS.items():
    print("%-22s live: %d" % (label, sum(1 for l in body.split("\n") if pattern.match(l))))
print("pk3 size:", os.path.getsize(PK3))

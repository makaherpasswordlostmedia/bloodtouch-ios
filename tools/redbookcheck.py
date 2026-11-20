import os, re

PACK = r"G:\blood_port\pack\BLOOD"

avail = []
inis = []
for dp, _d, ns in os.walk(PACK):
    for n in ns:
        full = os.path.join(dp, n)
        if re.fullmatch(r"blood\d+\.ogg", n, re.I):
            avail.append((os.path.relpath(dp, PACK).replace("\\", "/"), n.lower()))
        if n.lower().endswith(".ini"):
            inis.append(full)

print("loose bloodNN.ogg (redbook-name renders): %d" % len(avail))
for r, n in sorted(avail):
    print("   %-28s %s" % (r, n))

by_dir = {}
for r, n in avail:
    by_dir.setdefault(r, set()).add(n)

tracks = {}
for p in inis:
    cur = None
    rel = os.path.relpath(p, PACK).replace("\\", "/")
    for ln in open(p, encoding="utf-8", errors="replace"):
        m = re.match(r"\s*\[([^\]]+)\]", ln)
        if m:
            cur = m.group(1).strip().lower()
        t = re.match(r"\s*Track\s*=\s*(\d+)", ln, re.I)
        if t and cur:
            tracks.setdefault(rel, []).append((cur, int(t.group(1))))

print()
print("Track= keys per ini:")
for rel in sorted(tracks):
    base = os.path.dirname(rel) or "."
    have = by_dir.get(base, set()) | by_dir.get(".", set())
    need = set("blood%02i.ogg" % n for _k, n in tracks[rel])
    missing = sorted(need - have)
    print("  %-28s %d levels with Track, %d distinct tracks, missing %s" % (
        rel, len(tracks[rel]), len(need), missing if missing else "none"))

if not tracks:
    print("  (none found - mus_redbook can never resolve a track, so the CD-rip option is inert)")

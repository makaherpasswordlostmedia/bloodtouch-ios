import os, re, sys

PACK = r"G:\blood_port\pack\BLOOD"

# Raze's knownMusicExts order (raze_music.cpp) - first hit wins, OGG first.
MUSIC_EXTS = ["ogg", "flac", "mp3", "mp2", "xa", "xm", "mod", "it", "s3m", "mid", "hmp", "hmi", "xmi"]


def all_files(root):
    out = {}
    for dirpath, _dirs, names in os.walk(root):
        for n in names:
            rel = os.path.relpath(os.path.join(dirpath, n), root).replace("\\", "/").lower()
            out[rel] = os.path.join(dirpath, n)
    return out


def lookup_music(files, name):
    """Mirror FindMusic(): <name> with every known extension, then music/<name>."""
    stem = re.sub(r"\.[^.]*$", "", name, flags=re.I)
    for base in ("", "music/"):
        for ext in MUSIC_EXTS:
            cand = base + stem.lower() + "." + ext
            if cand in files:
                return cand, (ext == "mid" or ext in ("hmp", "hmi", "xmi"))
    return None, None


def audit(ini_path, label):
    files = all_files(PACK)
    text = open(ini_path, encoding="utf-8", errors="replace").read()
    lines = text.splitlines()

    print("=== %s  (%s)" % (label, os.path.basename(ini_path)))
    maps = []
    songs = []
    ep = None
    for ln in lines:
        m = re.match(r"\s*(Episode|Part|Episode\d*)\s*=", ln, re.I)
        if m:
            ep = ln.strip()
        mm = re.match(r"\s*Map\d*\s*=\s*(\S+)", ln, re.I)
        if mm:
            maps.append((ep, mm.group(1)))
        sm = re.match(r"\s*Song\s*=\s*(\S+)", ln, re.I)
        if sm:
            songs.append((ep, sm.group(1)))

    cutwavs = []
    for ln in lines:
        sm = re.match(r"\s*Song\s*=\s*(\S+)", ln, re.I)
        if sm:
            songs.append((ep, sm.group(1)))
        cm = re.match(r"\s*CutWav[AB]\s*=\s*(\S+)", ln, re.I)
        if cm:
            cutwavs.append(cm.group(1).replace("\\", "/").lower())

    seen = {}
    for ep, s in songs:
        key = s.lower()
        seen.setdefault(key, ep)
    print("  songs: %d distinct" % len(seen))
    bad = 0
    for key in sorted(seen):
        hit, midi = lookup_music(files, key)
        tag = "MIDI-ONLY" if midi else ("OK" if hit else "MISSING")
        if tag != "OK":
            bad += 1
        print("    %-12s %-10s %s" % (key, tag, hit or "-"))

    # A CutWav that does not resolve keeps 'ans' empty in OpenMovie(), which makes the
    # engine skip the .ivf entirely and play the Smacker instead.
    cutbad = 0
    for c in cutwavs:
        if c not in files:
            cutbad += 1
            print("    CutWav MISSING: %s" % c)
    print("  cutwav refs: %d, missing %d; songs needing work: %d" % (len(cutwavs), cutbad, bad))


audit(os.path.join(PACK, "blood.ini"), "base campaign")
for root, _d, names in os.walk(os.path.join(PACK, "addons")):
    for n in names:
        if n.lower().endswith(".ini"):
            audit(os.path.join(root, n), os.path.relpath(root, PACK))

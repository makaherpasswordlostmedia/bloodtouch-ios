"""Emulate Raze's Blood level-def loading (levels.cpp levelLoadDefaults + levelTryPlayMusic)
against the pack, to prove an addon ini is usable without booting the engine.

Checks per ini: every [EpisodeN] has Map1..MapK, each MapK names an existing section, the section
has a non-empty non-token Title, and its Track resolves to a bloodNN.ogg inside the addon dir or
the base game dir.
"""
import os
import re
import sys

PACK = r"G:/blood_port/pack/BLOOD"
CASE = {}
for root, dirs, files in os.walk(PACK):
    for f in files:
        CASE[(os.path.relpath(os.path.join(root, f), PACK)).replace("\\", "/").lower()] = True


def has(rel):
    return rel.lower() in CASE


def parse(path):
    sections, cur = {}, None
    for line in open(path, encoding="latin-1").read().splitlines():
        s = line.strip()
        if not s or s.startswith(";"):
            continue
        m = re.match(r"^\[(.+)\]$", s)
        if m:
            cur = sections[m.group(1).lower()] = {}
            continue
        if cur is None or "=" not in s:
            continue
        k, v = s.split("=", 1)
        cur[k.strip().lower()] = v.strip()
    return sections


def check(ini_rel, label):
    path = os.path.join(PACK, *ini_rel.split("/"))
    if not os.path.exists(path):
        print("%-12s MISSING %s" % (label, ini_rel))
        return 1
    secs = parse(path)
    problems = []
    levels = 0
    for e in range(1, 7):
        ep = secs.get("episode%d" % e)
        if ep is None:
            break
        if not ep.get("title") or ep["title"].startswith("$"):
            problems.append("episode%d title bad: %r" % (e, ep.get("title")))
        j = 1
        while "map%d" % j in ep:
            name = ep["map%d" % j]
            sec = secs.get(name.lower())
            if sec is None:
                problems.append("episode%d map%d -> [%s] section absent (engine I_Error)" % (e, j, name))
            else:
                levels += 1
                title = sec.get("title", "")
                if not title or title.startswith("$"):
                    problems.append("[%s] title bad: %r" % (name, title))
                track = sec.get("track", "")
                base_dir = ini_rel.rsplit("/", 1)[0] if "/" in ini_rel else ""
                if track.isdigit() and int(track) > 0:
                    ogg = "blood%02i.ogg" % int(track)
                    cand = [("%s/%s" % (base_dir, ogg)) if base_dir else ogg, ogg]
                    if not any(has(c) for c in cand):
                        problems.append("[%s] Track=%s -> %s not found" % (name, track, ogg))
                nxt = sec.get("endinga", "")
                if nxt.isdigit() and int(nxt) > 0:
                    tgt = ep.get("map%s" % nxt)
                    if tgt is None:
                        problems.append("[%s] EndingA=%s but episode%d has no Map%s" % (name, nxt, e, nxt))
                # base campaign maps live inside BLOOD.RFF, only addons ship loose .map files
                if base_dir and not has("%s/%s.map" % (base_dir, name)):
                    problems.append("[%s] %s.map missing" % (name, name))
            j += 1
    print("%-12s %-26s levels=%d  %s" % (label, ini_rel, levels, "OK" if not problems else "%d PROBLEM" % len(problems)))
    for p in problems[:8]:
        print("        - %s" % p)
    return len(problems)


bad = 0
for ini_rel, label in [("blood.ini", "base"),
                       ("addons/cryptic/CRYPTIC.INI", "cryptic"),
                       ("addons/deathwish/dw.ini", "deathwish"),
                       ("addons/marrow/MARROW.ini", "marrow")]:
    bad += check(ini_rel, label)
print("TOTAL PROBLEMS:", bad)
sys.exit(1 if bad else 0)

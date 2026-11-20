"""Inline KEX '$token' strings from localization/loc_english.txt into Blood addon .ini files.

Raze 1.11 reads level definitions straight out of the ini and has no KEX string table, so a
'Title = $dw_map1' line would reach the screen as a raw token. Usage:
    python work/detokenize.py            # report only
    python work/detokenize.py --write    # rewrite the ini files
"""
import re
import sys

LOC = r"G:/blood_port/work/loc_english.txt"
TARGETS = [
    r"G:/blood_port/pack/BLOOD/addons/deathwish/dw.ini",
    r"G:/blood_port/pack/BLOOD/addons/marrow/MARROW.ini",
]

VALUE = re.compile(r'\$([A-Za-z0-9_]+)\s*=\s*"((?:\\.|[^"\\])*)"')
ASSIGN = re.compile(r'^([^\s=][^=]*=\s*)\$([A-Za-z0-9_]+)\s*$')

ESCAPES = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\"}


def unescape(text):
    out, i = [], 0
    while i < len(text):
        c = text[i]
        if c == "\\" and i + 1 < len(text) and text[i + 1] in ESCAPES:
            out.append(ESCAPES[text[i + 1]])
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def load_table():
    text = open(LOC, encoding="utf-8", errors="replace").read()
    table = {}
    for m in VALUE.finditer(text):
        table.setdefault(m.group(1), unescape(m.group(2)))
    return table


def convert(path, table, write):
    raw = open(path, "rb").read().decode("latin-1")
    sep = "\r\n" if "\r\n" in raw else "\n"
    lines = raw.split(sep)
    done, missing, multiline = 0, [], 0

    for i, line in enumerate(lines):
        m = ASSIGN.match(line)
        if not m:
            continue
        value = table.get(m.group(2))
        if value is None:
            missing.append(m.group(2))
            continue
        if "\n" in value:
            multiline += 1
            value = value.replace("\n", "\\n")
        lines[i] = m.group(1) + value.strip()
        done += 1

    print("%s: replaced %d, missing %d, had-newline %d" % (path, done, len(set(missing)), multiline))
    if missing:
        print("   unresolved: %s" % ", ".join(sorted(set(missing))[:12]))

    if write and done:
        data = sep.join(lines).encode("latin-1")
        backup = path + ".kex"
        open(backup, "wb").write(raw.encode("latin-1"))
        open(path, "wb").write(data)
        print("   written, original kept as %s" % backup)
    return done


def main():
    write = "--write" in sys.argv
    table = load_table()
    print("localization tokens: %d" % len(table))
    total = 0
    for t in TARGETS:
        total += convert(t, table, write)
    print("total replaced: %d" % total)


main()

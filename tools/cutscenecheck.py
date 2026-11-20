# Replica of MainActivity.campaignInfo() cutscene/audio checks, run against the pack on disk.
# Mirrors Raze: OpenMovie() only tries <stem>.ivf when the ini CutWav resolves to a registered
# sound file, and VpxPlayer then streams the soundtrack named after the VIDEO stem.
import os

BASE = os.path.join('pack', 'BLOOD')
SND = ('.ogg', '.flac', '.mp3', '.opus', '.wav')


def exists(root, rel):
    return os.path.isfile(os.path.join(*([root] + rel.split('/'))))


def parse(ini):
    vids, wavs = [], []
    for line in open(ini, encoding='latin-1').read().splitlines():
        s = line.strip()
        if not s or s.startswith(';') or '=' not in s:
            continue
        key, val = s.split('=', 1)
        key = key.strip().lower()
        val = val.strip().strip('"').strip("'").replace('\\', '/')
        if not val:
            continue
        if key.startswith('cutscene') and 'level' not in key:
            vids.append(val)
        elif key.startswith('cutwav'):
            wavs.append(val)
    return vids, wavs


for inipath, d in ((os.path.join(BASE, 'blood.ini'), BASE),
                   (os.path.join(BASE, 'addons', 'cryptic', 'CRYPTIC.INI'), os.path.join(BASE, 'addons', 'cryptic')),
                   (os.path.join(BASE, 'addons', 'deathwish', 'dw.ini'), os.path.join(BASE, 'addons', 'deathwish')),
                   (os.path.join(BASE, 'addons', 'marrow', 'MARROW.ini'), os.path.join(BASE, 'addons', 'marrow'))):
    vids, wavs = parse(inipath)
    found = hd = silent = 0
    notes = []
    for vid in vids:
        stem = vid.rsplit('.', 1)[0] if '.' in vid else vid
        host = exists(d, vid) or exists(BASE, vid)
        ivf = exists(d, stem + '.ivf') or exists(BASE, stem + '.ivf')
        if not host and not ivf:
            notes.append('no file for ' + vid)
            continue
        found += 1
        if ivf:
            hd += 1
            if not any(exists(d, stem + e) or exists(BASE, stem + e) for e in SND):
                silent += 1
                notes.append('IVF without soundtrack file: ' + stem)
    nowav = [w for w in wavs if not (exists(d, w) or exists(BASE, w))]
    print('%-40s cutscenes %d, HD %d, HD-without-audio %d, CutWav missing %d'
          % (os.path.relpath(inipath, BASE), found, hd, silent, len(nowav)))
    for n in notes:
        print('    ', n)
    for w in nowav:
        print('     no CutWav file:', w)

import av, os, glob

ROOT = 'G:/blood_port/pack/BLOOD/'
for f in sorted(glob.glob(ROOT + '**/*.ivf', recursive=True)):
    stem = f[:-4]
    c = av.open(f)
    s = c.streams.video[0]
    vdur = (c.duration / av.time_base) if c.duration else s.frames / float(s.average_rate or 24)
    aud = None
    for e in ('ogg', 'flac', 'mp3', 'opus', 'wav'):
        if os.path.exists(stem + '.' + e):
            aud = stem + '.' + e
            break
    adur = ''
    if aud:
        try:
            a = av.open(aud)
            adur = '%.1fs' % (a.duration / av.time_base if a.duration else 0)
        except Exception as ex:
            adur = 'BROKEN ' + str(ex)[:40]
    print('%-42s %4dx%-4d %6.1fs %5dkbps  %-14s %s' % (
        f.replace(ROOT, '').replace(os.sep, '/'), s.width, s.height, vdur,
        os.path.getsize(f) * 8 / vdur / 1000,
        os.path.basename(aud) if aud else 'NO AUDIO', adur))

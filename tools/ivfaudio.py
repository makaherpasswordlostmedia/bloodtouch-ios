# Compare each .ivf's duration with its soundtrack file: VpxPlayer stops at the video's frame
# count, so a much longer audio track would be cut off mid-sentence.
import os
import av

BASE = os.path.join('pack', 'BLOOD')
vids = []
for root, _dirs, files in os.walk(BASE):
    for f in files:
        if f.lower().endswith('.ivf'):
            vids.append(os.path.join(root, f))
vids.sort()

for path in vids:
    stem = os.path.splitext(path)[0]
    aud = next((stem + e for e in ('.ogg', '.flac', '.mp3', '.opus', '.wav') if os.path.isfile(stem + e)), None)
    c = av.open(path)
    s = c.streams.video[0]
    vd = float(s.duration * s.time_base) if s.duration else 0.0
    c.close()
    c2 = av.open(aud)
    a = c2.streams.audio[0]
    ad = float(a.duration * a.time_base) if a.duration else 0.0
    rate = a.sample_rate
    c2.close()
    flag = '  <-- AUDIO LONGER' if ad > vd + 1.5 else ''
    print('%-46s video %5.1fs  audio %5.1fs  (%+0.1f, %d Hz)%s'
          % (os.path.relpath(path, BASE), vd, ad, ad - vd, rate, flag))

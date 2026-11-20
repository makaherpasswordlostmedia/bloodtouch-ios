# Decode every .ivf in the pack with libvpx/ffmpeg (via PyAV) to prove the conversions are intact:
# header check alone would not catch a truncated file or an unsupported VP8 profile.
import os
import av

BASE = os.path.join('pack', 'BLOOD')
ivfs = []
for root, _dirs, files in os.walk(BASE):
    for f in files:
        if f.lower().endswith('.ivf'):
            ivfs.append(os.path.join(root, f))
ivfs.sort()

for path in ivfs:
    stem = os.path.splitext(path)[0]
    aud = next((stem + e for e in ('.ogg', '.flac', '.mp3', '.opus', '.wav') if os.path.isfile(stem + e)), None)
    try:
        c = av.open(path)
        s = c.streams.video[0]
        frames = 0
        for _ in c.decode(s):
            frames += 1
        w, h = s.width, s.height
        codec = s.codec_context.name
        dur = float(s.duration * s.time_base) if s.duration else 0.0
        print('%-46s %s %dx%d frames=%d %.1fs audio=%s'
              % (os.path.relpath(path, BASE), codec, w, h, frames, dur, os.path.basename(aud) if aud else 'NONE'))
        c.close()
    except Exception as e:
        print('%-46s FAILED: %r' % (os.path.relpath(path, BASE), e))

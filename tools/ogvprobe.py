import av, os, sys

roots = [
    r"G:\Blood_gog\blood_refreshed_supply\movie",
    r"G:\blood_port\pack\BLOOD\movie",
]

for root in roots:
    print("=== " + root)
    names = sorted(n for n in os.listdir(root) if n.endswith((".ogv", ".ivf", ".ogg")))
    for n in names:
        p = os.path.join(root, n)
        try:
            c = av.open(p)
            vs = c.streams.video
            as_ = c.streams.audio
            v = vs[0] if vs else None
            dur = float(c.duration) * av.time_base if c.duration else -1
            print("  %-18s %5.1fs %7.2fMB  v=%s  a=%s" % (
                n, dur, os.path.getsize(p) / 1e6,
                ("%dx%d %s" % (v.width, v.height, v.codec_context.name)) if v else "-",
                (as_[0].codec_context.name if as_ else "-"),
            ))
            c.close()
        except Exception as e:
            print("  %-18s ERR %s" % (n, e))
    sub = os.path.join(root, "original")
    if os.path.isdir(sub):
        for n in sorted(os.listdir(sub)):
            if not n.endswith(".ogv"):
                continue
            p = os.path.join(sub, n)
            c = av.open(p)
            v = c.streams.video[0]
            dur = float(c.duration) * av.time_base if c.duration else -1
            print("  original/%-10s %5.1fs %7.2fMB  v=%dx%d %s" % (
                n, dur, os.path.getsize(p) / 1e6, v.width, v.height, v.codec_context.name))
            c.close()

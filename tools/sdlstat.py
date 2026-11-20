"""Measure whether the engine's render thread is still burning CPU.

gfxinfo and screenshot diffs both lie here: a static menu changes no pixels, so the only honest
liveness test is the SDLThread's accumulated CPU time across a short window. delta ~0 means blocked.
"""
import re
import subprocess
import sys
import time


def adb(shell):
    return subprocess.run(["adb", "shell", shell], capture_output=True, text=True,
                          errors="replace").stdout


def gamepid():
    return re.sub(r"\D", "", adb("pidof -s 'com.bloodtouch:game'"))


def secs(t):
    parts = [float(x) for x in t.split(":")]
    return sum(v * 60 ** (len(parts) - 1 - i) for i, v in enumerate(parts))


def rows(pid):
    # Threads: 36 total ... then 'TID USER PR NI VIRT RES SHR S %CPU %MEM TIME+ THREAD PROCESS'
    out = adb(f"top -H -b -n 1 -p {pid}")
    found = []
    for line in out.splitlines():
        tk = line.split()
        if len(tk) >= 13 and tk[0].isdigit() and ":" in tk[10]:
            found.append({"tid": tk[0], "state": tk[7], "cpu": tk[8], "time": secs(tk[10]), "name": tk[11]})
    return found


def main_thread(pid):
    cand = [r for r in rows(pid) if r["name"].startswith("SDL")]
    return max(cand, key=lambda r: r["time"]) if cand else None


if __name__ == "__main__":
    wait = float(sys.argv[1]) if len(sys.argv) > 1 else 3.0
    pid = gamepid()
    if not pid:
        print("sdl: pid=gone")
        sys.exit(0)
    a = main_thread(pid)
    if a is None:
        print(f"sdl: pid={pid} no SDLThread row")
        sys.exit(0)
    time.sleep(wait)
    b = main_thread(pid)
    if b is None or a["tid"] != b["tid"]:
        print(f"sdl: pid={pid} render thread {a['tid']} gone")
        sys.exit(0)
    print(f"sdl: pid={pid} tid={b['tid']} state={b['state']} cpu%={b['cpu']} "
          f"delta={b['time'] - a['time']:.2f}s")

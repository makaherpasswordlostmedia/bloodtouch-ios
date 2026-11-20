"""Tap helper for probe.sh: resolves screen size and widget centres from the device itself so the
probe keeps working whichever way the phone is lying."""
import io
import json
import os
import re
import subprocess
import sys

from PIL import Image

W = "G:/blood_port/work"
DIMS = os.path.join(W, "probe_dims.json")
# The layout we ship is the one the engine will load, so it is also the one the taps must follow.
MENU_XML = "G:/blood_port/bloodtouch/assets/menu.xml"
GAME_XML = "G:/blood_port/bloodtouch/assets/game_raze_dev101.xml"


def adb(*args):
    env = dict(os.environ, MSYS_NO_PATHCONV="1")
    return subprocess.run(["adb"] + list(args), env=env, capture_output=True)


def size():
    if os.path.exists(DIMS):
        return tuple(json.load(open(DIMS)))
    r = adb("exec-out", "screencap", "-p")
    im = Image.open(io.BytesIO(r.stdout))
    json.dump([im.width, im.height], open(DIMS, "w"))
    return im.width, im.height


def tap(x, y):
    adb("shell", "input", "tap", str(int(x)), str(int(y)))


def rect(tag, path):
    m = re.search(r'<%s [^>]*left="([\d.]+)" right="([\d.]+)" top="([\d.]+)" bottom="([\d.]+)"' % tag,
                  open(path).read())
    if not m:
        raise SystemExit("no %s in %s" % (tag, path))
    l, r, t, b = map(float, m.groups())
    return l, r, t, b


def tap_rect(tag, path):
    l, r, t, b = rect(tag, path)
    if re.search(r'<%s [^>]*hidden="1"' % tag, open(path).read()):
        print("%s is hidden in %s, not tapping" % (tag, path))
        return
    tap(w * (l + r) / 2.0, h * (t + b) / 2.0)
    print("%s tapped at %d,%d" % (tag, w * (l + r) / 2.0, h * (t + b) / 2.0))


mode = sys.argv[1]
w, h = size()

if mode == "dims":
    if os.path.exists(DIMS):
        os.remove(DIMS)
    print("screen %dx%d" % size())
elif mode == "play":
    adb("shell", "uiautomator", "dump", "/sdcard/ui.json")
    r = adb("exec-out", "cat", "/sdcard/ui.json")
    m = re.search(r'text="(?:PLAY|Play|Играть)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
                  r.stdout.decode("utf-8", "replace"))
    if m:
        x0, y0, x1, y1 = map(int, m.groups())
        tap((x0 + x1) / 2.0, (y0 + y1) / 2.0)
        print("PLAY at %dx%d box %s" % (x1 - x0, y1 - y0, m.group(1) and m.group(0)[-24:]))
    else:
        tap(w / 2.0, h * 0.768)
        print("PLAY not found, fell back to centre-bottom")
elif mode == "focus":
    r = adb("shell", "dumpsys", "window")
    m = re.search(r'mCurrentFocus=Window\{[^}]*\}', r.stdout.decode("utf-8", "replace"))
    print(m.group(0) if m else "no focus line")
elif mode == "dismiss":
    # The Meizu USB dialog keeps popping back over the game and every tap then lands in Settings,
    # which is what made the previous probe read a live menu as a hard freeze.
    f = adb("shell", "dumpsys", "window").stdout.decode("utf-8", "replace")
    focus = "".join(re.findall(r"mCurrentFocus=Window\{[^}]*\}", f))
    if "bloodtouch" in focus:
        print("focus already on bloodtouch: " + focus)
    else:
        print("focus elsewhere: " + focus)
        adb("shell", "uiautomator", "dump", "/sdcard/ui.json")
        d = adb("exec-out", "cat", "/sdcard/ui.json").stdout.decode("utf-8", "replace")
        m = re.search(r'text="(?:Отмена|Cancel|OK|ОК|Позже|Later|Зарядка|Charging)"[^>]*'
                      r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', d)
        if m:
            x0, y0, x1, y1 = map(int, m.groups())
            tap((x0 + x1) / 2.0, (y0 + y1) / 2.0)
            print("dismissed via button tap")
        else:
            adb("shell", "input", "keyevent", "4")
            print("no button found, sent one BACK")
elif mode == "dialog":
    # Our own Java dialogs (layout chooser, gyro) keep the same activity in mCurrentFocus, so
    # 'dismiss' cannot see them; this one looks for a button by text and presses it.
    adb("shell", "uiautomator", "dump", "/sdcard/ui.json")
    d = adb("exec-out", "cat", "/sdcard/ui.json").stdout.decode("utf-8", "replace")
    m = re.search(r'text="(?:Отмена|Cancel|ОК|OK|Выйти|Close)"[^>]*'
                  r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', d)
    if m:
        x0, y0, x1, y1 = map(int, m.groups())
        tap((x0 + x1) / 2.0, (y0 + y1) / 2.0)
        print("dialog button pressed: " + m.group(0).split("text=")[1].split(" bounds")[0])
    else:
        print("no dialog button found")
elif mode == "enter":
    tap_rect("enter", MENU_XML)
elif mode == "menu":
    tap_rect(sys.argv[2], MENU_XML)
elif mode == "widget":
    tap_rect(sys.argv[2], GAME_XML)
elif mode == "mid":
    tap(w / 2.0, h * 0.45)

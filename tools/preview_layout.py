"""Render the shipped touch layouts exactly the way libtouchcontrols draws them.

Art is squeezed horizontally by K=(26/16)/(2340/1080)=0.75 and centred in the tap rect, so this is
what the phone will show - green outlines are the tap areas, which is what must not overlap.
"""
import re
import os
from PIL import Image, ImageDraw

W, H = 2340.0, 1080.0
K = (26.0 / 16.0) / (W / H)
A = "G:/blood_port/bloodtouch/assets"
BACKDROP = "G:/blood_port/work/pc3.png"

# The XML schema has no image attribute - each control's art name is compiled into libraze_dev.so,
# so the preview needs its own tag -> asset map. Only used for rendering, not shipped.
IMG = {
    "back": "ui_back_arrow", "map": "map", "keyboard": "keyboard", "enter": "enter",
    "up_arrow": "arrow_up", "down_arrow": "arrow_down", "left_arrow": "arrow_left",
    "right_arrow": "arrow_right", "slide_mouse": "slide_arrow", "gyro": "gyro",
    "gamepad": "gamepad", "load_save_touch": "touchscreen_save",
    "attack": "shoot", "attack_alt": "shoot_alt", "use": "use", "jump": "jump",
    "crouch": "crouch", "quick_save": "save", "quick_load": "load",
    "use_inventory": "inventory", "show_weapons": "show_weapons", "next_weapon": "next_weap",
    "prev_weapon": "prev_weap", "swap_weapon": "swap", "stick": "fixed_stick_circle",
    "inventory_grid": "inventory_bg",
}
for _i in range(1, 10):
    IMG["weapon%d" % _i] = "key_%d" % _i
IMG["weapon0"] = "key_0"


def render(layouts, out):
    base = Image.open(BACKDROP).convert("RGBA").resize((int(W), int(H)))
    art_layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    boxes = []
    for layout in layouts:
        for m in re.finditer(r"<(\w+)\s+([^>]*?)/>", open(os.path.join(A, layout)).read()):
            tag = m.group(1)
            a = {k: float(v) for k, v in re.findall(r'(\w+)="(-?[\d.]+)"', m.group(2))}
            if a["hidden"]:
                continue
            x0, x1, y0, y1 = a["left"] * W, a["right"] * W, a["top"] * H, a["bottom"] * H
            boxes.append((tag, x0, y0, x1, y1))
            f = os.path.join(A, IMG.get(tag, tag) + ".png")
            if not os.path.exists(f):
                continue
            img = Image.open(f).convert("RGBA")
            dw = int(round((x1 - x0) * K))
            dh = int(round(y1 - y0))
            img = img.resize((max(dw, 1), max(dh, 1)), Image.LANCZOS)
            img.putalpha(img.split()[3].point(lambda v: int(v * 0.85)))
            art_layer.alpha_composite(img, (int(round(x0 + ((x1 - x0) - dw) / 2)), int(round(y0))))
    out_im = Image.alpha_composite(base, art_layer)
    d = ImageDraw.Draw(out_im)
    for tag, x0, y0, x1, y1 in boxes:
        d.rectangle([x0, y0, x1, y1], outline=(0, 255, 0, 160), width=2)
        d.text((x0 + 6, y0 + 4), tag, fill=(0, 255, 0, 255))
    out_im.convert("RGB").save(out)
    print("wrote", out, "widgets:", len(boxes))
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            a, b = boxes[i], boxes[j]
            # The look pad is a background area the fire buttons are meant to sit on.
            if a[0] == "touch" or b[0] == "touch":
                continue
            ox = min(a[3], b[3]) - max(a[1], b[1])
            oy = min(a[4], b[4]) - max(a[2], b[2])
            if ox > 1 and oy > 1:
                print("  TAP OVERLAP %s x %s %dx%d" % (a[0], b[0], ox, oy))


render(["game_raze_dev101.xml", "weapons_raze_dev101.xml", "inventory_raze_dev101.xml",
        "custom_buttons_0_raze_dev101.xml", "weaponwheel_raze_dev101.xml"],
       "G:/blood_port/work/preview_game.png")
render(["menu.xml"], "G:/blood_port/work/preview_menu.png")

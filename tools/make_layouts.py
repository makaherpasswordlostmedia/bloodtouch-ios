"""Generate the shipped touch layouts for 2340x1080.

libtouchcontrols squeezes widget ART horizontally by (ScaleX/ScaleY)/(screenW/screenH)
= (26/16)/(2340/1080) = 0.75, but leaves the tap area at the full XML rect.  Every rect below
is widened by 1/0.75 so the art lands at its true aspect (all 15 round widgets in the stock
.default file come out exactly 1.00 aspect under this factor, which is how it was calibrated).
"""
import os

W, H = 2340.0, 1080.0
K = (26.0 / 16.0) / (W / H)  # horizontal squeeze the engine applies to widget art

OUT = "G:/blood_port/work/live_layouts"

# The look pad is a background area: the fire buttons are meant to sit on top of it (that is how
# Zeta's own working Blood profile does it), so it is excluded from the overlap test.
BACKGROUNDS = {"touch"}


def rect(cx, cy, vw, vh=None):
    """Return left/right/top/bottom fractions for a widget whose *drawn* art is vw x vh px,
    centred on (cx, cy)."""
    vh = vw if vh is None else vh
    rw, rh = vw / K, vh
    return ((cx - rw / 2) / W, (cx + rw / 2) / W, (cy - rh / 2) / H, (cy + rh / 2) / H)


def box(x0, y0, x1, y1, vh):
    """Widget spanning screen x[x0,x1] whose art should come out vh px tall."""
    return (x0 / W, x1 / W, y0 / H, y1 / H)


def emit(name, r, hidden=0):
    l, t, rr, b = r[0], r[2], r[1], r[3]
    return '<%s enabled="1" hidden="%d" left="%.9g" right="%.9g" top="%.9g" bottom="%.9g" color="0" />' % (
        name, hidden, l, rr, t, b)


def report(tag, rows):
    print("\n== " + tag)
    boxes = []
    for name, r, hidden in rows:
        l, rr, t, b = r
        if hidden:
            continue
        boxes.append((name, l * W, rr * W, t * H, b * H))
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            a, c = boxes[i], boxes[j]
            if a[0] in BACKGROUNDS or c[0] in BACKGROUNDS:
                continue
            ox = min(a[2], c[2]) - max(a[1], c[1])
            oy = min(a[4], c[4]) - max(a[3], c[3])
            if ox > 1 and oy > 1:
                print("  OVERLAP %s x %s  %dx%d px" % (a[0], c[0], ox, oy))
    print("  visible widgets: %d" % len(boxes))


# ---------------------------------------------------------------- menu overlay
# Stock menu keeps back/keyboard top-left, save-load/gyro top-right, slide_mouse down the right
# edge. ENTER moves to the arrows' old bottom-right home and the arrows take the bottom-left.
menu = [
    ("back",            rect(97.5, 67.5, 135), 0),
    ("keyboard",        rect(292.5, 67.5, 135), 0),
    ("load_save_touch", rect(1890, 67.5, 135), 0),
    ("gyro",            rect(2242.5, 67.5, 135), 0),
    ("gamepad",         rect(2090, 67.5, 135), 1),
    ("slide_mouse",     box(1950, 135, 2340, 675, 540), 0),
    ("up_arrow",        rect(285, 742.5, 135), 0),
    ("down_arrow",      rect(285, 1012.5, 135), 0),
    ("left_arrow",      rect(105, 877.5, 135), 0),
    ("right_arrow",     rect(465, 877.5, 135), 0),
    ("enter",           rect(1960, 900, 360), 0),
]

# ---------------------------------------------------------------- game overlay
# Left thumb: stick + a weapon/inventory row. Right thumb: two columns of round buttons with the
# look pad underneath them, so the drawn right stick lands in the empty corridor between the
# columns instead of in the middle of the screen.
game_vis = [
    ("back",           rect(97.5, 67.5, 135)),
    ("map",            rect(292.5, 67.5, 135)),
    ("keyboard",       rect(487.5, 67.5, 135)),
    ("swap_weapon",    rect(97.5, 202.5, 135)),
    ("next_weapon",    rect(97.5, 337.5, 135)),
    ("prev_weapon",    rect(97.5, 472.5, 135)),
    ("use_inventory",  rect(487.5, 337.5, 135)),
    ("show_weapons",   rect(682.5, 337.5, 135)),
    ("quick_save",     rect(292.5, 472.5, 135)),
    ("quick_load",     rect(487.5, 472.5, 135)),
    ("stick",          box(0, 540, 780, 1080, 540)),
    ("touch",          box(1180, 135, 2340, 1080, 0)),
    ("attack_alt",     rect(1450, 300, 270)),
    ("use",            rect(1450, 570, 270)),
    ("crouch",         rect(1450, 840, 270)),
    ("jump",           rect(2100, 330, 270)),
    ("attack",         rect(2100, 700, 270)),
]
game_hid = ["attack2", "show_mouse", "quick_command",
            "activate_inventory", "crouch_toggle", "attack_alt2", "attack_alt_toggle",
            "run_toggle", "show_custom", "console", "kick", "gyro_toggle", "dpad_move"]

game = [(n, r, 0) for n, r in game_vis]
for n in game_hid:
    game.append((n, rect(97.5, 67.5, 135), 1))

# ------------------------------------------------- the other four touch groups
# libtouchcontrols builds six independent widget groups; the engine loads one XML per group and
# silently keeps its built-in rect for any group we do not ship.  The stock rects for the weapon
# number row (whole bottom edge) and the custom buttons (left middle) sit exactly on top of the
# stick / attack / crouch pads, which is what the user saw as "налазят друг на друга".
park = rect(97.5, 67.5, 135)          # inert home for widgets we switch off
weapons = []                          # 1..9 then 0, one row along the free top band
x0, cw = 580.0, (2340.0 - 580.0) / 10.0
for i, label in enumerate(["weapon1", "weapon2", "weapon3", "weapon4", "weapon5",
                           "weapon6", "weapon7", "weapon8", "weapon9", "weapon0"]):
    weapons.append((label, ((x0 + i * cw) / W, (x0 + (i + 1) * cw) / W, 0.0, 0.125), 0))

# Parked left of the look pad, which now covers the whole right side.
inventory = [("inventory_grid", ((620.0 / W), (1150.0 / W), (135.0 / H), (270.0 / H)), 0)]
custom = [(n, park, 1) for n in
          ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
           "quad_slide_1", "quad_slide_2", "quad_slide_3", "quad_slide_4"]]
wheel = [("weapon_wheel", park, 1)]

SETTINGS = ('<?xml version="1.0" ?>\n<settings alpha="0.5" look_sens="1" turn_sens="1" fwd_sens="1" '
            'strafe_sens="1" deadzone_sens="0.1" precision_sens="0.3" dig_move_y_sens="0.5" '
            'dig_move_x_sens="0.5" dbl_tap_left="0" dbl_tap_right="0" volume_up="0" volume_down="0" '
            'default_color="16777215" digital_move="0" always_run_default="1" invert_switch="0" '
            'joystick_look_switch="0" fixed_move_stick="0" show_custom="0" auto_hide_inventory="1" '
            'auto_hide_numbers="1" show_left_stick="1" show_right_stick="1" weapon_wheel_enabled="0" '
            'weapon_wheel_opaque="0" precision_shoot="0" />\n')

FILES = {"menu.xml": menu, "game_raze_dev101.xml": game, "weapons_raze_dev101.xml": weapons,
         "inventory_raze_dev101.xml": inventory, "custom_buttons_0_raze_dev101.xml": custom,
         "weaponwheel_raze_dev101.xml": wheel}

report("menu", menu)
for tag, rows in [("game", game), ("weapons", weapons), ("inventory", inventory),
                  ("custom_buttons", custom), ("weaponwheel", wheel)]:
    report(tag, rows)

print("\n== cross-group (everything that can be on screen during a level)")
live = []
for name in ["game_raze_dev101.xml", "weapons_raze_dev101.xml", "inventory_raze_dev101.xml",
             "custom_buttons_0_raze_dev101.xml", "weaponwheel_raze_dev101.xml"]:
    for n, r, h in FILES[name]:
        if not h:
            live.append((name.split("_")[0] + ":" + n, r[0] * W, r[1] * W, r[2] * H, r[3] * H))
hits = 0
for i in range(len(live)):
    for j in range(i + 1, len(live)):
        a, c = live[i], live[j]
        if a[0].split(":")[1] in BACKGROUNDS or c[0].split(":")[1] in BACKGROUNDS:
            continue
        ox = min(a[2], c[2]) - max(a[1], c[1])
        oy = min(a[4], c[4]) - max(a[3], c[3])
        if ox > 1 and oy > 1:
            hits += 1
            print("  OVERLAP %s x %s  %dx%d px" % (a[0], c[0], ox, oy))
print("  live widgets across groups: %d, overlaps: %d" % (len(live), hits))

for name, rows in FILES.items():
    with open(os.path.join(OUT, name), "w", newline="\n") as f:
        f.write('<?xml version="1.0" ?>\n')
        for n, r, h in rows:
            f.write(emit(n, r, h) + "\n")

with open(os.path.join(OUT, "touch_settings101.xml"), "w", newline="\n") as f:
    f.write(SETTINGS)

print("\nwrote " + ", ".join(sorted(FILES)) + ", touch_settings101.xml")

"""Calibration build: replace two widget textures with solid quads and give those widgets rects
whose width/height ratio differs from their defaults.

MobileTouchControls::Button::loadXML only refills controlPos - it never calls updateSize(), so the
drawn quad may keep the built-in default size while the tap area follows the XML. Measuring the two
quads on the title screen tells us which space actually drives the art, and what the aspect fix
does to it, before any of the shipped layouts are finalised.
"""
from PIL import Image, ImageDraw

A = "G:/blood_port/bloodtouch/assets/"


def quad(path, fill):
    im = Image.new("RGBA", (256, 256), fill + (255,))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 255, 255], outline=(0, 0, 0, 255), width=6)
    d.line([128, 6, 128, 250], fill=(0, 0, 0, 255), width=4)      # vertical mid-line: shows mirroring
    im.save(path)


quad(A + "enter.png", (255, 255, 255))
quad(A + "keyboard.png", (255, 0, 255))

# left, right, top, bottom as fractions of the screen.
rows = {
    "back":            (0.0, 0.0769231, 0.0, 0.125),
    "keyboard":        (0.55, 0.75, 0.10, 0.25),
    "load_save_touch": (0.769231, 0.846154, 0.0, 0.125),
    "gyro":            (0.923077, 1.0, 0.0, 0.125),
    "gamepad":         (0.846154, 0.923077, 0.0, 0.125),
    "slide_mouse":     (0.923077, 1.0, 0.1875, 0.6875),
    "up_arrow":        (0.0769231, 0.153846, 0.625, 0.75),
    "down_arrow":      (0.0769231, 0.153846, 0.875, 1.0),
    "left_arrow":      (0.0, 0.0769231, 0.75, 0.875),
    "right_arrow":     (0.153846, 0.230769, 0.75, 0.875),
    "enter":           (0.30, 0.40, 0.10, 0.60),
}
hidden = {"gamepad"}

with open(A + "menu.xml", "w", newline="\n") as f:
    f.write('<?xml version="1.0" ?>\n')
    for name, (l, r, t, b) in rows.items():
        f.write('<%s enabled="1" hidden="%d" left="%.9g" right="%.9g" top="%.9g" bottom="%.9g" color="0" />\n'
                % (name, 1 if name in hidden else 0, l, r, t, b))

print("expected on 2340x1080: enter quad 234x540 at (702,108); keyboard quad 468x162 at (1287,108)")

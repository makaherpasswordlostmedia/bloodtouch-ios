"""Круглая кнопка ENTER в стиле стоковых круглых виджетов Zeta (use.png/shoot.png - тёмный
полупрозрачный диск со светлым кольцом), чтобы виджет enter в menu.xml выглядел круглым:
картинка виджета берётся по имени, а форму поменять в XML нельзя (атрибутов image там нет)."""
from PIL import Image, ImageDraw, ImageFont

S = 256
im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)

DISC = (43, 43, 43, 187)
RING = (120, 120, 120, 205)
EDGE = (200, 200, 200, 150)
TEXT = (235, 235, 235, 200)

d.ellipse([8, 8, S - 8, S - 8], fill=DISC, outline=EDGE, width=3)
d.ellipse([26, 26, S - 26, S - 26], outline=RING, width=4)
d.ellipse([44, 44, S - 44, S - 44], outline=(70, 70, 70, 190), width=2)

font = None
for path in ["C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/tahoma.ttf", "C:/Windows/Fonts/arial.ttf"]:
    try:
        font = ImageFont.truetype(path, 46)
        break
    except OSError:
        continue
if font is None:
    font = ImageFont.load_default()

box = d.textbbox((0, 0), "ENTER", font=font)
d.text(((S - (box[2] - box[0])) / 2 - box[0], (S - (box[3] - box[1])) / 2 - box[1]), "ENTER", font=font, fill=TEXT)

im.save("G:/blood_port/bloodtouch/assets/enter.png")
preview = Image.new("RGBA", (S, S), (40, 40, 40, 255))
preview.paste(im, (0, 0), im)
preview.save("G:/blood_port/work/enter_round_preview.png")
print("enter.png записан")

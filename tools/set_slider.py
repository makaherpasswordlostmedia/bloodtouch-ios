"""Rewrite the slide_mouse line in the shipped menu.xml with one experiment's attributes.

The engine keeps a built-in rect for any tag the XML omits, so dropping the widget from the file
does nothing - the only levers are its attributes, which is what this varies.
"""
import re
import sys

MENU = "G:/blood_port/bloodtouch/assets/menu.xml"
hidden, left, right, top, bottom, enabled = sys.argv[1:7]
text = open(MENU, encoding="utf-8").read()
line = ('<slide_mouse enabled="%s" hidden="%s" left="%s" right="%s" top="%s" bottom="%s" color="0" />'
        % (enabled, hidden, left, right, top, bottom))
if re.search(r"<slide_mouse ", text):
    text = re.sub(r"<slide_mouse [^>]*/>", line, text)
else:
    text = text.rstrip("\n") + "\n" + line + "\n"
open(MENU, "w", encoding="utf-8", newline="\n").write(text)
print(line)

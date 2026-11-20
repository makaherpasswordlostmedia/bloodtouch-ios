#!/bin/sh
# Звук катсцен тоже берём из HD-дорожек Refreshed Supply: .ogv -> .ogg (128k стерео),
# имя - как у оригинального CutWav, чтобы править в ini только расширение.
FF=/cygdrive/g/ugly-mix/ffmpeg.exe
RS="/cygdrive/g/Blood_gog/blood_refreshed_supply"
PK=/cygdrive/g/blood_port/pack/BLOOD
for n in 1 2 3 4 5 6; do
    "$FF" -hide_banner -loglevel error -y -i "$RS/movie/cs$n.ogv" -vn -c:a libvorbis -qscale:a 5 "$PK/movie/cs${n}822m.ogg" && echo "    movie/cs${n}822m.ogg"
done
"$FF" -hide_banner -loglevel error -y -i "$RS/addons/Cryptic Passage/cryptic.ogv" -vn -c:a libvorbis -qscale:a 5 "$PK/movie/cryptic.ogg" && echo "    movie/cryptic.ogg"
for v in dw1a dw2a dw3a dw4a dw4b; do
    "$FF" -hide_banner -loglevel error -y -i "$RS/addons/Death Wish/$v.ogv" -vn -c:a libvorbis -qscale:a 5 "$PK/addons/deathwish/$v.ogg" \
        && rm -f "$PK/addons/deathwish/$v.wav" && echo "    deathwish/$v.ogg"
done
for v in ep1 ep2; do
    "$FF" -hide_banner -loglevel error -y -i "$RS/addons/MARROW/$v.ogv" -vn -c:a libvorbis -qscale:a 5 "$PK/addons/marrow/$v.ogg" \
        && rm -f "$PK/addons/marrow/$v.wav" && echo "    marrow/$v.ogg"
done
echo ГОТОВО

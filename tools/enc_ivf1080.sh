#!/bin/sh
# HD-ролики Refreshed Supply (Theora 1920x1080 24fps) -> IVF/VP8 1920x1080.
# 1080p выбрано не «для красоты»: CalcFullscreenScale() при ScaleToFit43 для 16:9
# берёт FSMode_ScaleToFit -> rect.height = весь экран, а ширина = 2340*16/9/2.1667 = 1920,
# то есть на 2340x1080 ролик 1920x1080 ложится пиксель в пиксель, а 1280x720 растягивается в 1.5 раза.
FF=/cygdrive/g/ugly-mix/ffmpeg.exe
SRC="/cygdrive/g/Blood_gog/blood_refreshed_supply/movie"
OUT=/cygdrive/g/blood_port/pack/BLOOD/movie
V="-vf scale=1920:1080:flags=lanczos,setsar=1 -r 24 -c:v libvpx -pix_fmt yuv420p -b:v 0 -crf 6 -deadline good -cpu-used 1 -row-mt 1 -f ivf"
for n in 1 2 3 4 5 6; do
    "$FF" -hide_banner -loglevel error -y -i "$SRC/cs$n.ogv" -an $V "$OUT/cs$n.ivf" && echo "    cs$n.ivf"
done
"$FF" -hide_banner -loglevel error -y -i "/cygdrive/g/Blood_gog/blood_refreshed_supply/addons/Cryptic Passage/cryptic.ogv" -an $V "$OUT/cryptic.ivf" && echo "    cryptic.ivf"
DW="/cygdrive/g/Blood_gog/blood_refreshed_supply/addons/Death Wish"
for v in dw1a dw2a dw3a dw4a dw4b; do
    "$FF" -hide_banner -loglevel error -y -i "$DW/$v.ogv" $V "/cygdrive/g/blood_port/pack/BLOOD/addons/deathwish/$v.ivf" && echo "    deathwish/$v.ivf"
done
MR="/cygdrive/g/Blood_gog/blood_refreshed_supply/addons/MARROW"
for v in ep1 ep2; do
    "$FF" -hide_banner -loglevel error -y -i "$MR/$v.ogv" $V "/cygdrive/g/blood_port/pack/BLOOD/addons/marrow/$v.ivf" && echo "    marrow/$v.ivf"
done
echo "ВСЁ"

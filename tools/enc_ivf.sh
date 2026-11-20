#!/bin/sh
# HD-ролики Refreshed Supply (Theora 1080p) -> IVF/VP8 720p, который Raze берёт раньше .smk
FF=/cygdrive/g/ugly-mix/ffmpeg.exe
SRC="/cygdrive/g/Blood_gog/blood_refreshed_supply/movie"
OUT=/cygdrive/g/blood_port/pack/BLOOD/movie
for n in 1 2 3 4 5 6; do
    "$FF" -hide_banner -loglevel error -y -i "$SRC/cs$n.ogv" -an \
        -vf "scale=1280:720:flags=lanczos,setsar=1" -r 24 \
        -c:v libvpx -pix_fmt yuv420p -b:v 0 -crf 6 -deadline good -cpu-used 1 -row-mt 1 \
        -f ivf "$OUT/cs$n.ivf" && echo "    cs$n.ivf готов"
done
# Cryptic Passage: тот же ролик лежит рядом с ini аддона, имя в ini - cryptic.smk
"$FF" -hide_banner -loglevel error -y -i "/cygdrive/g/Blood_gog/blood_refreshed_supply/addons/Cryptic Passage/cryptic.ogv" -an \
    -vf "scale=1280:720:flags=lanczos,setsar=1" -r 24 -c:v libvpx -pix_fmt yuv420p -b:v 0 -crf 6 -deadline good -cpu-used 1 -row-mt 1 \
    -f ivf "$OUT/cryptic.ivf" && echo "    cryptic.ivf готов"
# Death Wish и MARROW: .smk у них нет вовсе, берём видео + звук из их .ogv
DW="/cygdrive/g/Blood_gog/blood_refreshed_supply/addons/Death Wish"
for v in dw1a dw2a dw3a dw4a dw4b; do
    "$FF" -hide_banner -loglevel error -y -i "$DW/$v.ogv" \
        -vf "scale=1280:720:flags=lanczos,setsar=1" -r 24 -an -c:v libvpx -pix_fmt yuv420p -b:v 0 -crf 6 -deadline good -cpu-used 1 -row-mt 1 -f ivf "/cygdrive/g/blood_port/pack/BLOOD/addons/deathwish/$v.ivf" \
        && "$FF" -hide_banner -loglevel error -y -i "$DW/$v.ogv" -vn -acodec pcm_s16le -ar 22050 -ac 1 "/cygdrive/g/blood_port/pack/BLOOD/addons/deathwish/$v.wav" \
        && echo "    deathwish/$v готов"
done
MR="/cygdrive/g/Blood_gog/blood_refreshed_supply/addons/MARROW"
for v in ep1 ep2; do
    "$FF" -hide_banner -loglevel error -y -i "$MR/$v.ogv" \
        -vf "scale=1280:720:flags=lanczos,setsar=1" -r 24 -an -c:v libvpx -pix_fmt yuv420p -b:v 0 -crf 6 -deadline good -cpu-used 1 -row-mt 1 -f ivf "/cygdrive/g/blood_port/pack/BLOOD/addons/marrow/$v.ivf" \
        && "$FF" -hide_banner -loglevel error -y -i "$MR/$v.ogv" -vn -acodec pcm_s16le -ar 22050 -ac 1 "/cygdrive/g/blood_port/pack/BLOOD/addons/marrow/$v.wav" \
        && echo "    marrow/$v готов"
done
echo "ВСЁ"

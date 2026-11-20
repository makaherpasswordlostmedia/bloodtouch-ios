#!/bin/sh
# ВСЕ movie из Blood - Refreshed Supply -> IVF/VP8 в нативном разрешении и с нативным fps.
# Пути только в Windows-виде: ffmpeg.exe нативный, /cygdrive он не понимает.
# Масштабирования нет: nsecsperframe берётся из timebase IVF-заголовка (ReadIVFHeader),
# а 1920x1080 на экране 2340x1080 ложится пиксель в пиксель (ScaleToFit43 -> ScaleToFit по высоте).
FF=G:/ugly-mix/ffmpeg.exe
RS=G:/Blood_gog/blood_refreshed_supply
P=G:/blood_port/pack/BLOOD
one() {
    "$FF" -hide_banner -loglevel error -y -i "$1" \
        -vf "setsar=1" -an -c:v libvpx -pix_fmt yuv420p -b:v 0 -crf 6 \
        -deadline good -cpu-used 1 -row-mt 1 -f ivf "$2" \
      && "$FF" -hide_banner -loglevel error -y -i "$1" -vn -c:a libvorbis -q:a 4 "$3" \
      && echo "  OK $2"
}
mkdir -p /cygdrive/g/blood_port/pack/BLOOD/movie/original
for n in 1 2 3 4 5 6; do one "$RS/movie/cs$n.ogv" "$P/movie/cs$n.ivf" "$P/movie/cs$n.ogg"; done
one "$RS/movie/Atari.ogv"     "$P/movie/atari.ivf"     "$P/movie/atari.ogg"
one "$RS/movie/Kex.ogv"       "$P/movie/kex.ivf"       "$P/movie/kex.ogg"
one "$RS/movie/NightDive.ogv" "$P/movie/nightdive.ivf" "$P/movie/nightdive.ogg"
for n in 1 2 3 4 5 6; do one "$RS/movie/original/cs$n.ogv" "$P/movie/original/cs$n.ivf" "$P/movie/original/cs$n.ogg"; done
one "$RS/addons/Cryptic Passage/cryptic.ogv" "$P/movie/cryptic.ivf" "$P/movie/cryptic.ogg"
for v in dw1a dw2a dw3a dw4a dw4b; do one "$RS/addons/Death Wish/$v.ogv" "$P/addons/deathwish/$v.ivf" "$P/addons/deathwish/$v.ogg"; done
for v in ep1 ep2; do one "$RS/addons/MARROW/$v.ogv" "$P/addons/marrow/$v.ivf" "$P/addons/marrow/$v.ogg"; done
echo "ГОТОВО"

#!/bin/sh
# Гонит в телефон только те видеоролики/саундтреки катсцен, которых там нет или чей размер не совпал.
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
P=/cygdrive/g/blood_port/pack/BLOOD
D=/storage/emulated/0/BloodTouch/BLOOD
cd "$P" || exit 1
n=0
for f in $(find movie addons -type f \( -name '*.ivf' -o -name '*.ogg' \) | sort); do
    size=$(stat -c %s "$f")
    remote=$(adb shell "wc -c < $D/$f 2>/dev/null || echo -1" | tr -d '\r')
    if [ "$remote" != "$size" ]; then
        adb shell "mkdir -p $D/$(dirname "$f")" >/dev/null 2>&1
        adb push "$f" "$D/$f" >/dev/null 2>&1 && echo "  push $f ($size)" && n=$((n+1))
    fi
done
echo "закачано файлов: $n"

#!/bin/sh
# Докладывает дополнения Death Wish и MARROW в /sdcard/BloodTouch/BLOOD/addons.
# Ролики (.ogv), субтитры и .kpf не качаем - Raze их не читает.
ADB=/cygdrive/g/Android/Sdk/platform-tools/adb.exe
SRC="/cygdrive/g/Blood_gog/blood_refreshed_supply/addons"
DEST=/storage/emulated/0/BloodTouch/BLOOD/addons

"$ADB" wait-for-device

push_addon()
{
    name="$1"
    destname="$2"
    echo "=== $name -> $DEST/$destname"
    "$ADB" shell mkdir -p "$DEST/$destname"
    cd "$SRC/$name" || return 1
    for f in *; do
        case "$f" in
            *.ogv|*.srt|*.kpf|original) continue ;;
        esac
        "$ADB" push "$f" "$DEST/$destname/" >/dev/null
        echo "    $f"
    done
}

push_addon "Death Wish" "deathwish"
push_addon "MARROW" "marrow"

echo "Готово. В лаунчере дополнения появятся в списке сами (папки в BLOOD/addons)."

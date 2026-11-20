#!/bin/sh
# Ставит Zeta Touch и пак Blood на телефон. Нужен подключённый USB-телефон с ADB-отладкой.
set -e
ADB=/cygdrive/g/Android/Sdk/platform-tools/adb.exe
ROOT=/cygdrive/g/blood_port
PKG=com.opentouchgaming.razetouch
DEST=/storage/emulated/0/Android/data/$PKG/files

"$ADB" wait-for-device
"$ADB" shell getprop ro.product.model
echo "[1/2] установка APK"
"$ADB" install -r -g "$ROOT/zeta-touch-3.4.apk"

echo "[2/2] данные Blood (BLOOD + NBLOOD)"
"$ADB" shell mkdir -p "$DEST"
"$ADB" push "$ROOT/pack/BLOOD" "$DEST/"
"$ADB" push "$ROOT/pack/NBLOOD" "$DEST/"

echo "Готово. В лаунчере: RAZE -> BLOOD (движок raze_dev), либо NBLOOD - там работают ролики."

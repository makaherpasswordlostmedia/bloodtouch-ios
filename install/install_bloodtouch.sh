#!/bin/sh
# Ставит bloodtouch.apk и, если нужно, пак Blood на телефон.
# Нужен подключённый телефон с ADB-отладкой.
set -e
ADB=/cygdrive/g/Android/Sdk/platform-tools/adb.exe
ROOT=/cygdrive/g/blood_port
APK=$ROOT/bloodtouch/build/bloodtouch.apk
DEST=/storage/emulated/0/BloodTouch
OLD=/storage/emulated/0/OpenTouch/Zeta

# Git Bash переписывает любой аргумент, начинающийся с '/', поэтому "adb shell mkdir -p
# /storage/..." доходил как "C:/Program Files/Git/storage/...", а локальные /cygdrive-пути
# adb.exe вообще не понимает. Отключаю автоконверсию и перевожу локальные пути сам.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'
if command -v cygpath >/dev/null 2>&1; then
    # -m даёт "G:/blood_port/..." - adb.exe такой путь принимает и его можно дописывать слэшами
    APK=$(cygpath -m "$APK")
    PACK=$(cygpath -m "$ROOT/pack/BLOOD")
    LOGCAT=$(cygpath -m "$ROOT/work/phone_logcat.txt")
    ENGINE_LOG=$(cygpath -m "$ROOT/work/phone_raze.txt")
else
    PACK=$ROOT/pack/BLOOD
    LOGCAT=$ROOT/work/phone_logcat.txt
    ENGINE_LOG=$ROOT/work/phone_raze.txt
fi

"$ADB" wait-for-device
echo "телефон: $("$ADB" shell getprop ro.product.model | tr -d '\r')"

echo "[1/3] APK"
if ! "$ADB" install -r -g "$APK"; then
    # Flyme на MEIZU 21 отвечает INSTALL_FAILED_USER_RESTRICTED на любую установку с компьютера.
    # Кладу APK в «Загрузки» - оттуда он ставится руками одним тапом, остальной скрипт не нужен.
    echo "    adb install запрещён политикой оболочки - кладу APK в Загрузки телефона"
    "$ADB" push "$APK" /storage/emulated/0/Download/bloodtouch.apk
    echo "    готово: Телефон -> Загрузки -> bloodtouch.apk -> Установить, затем повторить этот скрипт"
    exit 1
fi
# -g не выдаёт MANAGE_EXTERNAL_STORAGE, а без неё на Android 11+ нет доступа к /sdcard/BloodTouch
"$ADB" shell appops set com.bloodtouch MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || echo "    appops не сработал - лаунчер сам предложит открыть настройку"

echo "[2/3] данные Blood -> $DEST/BLOOD"
if ! "$ADB" shell "test -f $DEST/BLOOD/BLOOD.RFF" >/dev/null 2>&1; then
    # пак уже лежал в старом корне Zeta Touch - копирую его внутри телефона, чтобы не качать 474 МБ по USB
    if "$ADB" shell "test -f $OLD/BLOOD/BLOOD.RFF" >/dev/null 2>&1; then
        echo "    копирую пак со старого пути $OLD/BLOOD (Zeta Touch остаётся рабочим)"
        "$ADB" shell "mkdir -p $DEST && cp -r $OLD/BLOOD $DEST/"
    else
        "$ADB" shell mkdir -p "$DEST/BLOOD"
        "$ADB" push "$PACK/." "$DEST/BLOOD/"
    fi
fi

# со старого пути катсцены могли не прийти, а без них blood.ini молча не играет ролики
if ! "$ADB" shell "test -f $DEST/BLOOD/movie/cs1.smk" >/dev/null 2>&1; then
    echo "    докачиваю movie/"
    "$ADB" shell mkdir -p "$DEST/BLOOD/movie"
    "$ADB" push "$PACK/movie/." "$DEST/BLOOD/movie/"
else
    echo "    пак на месте"
fi

# HD из Blood - Refreshed Supply: .ivf (VP8 720p) вместо Smacker, озвучка катсцен .ogg
# и саундтрек SC-55 в music/ (Raze берёт его по mus_extendedlookup вместо MIDI из SOUNDS.RFF)
echo "[2b] HD-ассеты Refreshed Supply"
"$ADB" shell mkdir -p "$DEST/BLOOD/music" >/dev/null 2>&1
for f in "$ROOT"/pack/BLOOD/movie/*.ivf "$ROOT"/pack/BLOOD/movie/*.ogg "$ROOT"/pack/BLOOD/music/*.ogg; do
    [ -f "$f" ] || continue
    rel=${f#"$ROOT"/pack/BLOOD/}
    if ! "$ADB" shell "test -f $DEST/BLOOD/$rel" >/dev/null 2>&1; then
        echo "    докачиваю $rel"
        "$ADB" push "$(cygpath -m "$f" 2>/dev/null || echo "$f")" "$DEST/BLOOD/$rel" >/dev/null
    fi
done

# мелкие конфиги правились уже после сборки пака (литеральные заголовки вместо KEX-токенов),
# поэтому всегда перезаписываю их поверх, независимо от того, качался ли пак целиком
for f in blood.ini surface.dat addons/cryptic/CRYPTIC.INI addons/deathwish/dw.ini addons/marrow/MARROW.ini; do
    if [ -f "$ROOT/pack/BLOOD/$f" ]; then
        "$ADB" push "$PACK/$f" "$DEST/BLOOD/$f" >/dev/null
        echo "    обновлён $f"
    fi
done

echo "[3/3] запуск и сбор диагностики"
LOG=/storage/emulated/0/BloodTouch/user_files/logs/blood_raze.txt
"$ADB" shell am start -n com.bloodtouch/.MainActivity >/dev/null
sleep 25

if "$ADB" shell "pidof -s 'com.bloodtouch:game'" >/dev/null 2>&1; then
    echo "    движок жив через 25 с - игра запустилась, смотри телефон"
else
    echo "    процесса :game нет - движок упал, тащу журнал"
fi

"$ADB" logcat -d -v brief > "$LOGCAT" 2>/dev/null
if "$ADB" pull "$LOG" "$ENGINE_LOG" >/dev/null 2>&1; then
    echo
    echo "---- engine log, последние 40 строк ----"
    tail -40 "$ROOT/work/phone_raze.txt"
    echo "----------------------------------------"
    echo "полный журнал: $ROOT/work/phone_raze.txt, logcat: $ROOT/work/phone_logcat.txt"
else
    echo "движок не создал $LOG - умер до открытия журнала; лаунчер покажет это текстом на экране,"
    echo "детали в $ROOT/work/phone_logcat.txt"
fi

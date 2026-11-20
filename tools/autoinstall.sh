#!/bin/sh
# Waits for the phone, installs the reverted launcher, starts the game and pulls back whatever the
# stock touch overlay dumped - so trimming its buttons needs nothing from anyone.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

ROOT=/cygdrive/g/blood_port
APK=$(cygpath -m "$ROOT/bloodtouch.apk")
WORK=$ROOT/work
LAYOUTS=/storage/emulated/0/BloodTouch/user_files/touch_layouts

log() { echo "$(date +%H:%M:%S) $*"; }

if ! adb get-state 2>/dev/null | grep -q '^device'; then
    log "waiting for the phone ..."
    adb wait-for-device
    sleep 15
fi

log "device seen: $(adb devices | sed -n 2p)"
adb install -r -g "$APK" 2>&1 | tail -2

log "starting the launcher"
adb shell am force-stop com.bloodtouch
adb shell am start -n com.bloodtouch/.MainActivity >/dev/null

log "waiting 50 s for the engine and the layout dump"
sleep 50

PID=$(adb shell pidof -s "com.bloodtouch:game")
log "engine pid: ${PID:-none}"
adb exec-out screencap -p > "$WORK/stock_overlay.png"
log "screenshot: $WORK/stock_overlay.png"

adb shell "ls -lR $LAYOUTS" > "$WORK/stock_layouts_ls.txt" 2>&1
adb pull "$LAYOUTS" "$WORK/stock_layouts" >/dev/null 2>&1 \
    && log "layout dumped: $WORK/stock_layouts" \
    || log "no layout dir yet - the dump did not run"

adb pull /storage/emulated/0/BloodTouch/user_files/logs/blood_raze.txt "$WORK/stock_raze.txt" >/dev/null 2>&1
adb logcat -d -v brief > "$WORK/stock_logcat.txt" 2>/dev/null
log done

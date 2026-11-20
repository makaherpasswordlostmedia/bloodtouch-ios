#!/bin/sh
# Which attribute actually makes the brightness strip disappear. Three candidates, each built into a
# real APK because the launcher re-copies user_files/touch_layouts from its own assets on every
# start, so pushing a file by hand would be stomped before the engine reads it.
export MSYS_NO_PATHCONV=1
W=G:/blood_port/work
B=G:/blood_port/bloodtouch
M=$B/assets/menu.xml
R() { echo "[$(date +%H:%M:%S)] $*" >> $W/slider_report.txt; }

run_variant() {
    NAME=$1; EN=$2; HI=$3; L=$4; RT=$5; T=$6; BO=$7
    python $W/set_slider.py "$HI" "$L" "$RT" "$T" "$BO" "$EN" > /dev/null
    R "variant $NAME: $(grep slide_mouse $M)"
    ( cd $B && sh build.sh > $W/slider_build_$NAME.log 2>&1 )
    if [ $? -ne 0 ] || [ ! -f $B/build/bloodtouch.apk ]; then
        R "build failed for $NAME - see slider_build_$NAME.log"
        return
    fi
    cp -f $B/build/bloodtouch.apk G:/blood_port/bloodtouch.apk
    R "installed md5: $(md5sum G:/blood_port/bloodtouch.apk | cut -c1-32)"
    adb install -r -g G:/blood_port/bloodtouch.apk >> $W/slider_report.txt 2>&1
    adb shell am force-stop com.bloodtouch
    adb shell am start -n com.bloodtouch/.MainActivity > /dev/null
    sleep 8
    python $W/probe_tap.py play >> $W/slider_report.txt 2>&1
    R "waiting 70 s for the engine main menu"
    sleep 70
    adb exec-out screencap -p > $W/slider_$NAME.png
    adb pull /storage/emulated/0/BloodTouch/user_files/touch_layouts/menu.xml $W/slider_$NAME.xml > /dev/null 2>&1
    R "saved slider_$NAME.png ($(ls -la $W/slider_$NAME.png | awk '{print $5}') B), device line: $(grep slide_mouse $W/slider_$NAME.xml | tr -d '\r')"
}

: > $W/slider_report.txt
adb get-state >/dev/null 2>&1 || adb wait-for-device
python $W/probe_tap.py dismiss >> $W/slider_report.txt 2>&1

# 1: the semantically right switch - disabled rather than hidden.
run_variant enabled0 0 1 0.923077 1 0.1875 0.6875
# 2: parked off the left edge; the rect keeps a normal size so no divisor goes to zero.
run_variant negoff   1 1 -0.25 -0.15 0.1875 0.6875
# 3: degenerate 3x1 px corner, worst case if the engine clamps coordinates.
run_variant tiny     1 1 0.9985 1 0 0.0009

R "done"

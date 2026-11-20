#!/bin/sh
# One self-driving run so nothing has to be typed on the phone.
# 1) wait for the USB to come back, 2) probe the remaining hang candidates on the build that is
# installed NOW (its layout still carries the brightness strip, which step 3 removes),
# 3) install the strip-free build and leave a main-menu screenshot behind for inspection.
export MSYS_NO_PATHCONV=1
W=G:/blood_port/work
LOG=$W/wait_report.txt
: > $LOG
S() { echo "[$(date +%H:%M:%S)] $*" >> $LOG; }

S "waiting for the device"
for i in $(seq 1 1080); do
    adb get-state >/dev/null 2>&1 && break
    [ $((i % 60)) -eq 0 ] && adb start-server > /dev/null 2>&1
    sleep 10
done
if ! adb get-state >/dev/null 2>&1; then
    S "device never came back after three hours"
    exit 1
fi
S "device is up"
sleep 8
adb shell input keyevent 224 > /dev/null 2>&1
sleep 2
FOCUS=$(python $W/probe_tap.py focus)
S "focus after wake: $FOCUS"
case "$FOCUS" in
    *keyguard*|*Keyguard*|*launcher*) S "screen looks locked or on the home screen - the run will still go, but a locked display makes every tap miss"; sleep 60 ;;
esac
python $W/probe_tap.py dismiss >> $LOG 2>&1

sh $W/h4.sh
S "h4 finished"

S "slider attribute experiment"
sh $W/slider_test.sh
S "done"

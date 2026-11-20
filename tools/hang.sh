#!/bin/sh
# One waiter, no manual runs: installs the build that carries the extracted touch layout, walks the
# Raze main menu into Options, then presses BACK the way he says it freezes - both the overlay arrow
# and the Android system key, in separate passes - and records WHY: whether the game process is still
# there, whether its threads are spinning or parked, and whether a key press still changes pixels.
# Liveness is measured by screenshot diff because dumpsys gfxinfo only counts the Java view tree, so
# the engine's own GL swaps never show up there (that is what made the last run read a live menu as 0
# frames). Artefacts land in work/hang_* so the run reads without the phone attached.
export MSYS_NO_PATHCONV=1
W=G:/blood_port/work
P=/storage/emulated/0/BloodTouch
L=$P/user_files/touch_layouts
R() { echo "[$(date +%H:%M:%S)] $*" >> $W/hang_report.txt; }
SHOT() { adb exec-out screencap -p > $W/hang_$1.png; }
FOCUS() { R "focus: $(python $W/probe_tap.py focus)"; }
# The cursor has to move when a menu key lands, so 'tap down_arrow and diff two shots' says whether
# the engine is still reacting; a static screen would prove nothing on its own.  Tapping up_arrow
# afterwards puts the cursor back so the pass stays reproducible.
LIVE() {
    adb exec-out screencap -p > $W/hang_a.png
    python $W/probe_tap.py menu down_arrow > /dev/null 2>&1
    sleep 2
    adb exec-out screencap -p > $W/hang_b.png
    python $W/probe_tap.py menu up_arrow > /dev/null 2>&1
    sleep 1
    D=$(python $W/alive.py $W/hang_a.png $W/hang_b.png)
    R "alive[$1]: $D"
    case "$D" in "changed 0 of "*) return 1;; esac
    return 0
}
GAMEPID() { adb shell "pidof -s 'com.bloodtouch:game'" | tr -dc '0-9'; }
DUMP() {
    T=$(GAMEPID)
    R "dump: game pid=${T:-gone}"
    [ -z "$T" ] && return
    adb shell "top -H -b -n 1 -p $T" > $W/hang_threads.txt 2>&1
    adb shell "su -c 'for t in /proc/$T/task/*; do echo \$t \$(cat \$t/stat) wchan=\$(cat \$t/wchan); done'" \
        > $W/hang_wchan.txt 2>&1
    adb shell "su -c 'for t in /proc/$T/task/*; do echo == \$t; cat \$t/syscall; done'" \
        > $W/hang_syscall.txt 2>&1
    # debuggerd -b gives real native stacks for a debuggable app even without root, which is the
    # difference between 'the main thread waits on a futex' and a guess.
    adb shell "su -c 'debuggerd -b $T'" > $W/hang_backtrace.txt 2>&1
    [ -s $W/hang_backtrace.txt ] || adb shell "debuggerd -b $T" > $W/hang_backtrace.txt 2>&1
    adb logcat -d -t 600 > $W/hang_logcat.txt 2>&1
    adb logcat -b events -d -t 300 > $W/hang_events.txt 2>&1
    adb shell dumpsys activity > $W/hang_activity.txt 2>&1
    adb shell dumpsys dropbox > $W/hang_dropbox.txt 2>&1
    adb pull $P/user_files/logs/blood_raze.txt $W/hang_engine_log.txt > /dev/null 2>&1
}

: > $W/hang_report.txt
rm -f $W/probe_dims.json
R "waiting for device"
adb wait-for-device
sleep 15
R "device up: $(adb devices | sed -n 2p)"
python $W/probe_tap.py dismiss >> $W/hang_report.txt 2>&1

adb install -r -g G:/blood_port/bloodtouch.apk >> $W/hang_report.txt 2>&1
adb shell appops set com.bloodtouch MANAGE_EXTERNAL_STORAGE allow
R "installed apk md5=$(md5sum G:/blood_port/bloodtouch.apk | cut -c1-8)"

# ---------------------------------------------------------------- pass 1: overlay BACK in Options
adb shell am force-stop com.bloodtouch
adb shell am start -n com.bloodtouch/.MainActivity > /dev/null
sleep 6
python $W/probe_tap.py dismiss >> $W/hang_report.txt 2>&1
python $W/probe_tap.py play >> $W/hang_report.txt 2>&1
R "waiting 70 s for the engine to reach its main menu"
sleep 70
python $W/probe_tap.py dismiss >> $W/hang_report.txt 2>&1
FOCUS
SHOT menu
adb pull $L/menu.xml $W/hang_device_menu.xml > /dev/null 2>&1
R "layouts on device: $(adb shell ls $L | tr -d '\r' | tr '\n' ' ')"
R "device menu.xml slide_mouse line: $(grep -o '<slide_mouse[^>]*>' $W/hang_device_menu.xml)"
LIVE menu-idle
# OPTIONS is the second item of the Blood main menu, so one cursor step then ENTER.
python $W/probe_tap.py menu down_arrow >> $W/hang_report.txt 2>&1
sleep 2
python $W/probe_tap.py menu enter >> $W/hang_report.txt 2>&1
sleep 6
SHOT options
LIVE options
python $W/probe_tap.py menu back >> $W/hang_report.txt 2>&1
sleep 5
SHOT after_back
if LIVE after_overlay_back; then
    R "overlay back: engine still reacts - no freeze this pass"
else
    R "FROZEN after the overlay back button - capturing why"
    DUMP
fi

# ---------------------------------------------------------------- pass 2: Android system BACK
adb shell am force-stop com.bloodtouch
adb shell am start -n com.bloodtouch/.MainActivity > /dev/null
sleep 6
python $W/probe_tap.py play >> $W/hang_report.txt 2>&1
sleep 70
python $W/probe_tap.py dismiss >> $W/hang_report.txt 2>&1
FOCUS
LIVE menu-again
adb shell input keyevent 4
sleep 5
SHOT after_sysback
FOCUS
if LIVE after_system_back; then
    R "system BACK: engine still reacts - no freeze this pass"
else
    R "FROZEN after the Android system BACK key - capturing why"
    DUMP
fi
R "done"

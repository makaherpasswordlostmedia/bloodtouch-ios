#!/bin/sh
# Reproduction of 'back in the main menu / Options freezes the game', driven ONLY by key events: the
# previous pass tapped widget rects, but `input tap` works in the panel's natural (portrait) space
# while the game runs landscape, so every tap landed somewhere else and the run proved nothing.
# Each step leaves a numbered screenshot, and liveness is read from CPU per thread as well as from the
# pictures: a parked main thread with a static screen is a deadlock, a 100% one is a spin.
export MSYS_NO_PATHCONV=1
W=G:/blood_port/work
P=/storage/emulated/0/BloodTouch
R() { echo "[$(date +%H:%M:%S)] $*" >> $W/h2_report.txt; }
SHOT() { adb exec-out screencap -p > $W/h2_$1.png; }
KEY() { adb shell input keyevent "$1"; }
DIFF() { python $W/alive.py $W/h2_$1.png $W/h2_$2.png; }
GAMEPID() { adb shell "pidof -s 'com.bloodtouch:game'" | tr -dc '0-9'; }
STATE() {
    T=$(GAMEPID)
    R "state[$1]: pid=${T:-gone} focus=$(python $W/probe_tap.py focus | sed 's/.*u0 //;s/}//')"
    [ -z "$T" ] && return
    adb shell "top -H -b -n 1 -p $T" > $W/h2_top_$1.txt 2>&1
    R "state[$1]: threads=$(grep -c . $W/h2_top_$1.txt) top: $(sed -n '2,4p' $W/h2_top_$1.txt | tr -s ' ' | cut -c1-70 | tr '\n' ' | ')"
}
DUMP() {
    T=$(GAMEPID)
    R "dump: game pid=${T:-gone}"
    [ -z "$T" ] && return
    adb shell "su -c 'for t in /proc/$T/task/*; do echo \$t \$(cat \$t/stat) wchan=\$(cat \$t/wchan); done'" > $W/h2_wchan.txt 2>&1
    adb shell "su -c 'for t in /proc/$T/task/*; do echo == \$t; cat \$t/syscall; done'" > $W/h2_syscall.txt 2>&1
    adb shell "su -c 'debuggerd -b $T'" > $W/h2_backtrace.txt 2>&1
    [ -s $W/h2_backtrace.txt ] || adb shell "debuggerd -b $T" > $W/h2_backtrace.txt 2>&1
    adb logcat -d -t 500 > $W/h2_logcat.txt 2>&1
    adb logcat -b events -d -t 300 > $W/h2_events.txt 2>&1
    adb shell dumpsys activity > $W/h2_activity.txt 2>&1
    adb pull $P/user_files/logs/blood_raze.txt $W/h2_engine_log.txt > /dev/null 2>&1
}
# $1 = label, $2 = keyevent to press for the 'back' the user means
PASS() {
    P1=$1
    adb shell am force-stop com.bloodtouch
    adb shell am start -n com.bloodtouch/.MainActivity > /dev/null
    sleep 6
    python $W/probe_tap.py play >> $W/h2_report.txt 2>&1
    R "$P1: waiting 65 s for the engine main menu"
    sleep 65
    STATE $P1-boot
    SHOT ${P1}_00_menu
    KEY 20; sleep 2; SHOT ${P1}_01_down
    A="${P1}_00_menu"; B="${P1}_01_down"
    R "$P1: after DPAD_DOWN $A -> $B: $(DIFF $A $B)"
    KEY 66; sleep 5; SHOT ${P1}_02_entered
    KEY 20; sleep 2; SHOT ${P1}_03_sub_down
    A="${P1}_02_entered"; B="${P1}_03_sub_down"
    R "$P1: inside the submenu $A -> $B: $(DIFF $A $B)"
    KEY "$2"; sleep 5; SHOT ${P1}_04_after_back
    STATE $P1-afterback
    KEY 20; sleep 3; SHOT ${P1}_05_final
    A="${P1}_04_after_back"; B="${P1}_05_final"
    R "$P1: after back $A -> $B: $(DIFF $A $B)"
}

: > $W/h2_report.txt
adb get-state >/dev/null 2>&1 || adb wait-for-device
python $W/probe_tap.py dismiss >> $W/h2_report.txt 2>&1
PASS A 4
PASS B 111
R "done - compare the h2_*.png sequence by eye; A used the Android BACK key, B used ESCAPE"

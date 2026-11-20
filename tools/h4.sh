#!/bin/sh
# Fourth pass. The plain Options->'<' press is proven innocent (h2 key events, h3 overlay widget,
# three cycles each, render thread at 100% CPU the whole time), so this run walks what a finger
# actually hits around Options: the brightness strip, the layout editor, and the Java-side buttons.
# Liveness is the render thread's CPU delta (sdlstat.py), not pixels - a static menu changes none.
export MSYS_NO_PATHCONV=1
W=G:/blood_port/work
P=/storage/emulated/0/BloodTouch
R() { echo "[$(date +%H:%M:%S)] $*" >> $W/h4_report.txt; }
SHOT() { adb exec-out screencap -p > $W/h4_$1.png; }
TAP() { python $W/probe_tap.py menu "$1" 2>&1 | tail -1; }
KEY() { adb shell input keyevent "$1"; }
# LIVE <label>: 0.00 s of render-thread CPU over 3 s means the engine loop is blocked.
LIVE() {
    L=$(python $W/sdlstat.py 3)
    R "live[$1]: $L"
    case "$L" in
        *"delta=0.00s"*|*pid=gone*|*"thread"*gone*)
            R "HANG at $1 - capturing why"
            DUMP
            ;;
    esac
}
DUMP() {
    T=$(python $W/gamepid.py)
    R "dump: game pid=${T:-gone}"
    [ -z "$T" ] && return
    adb shell "su -c 'for t in /proc/$T/task/*; do echo \$t \$(cat \$t/comm) wchan=\$(cat \$t/wchan); done'" > $W/h4_wchan.txt 2>&1
    adb shell "su -c 'for t in /proc/$T/task/*; do echo == \$t \$(cat \$t/comm); cat \$t/syscall; done'" > $W/h4_syscall.txt 2>&1
    adb shell "su -c 'debuggerd -b $T'" > $W/h4_backtrace.txt 2>&1
    [ -s $W/h4_backtrace.txt ] || adb shell "debuggerd -b $T" > $W/h4_backtrace.txt 2>&1
    adb logcat -d -t 800 > $W/h4_logcat.txt 2>&1
    adb shell dumpsys activity > $W/h4_activity.txt 2>&1
    adb pull $P/user_files/logs/blood_raze.txt $W/h4_engine_log.txt > /dev/null 2>&1
}

: > $W/h4_report.txt
adb get-state >/dev/null 2>&1 || adb wait-for-device
python $W/probe_tap.py dismiss >> $W/h4_report.txt 2>&1

adb shell am force-stop com.bloodtouch
adb shell am start -n com.bloodtouch/.MainActivity > /dev/null
sleep 6
python $W/probe_tap.py play >> $W/h4_report.txt 2>&1
R "waiting 65 s for the engine main menu"
sleep 65
R "dims: $(python $W/probe_tap.py dims)"
LIVE boot
SHOT 00_menu

# --- enter Options ------------------------------------------------------------
R "tap: $(TAP down_arrow)"
sleep 2
R "tap: $(TAP enter)"
sleep 5
SHOT 01_options
LIVE options

# --- B: the brightness strip, i.e. slide_mouse -> COMMAND_SET_BACKLIGHT -------
R "B slider: three swipes on the right edge"
adb shell input swipe 2250 250 2250 700 700
sleep 2
adb shell input swipe 2250 700 2250 250 700
sleep 2
adb shell input swipe 2250 470 2250 300 700
sleep 3
SHOT 02_slider
LIVE slider_drag

# --- A: the layout editor, the first Options entry the cursor already sits on --
R "A editor: $(TAP enter)"
sleep 6
SHOT 03_editor
LIVE editor_open
R "A back widget in editor: $(TAP back)"
sleep 5
SHOT 04_editor_back
LIVE editor_back
R "A key back in editor: sends 4"
KEY 4
sleep 5
SHOT 05_editor_keyback
LIVE editor_keyback
R "A leave options: $(TAP back)"
sleep 5
SHOT 06_main_menu
LIVE left_options

# --- C: the four menu-group buttons that go to Java, not to the engine --------
for w in load_save_touch keyboard gamepad gyro; do
    R "C $w: $(TAP $w)"
    sleep 4
    SHOT 07_$w
    LIVE $w
    R "C $w close: $(python $W/probe_tap.py dialog)"
    sleep 2
done

R "done"

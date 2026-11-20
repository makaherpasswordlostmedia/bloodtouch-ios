#!/bin/sh
# Third pass, and the first one that can actually hit a widget: the overlay's own '<' back button is
# pressed by tapping its rect in menu.xml, with the screen size re-measured WHILE the game is up.
# The earlier passes reused the launcher's portrait size, so every tap went outside the rotated
# landscape viewport and did nothing - which is also why key events looked healthy.
export MSYS_NO_PATHCONV=1
W=G:/blood_port/work
P=/storage/emulated/0/BloodTouch
R() { echo "[$(date +%H:%M:%S)] $*" >> $W/h3_report.txt; }
SHOT() { adb exec-out screencap -p > $W/h3_$1.png; }
DIFF() { python $W/alive.py $W/h3_$1.png $W/h3_$2.png; }
TAP() { python $W/probe_tap.py menu "$1" 2>&1 | tail -1; }
GAMEPID() { adb shell "pidof -s 'com.bloodtouch:game'" | tr -dc '0-9'; }
STATE() {
    T=$(GAMEPID)
    R "state[$1]: pid=${T:-gone} focus=$(python $W/probe_tap.py focus | sed 's/.*u0 //;s/}//')"
    [ -z "$T" ] && return
    adb shell "top -H -b -n 1 -p $T" > $W/h3_top_$1.txt 2>&1
}
DUMP() {
    T=$(GAMEPID)
    R "dump: game pid=${T:-gone}"
    [ -z "$T" ] && return
    adb shell "su -c 'for t in /proc/$T/task/*; do echo \$t \$(cat \$t/stat) wchan=\$(cat \$t/wchan); done'" > $W/h3_wchan.txt 2>&1
    adb shell "su -c 'for t in /proc/$T/task/*; do echo == \$t; cat \$t/syscall; done'" > $W/h3_syscall.txt 2>&1
    adb shell "su -c 'debuggerd -b $T'" > $W/h3_backtrace.txt 2>&1
    [ -s $W/h3_backtrace.txt ] || adb shell "debuggerd -b $T" > $W/h3_backtrace.txt 2>&1
    adb logcat -d -t 500 > $W/h3_logcat.txt 2>&1
    adb logcat -b events -d -t 300 > $W/h3_events.txt 2>&1
    adb shell dumpsys activity > $W/h3_activity.txt 2>&1
    adb pull $P/user_files/logs/blood_raze.txt $W/h3_engine_log.txt > /dev/null 2>&1
}

: > $W/h3_report.txt
adb get-state >/dev/null 2>&1 || adb wait-for-device
python $W/probe_tap.py dismiss >> $W/h3_report.txt 2>&1

adb shell am force-stop com.bloodtouch
adb shell am start -n com.bloodtouch/.MainActivity > /dev/null
sleep 6
python $W/probe_tap.py play >> $W/h3_report.txt 2>&1
R "waiting 65 s for the engine main menu"
sleep 65
# Re-measure now that the display is rotated, or every tap misses.
R "dims: $(python $W/probe_tap.py dims)"
STATE boot
SHOT 00_menu

R "tap: $(TAP down_arrow)"
sleep 2
SHOT 01_cursor
A=00_menu; B=01_cursor
R "cursor step $A -> $B: $(DIFF $A $B)"

R "tap: $(TAP enter)"
sleep 5
SHOT 02_options
STATE opened

# This is the press he reports: the '<' arrow drawn by the menu group.
R "tap: $(TAP back)"
sleep 5
SHOT 03_after_back
STATE after_back
R "tap: $(TAP down_arrow)"
sleep 3
SHOT 04_probe_after
A=03_after_back; B=04_probe_after
R "after overlay back $A -> $B: $(DIFF $A $B)"
if [ "$(GAMEPID)" = "" ]; then
    R "process gone after the overlay back - that is an exit, not a freeze"
else
    C=$(python $W/alive.py $W/h3_03_after_back.png $W/h3_04_probe_after.png)
    case "$C" in
        "changed 0 of "*) R "FROZEN with the overlay back button - capturing why"; DUMP;;
        *) R "engine still reacts after the overlay back";;
    esac
fi

# ...and the 'several times in a row' variant he described earlier.
for n in 1 2 3; do
    R "cycle $n: $(TAP enter)"
    sleep 4
    R "cycle $n: $(TAP back)"
    sleep 5
    SHOT cyc$n
    STATE cyc$n
done
R "done"

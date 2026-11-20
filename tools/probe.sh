#!/bin/sh
# Waits for the phone, installs whatever build is in G:/blood_port/bloodtouch.apk, then drives the
# launcher into a level and hammers the pause menu (open/close, then quick-save) while sampling the
# renderer's frame counter. A frozen counter means a real hang; chk_threads.txt then says whether the
# main thread is spinning or parked in a futex. Artefacts land in work/ so the run reads without the
# phone attached.
export MSYS_NO_PATHCONV=1
W=G:/blood_port/work
P=/storage/emulated/0/BloodTouch
R() { echo "[$(date +%H:%M:%S)] $*" >> $W/probe_report.txt; }
FR() { adb shell dumpsys gfxinfo com.bloodtouch | grep -m1 'Total frames rendered' | tr -dc '0-9'; }
SHOT() { adb exec-out screencap -p > $W/$1.png; }
RES() { adb shell "ls -l $P/res/raze_dev.pk3" 2>&1 | tr -d '\r'; }
DUMP() {
    T=$(adb shell pidof -s com.bloodtouch | tr -d '\r')
    [ -n "$T" ] && adb shell "top -H -b -n 1 -p $T" > $W/chk_threads.txt 2>&1
    [ -n "$T" ] && adb shell "su -c 'for t in /proc/$T/task/*; do echo \$t \$(cat \$t/stat) wchan=\$(cat \$t/wchan); done'" \
        > $W/chk_wchan.txt 2>&1
    [ -n "$T" ] && adb shell "su -c 'for t in /proc/$T/task/*; do echo == \$t; cat \$t/syscall; done'" \
        > $W/chk_syscall.txt 2>&1
    adb logcat -d -t 400 > $W/chk_logcat.txt 2>&1
    adb logcat -b events -d -t 200 > $W/chk_events.txt 2>&1
    adb shell dumpsys dropbox > $W/chk_dropbox.txt 2>&1
    adb pull $P/user_files/logs/blood_raze.txt $W/chk_engine_log_stuck.txt > /dev/null 2>&1
    adb shell dumpsys window > $W/chk_window.txt 2>&1
    R "focus: $(adb shell dumpsys window | grep -iE 'mCurrentFocus|mFocusedApp' | tr -d '\r' | tr '\n' ' ')"
    adb shell "ls -la $P/user_files/saves/; ls -la $P/*.sav" > $W/chk_saves.txt 2>&1
    adb shell "ls -la $P/user_files/touch_layouts/" > $W/chk_layouts_stuck.txt 2>&1
}

: > $W/probe_report.txt
rm -f $W/probe_dims.json
R "waiting for device"
while ! adb get-state >/dev/null 2>&1; do sleep 3; done
sleep 2
R "device up: $(adb devices | sed -n 2p)"

adb install -r -g G:/blood_port/bloodtouch.apk >> $W/probe_report.txt 2>&1
adb shell appops set com.bloodtouch MANAGE_EXTERNAL_STORAGE allow
R "installed"
R "res pk3 before launch: $(RES) - patched engine pk3 is 8826815 B, unpatched is 9127440 B"

adb shell am force-stop com.bloodtouch
adb shell am start -n com.bloodtouch/.MainActivity >/dev/null
sleep 5
SHOT chk_home
python $W/probe_tap.py play >> $W/probe_report.txt 2>&1
R "res pk3 right after Play: $(RES)"
sleep 55
SHOT chk_title
R "title captured - stock Zeta overlay expected from here on (we ship no layout xml)"

python $W/probe_tap.py enter
sleep 4
SHOT chk_ep
python $W/probe_tap.py enter
# CS1 plays now. The movies are 1920x1080 VP8 and VpxPlayer decodes them on ONE thread
# (cfg = {1,w,h}), so sample the frame counter across the cutscene instead of guessing at it.
M0=$(FR)
PM=$M0
for n in 1 2 3 4 5 6 7 8; do
    sleep 3
    M=$(FR)
    R "movie t=$((n*3))s frames $M0 -> $M  (+$((M-M0)) total, $(( (M-PM)/3 ))/s)"
    PM=$M
    [ $n -eq 3 ] && SHOT chk_movie1
    [ $n -eq 7 ] && SHOT chk_movie2
done
sleep 20
python $W/probe_tap.py mid
sleep 60
SHOT chk_game
# The overlay is the engine's own built-in one now, so measure it against the stock rect dump the
# engine itself wrote (work/stock_layouts), not against a file we ship.
python $W/check_geom.py $W/chk_game.png $W/stock_layouts/game_raze_dev101.xml.default attack use jump \
    >> $W/probe_report.txt 2>&1
PID=$(adb shell pidof -s com.bloodtouch | tr -d '\r')
python $W/check_geom.py $W/chk_game.png $W/stock_layouts/weapons_raze_dev101.xml.default weapon1 weapon5 \
    >> $W/probe_report.txt 2>&1
STATS() { adb shell "stat -c '%n %Y' $P/user_files/touch_layouts/*" | tr -d '\r' > $W/$1; }
STATS chk_layout_stat_before.txt
F1=$(FR); sleep 3; F2=$(FR)
R "in game pid=$PID frames $F1 -> $F2"

# pause menu: open, close, and repeat - the user saw the freeze on the way out, several times over
for n in 1 2 3; do
    adb shell input keyevent 4
    sleep 5
    SHOT chk_menu$n
    A=$(FR); sleep 3; B=$(FR)
    adb shell input keyevent 4
    sleep 8
    SHOT chk_resume$n
    C=$(FR); sleep 5; D=$(FR)
    R "cycle $n: menu frames $A -> $B ; after resume $C -> $D"
    if [ "$C" = "$D" ]; then
        R "STUCK after cycle $n resume - no frames produced"
        DUMP
        break
    fi
done

# quick save through the overlay button (centre taken from the shipped layout)
STATS chk_layout_stat_after.txt
python $W/statdiff.py $W/chk_layout_stat_before.txt $W/chk_layout_stat_after.txt >> $W/probe_report.txt 2>&1
python $W/probe_tap.py widget quick_save >> $W/probe_report.txt 2>&1
sleep 6
S1=$(FR); sleep 5; S2=$(FR)
SHOT chk_save
R "after quick_save frames $S1 -> $S2"
if [ "$S1" = "$S2" ]; then R "STUCK after quick_save"; DUMP; fi

adb shell "ls -la $P/user_files/touch_layouts/" > $W/chk_layouts.txt 2>&1
R "layouts on device: $(grep -c '<' $W/chk_layouts.txt 2>/dev/null) files; leftover custom xml (want 0): $(grep -oE '(game|weapons|inventory|weaponwheel|custom_buttons_0)_raze_dev101\.xml|menu\.xml|touch_settings101\.xml' $W/chk_layouts.txt | grep -v '\.default' | sort -u | tr '\n' ' ')"
adb pull $P/user_files/logs/blood_raze.txt $W/chk_engine_log.txt >> $W/probe_report.txt 2>&1
R "done"

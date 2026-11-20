#!/bin/sh
# Собирает журнал запуска Blood Touch: logcat + raze-лог + нативные краши.
ADB=/cygdrive/g/Android/Sdk/platform-tools/adb.exe
OUT=/cygdrive/g/blood_port/work
mkdir -p "$OUT"

"$ADB" logcat -c 2>/dev/null
echo "Жди 20 секунд после нажатия Start, потом Enter."
read _

"$ADB" logcat -d -v brief > "$OUT/logcat.txt" 2>&1
grep -E "SDL|OpenTouch|Raze|raze|libc|DEBUG|AndroidRuntime|FATAL|bloodtouch|FileJNI" "$OUT/logcat.txt" > "$OUT/logcat-filtered.txt"
echo "logcat -> $OUT/logcat-filtered.txt ($(wc -l < "$OUT/logcat-filtered.txt") строк)"

"$ADB" shell "cat /storage/emulated/0/BloodTouch/user_files/logs/blood_raze.txt" > "$OUT/blood_raze.txt" 2>&1
echo "raze-лог -> $OUT/blood_raze.txt ($(wc -c < "$OUT/blood_raze.txt") байт)"

"$ADB" shell "ls -t /data/tombstones 2>/dev/null | head -3" 2>&1

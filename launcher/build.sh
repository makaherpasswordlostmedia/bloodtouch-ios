#!/bin/sh
# Builds bloodtouch.apk without Gradle: aapt2 + javac + d8(R8) + zipalign + apksigner.
#
# Paths come out of this script's own location, so the tree can sit anywhere. Two forms are kept on
# purpose: $SELF for the MSYS/Cygwin tools (find, rm, mkdir, cd) and $ROOT in "G:/..." form for the
# Windows binaries, which is what aapt2/javac/zipalign actually receive.
# Override the toolchain with SDK=... R8=... KS=... sh build.sh if the defaults are not yours.
set -e

SELF=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cygpath -m "$SELF")
OUT=$ROOT/build
CYGOUT=$SELF/build
NAME=bloodtouch.apk

SDK=${SDK:-G:/Android/Sdk}
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
R8=${R8:-G:/blood-android/tools/r8.jar}
KS=${KS:-G:/blood-android/apk/debug.keystore}

rm -rf "$CYGOUT"
mkdir -p "$CYGOUT/classes" "$CYGOUT/dex" "$CYGOUT/gen"

echo "[1/7] aapt2 compile"
"$BT/aapt2.exe" compile --dir "$ROOT/app/res" -o "$OUT/res.zip"

echo "[2/7] aapt2 link"
"$BT/aapt2.exe" link \
    --manifest "$ROOT/app/AndroidManifest.xml" \
    -I "$PLATFORM" \
    --min-sdk-version 21 \
    --target-sdk-version 28 \
    -A "$ROOT/assets" \
    --java "$OUT/gen" \
    -o "$OUT/base.apk" \
    "$OUT/res.zip"

echo "[3/7] javac"
# The overlay labels are Cyrillic and the source is UTF-8; javac would otherwise read it as cp1251.
javac -nowarn -encoding UTF-8 --release 8 -classpath "$(cygpath -w "$PLATFORM")" -d "$OUT/classes" \
    "$OUT/gen/com/bloodtouch/R.java" \
    $(find "$SELF/app/java" -name '*.java' | cygpath -w -f -)

echo "[4/7] d8"
(cd "$CYGOUT/classes" && java -cp "$(cygpath -w "$R8")" com.android.tools.r8.D8 \
    --lib "$(cygpath -w "$PLATFORM")" --min-api 21 --output "$OUT/dex" \
    $(find . -name '*.class' | sed 's|^\./||'))

echo "[5/7] payload"
cp "$OUT/base.apk" "$OUT/$NAME"
python "$ROOT/add_payload.py" "$OUT/$NAME" "$OUT/dex/classes.dex" "$ROOT/jniLibs/arm64-v8a"

echo "[6/7] zipalign"
"$BT/zipalign.exe" -f 4 "$OUT/$NAME" "$OUT/aligned.apk"
mv "$CYGOUT/aligned.apk" "$CYGOUT/$NAME"

echo "[7/7] sign"
"$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android --out "$OUT/$NAME" "$OUT/$NAME"

"$BT/apksigner.bat" verify --print-certs "$OUT/$NAME" | head -3
ls -la "$CYGOUT/$NAME"

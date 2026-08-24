#!/usr/bin/env bash
# Render the config-screen shots as isolated compositions and convert to jpg.
#
# Uses the manual instrument flow — never `connectedDebugAndroidTest`, which
# uninstalls the app, wipes DataStore, and leaves the appstore broker with a
# dead binding.
#
# Usage:
#   scripts/render_config_shots.sh                     # stage to screencaps/shots/
#   OUTDIR=docs/screenshots scripts/render_config_shots.sh   # write the committed set
set -euo pipefail
cd "$(dirname "$0")/.."

devicedir=/sdcard/Android/data/com.jpweytjens.barberfish/files/config_shots
outdir=${OUTDIR:-screencaps/shots}
stage=$(mktemp -d)

./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell rm -rf "$devicedir"
adb shell am instrument -w \
    -e class com.jpweytjens.barberfish.ConfigShotsRenderTest \
    com.jpweytjens.barberfish.test/androidx.test.runner.AndroidJUnitRunner

adb pull "$devicedir" "$stage/config_shots"
mkdir -p "$outdir"
for f in "$stage"/config_shots/*.png; do
    name=$(basename "${f%.png}")
    magick "$f" -quality 92 "$outdir/$name.jpg"
    echo "  -> $outdir/$name.jpg"
done
rm -rf "$stage"

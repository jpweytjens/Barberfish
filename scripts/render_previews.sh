#!/usr/bin/env bash
# Render every field's picker preview to PNGs and build a contact sheet.
#
# Uses the manual instrument flow — never `connectedDebugAndroidTest`, which
# uninstalls the app, wipes DataStore, and leaves the appstore broker with a
# dead binding (see CLAUDE.md § "ADB install pitfall").
set -euo pipefail
cd "$(dirname "$0")/.."

devicedir=/sdcard/Android/data/com.jpweytjens.barberfish/files/previews
outdir=screencaps/previews

./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell rm -rf "$devicedir"
adb shell am instrument -w \
    -e class com.jpweytjens.barberfish.AllFieldPreviewsRenderTest \
    com.jpweytjens.barberfish.test/androidx.test.runner.AndroidJUnitRunner

rm -rf "$outdir"
adb pull "$devicedir" "$outdir"

montage \
    -label '%t' \
    -font /System/Library/Fonts/Supplemental/Arial.ttf \
    -background black -fill white \
    -geometry +8+8 \
    -tile 4x \
    "$outdir"/*.png \
    screencaps/preview_contact_sheet.png
echo "contact sheet: screencaps/preview_contact_sheet.png"

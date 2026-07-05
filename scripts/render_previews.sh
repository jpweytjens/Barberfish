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

# Grid of single-cell fields; the full-width HUD strips go in their own
# single-column montage appended below so they don't break the 4-column grid.
grid_pngs=$(ls "$outdir"/*.png | grep -v three-column)
montage \
    -label '%t' \
    -font /System/Library/Fonts/Supplemental/Arial.ttf \
    -background black -fill white \
    -geometry +8+8 \
    -tile 4x \
    $grid_pngs \
    "$outdir/.sheet_grid.png"
montage \
    -label '%t' \
    -font /System/Library/Fonts/Supplemental/Arial.ttf \
    -background black -fill white \
    -geometry +8+8 \
    -tile 1x \
    "$outdir/three-column.png" "$outdir/three-column-4col.png" \
    "$outdir/.sheet_hud.png"
magick "$outdir/.sheet_grid.png" "$outdir/.sheet_hud.png" \
    -background black -gravity center -append \
    screencaps/preview_contact_sheet.png
rm "$outdir/.sheet_grid.png" "$outdir/.sheet_hud.png"
echo "contact sheet: screencaps/preview_contact_sheet.png"

# Unlabeled variant for docs/data-fields.md: the field headers already name
# each tile, so the typeId filename labels are developer noise there.
# -font is needed even without labels: montage errors out when no default
# font is configured, and set -e would abort the script.
montage \
    -font /System/Library/Fonts/Supplemental/Arial.ttf \
    -background black \
    -geometry +8+8 \
    -tile 4x \
    $grid_pngs \
    "$outdir/.docs_grid.png"
montage \
    -font /System/Library/Fonts/Supplemental/Arial.ttf \
    -background black \
    -geometry +8+8 \
    -tile 1x \
    "$outdir/three-column.png" "$outdir/three-column-4col.png" \
    "$outdir/.docs_hud.png"
magick "$outdir/.docs_grid.png" "$outdir/.docs_hud.png" \
    -background black -gravity center -append \
    -depth 8 \
    docs/screenshots/all_fields.png
rm "$outdir/.docs_grid.png" "$outdir/.docs_hud.png"
echo "docs overview: docs/screenshots/all_fields.png"

# Per-state doc renders (Grade statuses for docs/algorithms.md).
for f in "$outdir"/states/*.png; do
    name=$(basename "${f%.png}")
    magick "$f" "docs/screenshots/$name.jpg"
    echo "docs state: docs/screenshots/$name.jpg"
done

# Threshold sweep GIFs for docs/data-fields.md. Frames run low→high; append
# the reversed middle so the loop ping-pongs instead of jump-cutting.
for mode in target range; do
    dir="$outdir/sweep/$mode"
    [ -d "$dir" ] || continue
    forward=$(ls "$dir"/*.png)
    backward=$(ls -r "$dir"/*.png | sed '1d;$d')
    magick -delay 8 -loop 0 $forward $backward \
        "docs/screenshots/threshold_${mode}_sweep.gif"
    echo "docs sweep: docs/screenshots/threshold_${mode}_sweep.gif"
done

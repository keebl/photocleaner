#!/usr/bin/env bash
# Pushes generated test photos to the running emulator and indexes them in MediaStore.
#
#   scripts/seed-emulator.sh          seed (replaces an earlier seed)
#   scripts/seed-emulator.sh --clean  remove the seeded photos
#
# Needs adb on PATH, one running emulator/device, and Pillow for the generator
# (set PYTHON to a virtualenv interpreter if the default python3 lacks it). With ffmpeg
# installed it also adds a short synthetic video dated 10 November 2022.
set -euo pipefail

CAMERA_DIR=/sdcard/Pictures/PhotoCleanerSeed
SHOT_DIR=/sdcard/Pictures/Screenshots
SHOT_FILE=Screenshot_seed.png
VIDEO_DIR=/sdcard/Movies/PhotoCleanerSeed
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

rescan() {
  # Ask MediaStore to rescan the primary volume so new/removed files show up.
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null
}

clean() {
  adb shell rm -rf "$CAMERA_DIR" "$SHOT_DIR/$SHOT_FILE" "$VIDEO_DIR"
}

if [[ "${1:-}" == "--clean" ]]; then
  clean
  rescan
  echo "Removed seeded photos."
  exit 0
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

"${PYTHON:-python3}" "$SCRIPT_DIR/generate_seed_images.py" "$work"
clean
adb shell mkdir -p "$CAMERA_DIR" "$SHOT_DIR"
adb push -q "$work/camera/." "$CAMERA_DIR/"
adb push -q "$work/screenshots/$SHOT_FILE" "$SHOT_DIR/$SHOT_FILE"
if command -v ffmpeg >/dev/null; then
  ffmpeg -loglevel error -f lavfi -i testsrc=duration=3:size=640x360:rate=25 \
    -metadata creation_time=2022-11-10T12:00:00Z -pix_fmt yuv420p "$work/video_2022.mp4"
  adb shell mkdir -p "$VIDEO_DIR"
  adb push -q "$work/video_2022.mp4" "$VIDEO_DIR/"
fi
rescan
echo "Seeded $CAMERA_DIR, $SHOT_DIR/$SHOT_FILE and (with ffmpeg) $VIDEO_DIR."

#!/usr/bin/env bash
# Pushes generated test photos to the running emulator and indexes them in MediaStore.
#
#   scripts/seed-emulator.sh          seed (replaces an earlier seed)
#   scripts/seed-emulator.sh --clean  remove the seeded photos
#
# Needs adb on PATH, one running emulator/device, and Pillow for the generator
# (set PYTHON to a virtualenv interpreter if the default python3 lacks it).
set -euo pipefail

CAMERA_DIR=/sdcard/Pictures/PhotoCleanerSeed
SHOT_DIR=/sdcard/Pictures/Screenshots
SHOT_FILE=Screenshot_seed.png
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

rescan() {
  # Ask MediaStore to rescan the primary volume so new/removed files show up.
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null
}

clean() {
  adb shell rm -rf "$CAMERA_DIR" "$SHOT_DIR/$SHOT_FILE"
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
rescan
echo "Seeded $CAMERA_DIR and $SHOT_DIR/$SHOT_FILE."

#!/usr/bin/env bash
# Local SMB test server for the NAS source (specs/android-nas.md), run without root.
#
#   scripts/nas-test-server.sh start   seed the share and start smbd on port 1445
#   scripts/nas-test-server.sh stop    stop smbd
#   scripts/nas-test-server.sh env     print the instrumentation arguments for the tests
#
# Needs Homebrew `samba` (brew install samba) and Pillow + ffmpeg for the seed (as
# scripts/seed-emulator.sh). Everything lives in the git-ignored scripts/.nas-test/: config,
# state, the generated password and the `photos` share. The emulator reaches the server at
# 10.0.2.2:1445. Login: your macOS user name and the password in .nas-test/password.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE="$SCRIPT_DIR/.nas-test"
CONF="$BASE/smb.conf"
SHARE="$BASE/share/photos"
PORT=1445
SMBD=/opt/homebrew/sbin/samba-dot-org-smbd
PDBEDIT=/opt/homebrew/opt/samba/bin/pdbedit
USER_NAME="$(id -un)"

write_conf() {
  mkdir -p "$BASE"/{private,lock,state,cache,pid,log,ncalrpc} "$SHARE" "$BASE/share/other"
  cat >"$CONF" <<EOF
[global]
  server role = standalone server
  smb ports = $PORT
  bind interfaces only = no
  disable netbios = yes
  server min protocol = SMB2_10
  private dir = $BASE/private
  lock directory = $BASE/lock
  state directory = $BASE/state
  cache directory = $BASE/cache
  pid directory = $BASE/pid
  ncalrpc dir = $BASE/ncalrpc
  log file = $BASE/log/smbd.log
  passdb backend = tdbsam:$BASE/private/passdb.tdb
  map to guest = never
  load printers = no
  printing = bsd
  printcap name = /dev/null
  disable spoolss = yes

[photos]
  path = $SHARE
  read only = no
  valid users = $USER_NAME

[other]
  path = $BASE/share/other
  read only = yes
  valid users = $USER_NAME
EOF
}

seed() {
  local work
  work="$(mktemp -d)"
  "${PYTHON:-python3}" "$SCRIPT_DIR/../scripts/generate_seed_images.py" "$work" >/dev/null
  rm -rf "$SHARE"
  mkdir -p "$SHARE/2022" "$SHARE/Screenshots" "$SHARE/@eaDir" "$SHARE/.hidden"
  cp "$work"/camera/*.jpg "$SHARE/2022/"
  cp "$work"/screenshots/*.png "$SHARE/Screenshots/"
  echo "not a photo" >"$SHARE/2022/notes.txt"
  cp "$work"/camera/*.jpg "$SHARE/@eaDir/" 2>/dev/null || true
  if command -v ffmpeg >/dev/null; then
    ffmpeg -loglevel error -f lavfi -i testsrc=duration=3:size=640x360:rate=25 \
      -pix_fmt yuv420p "$SHARE/2022/video_2022.mp4"
  fi
  # A NAS item's date is its modification time (iOS parity): copy the EXIF date there.
  "${PYTHON:-python3}" - "$SHARE" <<'PY'
import os, sys, datetime
from pathlib import Path
from PIL import Image
for p in Path(sys.argv[1]).rglob("*"):
    if p.suffix.lower() not in (".jpg", ".jpeg"):
        continue
    try:
        stamp = Image.open(p).getexif().get_ifd(0x8769).get(36867)
    except Exception:
        stamp = None
    if stamp:
        t = datetime.datetime.strptime(stamp, "%Y:%m:%d %H:%M:%S").timestamp()
        os.utime(p, (t, t))
PY
  rm -rf "$work"
}

start() {
  [[ -x "$SMBD" ]] || { echo "Install Samba first: brew install samba" >&2; exit 1; }
  write_conf
  seed
  if [[ ! -f "$BASE/password" ]]; then
    LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 20 >"$BASE/password"
  fi
  local pw
  pw="$(cat "$BASE/password")"
  printf "%s\n%s\n" "$pw" "$pw" | "$PDBEDIT" -s "$CONF" -a -u "$USER_NAME" -t >/dev/null 2>&1
  stop_quiet
  "$SMBD" -s "$CONF" --daemon --no-process-group
  sleep 1
  echo "smbd listening on port $PORT (share 'photos', user '$USER_NAME', password in $BASE/password)."
}

stop_quiet() {
  if [[ -f "$BASE/pid/samba-dot-org-smbd.pid" ]]; then
    kill "$(cat "$BASE/pid/samba-dot-org-smbd.pid")" 2>/dev/null || true
    rm -f "$BASE/pid/samba-dot-org-smbd.pid"
  fi
  pkill -f "$SMBD -s $CONF" 2>/dev/null || true
}

case "${1:-}" in
  start) start ;;
  stop) stop_quiet; echo "smbd stopped." ;;
  env)
    echo "-Pandroid.testInstrumentationRunnerArguments.nasUser=$USER_NAME" \
      "-Pandroid.testInstrumentationRunnerArguments.nasPassword=$(cat "$BASE/password")"
    ;;
  *) sed -n '2,11p' "$0"; exit 1 ;;
esac

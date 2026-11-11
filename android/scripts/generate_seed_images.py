#!/usr/bin/env python3
"""Generates synthetic test photos for the Android emulator (no personal photos).

Usage: generate_seed_images.py OUT_DIR   (OUT_DIR must be inside the system temp directory)

Writes OUT_DIR/camera/*.jpg and OUT_DIR/screenshots/*.png covering the review cases:
on this day in earlier years, exact duplicates, similar photos, a panorama, a leap-day
photo, a photo without capture date and spread-out dates for day/month/year browsing.
Requires Pillow; if it is missing, use a virtual environment:
  python3 -m venv .venv && .venv/bin/pip install Pillow && PYTHON=.venv/bin/python3 scripts/seed-emulator.sh
"""

from __future__ import annotations

import datetime as dt
import random
import sys
import tempfile
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit(
        "Pillow is required; install it in a virtual environment (see the module docstring)."
    )

EXIF_IFD = 0x8769
DATETIME = 306
DATETIME_ORIGINAL = 36867
OFFSET_TIME_ORIGINAL = 36881


def scene(seed: int, width: int, height: int) -> Image.Image:
    """A random composition of shapes; different seeds give different dHashes."""
    rng = random.Random(seed)
    img = Image.new("RGB", (width, height), tuple(rng.randrange(256) for _ in range(3)))
    draw = ImageDraw.Draw(img)
    for _ in range(12):
        x0, y0 = rng.randrange(width), rng.randrange(height)
        x1 = min(width, x0 + rng.randrange(width // 8, width // 2))
        y1 = min(height, y0 + rng.randrange(height // 8, height // 2))
        colour = tuple(rng.randrange(256) for _ in range(3))
        if rng.random() < 0.5:
            draw.rectangle([x0, y0, x1, y1], fill=colour)
        else:
            draw.ellipse([x0, y0, x1, y1], fill=colour)
    draw.text((20, 20), f"seed {seed}", fill=(255, 255, 255))
    return img


def save_jpeg(
    img: Image.Image, path: Path, taken: dt.datetime | None, quality: int = 90
) -> None:
    exif = Image.Exif()
    if taken is not None:
        stamp = taken.strftime("%Y:%m:%d %H:%M:%S")
        exif[DATETIME] = stamp
        exif_ifd = exif.get_ifd(EXIF_IFD)
        exif_ifd[DATETIME_ORIGINAL] = stamp
        # Without an offset, MediaStore drops capture times far from the file's mtime
        # (which is the push time), so dates in earlier years would be lost.
        exif_ifd[OFFSET_TIME_ORIGINAL] = "+00:00"
    img.save(path, "JPEG", quality=quality, exif=exif)


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    # Only write inside the system temp directory (seed-emulator.sh passes `mktemp -d`),
    # so a mistyped argument cannot scatter files elsewhere.
    temp_root = Path(tempfile.gettempdir()).resolve()
    out = Path(sys.argv[1]).resolve()
    if not out.is_relative_to(temp_root) or out == temp_root:
        sys.exit(f"OUT_DIR must be a directory inside {temp_root}")
    camera = out / "camera"
    shots = out / "screenshots"
    camera.mkdir(parents=True, exist_ok=True)
    shots.mkdir(parents=True, exist_ok=True)

    today = dt.date.today()
    noon = dt.time(12, 0)

    def on(year: int, month: int, day: int, hour: int = 12) -> dt.datetime:
        return dt.datetime.combine(dt.date(year, month, day), noon).replace(hour=hour)

    def this_day(year: int) -> dt.datetime:
        day = today.day
        if today.month == 2 and day == 29 and year % 4 != 0:
            day = 28
        return on(year, today.month, day)

    # On this day in earlier years.
    for i, year in enumerate((2019, 2021, 2023)):
        save_jpeg(
            scene(100 + i, 1600, 1200), camera / f"onthisday_{year}.jpg", this_day(year)
        )

    # Exact duplicates: same capture second and dimensions, different file size.
    dup_time = on(2022, 6, 15, 15)
    save_jpeg(
        scene(200, 1600, 1200), camera / "exact_dup_original.jpg", dup_time, quality=95
    )
    save_jpeg(
        scene(200, 1600, 1200), camera / "exact_dup_copy.jpg", dup_time, quality=60
    )

    # Similar photos: same scene re-exported smaller, a day later (not an exact duplicate).
    save_jpeg(scene(300, 1600, 1200), camera / "similar_original.jpg", on(2021, 9, 3))
    save_jpeg(
        scene(300, 1600, 1200).resize((800, 600)),
        camera / "similar_smaller.jpg",
        on(2021, 9, 4),
        quality=70,
    )

    # Panorama (>= 2:1) and a leap-day photo.
    save_jpeg(scene(400, 3000, 1000), camera / "panorama.jpg", on(2020, 7, 21))
    save_jpeg(scene(500, 1600, 1200), camera / "leapday_2024.jpg", on(2024, 2, 29))

    # No capture date: browsing falls back to the date the file was added.
    save_jpeg(scene(600, 1200, 1200), camera / "no_exif_date.jpg", None)

    # Spread-out dates for day/month/year browsing.
    rng = random.Random(42)
    for i in range(12):
        taken = on(
            rng.randint(2016, 2025),
            rng.randint(1, 12),
            rng.randint(1, 28),
            rng.randint(8, 20),
        )
        save_jpeg(scene(1000 + i, 1600, 1200), camera / f"random_{i:02d}.jpg", taken)

    # Phone screenshot (20:9, PNG, in a Screenshots folder).
    scene(700, 1080, 2400).save(shots / "Screenshot_seed.png", "PNG")

    print(
        f"Wrote {len(list(camera.iterdir()))} camera photos and "
        f"{len(list(shots.iterdir()))} screenshot(s) to {out}"
    )


if __name__ == "__main__":
    main()

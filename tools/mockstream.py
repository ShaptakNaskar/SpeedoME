#!/usr/bin/env python3
"""Stream fake GPS into the SpeedoME debug build through its adb mock hook (docs/plan.md §11).

    tools/mockstream.py route.gpx            # replay a GPX track (speed from <speed> or from positions)
    tools/mockstream.py drive.csv --rate 2   # CSV: lat,lon[,kmh[,acc]] one row per fix
    tools/mockstream.py --synthetic 60       # a 60 km/h loop around --at LAT,LON
    tools/mockstream.py --synthetic 5 --nospeed   # no Doppler speed: exercise the position fallback

The first run grants the mock-location app-op. Each fix is one `am broadcast`, sent through a single
persistent `adb shell`, so 1–5 Hz is realistic. Ctrl+C stops and removes the test provider.
"""
import argparse
import csv
import math
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.sappy.SpeedoMe.debug"


def haversine(a, b):
    r = 6371000.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp, dl = p2 - p1, math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def bearing(a, b):
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dl = math.radians(b[1] - a[1])
    y = math.sin(dl) * math.cos(p2)
    x = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def read_gpx(path):
    ns = {"g": "http://www.topografix.com/GPX/1/1"}
    pts = []
    for p in ET.parse(path).getroot().iter():
        if not p.tag.endswith("trkpt"):
            continue
        spd = next((c.text for c in p.iter() if c.tag.endswith("speed")), None)
        pts.append({"lat": float(p.get("lat")), "lon": float(p.get("lon")), "kmh": float(spd) * 3.6 if spd else None})
    return pts


def read_csv(path):
    pts = []
    with open(path) as f:
        for row in csv.reader(f):
            if not row or row[0].startswith("#") or not row[0].replace(".", "").replace("-", "").isdigit():
                continue
            pts.append({"lat": float(row[0]), "lon": float(row[1]),
                        "kmh": float(row[2]) if len(row) > 2 and row[2] else None,
                        "acc": float(row[3]) if len(row) > 3 and row[3] else None})
    return pts


def synthetic(kmh, at, rate):
    """An endless loop of ~1 km circumference at a constant speed."""
    lat0, lon0 = at
    radius = 160.0
    step = kmh / 3.6 / rate / radius
    a = 0.0
    while True:
        dlat = radius * math.cos(a) / 111320
        dlon = radius * math.sin(a) / (111320 * math.cos(math.radians(lat0)))
        yield {"lat": lat0 + dlat, "lon": lon0 + dlon, "kmh": kmh}
        a += step


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("file", nargs="?")
    ap.add_argument("--synthetic", type=float, metavar="KMH")
    ap.add_argument("--at", default="52.5163,13.3777", help="centre for --synthetic (default Berlin)")
    ap.add_argument("--rate", type=float, default=1.0, help="fixes per second (1-10)")
    ap.add_argument("--acc", type=float, default=4.0, help="horizontal accuracy (m)")
    ap.add_argument("--nospeed", action="store_true", help="omit Doppler speed")
    ap.add_argument("-s", "--serial", help="adb device serial")
    ap.add_argument("--pkg", default=PKG)
    a = ap.parse_args()
    # Background jobs inherit an ignored SIGINT; install handlers so Ctrl+C, timeout and kill all clean up.
    signal.signal(signal.SIGINT, signal.default_int_handler)
    signal.signal(signal.SIGTERM, signal.default_int_handler)

    adb = ["adb"] + (["-s", a.serial] if a.serial else [])
    subprocess.run(adb + ["shell", "appops", "set", a.pkg, "android:mock_location", "allow"], check=False)
    if a.synthetic is not None:
        lat, lon = (float(x) for x in a.at.split(","))
        pts = synthetic(a.synthetic, (lat, lon), a.rate)
    elif a.file:
        pts = iter(read_gpx(a.file) if a.file.lower().endswith(".gpx") else read_csv(a.file))
    else:
        ap.error("give a GPX/CSV file or --synthetic KMH")

    # Own session: Ctrl+C / timeout signal the whole group, and adb must survive to send MOCK_STOP.
    shell = subprocess.Popen(adb + ["shell"], stdin=subprocess.PIPE, text=True, start_new_session=True)
    prev, n, period = None, 0, 1.0 / max(.1, min(a.rate, 10.0))
    try:
        for p in pts:
            t0 = time.monotonic()
            cur = (p["lat"], p["lon"])
            kmh = p.get("kmh")
            if kmh is None and prev is not None:
                kmh = haversine(prev, cur) / period * 3.6
            args = f"--es latS {cur[0]:.7f} --es lonS {cur[1]:.7f} --ef acc {p.get('acc') or a.acc}"
            if kmh is not None and not a.nospeed:
                args += f" --ef kmh {kmh:.2f}"
            if prev is not None and haversine(prev, cur) > 0.5:
                args += f" --ef bearing {bearing(prev, cur):.1f}"
            shell.stdin.write(f"am broadcast -p {a.pkg} -a speedome.MOCK_FIX {args} >/dev/null\n")
            shell.stdin.flush()
            n += 1
            print(f"\rfix {n}: {cur[0]:.5f},{cur[1]:.5f} {kmh or 0:6.1f} km/h", end="", flush=True)
            prev = cur
            time.sleep(max(0.0, period - (time.monotonic() - t0)))
    except KeyboardInterrupt:
        pass
    finally:
        shell.stdin.write(f"am broadcast -p {a.pkg} -a speedome.MOCK_STOP >/dev/null\nexit\n")
        shell.stdin.flush()
        shell.wait(timeout=10)
        print("\nstopped")


if __name__ == "__main__":
    sys.exit(main())

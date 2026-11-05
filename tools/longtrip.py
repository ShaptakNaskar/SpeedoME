#!/usr/bin/env python3
"""Adds a synthetic long drive to the debug app's trips, for map stress tests.

    tools/longtrip.py [SERIAL]      # default emulator-5584

About 540 km and 14 h from Siliguri to Kolkata at 1 Hz: highway stretches with speed jitter (so the
speed colour changes every few seconds, like real traffic), towns, stops and a few GPS gaps.
The app is stopped while its database is rewritten; reopen it afterwards.
"""
import math
import random
import sqlite3
import subprocess
import sys
import tempfile
import time

PKG = "com.sappy.SpeedoMe.debug"
WAYPOINTS = [(26.72, 88.43), (26.10, 87.95), (25.87, 87.85), (25.00, 88.14), (24.80, 87.90),
             (24.10, 88.25), (23.40, 88.50), (22.57, 88.36)]


def drive(seed=7):
    rnd = random.Random(seed)
    legs = [(a, b, haversine(a, b)) for a, b in zip(WAYPOINTS, WAYPOINTS[1:])]
    total = sum(d for *_, d in legs)
    t = int(time.time() * 1000) - 16 * 3_600_000  # ends before now, so it tops the trip list
    pos, seg, kmh, target, left = 0.0, 0, 0.0, 90.0, 0
    points = []
    while pos < total:
        if left <= 0:  # pick the next stretch: highway, town, a stop or a GPS gap
            r = rnd.random()
            if r < .06:
                t += rnd.randint(60, 900) * 1000  # parked: no points
                kmh, left = 0.0, 1
                continue
            if r < .09:
                seg += 1
                t += rnd.randint(30, 300) * 1000
                pos += rnd.uniform(500, 4000)  # GPS lost for a while
                left = 1
                continue
            target = rnd.uniform(25, 50) if r < .35 else rnd.uniform(80, 125)
            left = rnd.randint(120, 1500)
        kmh += (target - kmh) * .08 + rnd.gauss(0, 3)
        kmh = max(3.0, min(131.0, kmh))
        pos += kmh / 3.6
        t += 1000
        left -= 1
        lat, lon = along(legs, pos)
        wiggle = math.sin(pos / 900) * 0.004  # gentle bends
        points.append((t, seg, lat + wiggle, lon + wiggle * .6, kmh / 3.6))
    return points, total


def along(legs, pos):
    for a, b, d in legs:
        if pos <= d:
            f = pos / d
            return a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f
        pos -= d
    return legs[-1][1]


def haversine(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (*a, *b))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))


def main() -> int:
    serial = sys.argv[1] if len(sys.argv) > 1 else "emulator-5584"
    adb = ["adb", "-s", serial]
    subprocess.run(adb + ["shell", "am", "force-stop", PKG], check=True)
    names = subprocess.run(adb + ["shell", "run-as", PKG, "ls", "databases"], check=True, capture_output=True, text=True).stdout.split()
    db = next(n for n in names if n.endswith(".db") or ("-" not in n and "." not in n))
    with tempfile.TemporaryDirectory() as tmp:
        local = f"{tmp}/{db}"
        for suffix in ("", "-wal", "-shm"):
            if db + suffix in names:
                with open(local + suffix, "wb") as f:
                    subprocess.run(adb + ["exec-out", "run-as", PKG, "cat", f"databases/{db}{suffix}"], check=True, stdout=f)
        con = sqlite3.connect(local)
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        old = [r[0] for r in con.execute("SELECT id FROM session WHERE name = 'Long synthetic drive'")]
        con.executemany("DELETE FROM point WHERE sessionId = ?", [(i,) for i in old])
        con.executemany("DELETE FROM session WHERE id = ?", [(i,) for i in old])
        points, total = drive()
        start, end = points[0][0], points[-1][0]
        moving = len(points)
        cur = con.execute(
            "INSERT INTO session (kind, state, mode, startedAt, endedAt, updatedAt, name, distanceM, movingS, elapsedS, maxMps, steps) "
            "VALUES ('TRIP', 'FINISHED', 'DRIVE', ?, ?, ?, 'Long synthetic drive', ?, ?, ?, ?, 0)",
            (start, end, end, total, moving, (end - start) / 1000, max(p[4] for p in points)),
        )
        sid = cur.lastrowid
        con.executemany(
            "INSERT INTO point (sessionId, t, segment, lat, lon, hAcc, speed, source, isMock) VALUES (?, ?, ?, ?, ?, 5, ?, 'gps', 0)",
            [(sid, t, seg, lat, lon, v) for t, seg, lat, lon, v in points],
        )
        con.commit()
        con.execute("PRAGMA journal_mode=DELETE")
        con.close()
        with open(local, "rb") as f:
            subprocess.run(adb + ["exec-in", "run-as", PKG, "sh", "-c", f"cat > databases/{db}"], check=True, stdin=f)
        subprocess.run(adb + ["shell", "run-as", PKG, "rm", "-f", f"databases/{db}-wal", f"databases/{db}-shm"], check=True)
    print(f"trip {sid}: {len(points)} points, {total / 1000:.0f} km, {(end - start) / 3_600_000:.1f} h")
    return 0


if __name__ == "__main__":
    sys.exit(main())

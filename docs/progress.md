# Progress log

The working log for building SpeedoME milestone by milestone (plan: [`plan.md`](plan.md) §12). Each milestone goes through **implement → test → verify on the emulator → commit**.

## How to resume after a break

1. Re-read this file, then `plan.md` §4–§11 for the milestone at hand.
2. Run `./gradlew check` and make sure it passes before changing anything.
3. Use `tools/emu.sh` for emulator checks: `start`, `install`, `setup-dev` (grants permissions, turns on developer options and the simulator), `tap "Text"`, `texts`, `shot NAME` (to /tmp/speedome-shots), `feed KMH SECONDS`, `crashes`. uiautomator escapes `&` as `&amp;` in texts.
   To inspect the database: `adb exec-out run-as com.sappy.SpeedoMe.debug cat databases/speedome.db` (also copy `-wal` and `-shm`), then open it with `sqlite3` from platform-tools.
4. Take library versions from `gradle/libs.versions.toml`. For new libraries use the latest stable releases (looked up 2026-09-24): serialization-json 1.11.0, coroutines 1.11.0, Room 2.8.5 + KSP 2.3.12, DataStore 1.2.1, MapLibre 13.6.1, Roborazzi 1.75.0, Robolectric 4.17, Turbine 1.2.1, benchmark 1.5.0, profileinstaller 1.4.1, androidx.test.ext:junit 1.3.0, uiautomator 2.4.0.

## Milestones

| # | Status | Notes |
|---|---|---|
| M0 Skeleton | done 2026-09-24 | commit 0587ecb |
| M1 Engine core | done 2026-09-24 | 28 JVM tests: filter, spikes, trains, no-Doppler, parked, tunnel, city distance, 10 Hz, pause, trips, steps, snapshot, resume (same boot + reboot), replay formats |
| M2 Simulator + debug screen | done 2026-09-24 | Verified on emulator: City/Highway/Walk presets, spike rejected, signal loss → NO FIX, no speed field → POSITION, trip start/pause/resume/stop |
| M3 Tracking service + sensors | done 2026-09-24 | Verified on emulator: permission dialogs chain (location → notifications), foreground `location` service with live notification, real LocationManager fixes (geo fix + velocity) → DOPPLER source, GnssStatus satellites, notification keeps updating with screen off, denied / coarse-only / step-permission cards |
| M4 Storage + resume + Trips | done 2026-09-24 | Verified on emulator: record → stop → summary sheet; Trips list (thumbnail) and detail (speed-coloured route, speed graph); Share GPX (chooser) and valid GPX 1.1 export; rename; delete; kill mid-trip → sticky service restarts and the same row continues with a new segment; kill with session aged 31 min → offer → Save & finish |
| M5 Gauge toolkit + first themes | done 2026-09-24 | Retro, Modern and Digital themes on a cached static layer plus a per-frame dynamic pass; auto-range in the engine (10 tests); theme carousel (swipe and ‹ ›); startup sweep; landscape car-mount layout; new settings. 19 Roborazzi golden images verified by `check`. Verified on emulator: all themes in portrait and landscape, and settings take effect (cream dial, overall average, fixed dial) |
| M6 Remaining themes + map + nerd | done | Night Focus, Speed Tape, Synthwave, Sunlight; Nerd page + info strip + heading/G switches; Map (MapLibre + OpenFreeMap dark, speed-coloured live route, puck, course/north-up, re-centre, speed card; verified offline from cache); AGSL effects (backlight glow, glass reflection, numeral bloom) with gradient fallbacks and a GPU effects setting. 41 goldens incl. 5 shader-off. |
| M7 Target + step mode | done | Engine target (SetTarget/ClearTarget, 180 s trend, ETA, arrive-by needed/ahead/LATE, arrival + distance past; carries over new sessions from zero; 9 tests). Target dialog (km presets, arrive-by time picker), target strip with haptic + flash on arrival, Modern ring, Night Focus last-km/reached warning, Tape annunciation, notification ProgressStyle (API 36+) / progress bar. Step mode moved from dev options to Settings → Mode. |
| M8 Reliability + power + mock provider | done | Background reliability screen (ticks, "Allow all the time" with explanation, battery exemption, OEM instructions + deep links for Xiaomi/Samsung/OnePlus-OPPO-Realme/Vivo/Huawei-Honor), one-time offer card on first recording, build flags. Sticky restart only with both upgrades (verified: kill → FGS back in ~1.4 s and trip continues; without exemption the restart is skipped and the trip auto-resumes on reopen). Live meter auto-stop after 15 min parked in background (dev: 20 s; verified). Sensor audit: compass/gyro and NMEA now also gated on app visibility. Debug mock provider + adb hook + `tools/mockstream.py` (verified driving Map/Nerd with MOCK badge). Raw GPS logger (dev). Engine: gap bridges > 360 km/h rejected as teleports. |
| M9 Polish + release prep | todo | |

## Decisions made during implementation

- **Reliability:** a background FGS start needs the battery exemption, and background location is needed for it to receive fixes, so the sticky restart requires both. Otherwise the service stops and the snapshot resumes on reopen. `mockstream.py` runs adb in its own session, because Ctrl+C and `timeout` signal the whole process group, and the MOCK_STOP cleanup must still reach the phone.

- **Target** lives only in the engine snapshot (no Room columns), so resume carries it. A target counts from the distance when it was set, and survives Reset/Start/Stop by rebasing to the new zero. The engine keeps a monotonic↔UTC anchor so ETA maths stays pure.

- **AGSL effects** live in `gauges/Effects.kt`: dithered glow and domed-glass shaders on API 33+, gradients below or when the GPU effects setting is off. Robolectric's native graphics renders RuntimeShader, so both paths are screenshot-tested. The bloom fallback is plain text (a stroked halo looked like a double outline on thin fonts).

- **Map:** MapView runs in TextureView mode (a SurfaceView doesn't composite inside Compose's Crossfade). Course comes from the route's last points first, because the emulator (and some receivers) report a stale 0° bearing. The live route is kept in memory by `LiveRoute` for the current session (live or trip) and restarts with each session. Horizontal swipes on the map pan it, so theme switching there uses the header arrows. The universal APK is ~51 MB because of MapLibre's native libraries; M9 should add ABI splits.

- **SDK levels:** compileSdk 37 (the Compose BOM needs it); targetSdk 36.
- **Launcher icons** stay in `mipmap-anydpi-v26`, because aapt2 can't find them in plain `mipmap-anydpi`.
- **The simulator core** (vehicle physics + noisy GNSS) lives in `:engine` (`engine.sim`), so engine tests and the app's dev simulator share it.
- **Nerd-only sensor data** (heading, G-force, satellites, NMEA) stays out of the deterministic engine. The app publishes it separately because it doesn't affect stats.
- **Zero clamp:** releasing the zero clamp needs 2 consecutive updates where both the estimate and the reading are > 0.8 m/s, so a parked phone never flickers. A filter reset releases it immediately.
- **Tracking accuracy:** at 1 Hz, speed error is < 2 km/h worst case and < 0.7 km/h on average with the lab's q = 6. The filter is deliberately responsive, and the needle spring does the visual smoothing.
- **Resume:** `EngineState.resumedAfter(gapMillis, nowNanos)` re-bases stored monotonic times, so resume works across reboots too.
- **Developer options:** tap Version 7× in Settings. The Simulator switch runs `SimulatorSource` (the shared `SimRig` on the app clock, feeding `TrackingEngine`).
- **Emulator helper:** `scratchpad/emu.sh` provides wait_boot, install, launch, shot, tap_text, hold_text, texts and crashes.
- **Emulator GPS feed:** `scratchpad/feed.sh <kmh> <seconds>` drives `adb emu geo fix lon lat alt sats knots` at 1 Hz. The velocity argument arrives as `Location.speed`, so the engine sees DOPPLER.
- **Service lifecycle:** `TrackingService` starts from `MainActivity.onStart()` (and after permission grants) when location is allowed. "Stop tracking" resets the live meter and stops the service. `SourceManager` runs GPS and step sensors only while the service is active, and never while the simulator is on.
- **Motion:** `MotionSource` is on-demand (acquire/release). Heading comes from the rotation vector, using the flatter of the top-edge and back-camera axes. Yaw rate is the gyro · gravity, so it's independent of how the phone is mounted.
- **Recorder bugs caught by device verification (both fixed):**
  - The "last stored fix" sentinel `Long.MIN_VALUE` overflowed in a subtraction, so no points were saved. It's now nullable.
  - The adoption marker was cleared by the engine's initial blank state, so a resumed trip got a new row. The marker is now cleared only when used.
- **Parked jitter:** while the speed reads 0 and the phone hasn't moved beyond `max(hAcc, 8 m)`, points aren't stored, so routes don't draw squiggles at stops.
- **Developer resume tests:** the dev panel has "Kill app" and "Kill, 31 min later" (debug builds only).
- **Auto-range lives in the engine:** `RangeState` in `EngineState`, updated on fixes and ticks, and reset by Reset / StartTrip / StopTrip. Pause and Resume don't reset it. The animated transition is UI-side (`GaugeDriver`, 450 ms ease).
- **Gauge rendering:** `GaugeView` records `drawStatic` into a GraphicsLayer inside `drawWithCache`, keyed by `StaticKey` through `derivedStateOf`, and runs `drawDynamic` every frame. The frame provider goes through `rememberUpdatedState`.
- **Text:** drawn with the native canvas using cached `Paint`s on the bundled OFL fonts, with variable weights set once per Paint.
- **Emulator frame timings** (SwiftShader software GPU, 1344×2992): about 4 ms frame loop, 5–7 ms draw recording and 5–7 ms GPU. Per-frame recomposition was checked and there is none. Real-device numbers are for M9's Macrobenchmark.
- **Landscape:** the Speed tab hides the tab bar, and the session controls move into the header (as in the Theme Lab).
- **Screenshot tests:** `./gradlew :gauges:recordRoborazziDebug` re-records the goldens in `gauges/src/test/screenshots`, and `check` runs `verifyRoborazziDebug`.
- **Emulator helper:** `tools/emu.sh tapdesc "Next theme"` taps by content description; `rotate 1|0` switches landscape and portrait.

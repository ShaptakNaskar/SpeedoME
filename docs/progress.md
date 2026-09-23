# Progress log

The working log for building SpeedoME milestone by milestone (plan: [`plan.md`](plan.md) §12). Each milestone goes through **implement → test → verify on the emulator → commit**.

## How to resume after a break

1. Re-read this file, then `plan.md` §4–§11 for the milestone at hand.
2. Run `./gradlew check` and make sure it passes before changing anything.
3. Start the emulator headless for visual checks:
   `~/Android/Sdk/emulator/emulator -avd Phone_67_A16 -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect -port 5584`
   Then wait for `sys.boot_completed`, install the debug APK and use `screencap` / `uiautomator dump`.
4. Take library versions from `gradle/libs.versions.toml`. For new libraries use the latest stable releases (looked up 2026-09-24): serialization-json 1.11.0, coroutines 1.11.0, Room 2.8.5 + KSP 2.3.12, DataStore 1.2.1, MapLibre 13.6.1, Roborazzi 1.75.0, Robolectric 4.17, Turbine 1.2.1, benchmark 1.5.0, profileinstaller 1.4.1, androidx.test.ext:junit 1.3.0, uiautomator 2.4.0.

## Milestones

| # | Status | Notes |
|---|---|---|
| M0 Skeleton | done 2026-09-24 | commit 0587ecb |
| M1 Engine core | done 2026-09-24 | 28 JVM tests: filter, spikes, trains, no-Doppler, parked, tunnel, city distance, 10 Hz, pause, trips, steps, snapshot, resume (same boot + reboot), replay formats |
| M2 Simulator + debug screen | done 2026-09-24 | Verified on emulator: City/Highway/Walk presets, spike rejected, signal loss → NO FIX, no speed field → POSITION, trip start/pause/resume/stop |
| M3 Tracking service + sensors | done 2026-09-24 | Verified on emulator: permission dialogs chain (location → notifications), foreground `location` service with live notification, real LocationManager fixes (geo fix + velocity) → DOPPLER source, GnssStatus satellites, notification keeps updating with screen off, denied / coarse-only / step-permission cards |
| M4 Storage + resume + Trips | in progress | |
| M5 Gauge toolkit + first themes | todo | |
| M6 Remaining themes + map + nerd | todo | |
| M7 Target + step mode | todo | |
| M8 Reliability + power + mock provider | todo | |
| M9 Polish + release prep | todo | |

## Decisions made during implementation

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

# SpeedoME: Design and Implementation Plan

*Status: design approved 2026-09-24 · Platform: Android only · Stack: Kotlin + Jetpack Compose*

Visual and behavioural reference: [`theme-lab.html`](theme-lab.html) (open it in a browser). It contains working reference versions of the speed filter, auto-range rules, needle springs, odometer drum and all nine themes. When this document and the lab disagree, this document wins; where the document is silent, copy the lab's constants.

---

## 1. Understanding summary

- **What:** SpeedoME, an Android-only GPS speedometer and trip computer, built natively in Kotlin + Jetpack Compose. Three tabs: **Speed** · **Trips** · **Settings**.
- **Why:** accurate speed with no GPS glitches (no 200 km/h spikes), a dial that adapts to how fast you're going, trips that survive the app being killed, and a look people enjoy using.
- **Who:** drivers with the phone in a mount, walkers and joggers (step mode), and GPS nerds.
- **Speed tab:**
  - Nine swipeable themes: Retro · Modern · Digital · Night Focus · Map · Nerd · Speed Tape · Synthwave · Sunlight.
  - Auto-range, which can be switched off.
  - Moving / overall / both averages, max speed, distance and time.
  - Target distance with arrival time and arrive-by.
  - Drive and step modes.
- **Trips:**
  - A live meter counts from the moment the app opens but is never saved.
  - Start/Stop records a trip to history, with GPX export.
  - Killed mid-trip → auto-resume if the gap is under 30 min, otherwise ask.
- **Constraints:**
  - Screen stays on while open.
  - Tracking continues in the background with a notification.
  - Battery-optimization exemption.
  - Optional "Allow all the time" location for self-healing restarts.
  - Fully testable with fake GPS (in-app simulator plus Android mock location).
- **Not in v1:** iOS, HUD mode, floating over other apps, speed alerts (deferred), Health Connect (dropped for now), Tachymeter theme, turn-by-turn navigation or routing, accounts or cloud sync, ads, analytics.

## 2. Assumptions (confirmed unless marked)

- **Pressing Start begins a fresh trip at zero.** The unsaved live counts are dropped. After Stop, a fresh live meter begins. The live meter has a Reset button.
- **Live meter in the background:** the unsaved live meter keeps counting in the background, but stops itself after ~15 min parked while the app is backgrounded. Recorded trips never stop on their own.
- **Night Focus defaults:** 0–260 dial, lit up to 140, green on black with an orange needle.
- **Play Store ready:** built so it can go on the Play Store (privacy policy, permission justifications), even if first installs are sideloaded. Play-sensitive features sit behind build flags.
- **Android version:** Android 8.0+ (minSdk 26). compileSdk 37 (required by Compose BOM 2026.09); targetSdk 36 until the Android 17 behaviour changes are reviewed in M9. GPU shader effects need Android 13+, with gradient fallbacks below that.
- **Display:**
  - Portrait and landscape.
  - km/h by default, mph as an option.
  - True-black backgrounds on dark themes.
- **Privacy:**
  - Everything stays on the phone: no account, server, analytics or ads.
  - Location leaves the device only through map tile requests and exports the user triggers.
- **Scale:** one user, one device. Multi-hour trips (tens of thousands of points) and hundreds of saved trips must stay fast.
- **Performance:**
  - The needle animates at the display refresh rate (60–120 Hz) without dropped frames.
  - A new GPS fix reaches the screen within one frame.
  - GPS runs at the fastest rate the phone offers while tracking.
  - GPS is fully off when nothing is tracking.
- **Reliability:** at most ~2 s of data lost on a kill.
- **Build environment:** builds on the developer's Linux machine (Android SDK 36, JDK 17) with no Google Play Services dependency.

## 3. Decision log

| # | Decision | Alternatives considered | Why |
|---|---|---|---|
| D1 | Android only | iOS later via Kotlin Multiplatform; both from day one; Flutter | iOS hides satellite data, has no battery-optimization dialog or persistent notification, and needs a Mac to build. The engine stays Android-free, so an iOS port stays cheap. |
| D2 | Kotlin + Jetpack Compose | Flutter; C++/Rust through the NDK | Drawing and animation are roughly equal to Flutter. Every hard part (GNSS status, steps, foreground service, battery dialog) is a native Android API, so no bridge is needed. "Bare metal" gains nothing: the GPS chip (1–10 Hz) is the bottleneck, not the CPU. |
| D3 | Name: **SpeedoME** | Doppler, Needle, Tachy, Vmax, Dialed | Owner's choice. |
| D4 | Speed comes from the GNSS chip's own Doppler speed. It passes through gating and a Kalman filter (speed + acceleration), with position-derived speed as the fallback. | Differencing two positions | Position differencing is what causes 200 km/h spikes (a 50 m position jump in 1 s = 180 km/h). |
| D5 | Never reject a reading for being fast; reject only impossible jumps | A hard speed cap | Trains (300 km/h) and planes (850 km/h) are real. |
| D6 | Auto-range ladder. Shrink behaviour is a setting (default: shrink with delay). Can be turned off (fixed dial). | Fixed dial only | Owner wants it user-selectable. |
| D7 | Step mode dial is 0–10 and grows to 0–20 | Fixed 0–10; treat > 10 km/h as an error | A jog (8–12 km/h) shouldn't pin the needle. |
| D8 | Average display is a setting: moving / overall / both (default both) | One fixed average | Owner's choice. |
| D9 | Live meter (unsaved) + Start/Stop recorded trips | Auto-record everything | Quick glances shouldn't clutter Trips. |
| D10 | Resume automatically if the gap is < 30 min, otherwise ask | Always ask; always auto | Seamless for short kills, safe for stale sessions. |
| D11 | Nerd mode is its own theme page, plus an optional strip under any theme. Heading and G-force have separate switches. | Strip only; page only | Owner: both. |
| D12 | Nine themes (Retro, Modern, Digital, Night Focus, Map, Nerd, Speed Tape, Synthwave, Sunlight) | Tachymeter, race shift-lights, CRT terminal, airspeed dial | Owner picks. |
| D13 | Night Focus: fixed dial, customizable lit range, no auto-range | Auto-range on every theme | Range changes would defeat a minimal night display. |
| D14 | HUD dropped; floating-over-Maps replaced by the Map theme; speed alert deferred; Health Connect dropped for now | — | Owner's scope calls. |
| D15 | One process; a foreground service owns the engine | Separate tracking process; engine in the UI | Simplest, one source of truth. Persistence and resume cover crashes. |
| D16 | Gauges drawn in Compose Canvas + AGSL shaders | Rive; raw OpenGL/Vulkan | Full control of data-driven scales. GPU effects without hand-building text and layout. |
| D17 | "Allow all the time" location as an optional upgrade | While-in-use only | Together with the battery exemption, it lets the service restart itself mid-trip. The app works fully without it, and it can be switched off per build if Play objects. |
| D18 | Foreground service type `location` only | `location` + `health` | The hardware step counter counts on its own, and it saves a Play declaration. |
| D19 | No permanently held CPU wake lock | Hold a partial wake lock while recording | Each GPS fix wakes the CPU. Add a short per-fix wake lock only if screen-off tests show dropped fixes. |
| D20 | MapLibre Native + OpenFreeMap vector tiles, custom dark style | osmdroid raster; Google Maps | Free, no API key, fully stylable, OpenStreetMap data. |
| D21 | Distance = integrated filtered speed; straight line across gaps | Sum of position deltas | No creep while parked, and curves are measured correctly. |
| D22 | Accept mock locations from any source (MOCK badge). The debug build can itself be the mock provider, with an adb hook. | Reject mock locations | Everything can be tested without driving. |
| D23 | Room/SQLite, ≤ 1 stored point per second. One transaction per second writes the points plus an engine snapshot. | Periodic saves | ≤ 2 s loss, and resume is exact. |
| D24 | Keep-screen-on window flag | Screen wake lock | Needs no permission and releases automatically. The screen wake lock is deprecated anyway. |
| D25 | Needle follows the filter's prediction between fixes (speed + acceleration × time), through a per-theme spring | Step once per fix | Validated in the Theme Lab: smooth at 1 Hz. |
| D26 | Manual dependency wiring (a small app container), no DI framework | Hilt, Koin | A small app with fewer dependencies builds faster. |

---

## 4. Architecture

```
GPS fixes · GNSS status · NMEA · step counter/detector · rotation vector · accelerometer
                                   │  (background thread)
                    ┌──────────────▼───────────────┐
                    │ TrackingService (foreground,  │  notification, wake rules
                    │   type=location)              │
                    │  ┌─────────────────────────┐  │
                    │  │ engine (pure Kotlin)    │  │  gate → Kalman → stats →
                    │  │ reduce(state, event)    │  │  auto-range → ETA → steps
                    │  └───────────┬─────────────┘  │
                    └──────────────┼────────────────┘
                 StateFlow<TrackState>   accepted points + snapshot (every ~1 s)
                    ┌──────────────▼──┐        ┌──────────────┐
                    │ Compose UI      │        │ Room/SQLite  │
                    │ gauges, trips   │        │ (WAL)        │
                    └─────────────────┘        └──────────────┘
```

### Modules

| Module | Kind | Contents |
|---|---|---|
| `:engine` | Kotlin/JVM library, **no Android imports** | Event types, `EngineState`, `reduce()`, Kalman filter, gates, distance/stats, auto-range, ETA / arrive-by, step maths, GPX writer, replay harness. Unit-tested on the JVM. It is also the module an iOS port would reuse through Kotlin Multiplatform. |
| `:gauges` | Android library (Compose) | Gauge building blocks and the nine themes. Input: `TrackState` + `ThemeSettings`. Previews and screenshot tests. |
| `:app` | Android application | `TrackingService`, sensor adapters, Room, DataStore, screens (Speed / Trips / Settings), the map, dev tools, and the mock provider (debug only). |

### Engine API sketch

```kotlin
sealed interface EngineEvent {
  val tNanos: Long                                   // elapsedRealtimeNanos
  data class Fix(override val tNanos: Long, val lat: Double, val lon: Double,
                 val hAcc: Float?, val altM: Double?, val vAcc: Float?,
                 val speed: Float?, val speedAcc: Float?,      // null = chip gave none
                 val bearing: Float?, val bearingAcc: Float?, val isMock: Boolean) : EngineEvent
  data class Steps(override val tNanos: Long, val counterTotal: Long) : EngineEvent
  data class StepDetected(override val tNanos: Long) : EngineEvent
  data class Heading(override val tNanos: Long, val deg: Float, val accuracy: Int) : EngineEvent
  data class Accel(override val tNanos: Long, val fwd: Float, val lat: Float) : EngineEvent
  data class Satellites(override val tNanos: Long, val sats: List<Sat>) : EngineEvent
  data class Tick(override val tNanos: Long) : EngineEvent        // ~1 Hz housekeeping
  data class Command(override val tNanos: Long, val cmd: Cmd) : EngineEvent // start/stop/pause/reset/target…
}
fun reduce(s: EngineState, e: EngineEvent, cfg: EngineSettings): EngineState   // pure, deterministic
```

- **`EngineState`** is serializable (kotlinx.serialization). It is the resume snapshot, filter internals included.
- **`TrackState`** is the UI projection: the filtered speed, the display-target function inputs (speed, acceleration and fix time), the range, stats, target info, GPS quality and nerd data.
- **Threading:** sensors post events to a single-threaded dispatcher that owns the engine. The UI collects `StateFlow<TrackState>` and draws every frame. Neither side ever waits on the other.

## 5. Speed pipeline

### Sources

Every speed becomes *value ± uncertainty*:

| Source | When | Uncertainty used |
|---|---|---|
| Chip (Doppler) speed + accuracy | `hasSpeed()` and `hasSpeedAccuracy()` | Reported speed accuracy |
| Chip speed without accuracy | `hasSpeed()` only | Cautious value derived from position accuracy |
| Position-derived | No speed field | Displacement over the last ~2–3 s. Movement smaller than 0.6 × the combined position accuracy counts as 0. σ ≈ (hAcc₁+hAcc₂)·0.35/Δt. |
| Steps (step mode) | Cadence > 0 | cadence/60 × learned stride. σ 0.3 m/s when GPS is weak, 0.6 m/s otherwise. |

GPS comes from `LocationManager.GPS_PROVIDER` with `minTime = 0` and `minDistance = 0`, so updates arrive at the fastest rate the chip supports. There is no Google Play Services dependency.

### Stages

1. **Gate:**
   - Drop fixes that are stale or out of order (by `elapsedRealtimeNanos`).
   - Positions with accuracy worse than 20 m are kept off the route and never bridge gaps.
   - Ignore a speed whose implied acceleration versus the last estimate exceeds **1.5 g**.
2. **Kalman filter.**
   - State: [speed, acceleration]. Acceleration decays with τ = 3 s. Jerk noise q = 6.
   - Innovation gate at **4σ**: a reading further than that from the prediction is rejected.
   - **Recovery:** after **3 consecutive rejects** whose spread is ≤ max(2 m/s, 3σ), the filter resets to the latest reading, so it can never get stuck.
3. **Zero hysteresis:** the output snaps to 0 below 0.45 m/s and releases above 0.8 m/s. A parked car shows a steady 0.
4. **Display:**
   - Between fixes the needle chases a target of speed + acceleration × (time since fix), extrapolated at most 1.5 s and never below 0.
   - The needle follows that target through a per-theme spring (table in §9).
   - Digital readouts change only when the value moves more than 0.6 away from the shown integer.

### Distance, time, max

- **Distance:** trapezoidal integral of the filtered speed between fixes that are ≤ 3.2 s apart. Across longer gaps it adds the straight line between the last and the next good fix, which is drawn dashed on the route.
- **Moving time:** counts while speed is > 2 km/h (drive), or when there was a step in the last 5 s (step mode). A gap counts as moving if the bridged distance ÷ gap time is above that threshold.
- **Max speed:** comes only from the filtered speed.

### GPS quality dot

| Colour | Meaning |
|---|---|
| Green | Good fix with chip speed |
| Amber | Position fallback, or accuracy worse than ~10 m |
| Red | No fix for more than 3 s |

## 6. Feature logic (all in `:engine`)

### Auto-range

- **Ladders:**
  - Drive: 20 · 40 · 60 · 80 · 120 · 160 · 200 · 260 · 320 · 500 · 1000 km/h, starting at 0–20.
  - Step: 10 · 20.
- **Grow:** when speed ≥ 90 % of the dial, jump to the smallest range that fits where you'll be in ~2 s: `v + max(0, a)·2 s < 90 %`. Hard acceleration skips a step.
- **Shrink policy** (setting):
  - *With delay* (default): drop one step after 15 s below 80 % of the next-smaller range.
  - *Only grow* (until Reset/Stop).
  - *Immediate*: always the smallest range where v < 90 %.
  - *Off*: a fixed dial chosen by the user.
- **Animation:** the scale max eases over 450 ms. Old labels fade out while new labels fade in. The needle is always mapped with the current animated max.
- **Where it applies:** Retro, Modern, Digital (bar graph), Synthwave (bar), Sunlight (bar), and the Map's route-colour scale. It does not apply to Night Focus (fixed dial), Speed Tape (infinite tape) or Nerd.

### Stats and averages

- **Moving average** = distance ÷ moving time. **Overall average** = distance ÷ elapsed time. Paused time counts toward neither.
- **Average display** setting: moving / overall / both (default: both).

### Target distance and arrive-by

- Remaining = target − distance since the target was set.
- **Arrival estimate:** uses the trend speed, which is the average over the last 180 s with stops included. If there are fewer than 15 s of history, it uses the current speed instead. It shows "—" if the trend is < 0.3 m/s.
- **Arrive-by:**
  - Needed average = remaining ÷ time left.
  - Ahead/behind = arrive-by − arrival estimate.
  - Shows "LATE" once the deadline has passed.
- **On arrival:** haptic + animation, then shows how far past the target you've gone.
- Progress also appears in the notification (`Notification.ProgressStyle` on Android 16+, a standard progress bar below that).

### Step mode

- **Step counting:** `TYPE_STEP_COUNTER` provides exact totals (delta from session start). `TYPE_STEP_DETECTOR` provides live cadence (steps/min over ~10 s). Both need the `ACTIVITY_RECOGNITION` permission (API 29+).
- **Stride:** learned whenever GPS is good, as an EMA of chip speed ÷ step frequency. Separate values for walking (< 140 spm) and running cadence. Default 0.74 m.
- **Step speed:** feeds the Kalman filter as the third source.
- **Display:** pace (min/km), cadence and steps. Dial range 0–10 → 0–20.

### Night Focus lighting

- The lit portion runs from 0 to the focus speed (setting: 60–160, default 140).
- The upper scale fades in (~0.3 s) once the needle reaches focus − 5, and fades out below focus − 10.
- Everything else stays dark unless it needs attention: GPS lost, ≤ 1 km to the target, target reached, or phone battery < 15 %.
- Brightness is a setting (dim / mid / full).

## 7. Sessions, storage and resume

### Session model

| | Live meter | Recorded trip |
|---|---|---|
| Starts | When the app opens (Speed tab) | **Start**: a fresh trip at zero, and the live meter is dropped |
| Controls | Reset | Pause / Resume / **Stop** |
| Ends | Reset, "Stop tracking" in the notification, or ~15 min parked in the background | Stop opens a summary sheet. The trip is saved by default, with a Discard button. A fresh live meter then begins. |
| In Trips | Never | Yes |

### Room schema (WAL mode)

- **`session`**
  - Fields: `id`, `kind` (live/trip), `state` (active/paused/finished), `mode` (drive/step), `startedAt`, `endedAt`, `name`.
  - Target: `targetKm`, `arriveBy`.
  - Totals: `distanceM`, `movingS`, `elapsedS`, `maxMps`, `steps`.
  - `engineSnapshot` (blob, serialized `EngineState`).
- **`point`**
  - Keys and time: `sessionId`, `t` (UTC ms), `segment`.
  - Position: `lat`, `lon`, `alt`, `hAcc`, `vAcc`.
  - Speed: `rawSpeed`, `filtSpeed`, `speedAcc`, `source` (doppler/position/steps).
  - `bearing`, `isMock`.
  - At most 1 point per second is stored, even on faster chips (the engine still uses every fix). A 10-hour drive is about 4 MB.
- **Write path:** accepted points go into an in-memory buffer. About once a second, **one transaction** inserts the buffered points and updates the session row, including the fresh snapshot. A kill loses at most ~2 s.

### Resume (on app start or service restart)

- **Gap < 30 min:** continue automatically.
  - A new segment starts, and the gap is drawn dashed.
  - The straight-line distance is added. The gap counts as moving time only if you clearly moved.
  - The gap can never set a max speed.
  - Toast: "Resumed: 12 min gap".
- **Gap ≥ 30 min:**
  - Trip: ask Resume / Save & finish / Discard.
  - Live meter: ask Continue / Start fresh.

### Trips tab

- **List:** date, mode, distance, duration, averages, max, and a route thumbnail.
- **Detail:** map with the route coloured by speed, all stats, and a speed-over-time graph.
- **Actions:** rename, delete, export GPX.
- **GPX export:** GPX 1.1 with time, elevation and speed (Garmin TrackPointExtension). It goes through the share sheet or a save-to-folder picker (Storage Access Framework).

## 8. Background, permissions and power

- **Screen:** `FLAG_KEEP_SCREEN_ON` on the activity window while the app is visible. There is no screen wake lock.
- **Tracking service:**
  - Foreground service, `foregroundServiceType="location"`, started as soon as the Speed tab opens. Starting while visible is what keeps location flowing in the background under the while-in-use permission.
  - Returns `START_STICKY`.
  - If a trip or live meter is active and the process was killed, a sticky restart resumes tracking in the background. This needs **both** "Allow all the time" location and the battery-optimization exemption. Without them it falls back to resuming when the app is reopened.
- **Notification** (silent, ongoing, low importance):
  - Shows speed, distance and time, plus target progress and arrival time when a target is set.
  - Actions: live meter → **Record** · **Stop tracking**; recording → **Pause** · **Stop**.
- **Sensors on demand:** compass and accelerometer run only when the Nerd page or the heading / G-force switches are visible. NMEA only on the Nerd page. In the background, only GPS runs (plus the step counter in step mode).
- **CPU:** no permanently held wake lock (D19). Screen-off tests decide whether a per-fix wake lock is needed.

### Permissions (asked when first needed, with one line of why)

1. **Precise location:** required. If the user grants approximate only, a banner explains that speed needs precise location.
2. **Notifications** (Android 13+).
3. **Physical activity:** on first entry to step mode.
4. **"Allow all the time" location:** optional upgrade, preceded by an explanation screen. On Android 11+ it opens system Settings. Gated by build flag `BACKGROUND_LOCATION_ENABLED`.
5. **Battery optimization:** `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). Falls back to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. Gated by build flag `BATTERY_EXEMPTION_DIALOG`.

### Background reliability checklist (Settings)

- A green tick for each item above.
- Brand-specific instructions, with a deep link where the OEM exposes one, for:
  - Xiaomi/Redmi/POCO (Autostart + "No restrictions").
  - Samsung ("Never sleeping apps").
  - OnePlus/Oppo/Realme (battery "Don't optimize" + auto-launch).
  - Vivo.
  - Huawei/Honor.
- The first time recording starts, a card offers the walkthrough.

## 9. Rendering and themes

### Toolkit (`:gauges`)

- **Building blocks:** dial face, scale (ticks + labels with range cross-fade), needle, arc bar, 7-segment digits (with ghost segments), rolling odometer drum (tenths digit inverted), segmented bar, stat chips, target ring.
- **Caching:** static layers use `drawWithCache` / `graphicsLayer`. Only the needle, digits and glow redraw each frame.
- **AGSL effects** (`RuntimeShader`, API 33+): backlight glow, glass reflection, numeral bloom. Below 33 they fall back to gradients.
- **Fonts:** bundled, open-licence (OFL) fonts:
  - Oswald: Retro.
  - Outfit: Modern / Map card / Sunlight.
  - Barlow Semi Condensed: Night Focus / app chrome.
  - B612 Mono: Nerd / Speed Tape / Digital labels.
  - Exo 2 Black Italic: Synthwave.
  - The 7-segment digits are drawn, not a font.
- **Motion:**
  - Startup sweep: 0 → max → 0 over 1.3 s on launch, and on a theme change while stopped. Can be turned off; skipped under reduced motion.
  - Theme switching by swipe or the ‹ › arrows, with a cross-fade. The last theme is remembered.
- **Layout:** every theme has a portrait layout and a landscape layout (gauge left, stats right, for car mounts).

### Needle springs (from the Theme Lab)

ω is the spring's natural frequency; ζ is its damping (below 1 overshoots).

| Theme | ω (rad/s) | ζ | Feel |
|---|---|---|---|
| Retro | 6.5 | 0.5 | Lazy, slight overshoot (cable-driven) |
| Modern | 13 | 1.0 | Tight |
| Digital | 18 | 1.0 | Bar snaps |
| Night Focus | 11 | 1.0 | Calm |
| Map / Nerd / Sunlight | 16 | 1.0 | Readout |
| Speed Tape | 12 | 1.0 | Tape scroll |
| Synthwave | 10 | 0.75 | A little bounce |

### The nine themes

1. **Retro:**
   - Chrome bezel, glass-dome reflection, Oswald numerals, orange needle.
   - Six-digit rolling odometer drum, tenths inverted. It sits between the hub and the 0 / max labels (the lab fix of 2026-09-24 made it narrower and higher).
   - Faces: black (default) or cream.
2. **Modern:**
   - True black, a thick 270° glowing arc with a conic gradient in the accent colour, and a white tip.
   - Large Outfit 200 numerals. An outer ring shows target progress.
   - Accent colour is a setting (amber default).
3. **Digital:**
   - 1980s dash: a 36-segment ramp bar graph (auto-range), large skewed 7-segment digits with ghost segments, and a VFD mesh overlay.
   - Small TRIP / AVG / MAX readouts, also in 7-segment.
   - Colours: VFD cyan (default), LED red, LCD amber.
4. **Night Focus:** see §6. Barlow Semi Condensed, green `#7DFF9A`, orange needle `#FF7A22`.
5. **Map:**
   - MapLibre Native with the OpenFreeMap vector style, restyled dark to match the app.
   - Route coloured by speed (blue → green → amber → red, scaled to the auto-range dial). Gaps dashed.
   - Puck with a heading cone. Travel-direction-up or north-up (setting). Zoom follows speed.
   - Compact speed card bottom-left, OpenStreetMap attribution, re-centre button after panning.
6. **Nerd:**
   - Sky plot (colour = constellation, filled = used in fix, size = C/N0) and a constellation used/in-view table.
   - C/N0 bars.
   - LAT/LON, altitude ± vAcc, hAcc, raw/filtered speed ± accuracy, speed source, rate and fix age.
   - Compass heading vs GPS course, satellites used, filter rejects.
   - G-force circle with trail. Badges: L1+L5, chipset (`getGnssHardwareModelName`), MOCK.
   - Scrolling NMEA.
   - Optional compact **nerd strip** under any theme.
7. **Speed Tape:**
   - Aircraft-style vertical tape (±60 km/h window, ±12 in step mode) with a rolling-digit pointer box.
   - Yellow speed-trend arrow showing the speed 10 s ahead.
   - Altitude tape, heading strip, and PFD-style green/magenta annunciations for stats and target.
8. **Synthwave:**
   - Gradient sky, striped sun, wireframe mountains.
   - A neon perspective grid that scrolls at your real speed.
   - Chrome-italic digits, a cyan speed bar and neon stats.
9. **Sunlight:**
   - White background, huge black Outfit 800 digits, and a thick black range bar.
   - A 2×2 stats grid. Maximum contrast for direct sun.

**On any theme** (Settings switches): nerd strip, heading, G-force. Night Focus hides them.

## 10. Settings inventory

| Group | Items |
|---|---|
| Display | Units (km/h, mph) · startup sweep on/off · average display (moving/overall/both) |
| Show on gauges | Nerd strip · heading · G-force |
| Auto-range | On/off · shrink policy (with delay / only grow / immediate) · fixed dial when off |
| Theme options | Retro face · Digital colour · Night Focus (lit up to, dial max, brightness) · accent colour · map orientation |
| Tracking | Default mode (drive/step) · live meter auto-stop (15 min) |
| Background reliability | Checklist (see §8) |
| Developer (hidden) | See §11 |

## 11. Testing and developer tools

- **Engine tests** (JVM, run in seconds):
  - Synthetic scenarios: accelerate / cruise / brake / stop, tunnel gap, 200 km/h spike, reject-streak recovery, chip with no speed, 300 km/h train, 850 km/h jet, walking with steps, fix rate 1/5/10 Hz.
  - Rule tests: auto-range grow/shrink, ETA and arrive-by maths, resume after gaps of 5 / 29 / 31 / 600 min.
  - **Recorded drives:** raw logs from real phones replayed through `reduce()`. Distance, max and reject counts are compared against golden files.
- **Gauge tests:** screenshot tests (Roborazzi) of every theme at 0, mid, max, mid-range-change, and Night Focus lit/unlit, in portrait and landscape. Shader-off path covered.
- **Performance:** Macrobenchmark frame timing on the Speed tab (target: no janky frames at 120 Hz on a mid-range phone). Baseline Profile for startup.
- **Developer panel** (tap the version number 7×):
  - Simulator presets: Walk, Cycle, City, Highway, Train, Jet, plus manual.
  - Fault buttons: signal loss, spike, drop speed field, kill process.
  - Replay a GPX/NMEA/CSV file.
  - **Raw logger:** writes every raw fix and GNSS status to a file; each log becomes a test.
  - Live filter internals and frame-time readout.
- **Mock location (debug build):**
  - Declares `ACCESS_MOCK_LOCATION`, so it can be picked in Developer options → Select mock location app. It then feeds `GPS_PROVIDER` through `addTestProvider` / `setTestProviderLocation`.
  - An adb hook sends single fixes: `adb shell am broadcast -p <applicationId> -a speedome.MOCK_FIX --ef lat … --ef lon … --ef kmh … --ef acc …`.
  - `tools/mockstream.py` streams GPX/CSV at 1–10 Hz.
  - Release builds accept mock locations from any source and show the MOCK badge.
- **Before release:**
  - A 30 min screen-off drive per available phone brand.
  - A forced kill mid-trip (with and without "Allow all the time").
  - Battery use per hour, screen on and screen off.
  - Doze check: parked 1 h, then drive off.

## 12. Implementation plan

Each milestone ends with something runnable and its acceptance checks passing. The in-app simulator arrives early (M2), so every later milestone can be exercised without driving.

### Repository layout

```
SpeedoME/
├─ engine/            # :engine — pure Kotlin (JVM), tests + recorded-drive fixtures
├─ gauges/            # :gauges — Compose gauge toolkit + 9 themes, screenshot tests
├─ app/               # :app — service, sensors, Room, DataStore, screens, map, dev tools
│  └─ src/debug/      # mock-location provider, adb receiver (debug only)
├─ tools/mockstream.py
├─ docs/plan.md · docs/theme-lab.html
└─ gradle/libs.versions.toml
```

**Build setup:**
- Gradle version catalog; latest stable AGP, Kotlin and Compose BOM at project start.
- compileSdk 37, targetSdk 36, minSdk 26, JDK 17. Pinned at M0: AGP 9.4.1, Gradle 9.7.1, Kotlin 2.4.20, Compose BOM 2026.09.00.
- Build types: `debug` (dev panel on by default, mock provider, adb hook) and `release`.
- Build flags: `BACKGROUND_LOCATION_ENABLED` and `BATTERY_EXEMPTION_DIALOG` (default true), so a Play build can switch either off without code changes.

**Dependencies (all open-source):**
- Compose (BOM), Material 3 (app chrome only), Navigation Compose.
- Lifecycle (service + process), Room, DataStore, Coroutines/Flow, kotlinx.serialization.
- MapLibre Native Android.
- Tests: JUnit, Turbine, Roborazzi, Macrobenchmark + Baseline Profile.
- No Google Play Services.

### Milestones

| # | Milestone | Deliverables | Done when |
|---|---|---|---|
| **M0** | Skeleton | Three modules, version catalog, app with Speed / Trips / Settings tabs (placeholders), true-black app theme, lint + formatting, `./gradlew check` green | App installs on your phone. The engine test task runs. |
| **M1** | Engine core | Event/state types, `reduce()`, gates, Kalman + recovery, zero hysteresis, position fallback, distance / moving / max, snapshot serialization, replay harness | All synthetic scenarios pass (spike rejected, train accepted, no-speed chip works, recovery after 3 rejects). Output matches the Theme Lab behaviour. |
| **M2** | Simulator + debug screen | In-app simulator feeding the engine (presets, signal loss, spike, drop speed), text debug screen showing `TrackState` live, hidden dev-panel switch | Every preset drives believable numbers on the device without GPS. |
| **M3** | Tracking service + sensors | Foreground `location` service started from the Speed tab, `GPS_PROVIDER` at max rate, GnssStatus, NMEA, step sensors, rotation vector, accelerometer on demand, notification (+ actions), keep-screen-on, permission flow 1–3 | Real GPS drives the debug screen. Screen-off: the notification keeps updating. Denied permissions degrade gracefully. |
| **M4** | Storage + resume + Trips | Room schema, per-second transaction with snapshot, live meter vs trip lifecycle (Start / Pause / Stop / Reset / summary sheet), resume rules, Trips list + detail (stats + speed graph), GPX export | Kill during a trip: < 30 min resumes seamlessly (dashed gap), ≥ 30 min asks. Exported GPX opens in OsmAnd. |
| **M5** | Gauge toolkit + first themes | `:gauges` building blocks, spring needle, auto-range (+ settings), startup sweep, odometer drum, theme carousel (swipe + ‹ ›), Retro, Modern, Digital, portrait + landscape, screenshot tests | The three themes match the Theme Lab look. No janky frames at 60/120 Hz with the simulator running. |
| **M6** | Remaining themes + map + nerd | Night Focus, Speed Tape, Synthwave, Sunlight, Nerd page + nerd strip + heading / G switches, Map (MapLibre + dark OpenFreeMap style + speed-coloured route + card), AGSL effects with fallbacks | All nine themes in the carousel. Screenshot tests pass on both shader paths. The map works offline-from-cache for recently viewed areas. |
| **M7** | Target + step mode | Target distance, arrival estimate (180 s trend), arrive-by, notification progress, step mode (cadence, stride learning, pace, step-speed source, 10 → 20 dial) | Engine tests for arrival maths pass. A walk with GPS weak still gives sane speed from steps. |
| **M8** | Reliability + power | "Allow all the time" upgrade flow, battery-optimization dialog, OEM checklist screen, sticky self-restart, live meter 15 min auto-stop, sensor on-demand audit, debug mock provider + adb hook + `mockstream.py` | Forced kill with both permissions: tracking restarts in the background. Mock streaming from the PC drives every theme. |
| **M9** | Polish + release prep | Settings inventory complete, mph, accessibility pass (TalkBack labels, contrast), Baseline Profile, app icon, privacy policy page, Play declarations (FGS location, background location, battery exemption), store listing screenshots | Pre-release device checks (§11) pass on every phone available. |

## 13. Risks and mitigations

| Risk | Mitigation |
|---|---|
| OEM app killers (Xiaomi, Samsung, OnePlus…) stop tracking | Checklist + deep links, sticky restart with background location + exemption, per-second persistence, resume rules |
| Play rejects background location or the battery-exemption permission | Both are optional upgrades behind build flags; the app works fully without them |
| Chips that report no speed or no speed accuracy, or only 1 Hz | Uncertainty-weighted fallbacks; raw logs from real phones become regression tests |
| MapLibre memory/battery, or a native crash taking tracking down | The map loads only while the Map theme or Trip detail is visible; resume covers crashes; re-evaluate the separate-process option (D15) if crash rates warrant |
| OpenFreeMap is donation-funded with no SLA | Style URL is configurable; alternatives are MapTiler or a self-hosted Protomaps file |
| Always-on screen drains battery | True-black themes, sensors on demand, GPS off when idle; a dim-after-idle setting is a later option |
| AGSL unavailable below Android 13 | Gradient fallbacks, screenshot-tested |

## 14. Resolved before coding (2026-09-24)

1. **Application ID:** `com.sappy.SpeedoMe` (debug: `com.sappy.SpeedoMe.debug`). Kotlin packages use the lowercase `com.sappy.speedome`, following Kotlin convention.
2. **Signing:** release builds temporarily reuse the `sappy-release` key from the hanamimi project, via the git-ignored `keystore.properties`. It will be replaced with a dedicated SpeedoME keystore.
3. **Name:** "SpeedoME" (the debug build shows as "SpeedoME Dev").
4. **Distribution:** direct install for now. The Play Store paperwork in M9 is deferred.
5. **Test devices:** a Nothing Phone 2 running stock AOSP Android 17, plus the local emulators (Android 16 images).

**M0 status: done.** Three modules; `./gradlew check` green (engine tests pass, lint clean); debug and signed release APKs verified on the Android 16 emulator; the debug build installs and launches on the Nothing Phone 2.

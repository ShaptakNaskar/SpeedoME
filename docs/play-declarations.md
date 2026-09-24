# Google Play declarations (draft)

This is the text to paste into Play Console forms if SpeedoME is published there. The app is sideloaded for now. Both optional upgrades can be turned off at build time with `BuildConfig.BACKGROUND_LOCATION_ENABLED` and `BuildConfig.BATTERY_EXEMPTION_DIALOG` (`app/build.gradle.kts`) if Play rejects either one.

## Foreground service: `location`

- **Declared type:** `FOREGROUND_SERVICE_LOCATION`.
- **Why it's needed:** SpeedoME is a speedometer and trip recorder. The foreground service keeps receiving GPS fixes while the user drives or walks with the screen off or another app in front (for example a navigation app). It is started only from the visible app or from its own notification actions. It shows an ongoing notification with live speed and Pause/Stop controls.
- **User impact if deferred or stopped:** the speed display and the recorded trip would stop or show gaps in distance and route.
- **Video:** screen recording of the app starting a trip, switching to another app, and the notification continuing to update speed.

## Background location (`ACCESS_BACKGROUND_LOCATION`)

- **Feature:** automatic resumption of a recorded trip after the system kills the app during the trip.
- **Why it's needed:** after Android stops the process mid-trip, the service restarts itself (`START_STICKY`) without the app being in the foreground. Without background location, that restarted service receives no GPS fixes, and the trip has a gap until the user reopens the app.
- **Where it's asked:** Settings → Background reliability, or the one-time card when the first trip starts. An explanation screen comes first, and the app works fully when it is declined.
- **Data use:** location stays on the device (see `docs/privacy.md`).

## `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

- **Acceptable-use category:** the app's core function is continuous location tracking during a user-initiated session (a trip), and battery optimisation delays or drops its GPS updates.
- **Where it's asked:** Settings → Background reliability only, as an optional item with an explanation. The app never asks on its own.

## Data safety form

| Question | Answer |
|---|---|
| Data collected | Location (precise), physical activity (steps). Both are processed on the device only. |
| Data shared | None. |
| Encrypted in transit | Yes: map tiles over HTTPS; no user data is transmitted. |
| Deletion | Trips can be deleted in the app; uninstalling removes everything. |
| Required? | Precise location is required for the core feature; the rest are optional. |

## Content rating and target audience

Utility and tools. No user-generated content, no ads, no purchases. Not designed for children.

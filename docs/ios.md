# iOS build

The iOS app shares the engine (`engine/`, Kotlin Multiplatform) and draws its UI with Compose
Multiplatform (`ios/`). `iosApp/` is a thin SwiftUI host. No Mac needed:

- **Compile check on Linux:** `./gradlew :engine:compileKotlinIosArm64 :ios:compileKotlinIosArm64`
  catches iOS compile errors locally. Linking the framework and building the app need macOS.
- **Build:** every push to `main` that touches the engine or iOS code runs `.github/workflows/ios.yml`
  on GitHub's free macOS runner (free for public repos). Run it by hand from the Actions tab
  (*iOS build → Run workflow*) too. It uploads `SpeedoME-unsigned.ipa` as the run's artifact.
  From a terminal: `gh run download --name SpeedoME-ios-unsigned`.
- **Sign:** with your certificate and provisioning profile, e.g.
  `zsign -k cert.p12 -p PASSWORD -m profile.mobileprovision -o SpeedoME.ipa SpeedoME-unsigned.ipa`
  (zsign runs on Linux), or your usual signing tool. The app needs no special entitlements.
- **Install and debug from Linux:** `ideviceinstaller -i SpeedoME.ipa` (libimobiledevice), live
  logs with `idevicesyslog -p SpeedoME`, crash reports with `idevicecrashreport`.

Status: 0.1 shows live GPS speed through the shared engine. Next: gauges (move `gauges/` to
Compose Multiplatform), then screens, settings, trips (Room KMP) and the map (MapLibre iOS).

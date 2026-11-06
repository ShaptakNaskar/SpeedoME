# Acknowledgements and third-party licences

## Thank you, Waypoint

Special thanks to **[the-geeky-couldron](https://github.com/the-geeky-couldron)** and all
**[Waypoint developers and contributors](https://github.com/the-geeky-couldron/waypoint/graphs/contributors)**
for creating and sharing the map worlds that made SpeedoME's world-map integration possible.

SpeedoME adapts 38 map-style documents and their palettes from
[Waypoint](https://github.com/the-geeky-couldron/waypoint), imported at
[`124e1735ad95a7192f0d29dc21e45e1de2de9301`](https://github.com/the-geeky-couldron/waypoint/tree/124e1735ad95a7192f0d29dc21e45e1de2de9301).
Waypoint's code is MIT licensed: **Copyright (c) 2026 Waypoint contributors**.
Its [full MIT notice](shared/src/commonMain/composeResources/files/worlds/LICENSE-waypoint.txt)
is retained and shipped in both apps.

Our adaptations replace map lettering with Noto Sans, remove runtime texture images,
use neutral world names, and add original SpeedoME speedometers. We do not bundle
Waypoint's proprietary/game fonts or sourced game artwork. Inherited style licences
still apply; Waypoint's MIT licence does not replace them. No affiliation or
endorsement by the original game publishers is implied.

## Components and data

| Component | Licence / attribution |
|---|---|
| SpeedoME application code | [GNU GPL version 3 or later](LICENSE) |
| Waypoint style adaptations | [MIT](https://github.com/the-geeky-couldron/waypoint/blob/124e1735ad95a7192f0d29dc21e45e1de2de9301/LICENSE), Waypoint contributors |
| OSM Liberty / Mapbox Open Styles ancestry | [BSD-3-Clause style code and CC BY 3.0 design](https://github.com/maputnik/osm-liberty/blob/gh-pages/LICENSE.md); [Mapbox's original notices](https://github.com/mapbox/mapbox-gl-styles/blob/master/LICENSE.md) |
| OpenStreetMap data | © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), ODbL 1.0 |
| OpenMapTiles schema/design | © [OpenMapTiles contributors](https://openmaptiles.org/), [CC BY 4.0 and BSD terms](https://github.com/openmaptiles/openmaptiles/blob/master/LICENSE.md) |
| Tiles, remote glyphs and sprites | [OpenFreeMap](https://openfreemap.org/); underlying map/font/icon rights remain with their authors |
| MapLibre Native (Android 13.6.1) | BSD-2-Clause; [full Android/native dependency notices](https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/platform/android/LICENSE.md) |
| MapLibre Compose 0.20.0 | [BSD-3-Clause](https://github.com/maplibre/maplibre-compose/blob/v0.20.0/LICENSE) |
| MapLibre Native FFI 0.202610.0 | [BSD-2-Clause](https://github.com/maplibre/maplibre-native-ffi/blob/core/v0.202610.0/LICENSE); embedded Native core [`43e1060`](https://github.com/maplibre/maplibre-native/blob/43e10605cd5b8b24d1cd411ce9544b70130fc040/LICENSES.core.md) |
| Spatial K 0.8.0 | [MIT](https://github.com/maplibre/spatial-k/blob/v0.8.0/LICENSE) |
| AndroidX, Compose, Kotlin, kotlinx, Skiko | Apache-2.0; upstream notices retained in the bundled notice file |
| Skia | [BSD-3-Clause](https://github.com/google/skia/blob/main/LICENSE) |
| SQLite | [Public domain](https://www.sqlite.org/copyright.html) |
| Oswald, Outfit, B612 Mono, Barlow Semi Condensed, Rye, VT323, Silkscreen, Orbitron, Cinzel, Courier Prime | SIL Open Font License 1.1; [font-specific copyright/licence texts](gauges/src/androidMain/assets/licenses) |
| Noto Sans remote map lettering | SIL Open Font License 1.1; copyright and OFL text included in the shared notices |
| Maki remote map icons | [CC0 1.0](https://github.com/mapbox/maki) |
| Terrain/hill shading | [AWS Terrain Tiles](https://registry.opendata.aws/terrain-tiles/), Mapzen/Tilezen; [provider-specific attribution](https://github.com/tilezen/joerd/blob/master/docs/attribution.md) includes USGS, NOAA, Copernicus and national data providers |

The complete [bundled licence and attribution notices](shared/src/commonMain/composeResources/files/licenses/THIRD_PARTY_NOTICES.txt)
are packaged through shared Compose resources on **Android and iOS**, including the ten
bundled font families, Waypoint and inherited style notices, native map dependencies,
terrain providers, and SpeedoME's GPL text. They can be read in
**Settings → About SpeedoME → Open-source licences → Full notices**.
Third-party works retain their own licences; SpeedoME's GPL applies to its application code.

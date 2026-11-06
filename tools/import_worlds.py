#!/usr/bin/env python3
"""Imports Waypoint's map worlds (MIT, github.com/the-geeky-couldron/waypoint) as SpeedoME map styles.

    tools/import_worlds.py            # writes shared/src/commonMain/composeResources/files/worlds/<id>.json

Only style documents are taken; inherited style licences still apply (ACKNOWLEDGEMENTS.md).
They are rewritten to avoid bundling proprietary fonts or runtime artwork:
- lettering uses OpenFreeMap's Noto Sans (Waypoint bundles game and commercial fonts);
- texture images Waypoint adds at runtime (pixel grass/stone/water, paper fibre) are dropped,
  leaving each layer's own flat colour.
Tiles, sprites and fonts stay on OpenFreeMap, like SpeedoME's own map.
"""
import json
import pathlib
import sys
import urllib.request

COMMIT = "124e1735ad95a7192f0d29dc21e45e1de2de9301"
RAW = f"https://raw.githubusercontent.com/the-geeky-couldron/waypoint/{COMMIT}/app/src/main/assets/styles/"
OUT = pathlib.Path(__file__).resolve().parent.parent / "shared/src/commonMain/composeResources/files/worlds"

# Waypoint style file -> SpeedoME world id (neutral names, see ui/worlds/Worlds.kt).
IDS = {
    "los_santos": "palm_city", "old_west": "old_west", "night_city": "neon_city", "pixel": "pixel_world",
    "green_terminal": "green_terminal", "san_andreas": "back_streets", "heat": "midnight_run", "rivia": "wayfarer",
    "vice_coast": "sunset_coast", "liberty": "concrete_grid", "ctos": "connected_city", "hyrule": "explorer",
    "skyrim": "northern_road", "lands_between": "golden_thread", "horizon": "open_road", "assassin": "desert_sands",
    "far_cry": "backcountry", "ghost": "ink_wind", "bridges": "courier", "horizon_wilds": "wilds", "spider": "skyline",
    "hogwarts": "highlands", "starfield": "surface_survey", "kingdom": "old_kingdom", "saints": "violet_city",
    "just_cause": "islands", "mafia": "prohibition", "division": "quarantine", "sea_chart": "sea_chart",
    "dos_atlas": "dos_atlas", "aincrad": "new_chapter", "counter_strike": "tactical_radar", "defcon": "vector_command",
    "field_ops": "field_ops", "retro_quest": "quest_16bit", "home_console": "home_console", "stalker": "exclusion_zone",
    "nier": "machine_atlas",
}

BOLD = {"Waypoint Condensed", "Waypoint Technical", "Waypoint Borda", "Waypoint Stratum", "Waypoint Terminal"}


def convert(style: dict, world_id: str) -> dict:
    style["name"] = f"SpeedoME {world_id} (from Waypoint, MIT)"
    layers = []
    for layer in style["layers"]:
        paint = layer.get("paint", {})
        if any(v == "paper-fibre" for v in paint.values()):
            continue  # a texture-only overlay
        for key in [k for k in paint if k.endswith("-pattern")]:
            del paint[key]  # every patterned layer also has its own colour
        layout = layer.get("layout", {})
        if "text-font" in layout:
            fonts = layout["text-font"]
            layout["text-font"] = ["Noto Sans Bold" if any(f in BOLD for f in fonts) else "Noto Sans Regular"]
        layers.append(layer)
    style["layers"] = layers
    return style


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for waypoint_id, world_id in IDS.items():
        with urllib.request.urlopen(RAW + waypoint_id + ".json", timeout=30) as r:
            style = convert(json.load(r), world_id)
        text = json.dumps(style, separators=(",", ":"), ensure_ascii=False)
        assert "Waypoint " not in text and "-pattern" not in text, world_id
        (OUT / f"{world_id}.json").write_text(text, encoding="utf-8")
        print(f"{world_id:16} {len(text) // 1024:3} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())

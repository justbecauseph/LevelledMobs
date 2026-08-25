# LevelledMobs: Apotheosis Integration

This Fabric addon makes a mob's LevelledMobs level follow the nearest player's Apotheosis World Tier. It requires Minecraft 26.2, LevelledMobs Fabric 1.x, and Apotheosis 10.x.

## Rule configuration

Select the addon strategy in `config/lampas/levelledmobs/rules.json`:

```json
{
  "apotheosis_scaled_mobs": {
    "priority": 100,
    "min_level": 1,
    "max_level": 50,
    "strategy": {
      "name": "APOTHEOSIS_WORLD_TIER",
      "distribution": "random_partition",
      "no_player": "rule_min"
    }
  }
}
```

Without explicit bands, the effective rule range is divided evenly across Haven, Frontier, Ascent, Summit, and Pinnacle. For a range of 1–50, those bands are 1–10, 11–20, 21–30, 31–40, and 41–50.

Explicit inclusive bands can override the automatic partition:

```json
"strategy": {
  "name": "APOTHEOSIS_WORLD_TIER",
  "distribution": "random_partition",
  "no_player": "rule_min",
  "tier_bands": {
    "haven": { "min": 1, "max": 5 },
    "frontier": { "min": 6, "max": 12 },
    "ascent": { "min": 13, "max": 22 },
    "summit": { "min": 23, "max": 35 },
    "pinnacle": { "min": 36, "max": 50 }
  }
}
```

All five bands are required when overriding them. Bands must be ordered, non-overlapping, and inside the rule's `min_level` and `max_level`. Invalid strategy configuration safely uses the rule minimum and logs one warning for that rule/configuration.

## Multiplayer and persistence

Apotheosis World Tier is per player. The addon mirrors Apotheosis's monster behavior by using the nearest player anywhere in the mob's dimension. If no player exists in that dimension, the rule minimum is used.

The tier is consulted only when LevelledMobs first assigns the mob's persistent level. Existing mobs are not relevelled when a player moves or changes World Tier. Apotheosis continues to own its tier augments; the addon neither applies nor removes them.

## Local build and tests

The sibling Apotheosis checkout does not currently expose a loader-specific Maven coordinate. By default the build verifies and consumes this external Fabric artifact without embedding it:

```text
../Apotheosis/fabric/build/libs/Apotheosis-26.2-10.0.0.jar
```

Override the path without changing tracked files:

```powershell
.\gradlew.bat :levelledmobs-apotheosis:build -Papotheosis_fabric_jar=C:\path\to\Apotheosis-26.2-10.0.0.jar
```

The build verifies the JAR's SHA-256, Fabric metadata, mod ID, and version. Override `apotheosis_fabric_sha256` deliberately when validating a newly rebuilt trusted artifact.

The headless Fabric GameTest also uses the exact sibling Fabric outputs for Apotheosis's hard runtime dependencies:

```text
../Placebo/fabric/build/libs/Placebo-26.2-11.0.0.jar
../Apothic-Attributes/fabric/build/libs/ApothicAttributes-26.2-4.0.0.jar
```

Their paths can be overridden with `placebo_fabric_jar` and `apothic_attributes_fabric_jar`. The normal addon build runs the seven pure JUnit tests and the owned GameTest:

```powershell
.\gradlew.bat :levelledmobs-apotheosis:build
```

Run only the live integration test with:

```powershell
.\gradlew.bat :levelledmobs-apotheosis:runGameTest
```

The GameTest boots Minecraft 26.2 on Fabric and verifies nearest-player World Tier selection, entity-load queue processing, unlimited-distance selection, LevelledMobs/Apotheosis modifier coexistence, and persistent levels after a player's tier changes.

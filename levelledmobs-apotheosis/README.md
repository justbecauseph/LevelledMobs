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

## Mobs that are levelled

LevelledMobs Fabric levels adult hostile mobs, meaning every mob that implements `Enemy`: all `Monster` subclasses plus ghasts, phantoms, shulkers, hoglins and the Ender Dragon. Slimes and magma cubes are never levelled because they split on death. Babies are skipped. The managed re-tier listener applies the same filter.

`/lm summon` and `/lm level` keep the level the admin gives, even outside the matching rule's level band. The mob's attributes per level come from the rules that match it, as for natural levelling, or from the built-in defaults when no rule matches.

## Ranged and explosion damage

Damage from a levelled mob's projectiles and from its own blast is multiplied by 1 + v × (level − 1), for players and mobs alike, where v is the fraction per level for that mob. Projectile damage covers everything a projectile the mob owns deals: arrows, tridents, a ghast fireball's hit and explosion, and a witch's splash potion of Harming. A creeper's blast counts as its own; TNT or an End Crystal a mob sets off does not. Melee hits grow through `attack_damage` and are not multiplied. Bosses use their own multiplier instead (below).

The fraction per level comes from the rule that levelled the mob, or from `settings.json` when that rule sets none:

- **Rule attributes.** A rule's `attributes` may set `projectile_damage` (projectiles) and `explosion_damage` (the mob's own blast). Like its other attributes, the value merges by priority, so a higher-priority rule's value wins. These keys do not change the mob's vanilla attributes. A value of `0` turns that damage off.
- **Settings defaults.** `defaultProjectileDamageMultiplier` (default `0.05`) and `defaultExplosionDamageMultiplier` (default `0.01`) in `settings.json` apply to mobs whose rule sets no value. A missing key uses the default. A value that is not a finite number of zero or more is ignored with a warning. The settings apply on start-up and on `/lm reload`.

The mob stores its fraction when it is levelled, relevelled or admin-set, and saves it as `lampas:projectile_damage` and `lampas:explosion_damage`. Editing a rule does not change the fraction of a mob that is already levelled until that mob is levelled again. A mob saved before these values existed stores none and uses the settings defaults.

```json
"attributes": { "projectile_damage": 0.05, "explosion_damage": 0.01 }
```

## Bosses

The Ender Dragon, Wither, Warden, Elder Guardian and anything tagged `c:bosses` or `fabric:bosses` are levelled only when `allowBossLeveling` is `true` in `settings.json`. The setting is applied on start-up and on `/lm reload`. A boss rule gives them their own health per level while the tier rule keeps setting the level band:

```json
"boss_scaling": {
  "priority": 50,
  "conditions": {
    "entities": {
      "include": ["minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian"],
      "include-tags": ["c:bosses", "fabric:bosses"]
    }
  },
  "attributes": { "max_health": 290 }
}
```

Bosses are tuned for two full parties of five:

- **Group factor.** When a boss is levelled, its max health is multiplied by max(10, n) / 10, where n is the number of non-spectator players in its dimension within 128 blocks. Up to ten players face the ten-player boss; fifteen players face 1.5 times its health. The factor is a separate `lampas:levelled/group_factor` modifier (`add_multiplied_total`), saved with the mob as `lampas:group_factor`, and kept on chunk reload, managed relevel and `/lm level`. A boss first levelled by `/lm summon` or `/lm level` counts the players around it the same way. Players who arrive after the boss is levelled do not change it.
- **Damage.** Most boss attacks deal fixed amounts that `attack_damage` never reaches: the Wither's skulls and explosions, the Ender Dragon's head, wings and breath, the Warden's sonic boom, and the Elder Guardian's beam and spikes. A levelled boss's damage of that kind, including damage from projectiles and clouds it owns, is multiplied by 1 + 0.025 × (level − 1) (×5.975 at level 200). This replaces LevelledMobs' projectile and explosion multipliers for that damage. Melee hits from a boss with an `attack_damage` attribute (the Warden's, and the Elder Guardian's hit that follows its beam) already grow through that attribute and are not multiplied again.

Vanilla caps max health at 1024. Boss health above that needs a mod that lifts the cap, such as AttributeFix.

## Multiplayer and persistence

Apotheosis World Tier is per player. The addon mirrors Apotheosis's monster behavior by using the nearest player anywhere in the mob's dimension. If no player exists in that dimension, the rule minimum is used.

In standalone mode the tier is consulted only when LevelledMobs first assigns the mob's persistent level. Existing mobs are not relevelled when a player moves or changes World Tier. Apotheosis continues to own its tier augments; the addon neither applies nor removes them.

## Managed mode (Yggdrasil)

When Yggdrasil installs the Apotheosis tier provider, the mobs it manages take their tier and level bounds from their creation context instead of from nearby players. When that context changes, a relevel is queued for the mob. The relevel moves the mob into the new tier band and keeps its rule id. Its health is kept by ratio, and its level and modifiers are replaced. A level that is still inside the band is left alone. Admin-set and quarantined mobs are never relevelled.

If the managed context is briefly unavailable, for example while content is being published, the relevel is retried on later ticks for up to 1200 attempts. After that the mob is dropped from the queue and keeps its current level. A retry never quarantines the mob.

Loading a mob never relevels it, so edits to the band settings in `rules.json` do not reroll existing mobs. Standalone behaviour, with no provider installed, is unchanged.

## Local build and tests

The sibling Apotheosis checkout does not currently expose a loader-specific Maven coordinate. By default the build verifies and consumes this external Fabric artifact without embedding it:

```text
../Apotheosis/fabric/build/libs/Apotheosis-26.2-10.0.1.jar
```

Override the path without changing tracked files:

```powershell
.\gradlew.bat :levelledmobs-apotheosis:build -Papotheosis_fabric_jar=C:\path\to\Apotheosis-26.2-10.0.1.jar
```

The build verifies the JAR's SHA-256, Fabric metadata, mod ID, and version. Override `apotheosis_fabric_sha256` deliberately when validating a newly rebuilt trusted artifact.

The headless Fabric GameTest also uses the exact sibling Fabric outputs for Apotheosis's hard runtime dependencies:

```text
../Placebo/fabric/build/libs/Placebo-26.2-11.0.1.jar
../Apothic-Attributes/fabric/build/libs/ApothicAttributes-26.2-4.0.1.jar
```

Their paths can be overridden with `placebo_fabric_jar` and `apothic_attributes_fabric_jar`. The GameTest runtime also loads Gateways, Apothic Enchanting, Apothic Spawners, Patchouli and Lootr, overridden with `gateways_fabric_jar`, `apothic_enchanting_fabric_jar`, `apothic_spawners_fabric_jar`, `patchouli_fabric_jar` and `lootr_fabric_jar`. The normal addon build runs the seven pure JUnit tests and the owned GameTests:

```powershell
.\gradlew.bat :levelledmobs-apotheosis:build
```

Run only the live integration test with:

```powershell
.\gradlew.bat :levelledmobs-apotheosis:runGameTest
```

The GameTests boot Minecraft 26.2 on Fabric. The standalone test verifies nearest-player World Tier selection, entity-load queue processing, unlimited-distance selection, LevelledMobs/Apotheosis modifier coexistence, and persistent levels after a player's tier changes. The managed test verifies that a creation-tier change relevels a loaded mob into the new band, with its health kept by ratio. The death-XP tests verify that a levelled mob's experience reward follows the `xp` multiplier merged from its matching rules, and falls back to the global `defaultXpMultiplier` when no matching rule sets one. The boss tests verify that phantoms, hoglins and ghasts are levelled and slimes and magma cubes are not; that a Wither and a Warden take the boss rule's health only while `allowBossLeveling` is on; that 1, 10 and 15 nearby players give a group factor of 1, 1 and 1.5, which survives an NBT reload; and that a levelled Wither's skull, Ender Dragon's head, Warden's sonic boom and Elder Guardian's beam deal multiplied damage to mobs and players while the Warden's and Elder Guardian's melee hits are not multiplied again. The ranged damage tests verify that a levelled skeleton's arrow and a levelled creeper's explosion deal multiplied damage to a player, and to a mob only once: at the settings defaults (×2.0 and ×1.2 at level 21), at the pack's rule values (×10.95 and ×2.99 at level 200), and with `explosion_damage` set to `0` (the blast is ×1.0 while the arrow keeps its default). A creeper's stored explosion fraction survives an NBT reload, and a save without the key falls back to the default. TNT the creeper sets off and the skeleton's melee hit are not multiplied. The admin-level test verifies that `setLevel` gives a zombie and a Wither the max health and armor per level from their matching rules, keeps the admin-given level above the rules' band, and gives a mob that matches no rule the built-in defaults.

# LevelledMobs → Fabric / LampasCore: Feature Matrix

This document provides a comprehensive audit of all major features in `justbecauseph/LevelledMobs` (Kotlin/Paper), classifying each feature for native porting to Fabric/LampasCore without Bukkit compatibility layers.

---

## 1. Classification Categories

| Classification | Description |
|---|---|
| `UNCHANGED_CORE` | Pure mathematical, rule parsing, filtering, or configuration logic that operates on platform-neutral abstractions (`MobContext`, `EffectiveRule`). |
| `PORT_TO_MINECRAFT_API` | Direct replacement using vanilla Minecraft / Fabric API server primitives (`LivingEntity`, `EntityAttributeModifier`, `Text`, `NbtCompound`, Brigadier). |
| `PORT_TO_FABRIC_EVENT` | Handled via standard Fabric API event callbacks (`ServerEntityEvents`, `ServerChunkEvents`, `ServerLifecycleEvents`, `AttackEntityCallback`). |
| `REQUIRES_MIXIN` | Requires targeted bytecode injection via Fabric Mixin due to missing or inadequate Fabric API event hooks (e.g. NBT read/write, death XP intercept, entity conversion tracking). |
| `OPTIONAL_INTEGRATION` | Third-party mod or provider SPI integrations (TextPlaceholderAPI, Common Protection API / FLAN / GOML, Player Level provider). |
| `NOT_PORTING` | Bukkit-specific legacy subsystems, packet reflection hacks, bStats, update checkers, or obsolete Minecraft compatibility layers. |

---

## 2. Comprehensive Feature Matrix

| # | Feature / Subsystem | Upstream Kotlin Class(es) | Classification | Target Fabric / LampasCore Implementation & Strategy |
|---|---|---|---|---|
| 1 | **Rule Definition & Schema** | `rules/RuleInfo.kt`, `rules/RulesParser.kt` | `UNCHANGED_CORE` | `rules/LevelRule.java`, `rules/RuleParser.java`. Parse YAML/JSON5 rules into immutable rule structures. Strip Bukkit types. |
| 2 | **Rule Inheritance & Presets** | `rules/RuleInfo.kt#mergePresetRules`, `rules/MergableRule.kt` | `UNCHANGED_CORE` | `rules/EffectiveRule.java`, `rules/RuleManager.java`. Rule inheritance and merging compiled during reload. |
| 3 | **Rule Merging & Priority** | `rules/RulesManager.kt`, `rules/ApplicableRulesResult.kt` | `UNCHANGED_CORE` | Multi-rule matching sorted by priority, merging condition-matching rules into a single `EffectiveRule`. |
| 4 | **Entity Matching (ID & Tags)** | `rules/RuleInfo.kt#conditionsEntities`, `rules/ModalListParsingInfo.kt` | `PORT_TO_MINECRAFT_API` | Match via `Identifier` (`Registries.ENTITY_TYPE.getId(...)`) and `TagKey<EntityType<?>>`. Supports modded mobs automatically. |
| 5 | **Biome Matching (ID & Tags)** | `rules/RuleInfo.kt#conditionsBiomes` | `PORT_TO_MINECRAFT_API` | Match via `RegistryKey<Biome>` and `TagKey<Biome>`. Dimension/biome tags support BetterNether/BetterEnd. |
| 6 | **Dimension / World Filters** | `rules/RuleInfo.kt#conditionsWorlds` | `PORT_TO_MINECRAFT_API` | Match via `RegistryKey<World>` (`ServerWorld.getRegistryKey()`). No hardcoded dimension trios. |
| 7 | **Spawn Reason Filters** | `misc/LMSpawnReason.kt`, `enums/InternalSpawnReason.kt` | `PORT_TO_MINECRAFT_API` | `data/SpawnReason.java` enum. Classified from spawn provenance (natural, spawner, egg, trial spawner, command, etc.). |
| 8 | **Altitude (Y-Min / Y-Max)** | `rules/RuleInfo.kt#conditionsApplyAboveY`, `conditionsApplyBelowY` | `UNCHANGED_CORE` | Evaluated against `MobContext#blockPos().getY()`. Integer range clamping. |
| 9 | **Spawn Distance Rules** | `rules/strategies/SpawnDistanceStrategy.kt` | `UNCHANGED_CORE` | `rules/strategy/SpawnDistanceStrategy.java`. Distance from configured origin (default world spawn) / ringed tiers + variance. |
| 10 | **Y-Distance Rules** | `rules/strategies/YDistanceStrategy.kt` | `UNCHANGED_CORE` | `rules/strategy/YDistanceStrategy.java`. Depth/height scaling from transition Y with downward/upward scale options. |
| 11 | **Random Level Strategy** | `rules/strategies/RandomLevellingStrategy.kt`, `RandomVarianceGenerator.kt` | `UNCHANGED_CORE` | `rules/strategy/RandomLevellingStrategy.java`. Min/max range, weighted curves, random variance generator. |
| 12 | **Player-Based Strategy** | `rules/strategies/PlayerLevellingStrategy.kt` | `PORT_TO_MINECRAFT_API` | `rules/strategy/PlayerLevellingStrategy.java`. Derives level from nearest `ServerPlayerEntity` XP / stats via `PlayerLevelProvider`. |
| 13 | **Custom Formula Strategy** | `rules/strategies/CustomStrategy.kt`, `rules/LevelTierMatching.kt` | `UNCHANGED_CORE` | `rules/strategy/CustomStrategy.java`. Mathematical formula evaluation and tier matching tables. |
| 14 | **Mob Level Generation** | `managers/LevelManager.kt#generateLevel` | `UNCHANGED_CORE` | `level/LevelCalculationService.java`. Aggregates strategies, clamps to min/max level bounds, produces integer level. |
| 15 | **Attribute Modifiers (General)** | `rules/FineTuningAttributes.kt`, `managers/LevelManager.kt` | `PORT_TO_MINECRAFT_API` | `attributes/AttributeScalingService.java`. Applies `EntityAttributeModifier` with deterministic IDs (`lampas:levelled/<attr>`). |
| 16 | **Max Health Scaling** | `rules/FineTuningAttributes.kt`, `managers/LevelManager.kt` | `PORT_TO_MINECRAFT_API` | Modifies `GENERIC_MAX_HEALTH`. Implements `HealthPolicy` (full HP on spawn; preserved HP percentage on chunk reload). |
| 17 | **Attack Damage Scaling** | `rules/FineTuningAttributes.kt`, `managers/LevelManager.kt` | `PORT_TO_MINECRAFT_API` | Modifies `GENERIC_ATTACK_DAMAGE`. Deterministic modifier prevents stacking. |
| 18 | **Movement Speed Scaling** | `rules/FineTuningAttributes.kt` | `PORT_TO_MINECRAFT_API` | Modifies `GENERIC_MOVEMENT_SPEED` with multiplier/addition formulas. |
| 19 | **Armor & Armor Toughness** | `rules/FineTuningAttributes.kt` | `PORT_TO_MINECRAFT_API` | Modifies `GENERIC_ARMOR` and `GENERIC_ARMOR_TOUGHNESS`. |
| 20 | **Knockback & Attack Knockback** | `rules/FineTuningAttributes.kt` | `PORT_TO_MINECRAFT_API` | Modifies `GENERIC_KNOCKBACK_RESISTANCE` and `GENERIC_ATTACK_KNOCKBACK`. |
| 21 | **Follow Range Scaling** | `rules/FineTuningAttributes.kt` | `PORT_TO_MINECRAFT_API` | Modifies `GENERIC_FOLLOW_RANGE`. |
| 22 | **XP Scaling & Multipliers** | `rules/FineTuningAttributes.kt`, `listeners/EntityDeathListener.kt` | `REQUIRES_MIXIN` | `drops/XpScalingService.java`. Injects into `LivingEntity#dropXp` to scale experience points without double-spawning. |
| 23 | **Vanilla Loot Scaling (Stage A)** | `rules/FineTuningAttributes.kt`, `listeners/EntityDeathListener.kt` | `REQUIRES_MIXIN` | `drops/DropScalingService.java`. Injects into loot generation / death drops to multiply item stack counts by level curve. |
| 24 | **Custom Drop Tables (Stage B)** | `customdrops/CustomDropsHandler.kt`, `CustomDropsParser.kt` | `PORT_TO_MINECRAFT_API` | `drops/custom/CustomDropsHandler.java`. Parses custom drop items, sliding chance, min/max level conditions, item registry IDs. |
| 25 | **Equipment Assignment** | `customdrops/EquippedItemsInfo.kt`, `managers/LevelManager.kt` | `PORT_TO_MINECRAFT_API` | `drops/equipment/MobEquipmentService.java`. Equips armor & hands using modern Minecraft Data Components. |
| 26 | **Nametag Formatting** | `nametag/Definitions.kt`, `nametag/ComponentUtils.kt` | `PORT_TO_MINECRAFT_API` | `nametag/NametagService.java`. Sets server-side custom name (`setCustomName`, `setCustomNameVisible`). |
| 27 | **Placeholder Integration** | `managers/PlaceholderApiIntegration.kt` | `OPTIONAL_INTEGRATION` | `nametag/LevelledMobsPlaceholders.java` integrating with `Patbox/TextPlaceholderAPI` (`%lampas:mob_level%`, etc.). |
| 28 | **Nametag Event Triggers** | `nametag/NametagSenderHandler.kt`, `listeners/EntityDamageListener.kt` | `PORT_TO_FABRIC_EVENT` | Event-driven refresh on damage and heal; zero continuous per-tick polling. |
| 29 | **Spawn Provenance & Pipeline** | `listeners/EntitySpawnListener.kt`, `managers/MobsQueueManager.kt` | `PORT_TO_FABRIC_EVENT` + `REQUIRES_MIXIN` | `level/MobLifecycleService.java`. Intercepts spawns via `ServerEntityEvents.ENTITY_LOAD` and targeted Mixins for spawner/egg origin. |
| 30 | **Queue & Budgeted Processing** | `managers/MobsQueueManager.kt` | `PORT_TO_FABRIC_EVENT` | `level/MobProcessingQueue.java`. Per-tick work budget (`max-mobs-per-tick: 50`) hooked to `ServerTickEvents.END_SERVER_TICK`. |
| 31 | **NBT Persistence** | `managers/NBTManager.kt`, `misc/NamespacedKeys.kt` | `REQUIRES_MIXIN` | `data/LevelledMobHolder.java` duck-typing interface. Mixin into `writeCustomDataToNbt` and `readCustomDataFromNbt`. |
| 32 | **Chunk Loading Validation** | `listeners/ChunkLoadListener.kt` | `PORT_TO_FABRIC_EVENT` | `events/ChunkLifecycleHandler.java`. Validates existing NBT on chunk load; guarantees level invariance (no reroll). |
| 33 | **Entity Transformations** | `listeners/EntityTransformListener.kt` | `REQUIRES_MIXIN` | Mixin into entity conversion methods (`ZombieEntity#convertTo`, `SlimeEntity#remove`) to propagate `LevelledMobData`. |
| 34 | **Taming Handling** | `listeners/EntityTameListener.kt` | `PORT_TO_FABRIC_EVENT` | `events/EntityLifecycleHandler.java`. Handles taming state transitions (unleveling or locked level according to config). |
| 35 | **Item Pickup Protection** | `listeners/EntityPickupItemListener.kt` | `PORT_TO_FABRIC_EVENT` | Prevents mobs from multiplying picked-up player items on death. |
| 36 | **Combat Damage Pipeline** | `listeners/EntityDamageListener.kt`, `listeners/CombustListener.kt` | `PORT_TO_FABRIC_EVENT` + `REQUIRES_MIXIN` | `events/CombatHandler.java`. Scales custom attack/projectile damage while avoiding double-multiplication with attribute damage. |
| 37 | **Boss Classification** | `rules/RuleInfo.kt`, `managers/LevelManager.kt` | `PORT_TO_MINECRAFT_API` | `level/BossClassifier.java`. Excludes Ender Dragon, Wither, and tagged modded bosses by default. |
| 38 | **Admin Commands** | `commands/CommandHandler.kt`, `commands/subcommands/*` | `PORT_TO_MINECRAFT_API` | `command/LevelledMobsCommand.java`. Brigadier tree for `/lm info`, `/lm reload`, `/lm inspect`, `/lm level`, `/lm summon`, `/lm debug`, `/lm rules`. |
| 39 | **Permissions Service** | `rules/RuleInfo.kt#conditionsPermission` | `PORT_TO_MINECRAFT_API` + `OPTIONAL_INTEGRATION` | `permission/PermissionService.java`. OP level fallback + Fabric Permissions API provider. |
| 40 | **Public API & Callbacks** | `LevelInterface.kt`, `events/MobPreLevelEvent.kt`, `MobPostLevelEvent.kt` | `PORT_TO_MINECRAFT_API` | `api/LevelledMobsApi.java`, `api/events/MobPreLevelCallback.java`, `MobPostLevelCallback.java`. |
| 41 | **Region Claims Compatibility** | `managers/WorldGuardIntegration.kt` | `OPTIONAL_INTEGRATION` | `compatibility/RegionProtectionProvider.java`. SPI abstraction for FLAN, GOML, and Common Protection API. |
| 42 | **NMS Packet Nametags** | `nametag/NmsNametagSender.kt`, `nametag/NmsMappings.kt` | `NOT_PORTING` | Replaced entirely with vanilla server-side nametags and TextPlaceholderAPI formatting. |
| 43 | **Legacy Plugin Configs** | `misc/FileMigrator.kt`, `misc/FileLoader.kt` | `NOT_PORTING` | Replaced with native JSON5/YAML schemas. Optional offline migration tool deferred to Chunk 10. |
| 44 | **bStats & Update Checker** | `util/UpdateChecker.kt`, `bstats/` | `NOT_PORTING` | Fabric mods do not use Bukkit bStats or custom update checkers. |

---

## 3. Upstream Subsystem Mapping Summary

```text
upstream/rules/          ──► lampas/levelledmobs/rules/ (RuleEngine, Predicates, EffectiveRule)
upstream/strategies/     ──► lampas/levelledmobs/rules/strategy/ (LevellingStrategy SPI)
upstream/managers/       ──► lampas/levelledmobs/level/, attributes/, data/
upstream/customdrops/    ──► lampas/levelledmobs/drops/
upstream/nametag/        ──► lampas/levelledmobs/nametag/ (TextPlaceholderAPI)
upstream/listeners/      ──► lampas/levelledmobs/events/ + mixin/levelledmobs/
upstream/commands/       ──► lampas/levelledmobs/command/ (Brigadier)
```

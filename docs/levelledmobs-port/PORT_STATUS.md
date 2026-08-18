# LevelledMobs → Fabric / LampasCore: Port Status Tracker

This document tracks progress across all 12 execution chunks defined in [`porting/CHUNKS.md`](file:///C:/Users/markj/source/repos/LevelledMobs/porting/CHUNKS.md) and [`porting/PLAN.md`](file:///C:/Users/markj/source/repos/LevelledMobs/porting/PLAN.md).

---

## 1. Overall Progress Summary

| Chunk | Title | Status | Progress | Key Deliverables |
|---|---|---|---|---|
| **Chunk 0** | Pre-Port Audit & Feature Matrix | **DONE** | 100% | `FEATURE_MATRIX.md`, `BUKKIT_DEPENDENCIES.md`, `PORT_STATUS.md` |
| **Chunk 1** | Fabric Skeleton & Vertical Slice MVP | **DONE** | 100% | `LevelledMobsModule`, `LevelledMobData`, NBT Mixin, `/lm inspect` |
| **Chunk 2** | Context Abstractions & Rule Engine Core | **DONE** | 100% | `MobContext`, `LevelRule`, `RuleParser`, `RuleManager` |
| **Chunk 3** | Levelling Strategy Subsystem | **DONE** | 100% | Random, Distance, Y, Player, Custom strategies |
| **Chunk 4** | Attribute Scaling Engine & Combat Pipeline | **DONE** | 100% | 8 attributes, idempotent modifiers, `HealthPolicy` |
| **Chunk 5** | Entity Lifecycle & Spawning Pipeline | **DONE** | 100% | Spawning provenance, conversions, chunk loading, queue |
| **Chunk 6** | Drops, Loot & XP Scaling System | **DONE** | 100% | XP scaling, loot multipliers, custom drop engine |
| **Chunk 7** | Nametag & Text Formatting Engine | **DONE** | 100% | Server-side nametags, TextPlaceholderAPI integration |
| **Chunk 8** | Commands, Permissions & Public API | **DONE** | 100% | Brigadier command tree, permission service, public API |
| **Chunk 9** | External Integrations & Mod Ecosystem | **NOT STARTED** | 0% | Modded mob support, region protection SPI |
| **Chunk 10** | Config System & Legacy Importer | **NOT STARTED** | 0% | Native configs, YAML/JSON5, legacy migration tool |
| **Chunk 11** | Test Suite, Benchmarks & DoD Signoff | **NOT STARTED** | 0% | Unit tests, GameTests, 5k mob benchmark, DoD audit |

---

## 2. Chunk-by-Chunk Detailed Checklist

### Chunk 0 — Pre-Port Audit & Feature Matrix
- [x] Upstream codebase audited (`rules/`, `strategies/`, `managers/`, `customdrops/`, `nametag/`, `listeners/`, `commands/`).
- [x] All 40+ features classified into architectural buckets in `FEATURE_MATRIX.md`.
- [x] Bukkit/Paper API dependencies cataloged with Fabric replacements in `BUKKIT_DEPENDENCIES.md`.
- [x] Initial `PORT_STATUS.md` created.

### Chunk 1 — Fabric Module Skeleton & Vertical Slice MVP
- [x] Fabric module skeleton initialized in `lampas.levelledmobs`.
- [x] `LevelledMobData` record and `LevelledMobHolder` duck interface created.
- [x] Mixin into `LivingEntity.writeCustomDataToNbt` and `readCustomDataFromNbt`.
- [x] Minimal spawn intercept assigning level 1–10 to vanilla hostiles.
- [x] Attribute modifier application for Max Health and Attack Damage.
- [x] Server-side nametag display (`Lv. X Mob`).
- [x] Brigadier `/lm inspect` command.
- [x] Project compiles with no Bukkit/Paper dependencies.

### Chunk 2 — Context Abstractions & Rule Engine Core
- [x] `MobContext`, `PlayerContext`, `SpawnContext`, `WorldContext` interfaces.
- [x] Rule data structures: `LevelRule`, `RulePredicate`, `EffectiveRule`.
- [x] Condition evaluators for entity IDs, entity tags, biome keys, biome tags, dimension keys, altitude, spawn reason.
- [x] Rule priority resolution and multi-rule property merging.
- [x] Thread-safe atomic rule swapping (`AtomicReference<CompiledRules>`).
- [x] Unit tests for rule parsing, matching, and merging.

### Chunk 3 — Levelling Strategy Subsystem
- [x] `LevelStrategy` interface.
- [x] `RandomLevellingStrategy` with min/max, weighted distribution, and variance.
- [x] `SpawnDistanceStrategy` with origin coordinates, distance per level, base level, and min/max clamps.
- [x] `YDistanceStrategy` supporting depth/height scaling.
- [x] `PlayerLevellingStrategy` with `PlayerLevelProvider` interface.
- [x] `CustomStrategy` with mathematical formulas and `LevelTierMatching`.
- [x] Unit tests for all strategies matching upstream mathematical parity.

### Chunk 4 — Attribute Scaling Engine & Combat Pipeline
- [x] Full support for 8 attributes (`MAX_HEALTH`, `ATTACK_DAMAGE`, `MOVEMENT_SPEED`, `ARMOR`, `ARMOR_TOUGHNESS`, `KNOCKBACK_RESISTANCE`, `ATTACK_KNOCKBACK`, `FOLLOW_RANGE`).
- [x] Deterministic modifier namespacing (`lampas:levelled/<attribute>`).
- [x] Guaranteed idempotency (strip previous modifier before applying new modifier).
- [x] `HealthPolicy` (full HP on spawn, preserved percentage on chunk load/relevel).
- [x] Combat damage pipeline with melee, projectile, and explosion scaling.
- [x] Unit and combat tests.

### Chunk 5 — Entity Lifecycle, Transformations & Spawning Pipeline
- [x] Centralized spawn pipeline in `MobLifecycleService.level(LivingEntity, SpawnReason)`.
- [x] `SpawnReason` classification from all game sources.
- [x] Entity transformation handler preserving `LevelledMobData` (Zombie → Drowned, Slime split, etc.).
- [x] `ChunkLifecycleHandler` validating existing NBT on chunk load without rerolling.
- [x] `BossClassifier` excluding Ender Dragon, Wither, and tagged bosses.
- [x] `MobProcessingQueue` with per-tick budget (`max-mobs-per-tick: 50`).
- [x] `MIXINS.md` fully documented.

### Chunk 6 — Drops, Loot & XP Scaling System
- [x] `XpScalingService` hooked into death XP drop calculation.
- [x] Stage A drop scaling: vanilla loot multiplier based on `EffectiveRule`.
- [x] Stage B custom drops: registry item ID matching, sliding chance, level requirements, quantities.
- [x] `MobEquipmentService` with modern Minecraft Data Components and drop chances.

### Chunk 7 — Nametag & Text Formatting Engine
- [x] `TextFormatter` abstracting Minecraft `Text` styling.
- [x] TextPlaceholderAPI integration with registered `%lampas:*%` placeholders.
- [x] Template placeholder parser supporting `<level>`, `<mob_name>`, `<health>`, `<max_health>`, `<health_percent>`.
- [x] Event-driven nametag updates on damage and healing without per-tick polling.

### Chunk 8 — Commands, Permissions & Public API
- [x] Brigadier command tree registered under `/levelledmobs` and alias `/lm`.
- [x] Subcommands: `/lm info`, `/lm reload`, `/lm inspect`, `/lm rules`, `/lm level`, `/lm summon`, `/lm debug`.
- [x] `PermissionService` with OP fallback and Fabric Permissions API.
- [x] Public API: `LevelledMobsApi` interface and singleton.
- [x] Event callbacks: `MobPreLevelCallback` and `MobPostLevelCallback`.

### Chunk 9 — External Integrations & Mod Ecosystem Compatibility
- [ ] `RegionProtectionProvider` SPI for claims mods (FLAN, GOML, Common Protection API).
- [ ] Modded entity registry IDs and tags verified without hardcoded enums.
- [ ] Modded biomes and dimensions tested with rule conditions.
- [ ] `PlayerLevelProvider` SPI for RPG level mods.

### Chunk 10 — Configuration System & Legacy Importer
- [ ] Native YAML / JSON5 configuration loader (`settings.yml`, `rules.yml`, `drops.yml`, `messages.yml`).
- [ ] Configuration validation and schema verification.
- [ ] `LegacyLevelledMobsConfigImporter` for translating Bukkit LevelledMobs configs.

### Chunk 11 — Comprehensive Test Suite, Benchmarks & DoD Signoff
- [ ] JUnit 5 unit tests for rule resolution, strategies, attributes, and placeholders.
- [ ] Fabric GameTests for spawn, persistence, chunk reload, combat scaling, transformations, and drops.
- [ ] 5,000 mob performance benchmark verifying zero recurring per-tick overhead for passive mobs.
- [ ] Complete audit against Definition of Done checklist.
- [ ] GPLv3 attribution and licensing verified.

---

## 3. Current Phase / Next Steps
- **Active Task**: Completed Chunk 8 (Commands, Permissions & Public API).
- **Next Target**: Chunk 9 (External Integrations & Mod Ecosystem Compatibility).

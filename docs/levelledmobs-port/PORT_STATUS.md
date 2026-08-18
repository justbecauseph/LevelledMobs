# LevelledMobs → Fabric / LampasCore: Port Status Tracker

This document tracks progress across all 12 execution chunks defined in [`porting/CHUNKS.md`](file:///C:/Users/markj/source/repos/LevelledMobs/porting/CHUNKS.md) and [`porting/PLAN.md`](file:///C:/Users/markj/source/repos/LevelledMobs/porting/PLAN.md).

---

## 1. Overall Progress Summary

| Chunk | Title | Status | Progress | Key Deliverables |
|---|---|---|---|---|
| **Chunk 0** | Pre-Port Audit & Feature Matrix | **DONE** | 100% | `FEATURE_MATRIX.md`, `BUKKIT_DEPENDENCIES.md`, `PORT_STATUS.md` |
| **Chunk 1** | Fabric Skeleton & Vertical Slice MVP | **DONE** | 100% | `LevelledMobsModule`, `LevelledMobData`, NBT Mixin, `/lm inspect` |
| **Chunk 2** | Context Abstractions & Rule Engine Core | **NOT STARTED** | 0% | `MobContext`, `LevelRule`, `RuleParser`, `RuleManager` |
| **Chunk 3** | Levelling Strategy Subsystem | **NOT STARTED** | 0% | Random, Distance, Y, Player, Custom strategies |
| **Chunk 4** | Attribute Scaling Engine & Combat Pipeline | **NOT STARTED** | 0% | 8 attributes, idempotent modifiers, `HealthPolicy` |
| **Chunk 5** | Entity Lifecycle & Spawning Pipeline | **NOT STARTED** | 0% | Spawning provenance, conversions, chunk loading, queue |
| **Chunk 6** | Drops, Loot & XP Scaling System | **NOT STARTED** | 0% | XP scaling, loot multipliers, custom drop engine |
| **Chunk 7** | Nametag & Text Formatting Engine | **NOT STARTED** | 0% | Server-side nametags, TextPlaceholderAPI integration |
| **Chunk 8** | Commands, Permissions & Public API | **NOT STARTED** | 0% | Brigadier command tree, permission service, public API |
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
- [ ] `MobContext`, `PlayerContext`, `SpawnContext`, `WorldContext` interfaces.
- [ ] Rule data structures: `LevelRule`, `RulePredicate`, `EffectiveRule`.
- [ ] Condition evaluators for entity IDs, entity tags, biome keys, biome tags, dimension keys, altitude, spawn reason.
- [ ] Rule priority resolution and multi-rule property merging.
- [ ] Thread-safe atomic rule swapping (`AtomicReference<CompiledRules>`).
- [ ] Unit tests for rule parsing, matching, and merging.

### Chunk 3 — Levelling Strategy Subsystem
- [ ] `LevelStrategy` interface.
- [ ] `RandomLevellingStrategy` with min/max, weighted distribution, and variance.
- [ ] `SpawnDistanceStrategy` with origin coordinates, distance per level, base level, and min/max clamps.
- [ ] `YDistanceStrategy` supporting depth/height scaling.
- [ ] `PlayerLevellingStrategy` with `PlayerLevelProvider` interface.
- [ ] `CustomStrategy` with mathematical formulas and `LevelTierMatching`.
- [ ] Unit tests for all strategies matching upstream mathematical parity.

### Chunk 4 — Attribute Scaling Engine & Combat Pipeline
- [ ] Full support for 8 attributes (`MAX_HEALTH`, `ATTACK_DAMAGE`, `MOVEMENT_SPEED`, `ARMOR`, `ARMOR_TOUGHNESS`, `KNOCKBACK_RESISTANCE`, `ATTACK_KNOCKBACK`, `FOLLOW_RANGE`).
- [ ] Deterministic modifier namespacing (`lampas:levelled/<attribute>`).
- [ ] Guaranteed idempotency (strip previous modifier before applying new modifier).
- [ ] `HealthPolicy` (full HP on spawn, preserved percentage on chunk load/relevel).
- [ ] Combat damage pipeline with melee, projectile, and explosion scaling.
- [ ] Unit and combat tests.

### Chunk 5 — Entity Lifecycle, Transformations & Spawning Pipeline
- [ ] Centralized spawn pipeline in `MobLifecycleService.level(LivingEntity, SpawnReason)`.
- [ ] `SpawnReason` classification from all game sources.
- [ ] Entity transformation handler preserving `LevelledMobData` (Zombie → Drowned, Slime split, etc.).
- [ ] `ChunkLifecycleHandler` validating existing NBT on chunk load without rerolling.
- [ ] `BossClassifier` excluding Ender Dragon, Wither, and tagged bosses.
- [ ] `MobProcessingQueue` with per-tick budget (`max-mobs-per-tick: 50`).
- [ ] `MIXINS.md` fully documented.

### Chunk 6 — Drops, Loot & XP Scaling System
- [ ] `XpScalingService` hooked into death XP drop calculation.
- [ ] Stage A drop scaling: vanilla loot multiplier based on `EffectiveRule`.
- [ ] Stage B custom drops: registry item ID matching, sliding chance, level requirements, quantities.
- [ ] `MobEquipmentService` with modern Minecraft Data Components and drop chances.

### Chunk 7 — Nametag & Text Formatting Engine
- [ ] `TextFormatter` abstracting Minecraft `Text` styling.
- [ ] TextPlaceholderAPI integration with registered `%lampas:*%` placeholders.
- [ ] Template placeholder parser supporting `<level>`, `<mob_name>`, `<health>`, `<max_health>`, `<health_percent>`.
- [ ] Event-driven nametag updates on damage and healing without per-tick polling.

### Chunk 8 — Commands, Permissions & Public API
- [ ] Brigadier command tree registered under `/levelledmobs` and alias `/lm`.
- [ ] Subcommands: `/lm info`, `/lm reload`, `/lm inspect`, `/lm rules`, `/lm level`, `/lm summon`, `/lm debug`.
- [ ] `PermissionService` with OP fallback and Fabric Permissions API.
- [ ] Public API: `LevelledMobsApi` interface and singleton.
- [ ] Event callbacks: `MobPreLevelCallback` and `MobPostLevelCallback`.

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
- **Active Task**: Completed Chunk 1 (Fabric Module Skeleton & Vertical Slice MVP).
- **Next Target**: Chunk 2 (Context Abstractions & Rule Engine Core).

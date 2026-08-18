# LevelledMobs → Fabric / LampasCore Port Plan

## 1. Objective

Port the functionality of:

`justbecauseph/LevelledMobs`

from a Bukkit/Paper plugin into a **native Fabric server-side implementation** inside `LampasCore`.

The goal is behavioral compatibility with the important LevelledMobs features, **not** source-level compatibility with Bukkit.

The finished implementation must:

- Level naturally spawned mobs.
- Persist mob level and LevelledMobs metadata.
- Scale mob attributes according to configured rules.
- Support rule-based level calculation.
- Support the main LevelledMobs strategies.
- Preserve levels across chunk unload/load and server restart.
- Modify XP and drops according to level.
- Support configurable nametags.
- Handle transformations, spawning, taming, equipment, death, damage, and chunk loading correctly.
- Provide administrative commands.
- Remain performant with large mob populations.
- Work with modded Fabric entities where practical.
- Avoid introducing Bukkit/Paper compatibility layers.

Upstream is GPLv3-or-later, so copied/derived code must retain appropriate GPL licensing and attribution.

---

Dependencies:

```
C:\Users\markj\source\repos\TextPlaceholderAPI
C:\Users\markj\source\repos\fabric-api
```

# 2. Core Porting Principle

Do **not** mechanically translate:

```text
Bukkit API
    ↓
Fabric equivalent
```

class by class.

Instead separate LevelledMobs into:

```text
               ┌────────────────────┐
               │ Configuration      │
               └─────────┬──────────┘
                         │
               ┌─────────▼──────────┐
               │ Rule Engine        │
               ├────────────────────┤
               │ Level Strategies   │
               ├────────────────────┤
               │ Attribute Scaling  │
               ├────────────────────┤
               │ Drop Calculation   │
               ├────────────────────┤
               │ Nametag Formatting │
               └─────────┬──────────┘
                         │
                 Platform Boundary
                         │
               ┌─────────▼──────────┐
               │ Fabric / Minecraft │
               ├────────────────────┤
               │ Entity events      │
               │ NBT persistence    │
               │ Attributes         │
               │ Commands           │
               │ Networking         │
               │ Mixins             │
               └────────────────────┘
```

The rule engine should know as little as possible about Fabric or Minecraft internals.

---

# 3. Initial Repository Audit

Before modifying code, inspect upstream and produce:

```text
docs/levelledmobs-port/
├── FEATURE_MATRIX.md
├── BUKKIT_DEPENDENCIES.md
└── PORT_STATUS.md
```

### `FEATURE_MATRIX.md`

For every major LevelledMobs feature classify it as:

```text
UNCHANGED_CORE
PORT_TO_MINECRAFT_API
PORT_TO_FABRIC_EVENT
REQUIRES_MIXIN
OPTIONAL_INTEGRATION
NOT_PORTING
```

At minimum audit:

- Rules
- Rule inheritance
- Rule merging
- Entity filters
- Biome filters
- Dimension/world filters
- Spawn reason filters
- Y-distance rules
- Spawn-distance rules
- Player-distance/player-level rules
- Random strategies
- Custom strategies
- Level generation
- Attribute modifiers
- Health scaling
- Damage scaling
- Movement speed
- Armor
- Armor toughness
- Knockback resistance
- Attack knockback
- Follow range
- XP scaling
- Vanilla drops
- Custom drops
- Equipment
- Nametags
- Spawn handling
- Chunk loading
- Entity transformations
- Taming
- Item pickup
- Death handling
- Damage handling
- Mob persistence
- Commands
- Debugging
- Plugin integrations

Do not start mass porting until this matrix exists.

---

# 4. Identify Reusable Core Code

Prioritize preserving concepts from these upstream areas:

```text
rules/
rules/strategies/
result/
enums/
customdrops/
misc/
```

Candidate classes/concepts:

```text
RuleInfo
ApplicableRulesResult
RuleCheckResult
MinAndMax
LMMultiplier
LevelTierMatching
FineTuningAttributes

LevellingStrategy
RandomLevellingStrategy
SpawnDistanceStrategy
YDistanceStrategy
PlayerLevellingStrategy
CustomStrategy
```

Do not carry Bukkit types into these classes.

For example, change code conceptually from:

```kotlin
fun applies(entity: LivingEntity): Boolean
```

where `LivingEntity` is Bukkit into an abstraction such as:

```java
boolean applies(MobContext context);
```

---

# 5. Introduce a Platform-Neutral MobContext

Create:

```text
levelled/
└── context/
    ├── MobContext.java
    ├── PlayerContext.java
    ├── SpawnContext.java
    └── WorldContext.java
```

Suggested `MobContext`:

```java
public interface MobContext {
    UUID uuid();

    EntityType<?> entityType();

    Identifier entityId();

    ServerWorld world();

    BlockPos blockPos();

    Vec3d position();

    boolean isBaby();

    boolean isBoss();

    boolean isTamed();

    boolean hasCustomName();

    Optional<ServerPlayerEntity> nearestPlayer();

    SpawnReason spawnReason();
}
```

Rule code consumes `MobContext`.

Avoid letting `RulesManager` depend directly on Fabric callbacks.

---

# 6. Main Package Structure

Recommended `LampasCore` structure:

```text
src/main/java/.../lampas/
├── levelledmobs/
│   ├── LevelledMobsModule.java
│   │
│   ├── api/
│   ├── config/
│   ├── context/
│   ├── data/
│   ├── level/
│   ├── rules/
│   │   └── strategy/
│   ├── attributes/
│   ├── drops/
│   ├── nametag/
│   ├── events/
│   ├── command/
│   ├── compatibility/
│   ├── debug/
│   └── util/
│
└── mixin/
    └── levelledmobs/
```

Keep Mixins separate from business logic.

A Mixin should normally invoke a service:

```java
LevelledMobsServices.spawning().onEntitySpawn(entity);
```

rather than implementing leveling rules itself.

---

# 7. Levelled Mob Data Model

Replace Bukkit `PersistentDataContainer` / NBT API usage.

Create:

```java
public record LevelledMobData(
    int level,
    boolean levelled,
    String ruleSet,
    long generatedAt
) {}
```

Minimum persisted values:

```text
lampas:level
lampas:levelled
lampas:rule
lampas:spawn_reason
lampas:original_max_health
```

Potential future fields:

```text
lampas:drop_multiplier
lampas:xp_multiplier
lampas:nametag_template
lampas:source_level
```

---

# 8. Persistence

Use vanilla entity NBT.

Preferred implementation:

```text
MobEntity / LivingEntity
     ↓
Mixin
     ↓
writeCustomDataToNbt()
readCustomDataFromNbt()
```

Create an interface:

```java
public interface LevelledMobHolder {

    LevelledMobData lampas$getLevelData();

    void lampas$setLevelData(LevelledMobData data);
}
```

Mixin example target:

```text
LivingEntityMixin
or
MobEntityMixin
```

Inject into:

```text
writeCustomDataToNbt
readCustomDataFromNbt
```

Do not maintain the authoritative level solely in a global UUID map.

The entity NBT should be the source of truth.

Caches are allowed for performance.

---

# 9. Spawn Pipeline

This is one of the highest-risk parts of the port.

Implement a centralized pipeline:

```text
entity created
      ↓
classify spawn
      ↓
is entity eligible?
      ↓
already levelled?
      ↓
build MobContext
      ↓
resolve applicable rules
      ↓
choose level strategy
      ↓
calculate level
      ↓
apply attributes
      ↓
apply equipment
      ↓
store LevelledMobData
      ↓
update nametag
      ↓
publish LevelledMobCreated event
```

Create:

```java
MobLevelingService.level(
    LivingEntity entity,
    SpawnReason reason
);
```

Every spawn mechanism should eventually enter this method.

---

# 10. Spawn Reasons

Create Fabric-native enum:

```java
public enum SpawnReason {
    NATURAL,
    CHUNK_GENERATION,
    SPAWNER,
    SPAWN_EGG,
    COMMAND,
    STRUCTURE,
    BREEDING,
    CONVERSION,
    REINFORCEMENT,
    PATROL,
    RAID,
    TRIAL_SPAWNER,
    PORTAL,
    CUSTOM,
    UNKNOWN
}
```

Do not attempt to exactly reproduce Bukkit `CreatureSpawnEvent.SpawnReason`.

Map Minecraft internals into a stable Lampas abstraction.

Use Mixins only where spawn provenance cannot be recovered through Fabric events.

---

# 11. Fabric Event Layer

First use Fabric API callbacks whenever possible.

Create:

```text
events/
├── ServerLifecycleEventsHandler.java
├── EntityLifecycleHandler.java
├── PlayerLifecycleHandler.java
├── ChunkLifecycleHandler.java
└── CombatHandler.java
```

Potential Fabric hooks:

```text
ServerEntityEvents
ServerChunkEvents
ServerLifecycleEvents
ServerTickEvents
AttackEntityCallback
UseEntityCallback
ServerPlayConnectionEvents
```

Do not use a Mixin when a stable Fabric callback provides the necessary semantics.

---

# 12. Mixins

Mixins are expected for gaps in Fabric's event coverage.

Likely candidates:

```text
MobEntity initialization
LivingEntity death
LivingEntity damage
Entity NBT serialization
mob transformation/conversion
XP generation
loot generation
mob equipment
name rendering / tracked data
```

Rules:

1. Prefer `@Inject`.
2. Prefer narrow method targets.
3. Avoid `@Overwrite`.
4. Avoid redirecting large vanilla behavior.
5. Put no rule calculations inside Mixins.
6. Document why each Mixin is necessary.

Create:

```text
MIXINS.md
```

containing:

```text
Mixin
Target
Injection point
Why Fabric API is insufficient
Failure impact
Version sensitivity
```

---

# 13. Rule Engine Port

This should be ported before most advanced features.

Create:

```text
LevelRule
RulePredicate
RuleResult
RuleManager
RuleParser
```

The pipeline:

```text
MobContext
    ↓
candidate rules
    ↓
predicate evaluation
    ↓
priority
    ↓
merge
    ↓
EffectiveRule
```

Create an immutable:

```java
public record EffectiveRule(
    IntRange levelRange,
    List<LevelStrategy> strategies,
    AttributeScaling attributes,
    DropScaling drops,
    XpScaling xp,
    NametagSettings nametag
) {}
```

Do not continuously reparse config during entity spawns.

Compile configuration into rules during reload.

---

# 14. Entity Matching

Fabric should use registry identifiers instead of Bukkit `EntityType`.

Example config:

```yaml
conditions:
  entities:
    include:
      - minecraft:zombie
      - minecraft:skeleton
      - betterend:end_slime

    exclude:
      - minecraft:wither
```

Support tags:

```yaml
entities:
  include-tags:
    - minecraft:skeletons
    - lampas:levelled_hostiles
```

Registry-based matching is critical for modded mobs.

Never hard-code a vanilla entity enum.

---

# 15. Biome Matching

Use Minecraft registry keys/tags.

Example:

```yaml
conditions:
  biomes:
    include:
      - minecraft:plains
      - minecraft:forest

    include-tags:
      - c:is_nether
```

Use:

```java
RegistryEntry<Biome>
RegistryKey<Biome>
TagKey<Biome>
```

This also improves compatibility with BetterNether/BetterEnd/worldgen mods.

---

# 16. Dimension Matching

Replace Bukkit world names with dimension registry IDs.

Example:

```yaml
dimensions:
  - minecraft:overworld
  - minecraft:the_nether
  - betterend:the_end
```

Use:

```java
RegistryKey<World>
```

Do not assume exactly three dimensions.

---

# 17. Level Strategies

Port strategies independently.

## Random

```text
minLevel
maxLevel
weighted distribution
variance
```

## Spawn Distance

Calculate distance from configured origin:

```text
world spawn
fixed coordinates
nearest player home if supported later
```

Then:

```text
level = base + floor(distance / distancePerLevel)
```

Apply configured min/max.

## Y Distance

Support dimension-aware inversion if required.

For example:

```text
deeper => stronger
higher => stronger
absolute distance from reference Y
```

## Player-Based

Resolve nearby players and derive mob level from:

```text
player XP
player advancement count
custom progression provider
```

Do not couple LevelledMobs directly to a particular player leveling mod.

Expose:

```java
PlayerLevelProvider
```

---

# 18. Attribute Scaling

Create:

```text
attributes/
├── AttributeScalingService.java
├── AttributeFormula.java
├── AttributeDefinition.java
└── AttributeSnapshot.java
```

Support at least:

```text
MAX_HEALTH
ATTACK_DAMAGE
MOVEMENT_SPEED
ARMOR
ARMOR_TOUGHNESS
KNOCKBACK_RESISTANCE
ATTACK_KNOCKBACK
FOLLOW_RANGE
```

Use vanilla:

```java
EntityAttributeInstance
EntityAttributeModifier
```

Use deterministic modifier IDs.

Example:

```text
lampas:levelled/max_health
lampas:levelled/attack_damage
```

Never stack another modifier each time an entity loads.

Algorithm:

```text
remove previous Lampas modifier
calculate expected modifier
apply expected modifier
```

Must be idempotent.

---

# 19. Health Handling

When leveling a mob:

```text
old max HP
↓
apply modifier
↓
new max HP
↓
set current health according to configured policy
```

Default:

```text
fresh spawn → new max HP
existing entity being reprocessed → preserve percentage
```

Never heal existing mobs accidentally on chunk load.

---

# 20. Combat Handling

Determine whether each multiplier belongs in:

```text
attribute system
```

or:

```text
damage pipeline
```

Prefer vanilla attributes whenever possible.

Do not multiply damage twice.

Add integration tests for:

```text
melee attacks
projectiles
explosions
fire
magic
environmental damage
```

---

# 21. XP Scaling

Create:

```java
XpScalingService
```

Formula example:

```text
finalXp = vanillaXp * levelMultiplier
```

Support:

```text
additive
multiplicative
formula-based
min/max
```

Hook XP calculation as close as possible to mob death XP creation.

Avoid spawning additional XP if vanilla XP has already been modified.

---

# 22. Drop System

Port in two stages.

## Stage A

Vanilla loot multiplier only.

```text
original drops
      ↓
EffectiveRule
      ↓
level-based multiplier
      ↓
final drops
```

## Stage B

Port LevelledMobs custom drops.

Fabric-native design should eventually support:

```yaml
drops:
  zombie:
    diamond:
      item: minecraft:diamond
      chance: 0.025
      min-level: 25
      amount:
        min: 1
        max: 2
```

Use vanilla item registry IDs.

Support modded items automatically.

---

# 23. Equipment

Port after attributes are stable.

Create:

```java
MobEquipmentService
```

Handle:

```text
main hand
off hand
helmet
chestplate
leggings
boots
```

Use registry identifiers.

Support:

```text
chance
level range
enchantments
drop chance
```

Avoid Item-NBT-API.

Use native Data Components for modern Minecraft versions.

---

# 24. Nametags

Do **not** port the Bukkit/NMS packet implementation.

Start with server-side entity custom names:

```java
entity.setCustomName(...)
entity.setCustomNameVisible(...)
```

Example:

```text
Lv. 15 Zombie
```

Use **TextPlaceholderAPI** as the standard placeholder layer for nametag rendering.

Recommended placeholders:

```text
%lampas:mob_level%
%lampas:mob_name%
%lampas:mob_health%
%lampas:mob_max_health%
%lampas:mob_health_percent%
%lampas:mob_entity_id%
%lampas:mob_spawn_reason%
```

Example configuration:

```yaml
nametag:
  format: "<gray>Lv. %lampas:mob_level% %lampas:mob_name%"
```

Create:

```java
LevelledMobsPlaceholders
```

Responsibilities:

```text
register Lampas/LevelledMobs placeholders
resolve placeholder values from a mob context
bridge LevelledMobData into TextPlaceholderAPI
avoid duplicating placeholder parsing logic
```

Nametag rendering flow:

```text
LivingEntity
    ↓
LevelledMobData
    ↓
LevelledMobsPlaceholders
    ↓
TextPlaceholderAPI
    ↓
Minecraft Text
    ↓
entity.setCustomName(...)
```

Advanced per-player nametags can be a later networking feature.

---

# 25. Text Formatting and Placeholder API

Use **Patbox TextPlaceholderAPI** as the standard Fabric placeholder framework.

Repository:

```text
https://github.com/Patbox/TextPlaceholderAPI or C:\Users\markj\source\repos\TextPlaceholderAPI
```

This replaces the Bukkit PlaceholderAPI role and removes the need to invent a Lampas-specific placeholder engine.

Do not carry MineDown directly unless required.

Prefer:

```text
Minecraft Text
+
TextPlaceholderAPI
```

for configurable text.

TextPlaceholderAPI should be usable across LampasCore systems, including:

```text
mob nametags
death messages
commands
debug output
bossbars
scoreboards
HUD integrations
chat integrations
future NPC/display systems
```

Keep LevelledMobs-specific registration isolated behind:

```java
LevelledMobsPlaceholders
```

Do **not** expose TextPlaceholderAPI calls throughout the rule engine or attribute services.

The dependency belongs at the presentation/integration layer only.

Use the TextPlaceholderAPI release that matches the project's target Minecraft/Fabric version. Do not blindly depend on repository HEAD.

Prefer a normal mod dependency when LampasCore already distributes TextPlaceholderAPI as part of the modpack. JiJ is acceptable if LampasCore must be self-contained and the selected TextPlaceholderAPI version supports that distribution model.

---

# 26. Configuration

Do not require LevelledMobs' exact YAML internals initially.

Preserve conceptual compatibility where practical.

Recommended:

```text
config/lampas/levelledmobs/
├── settings.yml
├── rules.yml
├── drops.yml
└── messages.yml
```

Or JSON5 if that is already the LampasCore configuration convention.

The internal data model must not depend on the serialization format.

---

# 27. Configuration Migration

Later, create:

```java
LegacyLevelledMobsConfigImporter
```

which can consume original LevelledMobs configs.

Do **not** let legacy parsing complicate the core port.

Port order:

```text
native Fabric config
↓
feature stabilization
↓
legacy compatibility importer
```

---

# 28. Commands

Replace Bukkit commands/CommandAPI with Fabric's Brigadier registration.

Root:

```text
/levelledmobs
```

Aliases:

```text
/lm
```

Initial commands:

```text
/lm info
/lm reload
/lm inspect
/lm level <entity> <level>
/lm summon <entity> <level>
/lm debug
/lm rules
```

Useful inspection command:

```text
/lm inspect
```

Then clicking a mob or looking at one reports:

```text
Entity
Registry ID
UUID
Level
Spawn reason
Applied rules
Strategies
Base attributes
Final attributes
Drop multiplier
XP multiplier
```

This command should be implemented early because it greatly simplifies debugging.

---

# 29. Permissions

Do not embed LuckPerms as a required dependency.

Create:

```java
PermissionService
```

Default implementation:

```text
server operator level
```

Optional implementation:

```text
Fabric Permissions API
LuckPerms-compatible provider
```

Example nodes:

```text
lampas.levelledmobs.reload
lampas.levelledmobs.debug
lampas.levelledmobs.inspect
lampas.levelledmobs.summon
lampas.levelledmobs.level
```

---

# 30. External Compatibility

Do not port all Bukkit plugin integrations initially.

### Drop entirely

```text
Bukkit PlaceholderAPI
NBTAPI
Essentials
CommandAPI
Paper API
Spigot API
```

These should not exist in the Fabric implementation.

### Replace with Fabric equivalents where needed

```text
Bukkit PlaceholderAPI
→ Patbox TextPlaceholderAPI

WorldGuard
→ claims/region compatibility abstraction

MythicMobs
→ custom/modded entity support

LM_Items
→ vanilla/modded item registry IDs
```

Create:

```java
RegionProtectionProvider
PlayerLevelProvider
CustomEntityProvider
LevelledMobsPlaceholders
```

`LevelledMobsPlaceholders` registers Lampas placeholders with TextPlaceholderAPI.

Do not create a competing generic `PlaceholderProvider` unless a future requirement exists that TextPlaceholderAPI cannot satisfy.

Optional mods can implement the remaining compatibility interfaces later.

---

# 31. Modded Mob Compatibility

This should be considered a first-class requirement.

Avoid code such as:

```java
switch(entity.getType()) {
    case ZOMBIE:
    case SKELETON:
}
```

Prefer:

```java
Registries.ENTITY_TYPE.getId(entity.getType())
```

All living modded entities should be levelable unless:

```text
explicitly excluded
unsupported because of entity semantics
boss
player
armor stand/display/etc.
```

Config should control exclusions.

---

# 32. Boss Handling

Default exclude:

```text
minecraft:ender_dragon
minecraft:wither
```

unless explicitly enabled.

Also expose:

```java
BossClassifier
```

for modded bosses.

Potential signals:

```text
entity tag
registry tag
custom interface
config list
```

---

# 33. Transformations

Handle:

```text
zombie → drowned
villager → zombie villager
piglin → zombified piglin
slime splitting
mooshroom transformations
modded entity conversions
```

Policy:

```text
preserve existing level
```

unless a rule explicitly requests recalculation.

Transfer:

```text
LevelledMobData
relevant equipment metadata
level origin
```

---

# 34. Chunk Loading

On chunk load:

```text
iterate living entities
    ↓
has Lampas level metadata?
        yes → validate modifiers only
        no → determine whether legacy/unprocessed
```

Do **not** blindly reroll levels every chunk load.

Existing level:

```text
Level 27
```

must remain:

```text
Level 27
```

after:

```text
chunk unload
server restart
dimension reload
```

---

# 35. Queueing and Performance

Fabric should retain the principle but simplify it.

Create:

```java
MobProcessingQueue
```

Budget work per tick:

```yaml
processing:
  max-mobs-per-tick: 50
```

Avoid:

```text
processing every loaded mob every tick
```

Rules should normally execute only on:

```text
spawn
load
explicit relevel
config reload where necessary
```

---

# 36. Caching

Cache compiled rules.

Potential key:

```java
record RuleCacheKey(
    Identifier entityType,
    RegistryKey<World> dimension,
    RegistryKey<Biome> biome
) {}
```

Do not cache values involving:

```text
coordinates
nearest player
current Y
player level
spawn reason
```

unless those variables are represented in the cache key.

---

# 37. Server Thread Safety

Minecraft entity modification must happen on the server thread.

Config parsing can happen separately, but swap the finished ruleset atomically:

```java
AtomicReference<CompiledRules>
```

Flow:

```text
parse
validate
compile
atomic replace
```

Never expose half-loaded config.

---

# 38. Internal Events / API

Recreate the intent of upstream:

```text
MobPreLevelEvent
MobPostLevelEvent
SummonedMobPreLevelEvent
```

but as Fabric/Lampas API.

Example:

```java
public interface MobPreLevelCallback {
    Event<MobPreLevelCallback> EVENT = ...;
}
```

Allow integrations to:

```text
cancel levelling
change requested level
inspect rule result
modify attribute scaling
```

---

# 39. Placeholder Integration Contract

TextPlaceholderAPI is the canonical text-placeholder integration for this port.

Register a stable Lampas namespace.

Initial public placeholders:

```text
%lampas:mob_level%
%lampas:mob_name%
%lampas:mob_health%
%lampas:mob_max_health%
%lampas:mob_health_percent%
%lampas:mob_entity_id%
%lampas:mob_spawn_reason%
```

Requirements:

```text
placeholder resolution must be side-effect free
placeholder resolution must not recalculate a mob level
placeholder resolution must not reparse rules
placeholder resolution must read existing LevelledMobData
placeholder resolution must tolerate non-levelled entities
placeholder names should remain stable once released
```

Java integrations should continue to use:

```java
LevelledMobsApi
```

Text/config integrations should use:

```text
TextPlaceholderAPI
```

This provides two clean integration surfaces without exposing implementation internals.

---

# 40. Public API

Create a small stable API:

```java
LevelledMobsApi.getLevel(entity)

LevelledMobsApi.isLevelled(entity)

LevelledMobsApi.setLevel(entity, level)

LevelledMobsApi.relevel(entity)

LevelledMobsApi.getEffectiveRule(entity)
```

Do not expose internal manager implementations.

---

# 41. Tests

Port core rules into tests before wiring all game hooks.

## Unit tests

```text
Rule merging
Entity include/exclude
Dimension matching
Biome matching
Level min/max
Random strategy
Spawn-distance strategy
Y strategy
Attribute formula
Drop multiplier
XP multiplier
Tier calculation
```

Example:

```text
spawnDistance = 2500
distancePerLevel = 100
baseLevel = 1

expected level = 26
```

---

# 42. Game Tests

Use Minecraft GameTest where practical.

Required scenarios:

### Spawn

```text
spawn zombie
assert level exists
assert correct attributes
```

### Persistence

```text
spawn mob
level = N
save/reload
assert level == N
```

### Chunk

```text
level mob
unload chunk
reload chunk
assert level unchanged
assert modifiers not duplicated
```

### Combat

```text
level 1 zombie damage
level 20 zombie damage
assert correct scaling
```

### Drops

```text
kill levelled mob
verify configured loot behavior
```

### Transformation

```text
zombie → drowned
assert level preserved
```

---

# 43. Performance Test

Generate approximately:

```text
500
1,000
5,000
```

levelled entities.

Measure:

```text
spawn processing time
tick time
rule evaluation cost
chunk load cost
memory usage
nametag update cost
```

Performance target:

Normal levelled mobs should add effectively **zero recurring per-tick work** unless a feature genuinely needs polling.

---

# 44. Development Phases

## Phase 0 — Audit

Deliver:

```text
FEATURE_MATRIX.md
BUKKIT_DEPENDENCIES.md
PORT_STATUS.md
```

No gameplay code yet.

---

## Phase 1 — Fabric Module Skeleton

Implement:

```text
LevelledMobsModule
config directory
service registry
logging
Brigadier root command
```

Build must succeed.

Also add the Minecraft-version-compatible TextPlaceholderAPI dependency during this phase if LampasCore's dependency management is being established here. Registration of LevelledMobs placeholders can wait until the nametag phase.

---

## Phase 2 — Entity Data

Implement:

```text
LevelledMobHolder
LevelledMobData
NBT persistence Mixin
```

Test:

```text
assign level
restart server
level survives
```

This is the first hard milestone.

---

## Phase 3 — Minimal Leveling

Support:

```text
hostile vanilla mobs
random level 1–10
max health scaling
attack damage scaling
```

No custom drops.

No complex rules.

Goal:

```text
Zombie spawns as "Lv. 7 Zombie"
and actually has Lv. 7 stats.
```

---

## Phase 4 — Rule Engine

Port:

```text
RuleInfo concepts
RulesManager concepts
rule priority
rule merging
filters
```

Support:

```text
entity
entity tag
biome
biome tag
dimension
Y range
spawn reason
```

---

## Phase 5 — Strategies

Port:

```text
Random
SpawnDistance
YDistance
Player
Custom formula
```

Validate parity with upstream examples.

---

## Phase 6 — Full Attributes

Port fine-tuning concepts.

Implement:

```text
health
damage
speed
armor
armor toughness
knockback
follow range
```

Guarantee idempotency.

---

## Phase 7 — Lifecycle Coverage

Implement:

```text
spawn
chunk load
conversion
taming
equipment pickup
portal/dimension changes
summoning
spawners
spawn eggs
```

Add Mixins only where required.

---

## Phase 8 — Drops / XP

Implement:

```text
XP multiplier
vanilla drop multiplier
custom drops
equipment drops
```

---

## Phase 9 — Nametags and TextPlaceholderAPI

Add the project-compatible TextPlaceholderAPI dependency.

Implement:

```text
LevelledMobsPlaceholders
mob level placeholder
mob name placeholder
health placeholders
entity registry ID placeholder
spawn reason placeholder
nametag templates
visibility rules
refresh on damage
```

Validate that placeholders resolve correctly from the entity being rendered.

Do not build a separate placeholder parser.

Avoid constant global polling.

---

## Phase 10 — Commands and Debugging

Complete:

```text
reload
inspect
rules
debug
summon
setlevel
```

---

## Phase 11 — Mod Compatibility

Test with:

```text
vanilla
BetterNether
BetterEnd
common Fabric mob mods
```

Verify registry-based rules work without explicit code changes.

---

## Phase 12 — Legacy Configuration Import

Only after core stability.

Support translating useful upstream LevelledMobs configuration into Lampas configuration.

---

# 45. Features to Defer

Do not allow these to block the initial playable port:

```text
WorldGuard compatibility
PlaceholderAPI compatibility
Essentials integration
MythicMobs compatibility
LM_Items compatibility
SimplePets
full custom-drop parity
per-player packet nametags
legacy config 100% compatibility
old Minecraft compatibility layers
bstats
update checker
```

The first release should prioritize:

```text
level generation
rules
attributes
persistence
drops
nametags
TextPlaceholderAPI integration
modded entities
```

---

# 46. Code the Agent Must Not Port

Do not copy/reimplement these concepts literally:

```text
Bukkit Listener registration
JavaPlugin lifecycle
Bukkit Scheduler
PersistentDataContainer
Bukkit EntityType
Bukkit World
Bukkit Location
Bukkit ItemStack
plugin.yml
CommandAPI
PlaceholderAPI internals
NBTAPI
Paper utilities
Spigot utilities
NMS reflection/mapping layer
```

These represent the platform being replaced.

---

# 47. Recommended Mapping

| LevelledMobs / Bukkit | Lampas / Fabric |
|---|---|
| `JavaPlugin` | `ModInitializer` / module |
| Bukkit `LivingEntity` | Minecraft `LivingEntity` |
| Bukkit `EntityType` | `EntityType<?>` + registry ID |
| `World` | `ServerWorld` |
| `Location` | `BlockPos` / `Vec3d` |
| `PersistentDataContainer` | Entity NBT Mixin |
| `NamespacedKey` | `Identifier` |
| Bukkit events | Fabric callbacks / Mixins |
| Bukkit scheduler | Server tick callbacks |
| Bukkit attributes | `EntityAttributeInstance` |
| Bukkit commands | Brigadier |
| `ItemStack` | Minecraft `ItemStack` |
| Bukkit biome | `RegistryEntry<Biome>` |
| World name | dimension registry key |
| Bukkit PlaceholderAPI | TextPlaceholderAPI |
| WorldGuard | optional region provider |
| NBTAPI | vanilla NBT/Data Components |
| NMS nametag packets | Minecraft networking/client support |

---

# 48. Important Refactoring Targets

Break the Fabric implementation into:

```text
LevelCalculationService
RuleResolutionService
AttributeScalingService
MobPersistenceService
MobLifecycleService
DropService
NametagService
SpawnReasonResolver
```

Avoid creating:

```text
FabricLevelManager.java
```

with another 3,000+ lines.

---

# 49. Agent Execution Rules

The coding agent must follow these rules throughout the port:

1. **Keep the project compiling after each phase.**
2. Never perform a mass package rename and attempt to fix hundreds of errors afterward.
3. Port one subsystem at a time.
4. Add tests before replacing complicated rule behavior.
5. Do not add Bukkit/Paper APIs to the Fabric dependency tree.
6. Avoid compatibility libraries whose only purpose is emulating Bukkit.
7. Prefer Minecraft/Fabric APIs.
8. Use targeted Mixins when an appropriate Fabric callback does not exist.
9. Avoid `@Overwrite`.
10. Every Mixin must have a documented reason.
11. Support registry IDs rather than vanilla-only enums.
12. Treat modded entities/biomes/dimensions/items as normal registry entries.
13. Persist authoritative data on the entity itself.
14. Ensure all attribute application is idempotent.
15. Never reroll mob levels merely because a chunk loaded.
16. Never perform global entity scans every tick.
17. Do not port optional Bukkit integrations until core leveling works.
18. Use TextPlaceholderAPI instead of implementing a parallel placeholder framework.
19. Keep placeholder registration out of rule/attribute/persistence services.
20. Preserve GPL attribution for code derived from LevelledMobs.

---

# 50. Definition of MVP

The Fabric port reaches MVP when this scenario works:

```text
1. Start Fabric server.

2. Zombie naturally spawns.

3. Lampas determines:
      Entity = minecraft:zombie
      Biome = minecraft:plains
      Dimension = minecraft:overworld
      Spawn reason = NATURAL
      Distance from spawn = 1250 blocks

4. Rules resolve:
      Level range = 1–50
      Strategy = SPAWN_DISTANCE

5. Calculated:
      Level = 13

6. Lampas applies:
      +max health
      +attack damage
      XP multiplier
      drop multiplier

7. Entity displays:
      Lv. 13 Zombie

8. Entity enters unloaded chunk.

9. Chunk reloads.

10. Zombie is still level 13.

11. Server restarts.

12. Zombie is still level 13.

13. Killing the zombie produces:
      scaled XP
      expected loot

14. /lm inspect confirms the exact rules
    and calculations applied.
```

If that works reliably, the architectural port is successful.

---

# 51. Definition of Done

The full port is considered done when:

- [ ] No Bukkit or Paper classes exist in the Fabric module.
- [ ] No Bukkit/Paper dependencies exist in Gradle.
- [ ] Entity level persists in NBT.
- [ ] Attribute modifiers never duplicate.
- [ ] Natural spawns are handled.
- [ ] Spawners are handled.
- [ ] Spawn eggs are handled.
- [ ] Commands are handled.
- [ ] Entity conversions preserve levels.
- [ ] Chunk reloads preserve levels.
- [ ] Server restarts preserve levels.
- [ ] Entity registry conditions work.
- [ ] Biome conditions work.
- [ ] Dimension conditions work.
- [ ] Spawn reason conditions work.
- [ ] Random strategy works.
- [ ] Spawn-distance strategy works.
- [ ] Y-distance strategy works.
- [ ] Player strategy works.
- [ ] Attribute scaling works.
- [ ] XP scaling works.
- [ ] Drop scaling works.
- [ ] Nametags work.
- [ ] TextPlaceholderAPI is integrated.
- [ ] `%lampas:mob_level%` resolves correctly for levelled mobs.
- [ ] Core mob placeholders are reusable by other LampasCore text systems.
- [ ] No custom competing placeholder framework was introduced.
- [ ] Modded entities can be targeted via registry ID.
- [ ] Modded biomes can be targeted via registry ID/tag.
- [ ] `/lm inspect` exposes rule calculations.
- [ ] Config reload is safe.
- [ ] Core rule logic has unit tests.
- [ ] Lifecycle behavior has GameTests.
- [ ] No global per-tick mob scan exists.
- [ ] All Mixins are documented.
- [ ] GPL attribution/license obligations are preserved.

## Recommended first implementation slice

Tell the agent **not to start with full LevelledMobs parity**.

The first PR should contain only:

```text
LevelledMobsModule
        +
LevelledMobData
        +
LevelledMobHolder
        +
NBT persistence
        +
spawn interception
        +
random level 1–10
        +
health scaling
        +
attack damage scaling
        +
TextPlaceholderAPI integration
        +
basic nametag
        +
/lm inspect
```

That gives us a vertical slice through the entire architecture:

```text
SPAWN
  ↓
LEVEL
  ↓
STORE
  ↓
SCALE
  ↓
DISPLAY
  ↓
SAVE
  ↓
RELOAD
```

Once **that** is reliable, port `RulesManager`/strategies and the more complicated LevelledMobs behavior.

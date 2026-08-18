# LevelledMobs → Fabric / LampasCore: Bukkit & Paper Dependency Replacement Catalog

This document details every Bukkit, Paper, Spigot, and external plugin touchpoint found in `justbecauseph/LevelledMobs` and defines the exact Minecraft / Fabric replacement strategy.

---

## 1. Core Platform & Lifecycle Dependencies

| Upstream Bukkit / Paper Component | Purpose in Upstream | Target Fabric / Minecraft Replacement | Technical Details & Strategy |
|---|---|---|---|
| `org.bukkit.plugin.java.JavaPlugin` | Plugin entrypoint, lifecycle management (`onEnable`, `onDisable`). | `net.fabricmc.api.ModInitializer` / `lampas.levelledmobs.LevelledMobsModule` | Lifecycle initialization hooked into Fabric's `onInitializeServer` / module init. |
| `org.bukkit.scheduler.BukkitScheduler` / `io.papermc.paper.threadedregions.scheduler` | Ticking, delayed tasks, async workers. | `net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents` + `java.util.concurrent.CompletableFuture` | Work queue drained per-tick with fixed budget (`max-mobs-per-tick`). Heavy rule compilation run asynchronously. |
| `org.bukkit.configuration.file.YamlConfiguration` | Config file reading, default extraction. | Native YAML / JSON5 parser (`org.spongepowered.configurate` or native SnakeYAML) | Compiled into immutable Java records at load/reload time. No ad-hoc string lookups during mob tick. |
| `plugin.yml` / `paper-plugin.yml` | Plugin manifest and descriptor. | `fabric.mod.json` | Modern Fabric mod descriptor with metadata, entrypoints, and dependencies. |
| `org.bukkit.command.CommandExecutor` / `CommandAPI` | Command handling and tab completion. | Brigadier (`com.mojang.brigadier.CommandDispatcher`) via `CommandRegistrationCallback` | Type-safe tree registration with literal and argument builders. |

---

## 2. Entity & World Model Dependencies

| Upstream Bukkit / Paper Component | Purpose in Upstream | Target Fabric / Minecraft Replacement | Technical Details & Strategy |
|---|---|---|---|
| `org.bukkit.entity.LivingEntity` | Mob representation and modification. | `net.minecraft.entity.LivingEntity` | Direct vanilla entity reference in Minecraft server thread. |
| `org.bukkit.entity.EntityType` | Entity type categorization and matching. | `net.minecraft.entity.EntityType<?>` + `net.minecraft.util.Identifier` | Registered under `net.minecraft.registry.Registries.ENTITY_TYPE`. Allows seamless modded entity matching via `Identifier` and `TagKey<EntityType<?>>`. |
| `org.bukkit.World` | World context and coordinate space. | `net.minecraft.server.world.ServerWorld` | Dimension accessed via `RegistryKey<World>` (`world.getRegistryKey()`). |
| `org.bukkit.Location` | 3D coordinate and rotation vector. | `net.minecraft.util.math.Vec3d` + `net.minecraft.util.math.BlockPos` | Precise positioning via `Vec3d`, block level coordinates via `BlockPos`. |
| `org.bukkit.block.Biome` | Biome type matching. | `net.minecraft.registry.entry.RegistryEntry<Biome>` / `RegistryKey<Biome>` | Registry-driven biome checks with `TagKey<Biome>` support for modded dimensions. |
| `org.bukkit.generator.structure.Structure` | Structure condition matching. | `net.minecraft.world.gen.structure.Structure` / `TagKey<Structure>` | Evaluated via `ServerWorld.getStructureAccessor().getStructureAt(...)`. |
| `org.bukkit.NamespacedKey` | Keyed identifiers. | `net.minecraft.util.Identifier` | Vanilla Minecraft `Identifier("lampas", "level")`. |

---

## 3. Persistence & NBT Storage

| Upstream Bukkit / Paper Component | Purpose in Upstream | Target Fabric / Minecraft Replacement | Technical Details & Strategy |
|---|---|---|---|
| `org.bukkit.persistence.PersistentDataContainer` | Custom mob metadata storage (`level`, `levelled`, `rule`). | Vanilla Entity NBT via Mixin + `LevelledMobHolder` duck interface | Injected into `LivingEntity.writeCustomDataToNbt(NbtCompound)` and `LivingEntity.readCustomDataFromNbt(NbtCompound)` under the compound tag `lampas:levelled_mob`. |
| `de.tr7zw:item-nbt-api` | Item custom data manipulation. | Vanilla Minecraft Data Components (`net.minecraft.component.ComponentMap`) | Minecraft 1.20.5+ / 1.21+ uses immutable Data Components (`DataComponentTypes`) for item metadata and custom tags. |

---

## 4. Attribute & Combat Dependencies

| Upstream Bukkit / Paper Component | Purpose in Upstream | Target Fabric / Minecraft Replacement | Technical Details & Strategy |
|---|---|---|---|
| `org.bukkit.attribute.Attribute` | Attribute types (`GENERIC_MAX_HEALTH`, etc.). | `net.minecraft.entity.attribute.EntityAttribute` + `EntityAttributes` registry | Registered `RegistryEntry<EntityAttribute>` entries in `EntityAttributes` (`GENERIC_MAX_HEALTH`, `GENERIC_ATTACK_DAMAGE`, etc.). |
| `org.bukkit.attribute.AttributeModifier` | Attribute modifier instances and math operations. | `net.minecraft.entity.attribute.EntityAttributeModifier` | Deterministic `Identifier` namespaced modifiers (`lampas:levelled/<attribute>`). Removed and reapplied cleanly (idempotent). |
| `org.bukkit.event.entity.EntityDamageByEntityEvent` | Melee and projectile damage modification. | `events/CombatHandler.java` + Mixin on `LivingEntity.damage` | Intercepts damage calculation before damage absorption and armor calculations without double-dipping attribute modifiers. |
| `org.bukkit.event.entity.EntityCombustEvent` | Sunlight burning logic. | Mixin / event hook on `MobEntity.tick` | Custom sunlight resistance evaluation if configured. |

---

## 5. Event Handling & Lifecycle

| Upstream Bukkit / Paper Event | Purpose in Upstream | Target Fabric Replacement | Injection Point / Strategy |
|---|---|---|---|
| `CreatureSpawnEvent` / `EntitySpawnEvent` | Triggering mob level assignment. | `net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD` + Mixin on `MobEntity.initialize` | Primary hook on spawn; assigns level and applies attributes. |
| `ChunkLoadEvent` | Validating existing mobs on chunk load. | `net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD` | Validates NBT and modifiers; guarantees zero reroll of existing levels. |
| `EntityTransformEvent` | Preserving levels during Zombie → Drowned, etc. | Mixin on `MobEntity.convertTo` / `SlimeEntity.remove` | Copies `LevelledMobData` from parent entity to newly created child entity. |
| `EntityTameEvent` | Unleveling or locking tamed pets. | `net.fabricmc.fabric.api.event.player.UseEntityCallback` / Mixin on `TameableEntity.setOwner` | Checks `tamed-policy` from `EffectiveRule` and strips modifiers if configured. |
| `EntityDeathEvent` | Custom drops, equipment drops, XP scaling. | `ServerLivingEntityEvents.AFTER_DEATH` + Mixin on `LivingEntity.dropXp` and `LivingEntity.dropLoot` | Multiplies vanilla drop tables, evaluates custom drop chances, scales XP orbs. |
| `EntityPickupItemListener` | Tracking picked up items. | Mixin on `MobEntity.loot` | Tags picked up item stacks with custom NBT/component to prevent drop duplication on death. |

---

## 6. Text Presentation & Placeholder Integration

| Upstream Bukkit Component | Purpose in Upstream | Target Fabric Replacement | Technical Details & Strategy |
|---|---|---|---|
| `net.kyori.adventure.text.Component` | Text styling and formatting. | `net.minecraft.text.Text` + Adventure (MiniMessage) | Native Minecraft `Text` components. |
| `me.clip.placeholderapi.PlaceholderAPI` | Placeholder substitution in nametags. | `eu.pb4.placeholders.api.PlaceholderContext` & `Placeholders` (`TextPlaceholderAPI`) | Standard Fabric placeholder engine. Registers `%lampas:mob_level%`, `%lampas:mob_health%`, etc. |
| NMS Packet sending (`ClientboundSetEntityDataPacket`) | Per-player custom nametags. | Server-side entity custom name (`LivingEntity.setCustomName`) | Standard vanilla synchronized custom names. Packet-level per-player customization deferred to future enhancement. |

---

## 7. External Integrations Catalog

| External Bukkit Plugin | Upstream Integration | Fabric Equivalent / Replacement | Strategy |
|---|---|---|---|
| **WorldGuard** | Region-based mob rule overrides. | `compatibility/RegionProtectionProvider.java` | Optional SPI bridging to FLAN, GOML, and Common Protection API. |
| **PlaceholderAPI** | Placeholder expansion in nametags. | **Patbox TextPlaceholderAPI** | First-class integration via `LevelledMobsPlaceholders`. |
| **MythicMobs** | Custom mob exclusion and metadata. | Registry ID exclusion + `CustomEntityProvider` SPI | Automatically handles all Fabric modded entities by registry ID (`modid:mob_name`). |
| **bStats** | Bukkit metrics collection. | *None* (Excluded) | Excluded per plan. |
| **LM_Items** | Custom Bukkit item lookup. | Vanilla `net.minecraft.registry.Registries.ITEM` | Native registry identifiers (`modid:item_name`). |
| **EssentialsX** | Player home and spawn coordinates. | Vanilla spawn / bed spawn location | Uses `ServerPlayerEntity.getSpawnPointPosition()`. |

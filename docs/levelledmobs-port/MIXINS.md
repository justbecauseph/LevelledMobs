# LevelledMobs Fabric Mixins Architecture & Inventory

This document catalogs every Mixin introduced in `lampas.levelledmobs.mixin`, describing the injection point, method target, and architectural rationale.

---

## 1. Design Principles for Mixins

1. **Fabric Event First**: Fabric API callbacks (`ServerEntityEvents`, `ServerTickEvents`, `CommandRegistrationCallback`) are always preferred over Mixins.
2. **Narrow Injections**: Only target specific methods with `@Inject` (at `HEAD`, `TAIL`, or `RETURN`) or `@ModifyVariable`. Overwrites are strictly prohibited.
3. **No Domain Logic in Mixins**: Mixins act purely as non-invasive bridges that delegate to pure domain services (`MobLevelingService`, `CombatHandler`, `LevelledMobHolder`).

---

## 2. Active Mixin Catalog

### 1. `LivingEntityMixin`
- **Target Class**: `net.minecraft.world.entity.LivingEntity`
- **Interfaces Implemented**: `LevelledMobHolder`
- **Injections**:
  - `addAdditionalSaveData(CompoundTag tag)` at `TAIL`: Persists `lampas:level`, `lampas:levelled`, `lampas:rule`, and `lampas:generated_at` to the entity's custom NBT.
  - `readAdditionalSaveData(CompoundTag tag)` at `TAIL`: Deserializes persisted `LevelledMobData` when entities load from disk or chunk activation.
- **Rationale**: Provides duck-typing interface (`LevelledMobHolder`) and persistent NBT serialization so mob levels are permanently remembered across server restarts and chunk unloads without per-tick queries.

### 2. `LivingEntityCombatMixin`
- **Target Class**: `net.minecraft.world.entity.LivingEntity`
- **Injections**:
  - `actuallyHurt(ServerLevel level, DamageSource source, float amount)` at `HEAD` (`@ModifyVariable(argsOnly = true, ordinal = 0)`): Intercepts incoming damage amount and passes to `CombatHandler.modifyDamage(...)`.
- **Rationale**: Allows projectile damage (skeletons, ghasts) and explosion damage (creepers) from levelled mobs to be multiplied dynamically based on level, while preventing double-scaling on direct melee attacks (which vanilla already scales via `Attributes.ATTACK_DAMAGE`).

### 3. `EntityConversionMixin`
- **Target Class**: `net.minecraft.world.entity.Mob`
- **Injections**:
  - `convertTo(EntityType<T> entityType, ConversionParams conversionParams, ConversionCallback<T> conversionCallback)` at `RETURN`: Transfers `LevelledMobData` from the original mob to the newly converted mob.
- **Rationale**: Ensures entity transformations (such as Zombie transforming to Drowned in water, Villager converting to Zombie Villager, or Piglin converting to Zombified Piglin) retain their exact level and attribute scaling instead of resetting or rerolling.

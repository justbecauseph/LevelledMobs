package lampas.levelledmobs.level;

import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.attributes.HealthPolicy;
import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.LevelledMobModifier;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.nametag.NametagService;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.RuleManager;
import lampas.levelledmobs.rules.RuleResult;
import lampas.levelledmobs.rules.strategy.LevelStrategy;
import lampas.levelledmobs.rules.strategy.StrategyRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service coordinating mob leveling lifecycle and rule resolution.
 */
public class MobLevelingService {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");
    private static final int MAX_DEFERRED_MARKERS = 4_096;

    private final RuleManager ruleManager;
    private final AttributeScalingService attributeService;
    private final NametagService nametagService;
    private final BossClassifier bossClassifier;
    private final RandomSource random = RandomSource.create();
    private final Set<UUID> deferredEntities = ConcurrentHashMap.newKeySet();

    public MobLevelingService(RuleManager ruleManager, AttributeScalingService attributeService, NametagService nametagService) {
        this(ruleManager, attributeService, nametagService, new BossClassifier());
    }

    public MobLevelingService(RuleManager ruleManager, AttributeScalingService attributeService, NametagService nametagService, BossClassifier bossClassifier) {
        this.ruleManager = ruleManager;
        this.attributeService = attributeService;
        this.nametagService = nametagService;
        this.bossClassifier = bossClassifier != null ? bossClassifier : new BossClassifier();
    }

    /**
     * Primary entry point for leveling an entity with a specified SpawnReason.
     */
    public void level(LivingEntity entity, SpawnReason spawnReason) {
        onEntityLoad(entity, spawnReason);
    }

    /**
     * Called when an entity is loaded or spawned into a world.
     * Evaluates if the entity should be levelled via rules or have its existing level restored.
     */
    public void onEntityLoad(LivingEntity entity) {
        onEntityLoad(entity, SpawnReason.NATURAL);
    }

    /**
     * Called when an entity is loaded or spawned with a specific SpawnReason.
     */
    public void onEntityLoad(LivingEntity entity, SpawnReason spawnReason) {
        if (!(entity instanceof LevelledMobHolder holder)) {
            return;
        }

        // A direct lifecycle callback may follow an earlier deferred attempt.
        // Clear the old marker before this attempt; only the current strategy
        // result may request another queue entry.
        if (entity.getUUID() != null) {
            deferredEntities.remove(entity.getUUID());
        }

        LevelledMobData data = holder.lampas$getLevelData();

        if (data != null && data.quarantined()) {
            LOGGER.debug("Skipping quarantined LevelledMobs entity {} (UUID: {})",
                entity.getType().getDescription().getString(), entity.getUUID());
            return;
        }

        // 1. If mob already has persistent level data (e.g. from chunk load / server restart), restore attributes & nametag
        if (data != null && data.levelled()) {
            if (!attributeService.restorePersistedModifiers(entity, data)) {
                // Legacy entities have no trustworthy formula outcome. Their
                // serialized vanilla attributes are the established stats;
                // never reroll them against current defaults.
                LOGGER.debug("Retaining existing attributes for legacy or unknown LevelledMobs data on {} (UUID: {})",
                    entity.getType().getDescription().getString(), entity.getUUID());
            }
            nametagService.updateNametag(entity, data.level());
            return;
        }

        // 2. Check boss filter and custom entity bypass
        if (!bossClassifier.shouldLevel(entity) || lampas.levelledmobs.compatibility.ModdedMobHandler.shouldBypass(entity)) {
            return;
        }

        // 2b. Check region protection
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel &&
            !lampas.levelledmobs.compatibility.ModdedMobHandler.canLevelAt(serverLevel, entity.blockPosition(), entity)) {
            return;
        }

        // 3. Build MobContext and evaluate against RuleManager
        SpawnReason effectiveReason = SpawnReasonResolver.infer(entity, spawnReason);
        MobContext context = MobContext.of(entity, effectiveReason);
        RuleResult result = ruleManager.resolve(context);

        if (result.matched() && entity instanceof Monster && !entity.isBaby()) {
            EffectiveRule rule = result.effectiveRule();
            LevelStrategy strategy = StrategyRegistry.INSTANCE.resolveStrategy(rule.strategyName(), context, rule);

            int level = lampas.levelledmobs.compatibility.ModdedMobHandler.getPredefinedLevel(entity)
                .orElseGet(() -> strategy.calculateLevel(context, rule));

            // Strategies may defer an external managed encounter while its
            // immutable creation context is unavailable. Zero is not a
            // valid LevelledMobs level, so leave the entity untouched for a
            // bounded retry by the owning integration.
            if (level <= 0) {
                if (strategy.shouldRetryDeferredLevel(context, rule)) {
                    deferEntity(entity);
                    LOGGER.debug("Deferred leveling {} because strategy '{}' returned no level",
                        entity.getUUID(), strategy.name());
                } else {
                    markQuarantined(entity, "strategy returned no trusted level");
                }
                return;
            }

            // Trigger pre-level callback to allow modders to cancel or alter level
            lampas.levelledmobs.api.events.MobPreLevelCallback.Result preResult =
                lampas.levelledmobs.api.events.MobPreLevelCallback.EVENT.invoker().onPreLevel(entity, level, rule.primaryRuleId());

            if (preResult.isCancelled()) {
                return;
            }
            if (preResult.getNewLevel() > 0) {
                level = preResult.getNewLevel();
            }

            // A managed integration may own a narrower immutable creation
            // range than the configured LM strategy. Apply that constraint
            // after callbacks so predefined levels and callback rewrites
            // cannot bypass the authoritative encounter context.
            level = strategy.constrainLevel(context, rule, level);
            if (level <= 0) {
                if (strategy.shouldRetryDeferredLevel(context, rule)) {
                    deferEntity(entity);
                    LOGGER.debug("Deferred leveling {} because strategy '{}' rejected an unavailable level",
                        entity.getUUID(), strategy.name());
                } else {
                    markQuarantined(entity, "strategy rejected level outside trusted bounds");
                }
                return;
            }

            List<LevelledMobModifier> effectiveModifiers = attributeService.resolveEffectiveModifierPlan(level, rule);
            attributeService.applyModifiers(entity, effectiveModifiers, HealthPolicy.FRESH_SPAWN);

            LevelledMobData newData = LevelledMobData.of(level, rule.primaryRuleId())
                .withEffectiveModifiers(effectiveModifiers);
            holder.lampas$setLevelData(newData);
            nametagService.updateNametag(entity, level, rule.primaryRuleId());

            // Trigger post-level callback
            lampas.levelledmobs.api.events.MobPostLevelCallback.EVENT.invoker().onPostLevel(entity, level, rule.primaryRuleId());

            LOGGER.debug("Levelled {} (UUID: {}) to Lv. {} via strategy '{}' in rule '{}'",
                entity.getType().getDescription().getString(), entity.getUUID(), level, strategy.name(), rule.primaryRuleId());
        }
    }

    /**
     * Manually sets an entity's level and reapplies all scaling and nametag updates.
     */
    public void setLevel(LivingEntity entity, int level, String ruleSet) {
        if (!(entity instanceof LevelledMobHolder holder)) {
            return;
        }

        if (level > 0) {
            List<LevelledMobModifier> effectiveModifiers = attributeService.resolveEffectiveModifierPlan(level, null);
            attributeService.applyModifiers(entity, effectiveModifiers, HealthPolicy.PRESERVE_RATIO);

            LevelledMobData newData = LevelledMobData.of(level, ruleSet != null ? ruleSet : "manual")
                .withEffectiveModifiers(effectiveModifiers);
            holder.lampas$setLevelData(newData);
            nametagService.updateNametag(entity, level);
        } else {
            attributeService.removeModifiers(entity);
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
        }
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    /** Restores a converted entity from its persisted outcome without rerolling current rules. */
    public void restoreLevelledData(LivingEntity entity, LevelledMobData data) {
        if (!(entity instanceof LevelledMobHolder holder) || data == null || !data.levelled()) {
            return;
        }
        if (data.quarantined() || !attributeService.restorePersistedModifiers(entity, data)) {
            LOGGER.debug("Retaining existing attributes while restoring converted entity {} (UUID: {})",
                entity.getType().getDescription().getString(), entity.getUUID());
        }
        holder.lampas$setLevelData(data);
        nametagService.updateNametag(entity, data.level(), data.ruleSet());
    }

    /** Marks an unavailable or untrusted managed mob so later lifecycle callbacks cannot scale it. */
    void markQuarantined(LivingEntity entity, String reason) {
        if (!(entity instanceof LevelledMobHolder holder)) {
            return;
        }
        LevelledMobData current = holder.lampas$getLevelData();
        if (current == null) current = LevelledMobData.EMPTY;
        holder.lampas$setLevelData(current.withQuarantined(true));
        if (entity.getUUID() != null) deferredEntities.remove(entity.getUUID());
        LOGGER.warn("Quarantined LevelledMobs entity {} (UUID: {}): {}",
            entity.getType().getDescription().getString(), entity.getUUID(), reason);
    }

    /** Marks a managed entity for one bounded retry by MobProcessingQueue. */
    void deferEntity(LivingEntity entity) {
        if (entity != null && entity.getUUID() != null) {
            UUID uuid = entity.getUUID();
            if (deferredEntities.contains(uuid) || deferredEntities.size() < MAX_DEFERRED_MARKERS) {
                deferredEntities.add(uuid);
            } else {
                markQuarantined(entity, "deferred marker capacity reached");
            }
        }
    }

    /** Consumed by the queue after an onEntityLoad attempt. */
    boolean consumeDeferred(LivingEntity entity) {
        return entity != null && entity.getUUID() != null && deferredEntities.remove(entity.getUUID());
    }
}

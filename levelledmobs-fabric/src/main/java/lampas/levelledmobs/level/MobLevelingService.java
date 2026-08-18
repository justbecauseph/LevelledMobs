package lampas.levelledmobs.level;

import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.attributes.HealthPolicy;
import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
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

/**
 * Core service coordinating mob leveling lifecycle and rule resolution.
 */
public class MobLevelingService {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");

    private final RuleManager ruleManager;
    private final AttributeScalingService attributeService;
    private final NametagService nametagService;
    private final BossClassifier bossClassifier;
    private final RandomSource random = RandomSource.create();

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

        LevelledMobData data = holder.lampas$getLevelData();

        // 1. If mob already has persistent level data (e.g. from chunk load / server restart), restore attributes & nametag
        if (data != null && data.levelled()) {
            attributeService.applyModifiers(entity, data.level(), HealthPolicy.PRESERVE_RATIO);
            nametagService.updateNametag(entity, data.level());
            return;
        }

        // 2. Check boss filter
        if (!bossClassifier.shouldLevel(entity)) {
            return;
        }

        // 3. Build MobContext and evaluate against RuleManager
        SpawnReason effectiveReason = SpawnReasonResolver.infer(entity, spawnReason);
        MobContext context = MobContext.of(entity, effectiveReason);
        RuleResult result = ruleManager.resolve(context);

        if (result.matched() && entity instanceof Monster && !entity.isBaby()) {
            EffectiveRule rule = result.effectiveRule();
            LevelStrategy strategy = StrategyRegistry.INSTANCE.getStrategy(rule.strategyName());
            int level = strategy.calculateLevel(context, rule);

            LevelledMobData newData = LevelledMobData.of(level, rule.primaryRuleId());
            holder.lampas$setLevelData(newData);

            attributeService.applyModifiers(entity, level, rule, HealthPolicy.FRESH_SPAWN);
            nametagService.updateNametag(entity, level);

            LOGGER.debug("Levelled {} (UUID: {}) to Lv. {} via strategy '{}' in rule '{}'",
                entity.getType().getDescription().getString(), entity.getUUID(), level, rule.strategyName(), rule.primaryRuleId());
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
            LevelledMobData newData = LevelledMobData.of(level, ruleSet != null ? ruleSet : "manual");
            holder.lampas$setLevelData(newData);

            attributeService.applyModifiers(entity, level, HealthPolicy.PRESERVE_RATIO);
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
}

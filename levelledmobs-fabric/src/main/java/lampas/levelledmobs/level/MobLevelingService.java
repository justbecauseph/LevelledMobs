package lampas.levelledmobs.level;

import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.nametag.NametagService;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core service coordinating mob leveling lifecycle for the vertical slice.
 */
public class MobLevelingService {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");

    private final AttributeScalingService attributeService;
    private final NametagService nametagService;
    private final RandomSource random = RandomSource.create();

    public MobLevelingService(AttributeScalingService attributeService, NametagService nametagService) {
        this.attributeService = attributeService;
        this.nametagService = nametagService;
    }

    /**
     * Called when an entity is loaded or spawned into a world.
     * Evaluates if the entity should be levelled or have its existing level restored.
     */
    public void onEntityLoad(LivingEntity entity) {
        if (!(entity instanceof LevelledMobHolder holder)) {
            return;
        }

        LevelledMobData data = holder.lampas$getLevelData();

        // 1. If mob already has persistent level data (e.g. from chunk load / server restart), restore attributes & nametag
        if (data != null && data.levelled()) {
            attributeService.applyModifiers(entity, data.level());
            nametagService.updateNametag(entity, data.level());
            return;
        }

        // 2. Only level hostile monsters in the MVP vertical slice
        if (entity instanceof Monster && !entity.isBaby()) {
            int level = random.nextInt(10) + 1; // Random 1–10
            LevelledMobData newData = LevelledMobData.of(level, "default");
            holder.lampas$setLevelData(newData);

            attributeService.applyModifiers(entity, level);
            nametagService.updateNametag(entity, level);

            LOGGER.debug("Levelled {} (UUID: {}) to Lv. {}", entity.getType().getDescription().getString(), entity.getUUID(), level);
        }
    }

    /**
     * Manually sets an entity's level and reapplies all scaling and nametag updates.
     */
    public void setLevel(LivingEntity entity, int level, String ruleSet) {
        if (!(entity instanceof LevelledMobHolder holder)) {
            return;
        }

        LevelledMobData data = new LevelledMobData(level, level > 0, ruleSet != null ? ruleSet : "manual", System.currentTimeMillis());
        holder.lampas$setLevelData(data);

        if (level > 0) {
            attributeService.applyModifiers(entity, level);
            nametagService.updateNametag(entity, level);
        } else {
            attributeService.removeModifiers(entity);
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
        }
    }
}

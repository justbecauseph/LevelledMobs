package lampas.levelledmobs;

import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.command.LevelledMobsCommand;
import lampas.levelledmobs.level.MobLevelingService;
import lampas.levelledmobs.nametag.NametagService;
import lampas.levelledmobs.rules.RuleManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Fabric entrypoint for LevelledMobs module.
 */
public class LevelledMobsModule implements ModInitializer {
    public static final String MOD_ID = "lampas-levelledmobs";
    public static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");

    private static LevelledMobsModule instance;

    private RuleManager ruleManager;
    private AttributeScalingService attributeScalingService;
    private NametagService nametagService;
    private MobLevelingService mobLevelingService;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing LevelledMobs Fabric module...");

        this.ruleManager = new RuleManager();
        this.attributeScalingService = new AttributeScalingService();
        this.nametagService = new NametagService();
        this.mobLevelingService = new MobLevelingService(ruleManager, attributeScalingService, nametagService);

        // Register entity load event
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity livingEntity) {
                mobLevelingService.onEntityLoad(livingEntity);
            }
        });

        // Register Brigadier commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LevelledMobsCommand.register(dispatcher);
        });

        LOGGER.info("LevelledMobs Fabric module initialized successfully.");
    }

    public static LevelledMobsModule getInstance() {
        return instance;
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    public AttributeScalingService getAttributeScalingService() {
        return attributeScalingService;
    }

    public NametagService getNametagService() {
        return nametagService;
    }

    public MobLevelingService getMobLevelingService() {
        return mobLevelingService;
    }
}

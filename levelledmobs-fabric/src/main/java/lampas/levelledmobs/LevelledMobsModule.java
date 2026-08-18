package lampas.levelledmobs;

import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.command.LevelledMobsCommand;
import lampas.levelledmobs.events.ChunkLifecycleHandler;
import lampas.levelledmobs.events.EntityLifecycleHandler;
import lampas.levelledmobs.level.MobLevelingService;
import lampas.levelledmobs.level.MobProcessingQueue;
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
    private MobProcessingQueue processingQueue;
    private lampas.levelledmobs.config.ConfigLoader configLoader;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing LevelledMobs Fabric module...");

        this.ruleManager = new RuleManager();
        this.attributeScalingService = new AttributeScalingService();
        this.nametagService = new NametagService();
        this.mobLevelingService = new MobLevelingService(ruleManager, attributeScalingService, nametagService);
        this.processingQueue = new MobProcessingQueue(mobLevelingService);

        // Load configuration and rules
        this.configLoader = new lampas.levelledmobs.config.ConfigLoader();
        this.configLoader.load(ruleManager, nametagService, processingQueue);

        // Register lifecycle event handlers
        new EntityLifecycleHandler(processingQueue).register();
        new ChunkLifecycleHandler(processingQueue).register();

        // Register Brigadier commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LevelledMobsCommand.register(dispatcher, registryAccess);
        });

        LOGGER.info("LevelledMobs Fabric module initialized successfully.");
    }

    public static LevelledMobsModule getInstance() {
        return instance;
    }

    public static MobLevelingService getMobLevelingService() {
        return instance != null ? instance.mobLevelingService : null;
    }

    public static MobProcessingQueue getProcessingQueue() {
        return instance != null ? instance.processingQueue : null;
    }

    public static RuleManager getRuleManager() {
        return instance != null ? instance.ruleManager : null;
    }

    public static AttributeScalingService getAttributeScalingService() {
        return instance != null ? instance.attributeScalingService : null;
    }

    public static NametagService getNametagService() {
        return instance != null ? instance.nametagService : null;
    }

    public static lampas.levelledmobs.config.ConfigLoader getConfigLoader() {
        return instance != null ? instance.configLoader : null;
    }
}

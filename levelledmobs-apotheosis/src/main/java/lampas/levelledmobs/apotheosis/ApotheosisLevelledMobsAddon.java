package lampas.levelledmobs.apotheosis;

import dev.shadowsoffire.apotheosis.tiers.TierContextBridge;
import lampas.levelledmobs.rules.strategy.StrategyRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entrypoint for the optional-but-required-at-runtime Apotheosis integration module.
 */
public final class ApotheosisLevelledMobsAddon implements ModInitializer {
    public static final String MOD_ID = "levelledmobs-apotheosis";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        StrategyRegistry.INSTANCE.register(ApotheosisWorldTierStrategy.INSTANCE);
        StrategyRegistry.INSTANCE.registerOverride(
            (context, rule) -> rule != null && context != null && context.entity() instanceof Mob mob &&
                TierContextBridge.installed() && TierContextBridge.managed(mob),
            ApotheosisWorldTierStrategy.INSTANCE
        );
        LOGGER.info("Registered LevelledMobs strategy '{}' and managed-context override.", ApotheosisWorldTierStrategy.NAME);
    }
}

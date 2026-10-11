package lampas.levelledmobs.apotheosis;

import dev.shadowsoffire.apotheosis.tiers.TierContextBridge;
import lampas.levelledmobs.api.LevelledMobsApi;
import lampas.levelledmobs.rules.strategy.StrategyRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
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
        // Apotheosis announces a rewritten creation context for a loaded mob. Queue a bounded LevelledMobs
        // re-tier; LevelledMobs re-checks the band and leaves the mob alone when its level is still valid.
        // Only adult Monsters are levelled by LevelledMobs' lifecycle, so the same filter applies here.
        TierContextBridge.addContextListener(mob -> {
            if (mob instanceof Monster && !mob.isBaby()) {
                LevelledMobsApi.relevelManaged(mob);
            }
        });
        LOGGER.info("Registered LevelledMobs strategy '{}', managed-context override and re-tier listener.",
            ApotheosisWorldTierStrategy.NAME);
    }
}

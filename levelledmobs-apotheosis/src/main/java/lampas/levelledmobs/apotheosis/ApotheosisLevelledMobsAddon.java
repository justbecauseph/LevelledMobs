package lampas.levelledmobs.apotheosis;

import net.fabricmc.api.ModInitializer;
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
        LOGGER.info("Initializing LevelledMobs Apotheosis integration...");
    }
}

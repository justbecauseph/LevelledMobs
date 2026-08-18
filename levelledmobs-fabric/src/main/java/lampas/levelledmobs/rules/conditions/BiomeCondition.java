package lampas.levelledmobs.rules.conditions;

import lampas.levelledmobs.context.MobContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.Set;

/**
 * Evaluates biome registry key and biome tag conditions.
 */
public class BiomeCondition implements RuleCondition {
    private final Set<ResourceKey<Biome>> includeBiomes;
    private final Set<ResourceKey<Biome>> excludeBiomes;
    private final Set<TagKey<Biome>> includeTags;
    private final Set<TagKey<Biome>> excludeTags;

    public BiomeCondition(
        Set<ResourceKey<Biome>> includeBiomes,
        Set<ResourceKey<Biome>> excludeBiomes,
        Set<TagKey<Biome>> includeTags,
        Set<TagKey<Biome>> excludeTags
    ) {
        this.includeBiomes = includeBiomes != null ? includeBiomes : Collections.emptySet();
        this.excludeBiomes = excludeBiomes != null ? excludeBiomes : Collections.emptySet();
        this.includeTags = includeTags != null ? includeTags : Collections.emptySet();
        this.excludeTags = excludeTags != null ? excludeTags : Collections.emptySet();
    }

    @Override
    public boolean matches(MobContext context) {
        Holder<Biome> biomeHolder = context.biome();
        if (biomeHolder == null) {
            return includeBiomes.isEmpty() && includeTags.isEmpty();
        }

        // Check exclusions
        for (ResourceKey<Biome> key : excludeBiomes) {
            if (biomeHolder.is(key)) {
                return false;
            }
        }
        for (TagKey<Biome> tag : excludeTags) {
            if (biomeHolder.is(tag)) {
                return false;
            }
        }

        // If no inclusions specified, matches everything not excluded
        if (includeBiomes.isEmpty() && includeTags.isEmpty()) {
            return true;
        }

        // Check inclusions
        for (ResourceKey<Biome> key : includeBiomes) {
            if (biomeHolder.is(key)) {
                return true;
            }
        }
        for (TagKey<Biome> tag : includeTags) {
            if (biomeHolder.is(tag)) {
                return true;
            }
        }

        return false;
    }

    public Set<ResourceKey<Biome>> includeBiomes() {
        return includeBiomes;
    }

    public Set<ResourceKey<Biome>> excludeBiomes() {
        return excludeBiomes;
    }

    public Set<TagKey<Biome>> includeTags() {
        return includeTags;
    }

    public Set<TagKey<Biome>> excludeTags() {
        return excludeTags;
    }
}

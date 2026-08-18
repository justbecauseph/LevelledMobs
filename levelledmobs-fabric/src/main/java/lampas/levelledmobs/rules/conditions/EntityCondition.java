package lampas.levelledmobs.rules.conditions;

import lampas.levelledmobs.context.MobContext;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.Collections;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Evaluates entity type, registry identifier, and tag conditions.
 */
public class EntityCondition implements RuleCondition {
    private final Set<Identifier> includeEntities;
    private final Set<Identifier> excludeEntities;
    private final Set<TagKey<EntityType<?>>> includeTags;
    private final Set<TagKey<EntityType<?>>> excludeTags;

    public EntityCondition(
        Set<Identifier> includeEntities,
        Set<Identifier> excludeEntities,
        Set<TagKey<EntityType<?>>> includeTags,
        Set<TagKey<EntityType<?>>> excludeTags
    ) {
        this.includeEntities = includeEntities != null ? includeEntities : Collections.emptySet();
        this.excludeEntities = excludeEntities != null ? excludeEntities : Collections.emptySet();
        this.includeTags = includeTags != null ? includeTags : Collections.emptySet();
        this.excludeTags = excludeTags != null ? excludeTags : Collections.emptySet();
    }

    @Override
    public boolean matches(MobContext context) {
        Identifier entityId = context.entityId();
        EntityType<?> type = context.entityType();

        // Check exclusions first
        if (entityId != null && excludeEntities.contains(entityId)) {
            return false;
        }
        if (type != null) {
            for (TagKey<EntityType<?>> tag : excludeTags) {
                if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(tag)) {
                    return false;
                }
            }
        }

        // If no inclusions specified, matches everything not excluded
        if (includeEntities.isEmpty() && includeTags.isEmpty()) {
            return true;
        }

        // Check inclusions
        if (entityId != null && includeEntities.contains(entityId)) {
            return true;
        }
        if (type != null) {
            for (TagKey<EntityType<?>> tag : includeTags) {
                if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    public Set<Identifier> includeEntities() {
        return includeEntities;
    }

    public Set<Identifier> excludeEntities() {
        return excludeEntities;
    }

    public Set<TagKey<EntityType<?>>> includeTags() {
        return includeTags;
    }

    public Set<TagKey<EntityType<?>>> excludeTags() {
        return excludeTags;
    }
}

package lampas.levelledmobs.attributes;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Definition metadata for an entity attribute supported by LevelledMobs scaling.
 */
public record AttributeDefinition(
    String key,
    Holder<Attribute> attribute,
    Identifier modifierId,
    AttributeModifier.Operation defaultOperation
) {
    public static final AttributeDefinition MAX_HEALTH = new AttributeDefinition(
        "max_health",
        Attributes.MAX_HEALTH,
        Identifier.fromNamespaceAndPath("lampas", "levelled/max_health"),
        AttributeModifier.Operation.ADD_VALUE
    );

    public static final AttributeDefinition ATTACK_DAMAGE = new AttributeDefinition(
        "attack_damage",
        Attributes.ATTACK_DAMAGE,
        Identifier.fromNamespaceAndPath("lampas", "levelled/attack_damage"),
        AttributeModifier.Operation.ADD_VALUE
    );

    public static final AttributeDefinition MOVEMENT_SPEED = new AttributeDefinition(
        "movement_speed",
        Attributes.MOVEMENT_SPEED,
        Identifier.fromNamespaceAndPath("lampas", "levelled/movement_speed"),
        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
    );

    public static final AttributeDefinition ARMOR = new AttributeDefinition(
        "armor",
        Attributes.ARMOR,
        Identifier.fromNamespaceAndPath("lampas", "levelled/armor"),
        AttributeModifier.Operation.ADD_VALUE
    );

    public static final AttributeDefinition ARMOR_TOUGHNESS = new AttributeDefinition(
        "armor_toughness",
        Attributes.ARMOR_TOUGHNESS,
        Identifier.fromNamespaceAndPath("lampas", "levelled/armor_toughness"),
        AttributeModifier.Operation.ADD_VALUE
    );

    public static final AttributeDefinition KNOCKBACK_RESISTANCE = new AttributeDefinition(
        "knockback_resistance",
        Attributes.KNOCKBACK_RESISTANCE,
        Identifier.fromNamespaceAndPath("lampas", "levelled/knockback_resistance"),
        AttributeModifier.Operation.ADD_VALUE
    );

    public static final AttributeDefinition ATTACK_KNOCKBACK = new AttributeDefinition(
        "attack_knockback",
        Attributes.ATTACK_KNOCKBACK,
        Identifier.fromNamespaceAndPath("lampas", "levelled/attack_knockback"),
        AttributeModifier.Operation.ADD_VALUE
    );

    public static final AttributeDefinition FOLLOW_RANGE = new AttributeDefinition(
        "follow_range",
        Attributes.FOLLOW_RANGE,
        Identifier.fromNamespaceAndPath("lampas", "levelled/follow_range"),
        AttributeModifier.Operation.ADD_VALUE
    );

    public static final List<AttributeDefinition> ALL = List.of(
        MAX_HEALTH,
        ATTACK_DAMAGE,
        MOVEMENT_SPEED,
        ARMOR,
        ARMOR_TOUGHNESS,
        KNOCKBACK_RESISTANCE,
        ATTACK_KNOCKBACK,
        FOLLOW_RANGE
    );
}

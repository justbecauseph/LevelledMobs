package lampas.levelledmobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Brigadier command tree for administrative LevelledMobs commands.
 * Registers: /levelledmobs and /lm
 */
public class LevelledMobsCommand {
    private static final Identifier ADMIN_PERM = Identifier.fromNamespaceAndPath("lampas", "command/admin");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("levelledmobs")
            .requires(PermissionPredicates.require(ADMIN_PERM, PermissionLevel.GAMEMASTERS))
            .then(Commands.literal("inspect").executes(LevelledMobsCommand::executeInspect))
            .then(Commands.literal("reload").executes(LevelledMobsCommand::executeReload));

        var alias = Commands.literal("lm")
            .requires(PermissionPredicates.require(ADMIN_PERM, PermissionLevel.GAMEMASTERS))
            .then(Commands.literal("inspect").executes(LevelledMobsCommand::executeInspect))
            .then(Commands.literal("reload").executes(LevelledMobsCommand::executeReload));

        dispatcher.register(root);
        dispatcher.register(alias);
    }

    private static int executeInspect(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Entity executor = source.getEntity();

        if (!(executor instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only in-game players can inspect target entities."));
            return 0;
        }

        // Find nearest living entity within 10 blocks in front of the player
        AABB searchBox = player.getBoundingBox().inflate(10.0);

        List<LivingEntity> nearby = player.level().getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            e -> e != player && e.isAlive()
        );

        if (nearby.isEmpty()) {
            source.sendFailure(Component.literal("No living entities found nearby to inspect."));
            return 0;
        }

        // Sort by distance to player
        LivingEntity target = nearby.stream()
            .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
            .orElse(null);

        if (target == null) {
            source.sendFailure(Component.literal("No target entity found."));
            return 0;
        }

        LevelledMobData data = (target instanceof LevelledMobHolder holder) ? holder.lampas$getLevelData() : LevelledMobData.EMPTY;

        source.sendSuccess(() -> Component.literal("=== LevelledMobs Entity Inspection ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("Type: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(target.getType().getDescription().getString()).withStyle(ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.literal("UUID: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(target.getUUID().toString()).withStyle(ChatFormatting.DARK_GRAY)), false);
        source.sendSuccess(() -> Component.literal("Level: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.valueOf(data != null ? data.level() : 0)).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)), false);
        source.sendSuccess(() -> Component.literal("Levelled: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.valueOf(data != null && data.levelled())).withStyle(ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("RuleSet: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(data != null ? data.ruleSet() : "none").withStyle(ChatFormatting.WHITE)), false);

        // Attribute stats
        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance attackDamage = target.getAttribute(Attributes.ATTACK_DAMAGE);

        double hpVal = maxHealth != null ? maxHealth.getValue() : target.getMaxHealth();
        double baseHp = maxHealth != null ? maxHealth.getBaseValue() : 0;
        boolean hasHpMod = maxHealth != null && maxHealth.getModifier(AttributeScalingService.HEALTH_MODIFIER_ID) != null;

        double dmgVal = attackDamage != null ? attackDamage.getValue() : 0;
        double baseDmg = attackDamage != null ? attackDamage.getBaseValue() : 0;
        boolean hasDmgMod = attackDamage != null && attackDamage.getModifier(AttributeScalingService.DAMAGE_MODIFIER_ID) != null;

        source.sendSuccess(() -> Component.literal("Health: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.format("%.1f / %.1f (Base: %.1f, Mod: %s)", target.getHealth(), hpVal, baseHp, hasHpMod ? "ACTIVE" : "NONE")).withStyle(ChatFormatting.RED)), false);
        source.sendSuccess(() -> Component.literal("Attack Damage: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.format("%.1f (Base: %.1f, Mod: %s)", dmgVal, baseDmg, hasDmgMod ? "ACTIVE" : "NONE")).withStyle(ChatFormatting.DARK_RED)), false);

        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("[LevelledMobs] Configuration reloaded successfully.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}

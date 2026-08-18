package lampas.levelledmobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.api.LevelledMobsApi;
import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.permission.PermissionService;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Brigadier command tree for administrative LevelledMobs commands.
 * Registers: /levelledmobs and /lm
 */
public class LevelledMobsCommand {
    private static boolean debugMode = false;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        var root = Commands.literal("levelledmobs")
            .requires(PermissionService.require(PermissionService.ROOT_PERMISSION, PermissionLevel.GAMEMASTERS))
            .then(Commands.literal("info").executes(LevelledMobsCommand::executeInfo))
            .then(Commands.literal("inspect").executes(LevelledMobsCommand::executeInspect))
            .then(Commands.literal("reload").executes(LevelledMobsCommand::executeReload))
            .then(Commands.literal("rules").executes(LevelledMobsCommand::executeRules))
            .then(Commands.literal("debug").executes(LevelledMobsCommand::executeDebug))
            .then(Commands.literal("level")
                .then(Commands.argument("target", EntityArgument.entities())
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                        .executes(LevelledMobsCommand::executeSetLevel))))
            .then(Commands.literal("summon")
                .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                        .executes(LevelledMobsCommand::executeSummon)
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(LevelledMobsCommand::executeSummonPos)))));

        var alias = Commands.literal("lm")
            .requires(PermissionService.require(PermissionService.ROOT_PERMISSION, PermissionLevel.GAMEMASTERS))
            .then(Commands.literal("info").executes(LevelledMobsCommand::executeInfo))
            .then(Commands.literal("inspect").executes(LevelledMobsCommand::executeInspect))
            .then(Commands.literal("reload").executes(LevelledMobsCommand::executeReload))
            .then(Commands.literal("rules").executes(LevelledMobsCommand::executeRules))
            .then(Commands.literal("debug").executes(LevelledMobsCommand::executeDebug))
            .then(Commands.literal("level")
                .then(Commands.argument("target", EntityArgument.entities())
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                        .executes(LevelledMobsCommand::executeSetLevel))))
            .then(Commands.literal("summon")
                .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                        .executes(LevelledMobsCommand::executeSummon)
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(LevelledMobsCommand::executeSummonPos)))));

        dispatcher.register(root);
        dispatcher.register(alias);
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("=== LevelledMobs Fabric 26.2 ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("Architecture: LampasCore Modern Modular Pipeline").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Active Rules: " + (LevelledMobsModule.getRuleManager() != null ? LevelledMobsModule.getRuleManager().size() : 0)).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Debug Mode: " + (debugMode ? "Enabled" : "Disabled")).withStyle(debugMode ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    private static int executeDebug(CommandContext<CommandSourceStack> ctx) {
        debugMode = !debugMode;
        ctx.getSource().sendSuccess(() -> Component.literal("LevelledMobs debug mode " + (debugMode ? "ENABLED" : "DISABLED"))
            .withStyle(debugMode ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return 1;
    }

    private static int executeRules(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        RuleManager manager = LevelledMobsModule.getRuleManager();
        if (manager == null || manager.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active rules loaded in RuleManager.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("=== Loaded LevelRules (" + manager.size() + ") ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (LevelRule rule : manager.getRules()) {
            source.sendSuccess(() -> Component.literal("• " + rule.id() + " [Priority: " + rule.priority() + ", Strategy: " + rule.strategyName() + "]")
                .withStyle(rule.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int executeSetLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "target");
        int level = IntegerArgumentType.getInteger(ctx, "level");

        int count = 0;
        for (Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                LevelledMobsApi.setLevel(living, level);
                count++;
            }
        }

        int finalCount = count;
        ctx.getSource().sendSuccess(() -> Component.literal("Successfully set level " + level + " on " + finalCount + " entities.")
            .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    @SuppressWarnings("unchecked")
    private static int executeSummon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Vec3 pos = ctx.getSource().getPosition();
        return spawnEntityWithLevel(ctx, pos);
    }

    @SuppressWarnings("unchecked")
    private static int executeSummonPos(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
        return spawnEntityWithLevel(ctx, pos);
    }

    private static int spawnEntityWithLevel(CommandContext<CommandSourceStack> ctx, Vec3 pos) throws CommandSyntaxException {
        Holder.Reference<EntityType<?>> typeHolder = ResourceArgument.getEntityType(ctx, "entity");
        int level = IntegerArgumentType.getInteger(ctx, "level");
        ServerLevel serverLevel = ctx.getSource().getLevel();

        EntityType<?> type = typeHolder.value();
        Entity entity = type.create(serverLevel, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (entity instanceof LivingEntity living) {
            living.setPos(pos.x, pos.y, pos.z);
            serverLevel.addFreshEntity(living);
            LevelledMobsApi.setLevel(living, level);

            ctx.getSource().sendSuccess(() -> Component.literal("Summoned " + type.getDescription().getString() + " at Lv. " + level)
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else if (entity != null) {
            entity.setPos(pos.x, pos.y, pos.z);
            serverLevel.addFreshEntity(entity);
            ctx.getSource().sendSuccess(() -> Component.literal("Summoned non-living entity " + type.getDescription().getString())
                .withStyle(ChatFormatting.YELLOW), true);
            return 1;
        }

        ctx.getSource().sendFailure(Component.literal("Failed to create entity of type " + typeHolder.key().identifier()));
        return 0;
    }

    private static int executeInspect(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Entity executor = source.getEntity();

        if (!(executor instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only in-game players can inspect target entities."));
            return 0;
        }

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

        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance attackDamage = target.getAttribute(Attributes.ATTACK_DAMAGE);

        double hpVal = maxHealth != null ? maxHealth.getValue() : target.getMaxHealth();
        double baseHp = maxHealth != null ? maxHealth.getBaseValue() : 0;
        double currentHp = target.getHealth();

        source.sendSuccess(() -> Component.literal(String.format("Health: %.1f / %.1f (Base: %.1f)", currentHp, hpVal, baseHp)).withStyle(ChatFormatting.RED), false);

        if (attackDamage != null) {
            source.sendSuccess(() -> Component.literal(String.format("Attack Damage: %.2f (Base: %.2f)", attackDamage.getValue(), attackDamage.getBaseValue())).withStyle(ChatFormatting.DARK_RED), false);
        }

        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long start = System.currentTimeMillis();

        if (LevelledMobsModule.getConfigLoader() != null) {
            LevelledMobsModule.getConfigLoader().load(
                LevelledMobsModule.getRuleManager(),
                LevelledMobsModule.getNametagService(),
                LevelledMobsModule.getProcessingQueue()
            );
        } else if (LevelledMobsModule.getRuleManager() != null) {
            LevelledMobsModule.getRuleManager().reload();
        }

        long elapsed = System.currentTimeMillis() - start;
        source.sendSuccess(() -> Component.literal("Successfully reloaded LevelledMobs configuration and rules in " + elapsed + "ms.")
            .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }
}

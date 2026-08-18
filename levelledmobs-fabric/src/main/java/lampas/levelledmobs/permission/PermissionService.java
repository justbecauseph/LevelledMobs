package lampas.levelledmobs.permission;

import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.function.Predicate;

/**
 * Manages command permissions with operator levels and Fabric Permissions API support.
 */
public class PermissionService {
    public static final String ROOT_PERMISSION = "lampas.levelledmobs";
    public static final String INFO_PERM = "lampas.levelledmobs.info";
    public static final String INSPECT_PERM = "lampas.levelledmobs.inspect";
    public static final String RELOAD_PERM = "lampas.levelledmobs.reload";
    public static final String RULES_PERM = "lampas.levelledmobs.rules";
    public static final String SUMMON_PERM = "lampas.levelledmobs.summon";
    public static final String LEVEL_PERM = "lampas.levelledmobs.level";
    public static final String DEBUG_PERM = "lampas.levelledmobs.debug";

    public static Predicate<CommandSourceStack> require(String permissionNode, PermissionLevel defaultOpLevel) {
        Identifier id = Identifier.fromNamespaceAndPath("lampas", permissionNode.replace('.', '/'));
        return PermissionPredicates.require(id, defaultOpLevel);
    }
}

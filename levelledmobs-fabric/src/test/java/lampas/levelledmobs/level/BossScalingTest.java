package lampas.levelledmobs.level;

import lampas.levelledmobs.testsupport.TestMobs;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.warden.Warden;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Expected values are worked from the rollout formulas: max(10, n) / 10 and 1 + 0.025 * (level - 1). */
public class BossScalingTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void groupFactorIsOneUpToTheDesignGroupThenGrowsPerPlayer() {
        assertEquals(1.0, BossScaling.groupFactor(0), 1e-9);
        assertEquals(1.0, BossScaling.groupFactor(1), 1e-9);
        assertEquals(1.0, BossScaling.groupFactor(9), 1e-9);
        assertEquals(1.0, BossScaling.groupFactor(10), 1e-9);
        assertEquals(1.1, BossScaling.groupFactor(11), 1e-9);
        assertEquals(1.5, BossScaling.groupFactor(15), 1e-9);
        assertEquals(2.0, BossScaling.groupFactor(20), 1e-9);
    }

    @Test
    public void damageMultiplierAddsTwoAndAHalfPercentPerLevelAboveOne() {
        assertEquals(1.0, BossScaling.damageMultiplier(0), 1e-9);
        assertEquals(1.0, BossScaling.damageMultiplier(1), 1e-9);
        assertEquals(1.025, BossScaling.damageMultiplier(2), 1e-9);
        assertEquals(2.0, BossScaling.damageMultiplier(41), 1e-9);
        // The rollout plan's "x5.98 at L200".
        assertEquals(5.975, BossScaling.damageMultiplier(200), 1e-9);
    }

    @Test
    public void enemyMobsAreLevellableExceptCubeMobs() {
        assertTrue(MobLevelingService.isLevellableType(TestMobs.monster()));
        assertTrue(MobLevelingService.isLevellableType(TestMobs.allocate(Phantom.class, Mob.createMobAttributes().build())));
        assertTrue(MobLevelingService.isLevellableType(TestMobs.allocate(Ghast.class, Mob.createMobAttributes().build())));
        assertTrue(MobLevelingService.isLevellableType(TestMobs.allocate(Hoglin.class, Mob.createMobAttributes().build())));
        assertTrue(MobLevelingService.isLevellableType(TestMobs.allocate(Shulker.class, Mob.createMobAttributes().build())));
        assertTrue(MobLevelingService.isLevellableType(TestMobs.allocate(EnderDragon.class, Mob.createMobAttributes().build())));
        assertTrue(MobLevelingService.isLevellableType(TestMobs.allocate(WitherBoss.class, Mob.createMobAttributes().build())));

        assertFalse(MobLevelingService.isLevellableType(TestMobs.allocate(Slime.class, Mob.createMobAttributes().build())));
        assertFalse(MobLevelingService.isLevellableType(TestMobs.allocate(MagmaCube.class, Mob.createMobAttributes().build())));
        assertFalse(MobLevelingService.isLevellableType(TestMobs.allocate(Pig.class, Mob.createMobAttributes().build())));
        assertFalse(MobLevelingService.isLevellableType(null));
    }

    @Test
    public void onlyDirectMeleeHitsFromBossesWithAttackDamageCountAsAttackDamageHits() {
        EnderDragon dragon = TestMobs.allocate(EnderDragon.class, EnderDragon.createAttributes().build());
        WitherBoss wither = TestMobs.allocate(WitherBoss.class, WitherBoss.createAttributes().build());
        Warden warden = TestMobs.allocate(Warden.class, Warden.createAttributes().build());
        ElderGuardian elderGuardian = TestMobs.allocate(ElderGuardian.class, ElderGuardian.createAttributes().build());

        // Melee reads attack_damage: the Warden's and Elder Guardian's hits already scale with their level.
        assertTrue(BossScaling.isAttackDamageHit(warden, source(DamageTypes.MOB_ATTACK, warden, warden)));
        assertTrue(BossScaling.isAttackDamageHit(elderGuardian, source(DamageTypes.MOB_ATTACK, elderGuardian, elderGuardian)));

        // Fixed amounts: the sonic boom, the Elder Guardian's beam and the Wither's skulls.
        assertFalse(BossScaling.isAttackDamageHit(warden, source(DamageTypes.SONIC_BOOM, warden, warden)));
        assertFalse(BossScaling.isAttackDamageHit(elderGuardian,
            source(DamageTypes.INDIRECT_MAGIC, elderGuardian, elderGuardian)));
        assertFalse(BossScaling.isAttackDamageHit(wither, source(DamageTypes.WITHER_SKULL, null, wither)));

        // The Ender Dragon's head hit is a mob_attack, but the dragon has no attack_damage attribute.
        assertNull(dragon.getAttribute(Attributes.ATTACK_DAMAGE));
        assertFalse(BossScaling.isAttackDamageHit(dragon, source(DamageTypes.MOB_ATTACK, dragon, dragon)));
    }

    private static DamageSource source(ResourceKey<DamageType> type, Entity direct, Entity causing) {
        return new DamageSource(Holder.Reference.createStandAlone(new HolderOwner<>() {}, type), direct, causing);
    }
}

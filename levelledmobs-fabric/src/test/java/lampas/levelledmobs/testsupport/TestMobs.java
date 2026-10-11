package lampas.levelledmobs.testsupport;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.nametag.NametagService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Allocates Minecraft monsters without a world so that attribute, lifecycle and queue logic can be unit tested.
 *
 * <p>Instances are created with {@code Unsafe.allocateInstance}, so no constructor runs. Only the members the
 * LevelledMobs logic reads are initialised: identity, type, attribute map, position and max health. Health is held in
 * a plain field because the synced entity data is not available without a world.</p>
 */
public final class TestMobs {
    private TestMobs() {}

    public static TestMonster monster() {
        return monster(UUID.randomUUID());
    }

    public static TestMonster monster(UUID uuid) {
        try {
            TestMonster mob = (TestMonster) unsafe().allocateInstance(TestMonster.class);
            mob.uuid = uuid != null ? uuid : UUID.randomUUID();
            mob.health = 20.0f;
            mob.alive = true;

            setField(LivingEntity.class, mob, "attributes",
                new AttributeMap(Monster.createMonsterAttributes().build()));
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
            setField(Entity.class, mob, "type", EntityTypes.ZOMBIE);
            // Vector fields are read by position queries; start at the origin like a freshly placed mob.
            for (Field field : fieldsOfType(Entity.class, Vec3.class)) {
                setField(Entity.class, mob, field.getName(), Vec3.ZERO);
            }
            for (Field field : fieldsOfType(Entity.class, BlockPos.class)) {
                setField(Entity.class, mob, field.getName(), BlockPos.ZERO);
            }
            return mob;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not allocate a test monster", ex);
        }
    }

    /** Marks the mob as removed, as a discard or chunk unload does. */
    public static void discard(Entity mob) {
        try {
            setField(Entity.class, mob, "removalReason", Entity.RemovalReason.DISCARDED);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not mark the test mob removed", ex);
        }
    }

    /** Nametag service that records requests instead of writing to an entity. */
    public static final class RecordingNametagService extends NametagService {
        public final List<String> requests = new ArrayList<>();

        @Override
        public void updateNametag(LivingEntity entity, int level) {
            requests.add(level + "/default");
        }

        @Override
        public void updateNametag(LivingEntity entity, int level, String ruleSet) {
            requests.add(level + "/" + ruleSet);
        }
    }

    /** A monster whose health is a plain field, with mutable identity, aliveness and baby state. */
    public static final class TestMonster extends Monster implements LevelledMobHolder {
        private UUID uuid;
        private float health;
        private boolean alive;
        private boolean baby;
        private LevelledMobData levelData = LevelledMobData.EMPTY;

        /** Never invoked: instances come from {@link #monster()}, which bypasses constructors. */
        @SuppressWarnings("unused")
        private TestMonster() {
            super(null, null);
        }

        @Override
        public UUID getUUID() {
            return uuid;
        }

        @Override
        public float getHealth() {
            return health;
        }

        @Override
        public void setHealth(float value) {
            this.health = value;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        public void setAlive(boolean alive) {
            this.alive = alive;
        }

        @Override
        public boolean isBaby() {
            return baby;
        }

        public void setBaby(boolean baby) {
            this.baby = baby;
        }

        @Override
        public LevelledMobData lampas$getLevelData() {
            return levelData;
        }

        @Override
        public void lampas$setLevelData(LevelledMobData data) {
            this.levelData = data != null ? data : LevelledMobData.EMPTY;
        }

        @Override
        public ItemStack getItemBySlot(EquipmentSlot slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

        @Override
        public HumanoidArm getMainArm() {
            return HumanoidArm.RIGHT;
        }
    }

    private static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static void setField(Class<?> declaring, Object target, String name, Object value)
        throws ReflectiveOperationException {
        Field field = declaring.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static List<Field> fieldsOfType(Class<?> declaring, Class<?> type) {
        List<Field> matches = new ArrayList<>();
        for (Field field : declaring.getDeclaredFields()) {
            if (field.getType() == type && !Modifier.isStatic(field.getModifiers())) {
                matches.add(field);
            }
        }
        return matches;
    }
}

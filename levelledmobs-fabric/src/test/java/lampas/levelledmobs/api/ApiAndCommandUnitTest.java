package lampas.levelledmobs.api;

import lampas.levelledmobs.api.events.MobPostLevelCallback;
import lampas.levelledmobs.api.events.MobPreLevelCallback;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.permission.PermissionService;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ApiAndCommandUnitTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static class MockHolder implements LevelledMobHolder {
        private LevelledMobData data = LevelledMobData.of(42, "end_rules");

        @Override
        public LevelledMobData lampas$getLevelData() {
            return data;
        }

        @Override
        public void lampas$setLevelData(LevelledMobData data) {
            this.data = data;
        }
    }

    @Test
    public void testLevelledMobDataRecordContract() {
        MockHolder holder = new MockHolder();
        assertTrue(holder.lampas$getLevelData().levelled());
        assertEquals(42, holder.lampas$getLevelData().level());
        assertEquals("end_rules", holder.lampas$getLevelData().ruleSet());
    }

    @Test
    public void testMobPreLevelCallbackResults() {
        MobPreLevelCallback.Result proceedResult = MobPreLevelCallback.Result.proceed();
        assertFalse(proceedResult.isCancelled());
        assertEquals(-1, proceedResult.getNewLevel());

        MobPreLevelCallback.Result modifiedResult = MobPreLevelCallback.Result.proceed(75);
        assertFalse(modifiedResult.isCancelled());
        assertEquals(75, modifiedResult.getNewLevel());

        MobPreLevelCallback.Result cancelResult = MobPreLevelCallback.Result.cancel();
        assertTrue(cancelResult.isCancelled());
    }

    @Test
    public void testMobPostLevelCallbackInvocation() {
        AtomicInteger reportedLevel = new AtomicInteger(0);

        MobPostLevelCallback.EVENT.register((entity, level, ruleSet) -> {
            reportedLevel.set(level);
        });

        MobPostLevelCallback.EVENT.invoker().onPostLevel(null, 55, "boss_rule");
        assertEquals(55, reportedLevel.get());
    }

    @Test
    public void testPermissionServiceNodes() {
        assertEquals("lampas.levelledmobs.info", PermissionService.INFO_PERM);
        assertEquals("lampas.levelledmobs.inspect", PermissionService.INSPECT_PERM);
        assertEquals("lampas.levelledmobs.reload", PermissionService.RELOAD_PERM);
        assertEquals("lampas.levelledmobs.rules", PermissionService.RULES_PERM);
        assertEquals("lampas.levelledmobs.summon", PermissionService.SUMMON_PERM);
        assertEquals("lampas.levelledmobs.level", PermissionService.LEVEL_PERM);
        assertEquals("lampas.levelledmobs.debug", PermissionService.DEBUG_PERM);
    }
}

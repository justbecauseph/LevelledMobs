package lampas.levelledmobs.api.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Event triggered immediately before an entity is levelled.
 * Allows external mods to cancel levelling or modify the assigned level.
 */
public interface MobPreLevelCallback {
    Event<MobPreLevelCallback> EVENT = EventFactory.createArrayBacked(MobPreLevelCallback.class,
        (listeners) -> (entity, currentLevel, ruleSet) -> {
            int level = currentLevel;
            for (MobPreLevelCallback listener : listeners) {
                Result result = listener.onPreLevel(entity, level, ruleSet);
                if (result.isCancelled()) {
                    return result;
                }
                if (result.getNewLevel() > 0) {
                    level = result.getNewLevel();
                }
            }
            return Result.proceed(level);
        });

    Result onPreLevel(LivingEntity entity, int level, String ruleSet);

    class Result {
        private final boolean cancelled;
        private final int newLevel;

        private Result(boolean cancelled, int newLevel) {
            this.cancelled = cancelled;
            this.newLevel = newLevel;
        }

        public static Result proceed() {
            return new Result(false, -1);
        }

        public static Result proceed(int newLevel) {
            return new Result(false, newLevel);
        }

        public static Result cancel() {
            return new Result(true, -1);
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public int getNewLevel() {
            return newLevel;
        }
    }
}

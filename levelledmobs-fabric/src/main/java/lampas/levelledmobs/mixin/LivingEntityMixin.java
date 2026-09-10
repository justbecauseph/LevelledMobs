package lampas.levelledmobs.mixin;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.LevelledMobModifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements LevelledMobHolder {
    @Unique
    private LevelledMobData lampas$levelData = LevelledMobData.EMPTY;

    @Override
    public LevelledMobData lampas$getLevelData() {
        return this.lampas$levelData;
    }

    @Override
    public void lampas$setLevelData(LevelledMobData data) {
        this.lampas$levelData = (data != null) ? data : LevelledMobData.EMPTY;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void lampas$writeLevelDataToNbt(ValueOutput output, CallbackInfo ci) {
        if (this.lampas$levelData != null && this.lampas$levelData.quarantined()) {
            output.putBoolean("lampas:quarantined", true);
        }
        if (this.lampas$levelData != null && this.lampas$levelData.levelled()) {
            output.putInt("lampas:level", this.lampas$levelData.level());
            output.putBoolean("lampas:levelled", this.lampas$levelData.levelled());
            if (this.lampas$levelData.ruleSet() != null) {
                output.putString("lampas:rule", this.lampas$levelData.ruleSet());
            }
            output.putLong("lampas:generated_at", this.lampas$levelData.generatedAt());
            if (this.lampas$levelData.hasPersistedModifiers()) {
                output.putInt("lampas:retention_version", this.lampas$levelData.retentionVersion());
                var modifiers = output.childrenList("lampas:effective_modifiers");
                for (LevelledMobModifier modifier : this.lampas$levelData.effectiveModifiers()) {
                    if (modifier == null) continue;
                    var child = modifiers.addChild();
                    child.putString("attribute", modifier.attributeKey());
                    child.putString("modifier_id", modifier.modifierId());
                    child.putDouble("amount", modifier.amount());
                    child.putString("operation", modifier.operation());
                }
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void lampas$readLevelDataFromNbt(ValueInput input, CallbackInfo ci) {
        Optional<Integer> levelOpt = input.getInt("lampas:level");
        boolean quarantined = input.getBooleanOr("lampas:quarantined", false);
        if (levelOpt.isPresent() || quarantined) {
            int level = levelOpt.orElse(0);
            boolean levelled = input.getBooleanOr("lampas:levelled", levelOpt.isPresent());
            String rule = input.getStringOr("lampas:rule", "default");
            long generatedAt = input.getLongOr("lampas:generated_at", System.currentTimeMillis());
            int retentionVersion = input.getIntOr("lampas:retention_version", 0);
            var modifierInputs = input.childrenListOrEmpty("lampas:effective_modifiers");
            java.util.List<LevelledMobModifier> modifiers = new java.util.ArrayList<>();
            int readCount = 0;
            for (var child : modifierInputs) {
                if (readCount++ >= LevelledMobData.MAX_PERSISTED_MODIFIERS) break;
                modifiers.add(new LevelledMobModifier(
                    child.getStringOr("attribute", ""),
                    child.getStringOr("modifier_id", ""),
                    child.getDoubleOr("amount", Double.NaN),
                    child.getStringOr("operation", "")
                ));
            }
            this.lampas$levelData = new LevelledMobData(level, levelled, rule, generatedAt, retentionVersion, modifiers, quarantined);
        }
    }
}

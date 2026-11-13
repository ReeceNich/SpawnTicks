package uk.co.reecenicholls.spawnticks.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;
import uk.co.reecenicholls.spawnticks.runtime.EmptyTimeAccess;
import uk.co.reecenicholls.spawnticks.runtime.SpawnTicksRuntimeState;

/**
 * Keeps the Overworld spawn chunks from hitting the vanilla 300-tick empty-world timeout
 * while SpawnTicks is enabled, active, and {@code spawnChunkRadius > 0}.
 */
@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelMixin implements EmptyTimeAccess {
    @Shadow
    private int emptyTime;

    @Shadow
    public abstract void resetEmptyTime();

    @Override
    public int spawnticks$getEmptyTime() {
        return this.emptyTime;
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void spawnticks$keepSpawnChunksAwakeWhenEmpty(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (this.emptyTime > 0
                && SpawnTicksConfig.isEnabled()
                && SpawnTicksRuntimeState.isLevelTickHookHealthy()
                && level.dimension() == Level.OVERWORLD
                && level.players().isEmpty()
                && level.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS) > 0) {
            this.resetEmptyTime();
        }
    }
}

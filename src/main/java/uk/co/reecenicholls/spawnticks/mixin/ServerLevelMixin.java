package uk.co.reecenicholls.spawnticks.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Shadow
    public abstract void resetEmptyTime();

    @Inject(method = "tick", at = @At("HEAD"))
    private void keepSpawnChunksAwakeWhenEmpty(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (SpawnTicksConfig.isEnabled()
                && level.dimension() == Level.OVERWORLD
                && level.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS) > 0) {
            this.resetEmptyTime();
        }
    }
}

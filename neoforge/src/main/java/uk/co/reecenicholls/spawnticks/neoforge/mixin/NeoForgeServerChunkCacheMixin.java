//? if <1.21.2 {
package uk.co.reecenicholls.spawnticks.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;
import uk.co.reecenicholls.spawnticks.mixin.ChunkMapAccessor;
import uk.co.reecenicholls.spawnticks.runtime.SpawnTicksRuntimeState;
import uk.co.reecenicholls.spawnticks.runtime.UnattendedChunkTicker;

// Intercepts unattended chunk evaluations inside ServerChunkCache#tickChunks() on MC 1.21.0-1.21.1
// so that entity-ticking chunks without nearby players receive random block/fluid ticks without
// running inhabitedTime, NaturalSpawner, or weather/lightning checks.
@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class NeoForgeServerChunkCacheMixin {
    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Unique
    private long spawnticks$lastResolvedGameTime = Long.MIN_VALUE;

    @Unique
    private int spawnticks$cachedUnattendedTickSpeed;

    @WrapOperation(
        method = "tickChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/DistanceManager;shouldForceTicks(J)Z",
            remap = false
        ),
        require = 0
    )
    private boolean spawnticks$tickUnattendedEntityTickingChunk(
        DistanceManager instance,
        long chunkPosLong,
        Operation<Boolean> original
    ) {
        boolean forced = original.call(instance, chunkPosLong);
        if (forced) {
            return true;
        }
        if (!SpawnTicksConfig.isEnabled() || !SpawnTicksRuntimeState.isChunkTickHookHealthy()) {
            return false;
        }

        int randomTickSpeed = this.spawnticks$getCachedUnattendedTickSpeed();
        if (randomTickSpeed <= 0) {
            return false;
        }

        if (this.level.shouldTickBlocksAt(chunkPosLong)) {
            ChunkHolder holder = ((ChunkMapAccessor) this.chunkMap).spawnticks$getVisibleChunkIfPresent(chunkPosLong);
            LevelChunk chunk = holder != null ? holder.getTickingChunk() : null;
            if (chunk != null && this.level.isNaturalSpawningAllowed(chunk.getPos())) {
                UnattendedChunkTicker.randomTickBlocksOnly(this.level, chunk, randomTickSpeed);
            }
        }

        // Return false so vanilla skips incrementInhabitedTime, NaturalSpawner, and weather/lightning.
        return false;
    }

    @Unique
    private int spawnticks$getCachedUnattendedTickSpeed() {
        long gameTime = this.level.getGameTime();
        if (gameTime != this.spawnticks$lastResolvedGameTime) {
            this.spawnticks$lastResolvedGameTime = gameTime;
            this.spawnticks$cachedUnattendedTickSpeed = UnattendedChunkTicker.resolveUnattendedTickSpeed(this.level);
        }
        return this.spawnticks$cachedUnattendedTickSpeed;
    }
}
//?}

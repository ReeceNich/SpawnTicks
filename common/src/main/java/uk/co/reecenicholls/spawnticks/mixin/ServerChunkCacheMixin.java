//? if >=1.21.2 {
/*package uk.co.reecenicholls.spawnticks.mixin;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;
import uk.co.reecenicholls.spawnticks.runtime.SpawnTicksRuntimeState;
import uk.co.reecenicholls.spawnticks.runtime.UnattendedChunkTicker;

// Runs after vanilla tickChunks(ProfilerFiller, long, List) on MC 1.21.2+ to random-tick
// unattended entity-ticking chunks without touching inhabitedTime, NaturalSpawner, or weather.
@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Unique
    private final LongOpenHashSet spawnticks$alreadyTicked = new LongOpenHashSet();

    @Inject(
        method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;JLjava/util/List;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void spawnticks$tickUnattendedEntityTickingChunks(
        ProfilerFiller profiler,
        long timeInhabited,
        List<LevelChunk> vanillaTickedChunks,
        CallbackInfo ci
    ) {
        if (!SpawnTicksConfig.isEnabled() || !SpawnTicksRuntimeState.isChunkTickHookHealthy()) {
            return;
        }

        int randomTickSpeed = UnattendedChunkTicker.resolveUnattendedTickSpeed(this.level);
        if (randomTickSpeed <= 0) {
            return;
        }

        LongSet tickingChunks = ((DistanceManagerAccessor) this.distanceManager).spawnticks$getTickingChunks();
        if (tickingChunks.isEmpty()) {
            return;
        }

        boolean hasVanillaTicked = !vanillaTickedChunks.isEmpty();
        if (hasVanillaTicked) {
            for (int i = 0, size = vanillaTickedChunks.size(); i < size; i++) {
                this.spawnticks$alreadyTicked.add(vanillaTickedChunks.get(i).getPos().toLong());
            }
        }

        try {
            LongIterator it = tickingChunks.iterator();
            while (it.hasNext()) {
                long chunkPosLong = it.nextLong();
                if (hasVanillaTicked && this.spawnticks$alreadyTicked.contains(chunkPosLong)) {
                    continue;
                }
                if (!this.level.shouldTickBlocksAt(chunkPosLong)) {
                    continue;
                }
                ChunkHolder holder = ((ChunkMapAccessor) this.chunkMap).spawnticks$getVisibleChunkIfPresent(chunkPosLong);
                LevelChunk chunk = holder != null ? holder.getTickingChunk() : null;
                if (chunk != null && this.level.isNaturalSpawningAllowed(chunk.getPos())) {
                    UnattendedChunkTicker.randomTickBlocksOnly(this.level, chunk, randomTickSpeed);
                }
            }
        } finally {
            if (hasVanillaTicked) {
                this.spawnticks$alreadyTicked.clear();
            }
        }
    }
}
*/
//?}

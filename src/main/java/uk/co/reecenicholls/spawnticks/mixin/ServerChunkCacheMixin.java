package uk.co.reecenicholls.spawnticks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;
import uk.co.reecenicholls.spawnticks.runtime.EmptyTimeAccess;
import uk.co.reecenicholls.spawnticks.runtime.SpawnTicksRuntimeState;

/**
 * Intercepts unattended chunk evaluations inside {@link ServerChunkCache#tickChunks()} so that
 * entity-ticking chunks without nearby players receive random block/fluid ticks without running
 * inhabitedTime, NaturalSpawner, or weather/lightning checks.
 */
@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin {
    private static final int VANILLA_EMPTY_DIMENSION_PAUSE_TICKS = 300;

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
            ChunkHolder holder = this.chunkMap.getVisibleChunkIfPresent(chunkPosLong);
            LevelChunk chunk = holder != null ? holder.getTickingChunk() : null;
            if (chunk != null && this.level.isNaturalSpawningAllowed(chunk.getPos())) {
                this.spawnticks$randomTickBlocksOnly(chunk, randomTickSpeed);
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
            this.spawnticks$cachedUnattendedTickSpeed = this.spawnticks$resolveUnattendedTickSpeed();
        }
        return this.spawnticks$cachedUnattendedTickSpeed;
    }

    @Unique
    private int spawnticks$resolveUnattendedTickSpeed() {
        // Skip dimensions whose entities paused at the 300-tick empty limit so dropped items cannot pile up.
        if (this.level.players().isEmpty()
                && this.level instanceof EmptyTimeAccess access
                && access.spawnticks$getEmptyTime() >= VANILLA_EMPTY_DIMENSION_PAUSE_TICKS) {
            return 0;
        }

        return Math.max(0, this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING));
    }

    @Unique
    private void spawnticks$randomTickBlocksOnly(LevelChunk chunk, int randomTickSpeed) {
        ProfilerFiller profiler = this.level.getProfiler();
        profiler.push("tickBlocks");
        try {
            SpawnTicksConfig.UnattendedBlockFilter blockFilter = SpawnTicksConfig.getBlockFilter();
            ChunkPos chunkPos = chunk.getPos();
            int minX = chunkPos.getMinBlockX();
            int minZ = chunkPos.getMinBlockZ();
            LevelChunkSection[] sections = chunk.getSections();

            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section.isRandomlyTicking()) {
                    int sectionY = chunk.getSectionYFromSectionIndex(i);
                    int minY = SectionPos.sectionToBlockCoord(sectionY);

                    for (int j = 0; j < randomTickSpeed; j++) {
                        BlockPos pos = this.level.getBlockRandomPos(minX, minY, minZ, 15);
                        profiler.push("randomTick");
                        try {
                            BlockState state = section.getBlockState(
                                pos.getX() - minX,
                                pos.getY() - minY,
                                pos.getZ() - minZ
                            );
                            boolean blockRandomlyTicking = state.isRandomlyTicking();
                            FluidState fluidState = state.getFluidState();
                            boolean fluidRandomlyTicking = fluidState.isRandomlyTicking();
                            if ((blockRandomlyTicking || fluidRandomlyTicking) && !blockFilter.isExcluded(state)) {
                                if (blockRandomlyTicking) {
                                    state.randomTick(this.level, pos, this.level.random);
                                }
                                if (fluidRandomlyTicking) {
                                    fluidState.randomTick(this.level, pos, this.level.random);
                                }
                            }
                        } finally {
                            profiler.pop();
                        }
                    }
                }
            }
        } finally {
            profiler.pop();
        }
    }
}

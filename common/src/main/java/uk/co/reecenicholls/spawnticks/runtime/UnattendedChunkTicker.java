package uk.co.reecenicholls.spawnticks.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.VisibleForTesting;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;

/**
 * Shared unattended random block/fluid ticking helper used across Minecraft eras and loaders.
 */
public final class UnattendedChunkTicker {
    public static final int VANILLA_EMPTY_DIMENSION_PAUSE_TICKS = 300;

    private UnattendedChunkTicker() {
    }

    public static int resolveUnattendedTickSpeed(ServerLevel level) {
        // Skip dimensions whose entities paused at the 300-tick empty limit so dropped items cannot pile up.
        if (level.players().isEmpty()
                && level instanceof EmptyTimeAccess access
                && access.spawnticks$getEmptyTime() >= VANILLA_EMPTY_DIMENSION_PAUSE_TICKS) {
            return 0;
        }

        return Math.max(0, level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING));
    }

    public static void randomTickBlocksOnly(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
        //? if >=1.21.2 {
        /*ProfilerFiller profiler = net.minecraft.util.profiling.Profiler.get();*/
        //?} else {
        ProfilerFiller profiler = level.getProfiler();
        //?}
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
                        BlockPos pos = level.getBlockRandomPos(minX, minY, minZ, 15);
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
                                    state.randomTick(level, pos, level.random);
                                }
                                if (fluidRandomlyTicking) {
                                    fluidState.randomTick(level, pos, level.random);
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

    @VisibleForTesting
    public static int getMinBlockY(ServerLevel level) {
        //? if >=1.21.2 {
        /*return level.getMinY();*/
        //?} else {
        return level.getMinBuildHeight();
        //?}
    }

    @VisibleForTesting
    public static int getMaxBlockYExclusive(ServerLevel level) {
        //? if >=1.21.2 {
        /*return level.getMaxY() + 1;*/
        //?} else {
        return level.getMaxBuildHeight();
        //?}
    }
}

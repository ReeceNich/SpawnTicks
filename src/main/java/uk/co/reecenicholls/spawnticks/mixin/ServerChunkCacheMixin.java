package uk.co.reecenicholls.spawnticks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
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

/**
 * Intercepts unattended chunk evaluations inside {@link ServerChunkCache#tickChunks()} so that
 * entity-ticking chunks without nearby players receive random block/fluid ticks without running
 * inhabitedTime, NaturalSpawner, or weather/lightning checks.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    @Shadow
    @Final
    public ServerLevel level;

    @WrapOperation(
        method = "tickChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/DistanceManager;shouldForceTicks(J)Z",
            remap = false
        )
    )
    private boolean spawnticks$tickUnattendedEntityTickingChunk(
        DistanceManager instance,
        long chunkPosLong,
        Operation<Boolean> original,
        @Local LevelChunk chunk
    ) {
        boolean forced = original.call(instance, chunkPosLong);
        if (forced) {
            return true;
        }

        if (SpawnTicksConfig.isEnabled()
                && this.level.isNaturalSpawningAllowed(chunk.getPos())
                && this.level.shouldTickBlocksAt(chunkPosLong)) {
            int randomTickSpeed = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            if (randomTickSpeed > 0) {
                spawnticks$randomTickBlocksOnly(chunk, randomTickSpeed);
            }
        }

        // Return false so vanilla skips incrementInhabitedTime, NaturalSpawner, and weather/lightning.
        return false;
    }

    @Unique
    private void spawnticks$randomTickBlocksOnly(LevelChunk chunk, int randomTickSpeed) {
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
                    BlockState state = section.getBlockState(
                        pos.getX() - minX,
                        pos.getY() - minY,
                        pos.getZ() - minZ
                    );
                    if (state.isRandomlyTicking()) {
                        state.randomTick(this.level, pos, this.level.random);
                    }

                    FluidState fluidState = state.getFluidState();
                    if (fluidState.isRandomlyTicking()) {
                        fluidState.randomTick(this.level, pos, this.level.random);
                    }
                }
            }
        }
    }
}

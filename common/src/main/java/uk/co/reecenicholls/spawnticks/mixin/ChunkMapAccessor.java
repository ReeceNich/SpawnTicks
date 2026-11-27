package uk.co.reecenicholls.spawnticks.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Invoker("getVisibleChunkIfPresent")
    @Nullable
    ChunkHolder spawnticks$getVisibleChunkIfPresent(long chunkPos);
}

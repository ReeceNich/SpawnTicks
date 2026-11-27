package uk.co.reecenicholls.spawnticks.platform;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Loader abstraction implemented by platform modules (e.g. NeoForge, future Fabric).
 */
public interface SpawnTicksPlatform {
    SpawnTicksPlatform INSTANCE = ServiceLoader.load(SpawnTicksPlatform.class)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No SpawnTicksPlatform implementation found"));

    String getModVersion();

    void saveEnabled(boolean enabled);

    void saveBlockFilter(
        boolean allowPortalTicks,
        List<String> excludedBlocks,
        boolean persistPortals,
        boolean persistExcluded
    );

    int countModForcedChunks(ServerLevel level);

    /** Human-readable suffix for /spawnticks status forced-chunk count, or {@code null} to omit. */
    @Nullable
    String getModForcedChunksLabel();

    /**
     * Resolves {@code conflictingMixins} into human-readable mod labels in {@code outMods}.
     * Returns {@code true} if the platform mod list was available for lookup.
     */
    boolean resolveConflictingMods(Set<String> conflictingMixins, Set<String> outMods);
}

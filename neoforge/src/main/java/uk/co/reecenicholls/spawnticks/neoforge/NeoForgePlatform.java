package uk.co.reecenicholls.spawnticks.neoforge;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import uk.co.reecenicholls.spawnticks.SpawnTicksCommon;
import uk.co.reecenicholls.spawnticks.platform.SpawnTicksPlatform;

public final class NeoForgePlatform implements SpawnTicksPlatform {
    private static final Set<String> IGNORED_MOD_IDS = Set.of("minecraft", "neoforge", SpawnTicksCommon.MOD_ID);

    @Override
    public String getModVersion() {
        return SpawnTicksNeoForgeMod.getModVersion();
    }

    @Override
    public void saveEnabled(boolean enabled) {
        NeoForgeConfig.saveEnabled(enabled);
    }

    @Override
    public void saveBlockFilter(
        boolean allowPortalTicks,
        List<String> excludedBlocks,
        boolean persistPortals,
        boolean persistExcluded
    ) {
        NeoForgeConfig.saveBlockFilter(allowPortalTicks, excludedBlocks, persistPortals, persistExcluded);
    }

    @Override
    public int countModForcedChunks(ServerLevel level) {
        ForcedChunksSavedData savedData = level.getDataStorage().get(
            ForcedChunksSavedData.factory(),
            ForcedChunksSavedData.FILE_ID
        );
        if (savedData == null) {
            return 0;
        }
        LongOpenHashSet uniqueChunks = new LongOpenHashSet();
        collectTrackerChunks(savedData.getBlockForcedChunks(), uniqueChunks);
        collectTrackerChunks(savedData.getEntityForcedChunks(), uniqueChunks);
        return uniqueChunks.size();
    }

    @Override
    public String getModForcedChunksLabel() {
        return "NeoForge mod-forced";
    }

    @Override
    public boolean resolveConflictingMods(Set<String> conflictingMixins, Set<String> outMods) {
        List<? extends IModFileInfo> modFiles = collectLoadedModFiles();
        if (modFiles.isEmpty()) {
            return false;
        }
        for (String mixinClassName : conflictingMixins) {
            String resourcePath = mixinClassName.replace('.', '/') + ".class";
            for (IModFileInfo modFileInfo : modFiles) {
                IModFile modFile = modFileInfo.getFile();
                if (modFile == null) {
                    continue;
                }
                try {
                    Path classResource = modFile.findResource(resourcePath);
                    if (classResource != null && Files.exists(classResource)) {
                        addModLabels(modFileInfo, modFile, outMods);
                        break;
                    }
                } catch (RuntimeException ignored) {
                    // Custom or virtual mod file locators can reject unusual resource queries.
                }
            }
        }
        return true;
    }

    private static void collectTrackerChunks(ForcedChunkManager.TicketTracker<?> tracker, LongSet outChunks) {
        for (LongSet chunkSet : tracker.getChunks().values()) {
            outChunks.addAll(chunkSet);
        }
        for (LongSet chunkSet : tracker.getTickingChunks().values()) {
            outChunks.addAll(chunkSet);
        }
    }

    private static List<? extends IModFileInfo> collectLoadedModFiles() {
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.getModFiles();
        }
        LoadingModList loadingModList = LoadingModList.get();
        if (loadingModList != null) {
            return loadingModList.getModFiles();
        }
        return List.of();
    }

    private static void addModLabels(IModFileInfo modFileInfo, IModFile modFile, Set<String> outMods) {
        boolean addedModInfo = false;
        for (IModInfo modInfo : modFileInfo.getMods()) {
            String modId = modInfo.getModId();
            if (IGNORED_MOD_IDS.contains(modId)) {
                continue;
            }
            String displayName = modInfo.getDisplayName();
            String label = (displayName != null && !displayName.isBlank() && !displayName.equalsIgnoreCase(modId))
                ? displayName + " (" + modId + ")"
                : modId;
            outMods.add(label);
            addedModInfo = true;
        }
        if (!addedModInfo && modFile.getFileName() != null) {
            outMods.add(modFile.getFileName());
        }
    }
}

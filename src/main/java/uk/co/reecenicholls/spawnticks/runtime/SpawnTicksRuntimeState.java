package uk.co.reecenicholls.spawnticks.runtime;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Tracks runtime Mixin hook health and identifies conflicting mods when another
 * mod replaces or alters vanilla chunk/level ticking methods.
 */
public final class SpawnTicksRuntimeState {
    public static final String OWN_MIXIN_PREFIX = "uk.co.reecenicholls.spawnticks.";
    public static final String SERVER_CHUNK_CACHE_MIXIN = OWN_MIXIN_PREFIX + "mixin.ServerChunkCacheMixin";
    public static final String SERVER_LEVEL_MIXIN = OWN_MIXIN_PREFIX + "mixin.ServerLevelMixin";
    public static final String CHUNK_TICK_HANDLER_FRAGMENT = "tickUnattendedEntityTickingChunk";
    public static final String LEVEL_TICK_HANDLER_FRAGMENT = "keepSpawnChunksAwakeWhenEmpty";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EXPECTED_WRAP_BRIDGE_NAME = "mixinextras$bridge$shouldForceTicks";
    private static final String EXPECTED_WRAP_BRIDGE_PREFIX = EXPECTED_WRAP_BRIDGE_NAME + "$";
    private static final Set<String> IGNORED_MOD_IDS = Set.of("minecraft", "neoforge", "spawnticks");
    private static final Object LOCK = new Object();

    private static volatile boolean chunkTickVerified = false;
    private static volatile boolean levelTickVerified = false;
    private static volatile boolean chunkTickHookHealthy = true;
    private static volatile boolean levelTickHookHealthy = true;
    private static volatile String failureSummary = null;
    private static final Set<String> conflictingMixins = new LinkedHashSet<>();
    private static final Set<String> conflictingMods = new LinkedHashSet<>();
    private static boolean modResolutionCompleted = false;

    private SpawnTicksRuntimeState() {
    }

    /**
     * Records the post-apply bytecode verification result for a SpawnTicks Mixin hook.
     */
    public static void recordHookCheck(
        boolean isChunkHook,
        boolean healthy,
        Collection<String> foreignMixins,
        String reason
    ) {
        synchronized (LOCK) {
            if (isChunkHook) {
                chunkTickVerified = true;
            } else {
                levelTickVerified = true;
            }
            if (!healthy) {
                setHookUnhealthyLocked(isChunkHook);
                recordFailureLocked(foreignMixins, reason);
            }
        }
    }

    /**
     * Returns {@code true} when {@code mixinClassName} is non-null and belongs to another mod.
     */
    public static boolean isForeignMixin(String mixinClassName) {
        return mixinClassName != null && !mixinClassName.startsWith(OWN_MIXIN_PREFIX);
    }

    /**
     * Returns {@code true} when all required SpawnTicks Mixin hooks applied cleanly.
     */
    public static boolean isFullyActive() {
        return chunkTickHookHealthy && levelTickHookHealthy;
    }

    /**
     * Returns {@code true} when at least one SpawnTicks Mixin hook applied cleanly.
     */
    public static boolean isAnyHookHealthy() {
        return chunkTickHookHealthy || levelTickHookHealthy;
    }

    public static boolean isChunkTickHookHealthy() {
        return chunkTickHookHealthy;
    }

    public static boolean isLevelTickHookHealthy() {
        return levelTickHookHealthy;
    }

    public static List<String> getConflictingMixins() {
        synchronized (LOCK) {
            return List.copyOf(conflictingMixins);
        }
    }

    public static List<String> getConflictingMods() {
        synchronized (LOCK) {
            resolveConflictingModsLocked();
            return List.copyOf(conflictingMods);
        }
    }

    public static String getFailureSummary() {
        return failureSummary;
    }

    /**
     * Captures the current runtime Mixin state so automated tests can simulate
     * conflict states and restore the live runtime snapshot cleanly.
     */
    @VisibleForTesting
    public static TestStateSnapshot captureStateForTest() {
        synchronized (LOCK) {
            return new TestStateSnapshot(
                chunkTickVerified,
                levelTickVerified,
                chunkTickHookHealthy,
                levelTickHookHealthy,
                failureSummary,
                List.copyOf(conflictingMixins),
                List.copyOf(conflictingMods),
                modResolutionCompleted
            );
        }
    }

    /**
     * Applies a simulated conflict or health state for automated tests.
     */
    @VisibleForTesting
    public static void setStateForTest(
        boolean chunkHealthy,
        boolean levelHealthy,
        Collection<String> mockForeignMixins,
        String reason
    ) {
        synchronized (LOCK) {
            chunkTickVerified = true;
            levelTickVerified = true;
            chunkTickHookHealthy = chunkHealthy;
            levelTickHookHealthy = levelHealthy;
            failureSummary = null;
            conflictingMixins.clear();
            conflictingMods.clear();
            modResolutionCompleted = false;
            if (!chunkHealthy || !levelHealthy) {
                recordFailureLocked(mockForeignMixins, reason);
            }
        }
    }

    /**
     * Restores a snapshot previously captured via {@link #captureStateForTest()}.
     */
    @VisibleForTesting
    public static void restoreStateForTest(TestStateSnapshot snapshot) {
        synchronized (LOCK) {
            chunkTickVerified = snapshot.chunkTickVerified();
            levelTickVerified = snapshot.levelTickVerified();
            chunkTickHookHealthy = snapshot.chunkTickHookHealthy();
            levelTickHookHealthy = snapshot.levelTickHookHealthy();
            failureSummary = snapshot.failureSummary();
            conflictingMixins.clear();
            conflictingMixins.addAll(snapshot.conflictingMixins());
            conflictingMods.clear();
            conflictingMods.addAll(snapshot.conflictingMods());
            modResolutionCompleted = snapshot.modResolutionCompleted();
        }
    }

    /**
     * Snapshot of {@link SpawnTicksRuntimeState} used to isolate conflict tests.
     */
    @VisibleForTesting
    public record TestStateSnapshot(
        boolean chunkTickVerified,
        boolean levelTickVerified,
        boolean chunkTickHookHealthy,
        boolean levelTickHookHealthy,
        String failureSummary,
        List<String> conflictingMixins,
        List<String> conflictingMods,
        boolean modResolutionCompleted
    ) {
    }

    /**
     * Verifies during server startup that post-apply checks ran, checks for late
     * high-priority conflicts via runtime {@code @MixinMerged} and bridge/handler checks,
     * and logs any detected conflict cleanly.
     */
    public static void verifyRuntimeIntegrity() {
        synchronized (LOCK) {
            if (!chunkTickVerified) {
                chunkTickHookHealthy = false;
                appendFailureLocked("ServerChunkCacheMixin postApply verification did not run.");
            }
            if (!levelTickVerified) {
                levelTickHookHealthy = false;
                appendFailureLocked("ServerLevelMixin postApply verification did not run.");
            }

            checkRuntimeHookLocked(true, ServerChunkCache.class, "tickChunks");
            checkRuntimeHookLocked(false, ServerLevel.class, "tick", BooleanSupplier.class);

            if (!isFullyActive()) {
                resolveConflictingModsLocked();
                LOGGER.error(
                    "[SpawnTicks] Mixin conflict detected! {} Reason: {} | Hook status: chunkTick={}, levelTick={} | Conflicting mixin(s): {} | Suspected mod(s): {}",
                    formatDegradationNoticeLocked(),
                    failureSummary != null ? failureSummary : "Unknown incompatibility",
                    chunkTickHookHealthy ? "OK" : "FAILED",
                    levelTickHookHealthy ? "OK" : "FAILED",
                    conflictingMixins.isEmpty() ? "none detected" : String.join(", ", conflictingMixins),
                    conflictingMods.isEmpty() ? "unknown (check conflicting mixin package)" : String.join(", ", conflictingMods)
                );
            }
        }
    }

    private static String formatDegradationNoticeLocked() {
        if (!chunkTickHookHealthy && !levelTickHookHealthy) {
            return "Unattended random ticking and Overworld keep-awake have been safely disabled to prevent a crash.";
        }
        if (!chunkTickHookHealthy) {
            return "Unattended random ticking has been safely disabled to prevent a crash.";
        }
        return "Overworld keep-awake has been safely disabled (unattended random ticking remains active where chunks are kept loaded).";
    }

    private static void checkRuntimeHookLocked(
        boolean isChunkHook,
        Class<?> targetClass,
        String methodName,
        Class<?>... parameterTypes
    ) {
        try {
            Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);
            MixinMerged merged = method.getAnnotation(MixinMerged.class);
            if (merged != null && isForeignMixin(merged.mixin())) {
                setHookUnhealthyLocked(isChunkHook);
                addConflictingMixinLocked(merged.mixin());
                appendFailureLocked(
                    targetClass.getSimpleName() + "#" + methodName + " was overwritten by " + merged.mixin()
                );
            }

            if (isChunkHook) {
                verifyChunkRuntimeArtifactsLocked(targetClass);
            } else {
                verifyLevelRuntimeArtifactsLocked(targetClass);
            }
        } catch (NoSuchMethodException | SecurityException ex) {
            setHookUnhealthyLocked(isChunkHook);
            appendFailureLocked(
                "Could not verify " + targetClass.getSimpleName() + "#" + methodName + " (" + ex.getClass().getSimpleName() + ")"
            );
        }

        boolean unhealthy = isChunkHook ? !chunkTickHookHealthy : !levelTickHookHealthy;
        if (unhealthy && conflictingMixins.isEmpty()) {
            collectForeignMixinsFromRuntimeClassLocked(targetClass);
        }
    }

    private static void verifyChunkRuntimeArtifactsLocked(Class<?> targetClass) {
        if (!hasOwnMergedHandler(targetClass, CHUNK_TICK_HANDLER_FRAGMENT, SERVER_CHUNK_CACHE_MIXIN)) {
            setHookUnhealthyLocked(true);
            appendFailureLocked("ServerChunkCacheMixin handler was not merged onto ServerChunkCache");
        }
        if (!hasExpectedWrapBridgeMethod(targetClass)) {
            setHookUnhealthyLocked(true);
            appendFailureLocked(
                "ServerChunkCache#tickChunks MixinExtras @WrapOperation bridge for shouldForceTicks was not generated"
            );
        }
    }

    private static void verifyLevelRuntimeArtifactsLocked(Class<?> targetClass) {
        if (!EmptyTimeAccess.class.isAssignableFrom(targetClass)) {
            setHookUnhealthyLocked(false);
            appendFailureLocked("ServerLevel does not implement EmptyTimeAccess");
        }
        if (!hasOwnMergedHandler(targetClass, LEVEL_TICK_HANDLER_FRAGMENT, SERVER_LEVEL_MIXIN)) {
            setHookUnhealthyLocked(false);
            appendFailureLocked("ServerLevelMixin handler was not merged onto ServerLevel");
        }
    }

    private static boolean hasOwnMergedHandler(
        Class<?> targetClass,
        String handlerFragment,
        String expectedMixinClass
    ) {
        for (Method declaredMethod : targetClass.getDeclaredMethods()) {
            if (!declaredMethod.getName().contains(handlerFragment)) {
                continue;
            }
            MixinMerged merged = declaredMethod.getAnnotation(MixinMerged.class);
            if (merged != null && expectedMixinClass.equals(merged.mixin())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExpectedWrapBridgeMethod(Class<?> targetClass) {
        for (Method declaredMethod : targetClass.getDeclaredMethods()) {
            String name = declaredMethod.getName();
            if (name.equals(EXPECTED_WRAP_BRIDGE_NAME) || name.startsWith(EXPECTED_WRAP_BRIDGE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static void collectForeignMixinsFromRuntimeClassLocked(Class<?> targetClass) {
        for (Method declaredMethod : targetClass.getDeclaredMethods()) {
            MixinMerged merged = declaredMethod.getAnnotation(MixinMerged.class);
            if (merged != null && isForeignMixin(merged.mixin())) {
                addConflictingMixinLocked(merged.mixin());
            }
        }
    }

    private static void setHookUnhealthyLocked(boolean isChunkHook) {
        if (isChunkHook) {
            chunkTickHookHealthy = false;
        } else {
            levelTickHookHealthy = false;
        }
    }

    private static void recordFailureLocked(Collection<String> foreignMixins, String reason) {
        if (foreignMixins != null) {
            for (String mixin : foreignMixins) {
                addConflictingMixinLocked(mixin);
            }
        }
        appendFailureLocked(reason);
    }

    private static void addConflictingMixinLocked(String mixin) {
        if (isForeignMixin(mixin) && conflictingMixins.add(mixin)) {
            modResolutionCompleted = false;
        }
    }

    private static void appendFailureLocked(String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        if (failureSummary == null || failureSummary.isBlank()) {
            failureSummary = reason;
        } else if (!failureSummary.contains(reason)) {
            failureSummary = failureSummary + "; " + reason;
        }
    }

    private static void resolveConflictingModsLocked() {
        if (modResolutionCompleted || conflictingMixins.isEmpty()) {
            return;
        }

        List<? extends IModFileInfo> modFiles = collectLoadedModFiles();
        if (modFiles.isEmpty()) {
            return;
        }
        modResolutionCompleted = true;

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
                        addModLabelsLocked(modFileInfo, modFile);
                        break;
                    }
                } catch (RuntimeException ignored) {
                    // Custom or virtual mod file locators can reject unusual resource queries.
                }
            }
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

    private static void addModLabelsLocked(IModFileInfo modFileInfo, IModFile modFile) {
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
            conflictingMods.add(label);
            addedModInfo = true;
        }
        if (!addedModInfo && modFile.getFileName() != null) {
            conflictingMods.add(modFile.getFileName());
        }
    }
}

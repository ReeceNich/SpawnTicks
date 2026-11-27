package uk.co.reecenicholls.spawnticks.config;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import uk.co.reecenicholls.spawnticks.platform.SpawnTicksPlatform;

/**
 * Server-scoped configuration state for SpawnTicks, cached in volatile snapshots for
 * lock-free hot-path reads during chunk ticking.
 */
public class SpawnTicksConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object CONFIG_LOCK = new Object();

    private static volatile boolean cachedEnabled = true;
    private static volatile UnattendedBlockFilter cachedBlockFilter = UnattendedBlockFilter.DEFAULT;

    public static boolean isEnabled() {
        return cachedEnabled;
    }

    public static UnattendedBlockFilter getBlockFilter() {
        return cachedBlockFilter;
    }

    public static boolean isAllowUnattendedNetherPortalTicks() {
        return cachedBlockFilter.allowNetherPortalTicks();
    }

    public static List<String> getExcludedBlocks() {
        return cachedBlockFilter.configuredEntries();
    }

    public static void setEnabled(boolean enabled) {
        synchronized (CONFIG_LOCK) {
            cachedEnabled = enabled;
            SpawnTicksPlatform.INSTANCE.saveEnabled(enabled);
        }
    }

    /** Updates whether unattended Nether Portal random ticks are allowed and persists the setting. */
    public static void setAllowUnattendedNetherPortalTicks(boolean allow) {
        synchronized (CONFIG_LOCK) {
            applyAndSaveBlockFilterLocked(allow, cachedBlockFilter.configuredEntries(), true, false);
        }
    }

    /** Replaces the configured {@code excludedBlocks} list and persists the change. */
    public static void setExcludedBlocks(List<String> entries) {
        synchronized (CONFIG_LOCK) {
            applyAndSaveBlockFilterLocked(cachedBlockFilter.allowNetherPortalTicks(), entries, false, true);
        }
    }

    /** Parses, normalizes, and appends an entry to {@code excludedBlocks}, saving if loaded. */
    public static ExclusionAddResult addExcludedBlock(String rawEntry) {
        String canonical = normalizeExclusionEntry(stripOptionalQuotesAndTrim(rawEntry));
        if (canonical == null) {
            return new ExclusionAddResult(ExclusionAddResult.Status.INVALID_SYNTAX, "", 0);
        }

        int matchedCount = countMatchingBlocks(canonical);
        synchronized (CONFIG_LOCK) {
            List<String> currentEntries = cachedBlockFilter.configuredEntries();
            for (String existing : currentEntries) {
                if (canonical.equals(normalizeExclusionEntry(existing))) {
                    return new ExclusionAddResult(ExclusionAddResult.Status.ALREADY_PRESENT, canonical, matchedCount);
                }
            }

            List<String> updated = new ArrayList<>(currentEntries.size() + 1);
            updated.addAll(currentEntries);
            updated.add(canonical);
            applyAndSaveBlockFilterLocked(cachedBlockFilter.allowNetherPortalTicks(), updated, false, true);
        }

        ExclusionAddResult.Status status = matchedCount > 0
            ? ExclusionAddResult.Status.ADDED
            : ExclusionAddResult.Status.ADDED_UNMATCHED;
        return new ExclusionAddResult(status, canonical, matchedCount);
    }

    /** Removes a matching entry from {@code excludedBlocks} and returns the removed entry, or {@code null}. */
    @Nullable
    public static String removeExcludedBlock(String rawEntry) {
        String trimmed = stripOptionalQuotesAndTrim(rawEntry);
        if (trimmed.isEmpty()) {
            return null;
        }
        String canonical = normalizeExclusionEntry(trimmed);

        synchronized (CONFIG_LOCK) {
            List<String> currentEntries = cachedBlockFilter.configuredEntries();
            int matchIndex = -1;
            for (int i = 0; i < currentEntries.size(); i++) {
                String existing = currentEntries.get(i);
                if (trimmed.equals(existing)
                        || (canonical != null && canonical.equals(normalizeExclusionEntry(existing)))) {
                    matchIndex = i;
                    break;
                }
            }
            if (matchIndex < 0) {
                return null;
            }

            List<String> updated = new ArrayList<>(currentEntries);
            String removed = updated.remove(matchIndex);
            applyAndSaveBlockFilterLocked(cachedBlockFilter.allowNetherPortalTicks(), updated, false, true);
            return removed;
        }
    }

    /** Clears all entries from {@code excludedBlocks} and returns the previous entry count. */
    public static int clearExcludedBlocks() {
        synchronized (CONFIG_LOCK) {
            int count = cachedBlockFilter.configuredEntries().size();
            if (count == 0) {
                return 0;
            }
            applyAndSaveBlockFilterLocked(cachedBlockFilter.allowNetherPortalTicks(), List.of(), false, true);
            return count;
        }
    }

    private static void applyAndSaveBlockFilterLocked(
        boolean allowPortalTicks,
        @Nullable List<String> entries,
        boolean persistPortals,
        boolean persistExcluded
    ) {
        cachedBlockFilter = compileBlockFilter(allowPortalTicks, entries, false);
        SpawnTicksPlatform.INSTANCE.saveBlockFilter(
            allowPortalTicks,
            cachedBlockFilter.configuredEntries(),
            persistPortals,
            persistExcluded
        );
    }

    /** Synchronizes cached config state when the platform config loads or reloads from disk. */
    public static void applyLoadedConfig(
        boolean enabled,
        boolean allowPortalTicks,
        @Nullable List<? extends String> rawExcludedBlocks
    ) {
        synchronized (CONFIG_LOCK) {
            cachedEnabled = enabled;
            cachedBlockFilter = compileBlockFilter(allowPortalTicks, rawExcludedBlocks, true);
        }
    }

    /** Resets cached config state to defaults when a world's server config unloads. */
    public static void resetToDefaults() {
        synchronized (CONFIG_LOCK) {
            cachedEnabled = true;
            cachedBlockFilter = UnattendedBlockFilter.DEFAULT;
        }
    }

    /** Validates syntax only so removed-mod entries warn on load instead of wiping the list. */
    public static boolean isValidExclusionEntry(@Nullable Object value) {
        return value instanceof String entry && normalizeExclusionEntry(entry) != null;
    }

    /** Normalizes a block ID, {@code #tag}, or {@code namespace:*} wildcard, or returns {@code null} if invalid. */
    @Nullable
    public static String normalizeExclusionEntry(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("#")) {
            ResourceLocation tagId = tryParseNonEmptyId(trimmed.substring(1));
            return tagId != null ? "#" + tagId : null;
        }
        if (trimmed.endsWith(":*")) {
            String namespace = trimmed.substring(0, trimmed.length() - 2);
            return (!namespace.isEmpty() && ResourceLocation.isValidNamespace(namespace)) ? namespace + ":*" : null;
        }
        ResourceLocation blockId = tryParseNonEmptyId(trimmed);
        return blockId != null ? blockId.toString() : null;
    }

    @Nullable
    private static ResourceLocation tryParseNonEmptyId(String raw) {
        if (raw.isEmpty() || raw.contains("*")) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return (id != null && !id.getNamespace().isEmpty() && !id.getPath().isEmpty()) ? id : null;
    }

    private static String stripOptionalQuotesAndTrim(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    private static UnattendedBlockFilter compileBlockFilter(
        boolean allowPortalTicks,
        @Nullable List<? extends String> rawEntries,
        boolean logWarnings
    ) {
        ReferenceOpenHashSet<Block> blocks = new ReferenceOpenHashSet<>();
        Set<TagKey<Block>> tags = new LinkedHashSet<>();
        List<String> validEntries = new ArrayList<>();

        if (rawEntries != null) {
            for (String rawEntry : rawEntries) {
                String canonical = normalizeExclusionEntry(rawEntry);
                if (canonical == null) {
                    if (logWarnings) {
                        LOGGER.warn("[SpawnTicks] Ignoring invalid excludedBlocks entry '{}'.", rawEntry);
                    }
                    continue;
                }
                validEntries.add(rawEntry.trim());
                resolveEntryIntoFilter(rawEntry.trim(), canonical, blocks, tags, logWarnings);
            }
        }

        @SuppressWarnings("unchecked")
        TagKey<Block>[] tagArray = tags.isEmpty() ? UnattendedBlockFilter.EMPTY_TAGS : tags.toArray(TagKey[]::new);
        return new UnattendedBlockFilter(allowPortalTicks, blocks, tagArray, List.copyOf(validEntries));
    }

    private static void resolveEntryIntoFilter(
        String rawEntry,
        String canonical,
        ReferenceOpenHashSet<Block> blocks,
        Set<TagKey<Block>> tags,
        boolean logWarnings
    ) {
        if (canonical.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.parse(canonical.substring(1));
            tags.add(TagKey.create(Registries.BLOCK, tagId));
            return;
        }
        if (canonical.endsWith(":*")) {
            String namespace = canonical.substring(0, canonical.length() - 2);
            int matched = collectNamespaceBlocks(namespace, blocks);
            if (matched == 0 && logWarnings) {
                LOGGER.warn("[SpawnTicks] Excluded namespace wildcard '{}' matched 0 registered blocks.", rawEntry);
            }
            return;
        }
        ResourceLocation blockId = ResourceLocation.parse(canonical);
        // BuiltInRegistries.BLOCK is a DefaultedRegistry that falls back to AIR on get(); use getOptional().
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(blockId);
        if (block.isPresent()) {
            blocks.add(block.get());
        } else if (logWarnings) {
            LOGGER.warn("[SpawnTicks] Excluded block '{}' is not registered and will be ignored.", rawEntry);
        }
    }

    private static int collectNamespaceBlocks(String namespace, @Nullable ReferenceOpenHashSet<Block> outBlocks) {
        int matched = 0;
        for (Map.Entry<ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            if (entry.getKey().location().getNamespace().equals(namespace)) {
                if (outBlocks != null) {
                    outBlocks.add(entry.getValue());
                }
                matched++;
            }
        }
        return matched;
    }

    private static int countMatchingBlocks(String canonical) {
        if (canonical.startsWith("#")) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(canonical.substring(1)));
            //? if >=1.21.2 {
            /*return BuiltInRegistries.BLOCK.get(tag).map(HolderSet::size).orElse(0);*/
            //?} else {
            return BuiltInRegistries.BLOCK.getTag(tag).map(HolderSet::size).orElse(0);
            //?}
        }
        if (canonical.endsWith(":*")) {
            return collectNamespaceBlocks(canonical.substring(0, canonical.length() - 2), null);
        }
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(canonical)).isPresent() ? 1 : 0;
    }

    /** Immutable hot-path filter snapshot for unattended block and fluid random ticking. */
    public static final class UnattendedBlockFilter {
        @SuppressWarnings("unchecked")
        private static final TagKey<Block>[] EMPTY_TAGS = new TagKey[0];

        public static final UnattendedBlockFilter DEFAULT = new UnattendedBlockFilter(
            false,
            new ReferenceOpenHashSet<>(),
            EMPTY_TAGS,
            List.of()
        );

        private final boolean allowNetherPortalTicks;
        private final ReferenceOpenHashSet<Block> excludedBlocks;
        private final TagKey<Block>[] excludedTags;
        private final List<String> configuredEntries;

        private UnattendedBlockFilter(
            boolean allowNetherPortalTicks,
            ReferenceOpenHashSet<Block> excludedBlocks,
            TagKey<Block>[] excludedTags,
            List<String> configuredEntries
        ) {
            this.allowNetherPortalTicks = allowNetherPortalTicks;
            this.excludedBlocks = excludedBlocks;
            this.excludedTags = excludedTags;
            this.configuredEntries = configuredEntries;
        }

        public boolean allowNetherPortalTicks() {
            return this.allowNetherPortalTicks;
        }

        public List<String> configuredEntries() {
            return this.configuredEntries;
        }

        /** Returns {@code true} if {@code state} should be skipped while unattended. */
        public boolean isExcluded(BlockState state) {
            if (!this.allowNetherPortalTicks && state.getBlock() instanceof NetherPortalBlock) {
                return true;
            }
            return this.isExplicitlyExcluded(state);
        }

        /** Returns {@code true} if {@code state} matches an entry in {@code excludedBlocks}. */
        public boolean isExplicitlyExcluded(BlockState state) {
            if (!this.excludedBlocks.isEmpty() && this.excludedBlocks.contains(state.getBlock())) {
                return true;
            }
            for (TagKey<Block> tag : this.excludedTags) {
                if (state.is(tag)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Result of adding a block/tag/wildcard entry to {@code excludedBlocks}. */
    public record ExclusionAddResult(Status status, String canonicalEntry, int matchedBlocks) {
        public enum Status {
            ADDED,
            ADDED_UNMATCHED,
            ALREADY_PRESENT,
            INVALID_SYNTAX
        }
    }
}

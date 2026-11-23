package uk.co.reecenicholls.spawnticks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;
import uk.co.reecenicholls.spawnticks.runtime.SpawnTicksRuntimeState;

public class SpawnTicksCommand {
    private static final int MAX_STATUS_EXCLUDED_PREVIEW = 5;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spawnticks")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enable").executes(ctx -> {
                    SpawnTicksConfig.setEnabled(true);
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("[SpawnTicks] Enabled unattended random block ticking in entity-ticking chunks.").withStyle(ChatFormatting.GREEN),
                        true
                    );
                    return 1;
                }))
                .then(Commands.literal("disable").executes(ctx -> {
                    SpawnTicksConfig.setEnabled(false);
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("[SpawnTicks] Disabled unattended random block ticking in entity-ticking chunks.").withStyle(ChatFormatting.RED),
                        true
                    );
                    return 1;
                }))
                .then(buildPortalsBranch())
                .then(buildExcludeBranch())
                .then(Commands.literal("status").executes(ctx -> {
                    boolean enabled = SpawnTicksConfig.isEnabled();
                    SpawnTicksConfig.UnattendedBlockFilter blockFilter = SpawnTicksConfig.getBlockFilter();
                    boolean chunkHealthy = SpawnTicksRuntimeState.isChunkTickHookHealthy();
                    boolean levelHealthy = SpawnTicksRuntimeState.isLevelTickHookHealthy();
                    boolean fullyHealthy = chunkHealthy && levelHealthy;
                    ServerLevel overworld = ctx.getSource().getServer().overworld();
                    ServerLevel currentLevel = ctx.getSource().getLevel();
                    int spawnRadius = ctx.getSource().getServer().getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS);
                    int randomTickSpeed = ctx.getSource().getServer().getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
                    int entityTickingDiameter = Math.max(0, 2 * spawnRadius - 1);
                    int totalSpawnChunks = entityTickingDiameter * entityTickingDiameter;

                    BlockPos spawnPos = overworld.getSharedSpawnPos();
                    ChunkPos centerChunk = new ChunkPos(spawnPos);

                    MutableComponent statusBadge = buildStatusBadge(enabled, chunkHealthy, levelHealthy);
                    MutableComponent hooksLine = fullyHealthy
                        ? Component.literal("Active").withStyle(ChatFormatting.GREEN)
                        : Component.literal(formatHookConflictDetails()).withStyle(ChatFormatting.RED);
                    MutableComponent keepAwakeLine = buildKeepAwakeLine(enabled, levelHealthy, spawnRadius);
                    MutableComponent portalStatusLine = buildPortalStatusLine(blockFilter);

                    MutableComponent statusText = Component.literal("[SpawnTicks] Status: ")
                        .append(statusBadge)
                        .append(Component.literal("\n- Config Enabled: " + enabled))
                        .append(Component.literal("\n- Mixin Hooks: ")).append(hooksLine)
                        .append(Component.literal("\n- Overworld Keep-Awake: ")).append(keepAwakeLine)
                        .append(Component.literal("\n- Unattended Nether Portals: ")).append(portalStatusLine)
                        .append(Component.literal("\n- Excluded Blocks: " + formatExcludedBlocksSummary(blockFilter.configuredEntries())))
                        .append(Component.literal("\n- World Spawn: (" + spawnPos.getX() + ", " + spawnPos.getY() + ", " + spawnPos.getZ() + ") [Chunk " + centerChunk.x + ", " + centerChunk.z + "]"))
                        .append(Component.literal("\n- gamerule spawnChunkRadius: " + spawnRadius))
                        .append(Component.literal("\n- Active Spawn Chunk Area: " + entityTickingDiameter + "x" + entityTickingDiameter + " (" + totalSpawnChunks + " entity-ticking chunks)"))
                        .append(Component.literal("\n- Overworld Forced Chunks: " + formatForcedChunks(overworld)));

                    if (currentLevel != overworld) {
                        statusText.append(Component.literal(
                            "\n- Current Dimension (" + currentLevel.dimension().location() + ") Forced Chunks: "
                                + formatForcedChunks(currentLevel)
                        ));
                    }

                    statusText.append(Component.literal("\n- gamerule randomTickSpeed: " + randomTickSpeed));

                    ctx.getSource().sendSuccess(() -> statusText, false);
                    return 1;
                }))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPortalsBranch() {
        return Commands.literal("portals")
            .then(Commands.literal("allow").executes(ctx -> {
                SpawnTicksConfig.setAllowUnattendedNetherPortalTicks(true);
                boolean alsoExcluded = SpawnTicksConfig.getBlockFilter()
                    .isExplicitlyExcluded(Blocks.NETHER_PORTAL.defaultBlockState());
                String message = alsoExcluded
                    ? "[SpawnTicks] Allowed unattended Nether portal ticks in config, but minecraft:nether_portal is still blocked by excludedBlocks."
                    : "[SpawnTicks] Allowed unattended Nether portal random ticks (Zombified Piglins may spawn when players are absent).";
                ctx.getSource().sendSuccess(
                    () -> Component.literal(message).withStyle(ChatFormatting.YELLOW),
                    true
                );
                return 1;
            }))
            .then(Commands.literal("block").executes(ctx -> {
                SpawnTicksConfig.setAllowUnattendedNetherPortalTicks(false);
                ctx.getSource().sendSuccess(
                    () -> Component.literal("[SpawnTicks] Blocked unattended Nether portal random ticks (no unattended Zombified Piglin spawns).").withStyle(ChatFormatting.GREEN),
                    true
                );
                return 1;
            }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildExcludeBranch() {
        return Commands.literal("exclude")
            .then(Commands.literal("add")
                .then(Commands.argument("entry", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> suggestExcludableEntries(builder))
                    .executes(ctx -> executeExcludeAdd(ctx.getSource(), StringArgumentType.getString(ctx, "entry")))))
            .then(Commands.literal("remove")
                .then(Commands.argument("entry", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SpawnTicksConfig.getExcludedBlocks(), builder))
                    .executes(ctx -> executeExcludeRemove(ctx.getSource(), StringArgumentType.getString(ctx, "entry")))))
            .then(Commands.literal("list").executes(ctx -> {
                List<String> entries = SpawnTicksConfig.getExcludedBlocks();
                if (entries.isEmpty()) {
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("[SpawnTicks] Excluded blocks: None configured."),
                        false
                    );
                } else {
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("[SpawnTicks] Excluded blocks (" + entries.size() + "):\n- " + String.join("\n- ", entries)),
                        false
                    );
                }
                return 1;
            }))
            .then(Commands.literal("clear").executes(ctx -> {
                int cleared = SpawnTicksConfig.clearExcludedBlocks();
                if (cleared == 0) {
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("[SpawnTicks] Excluded blocks list is already empty.").withStyle(ChatFormatting.YELLOW),
                        false
                    );
                } else {
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("[SpawnTicks] Cleared " + cleared + " entry(s) from excludedBlocks.").withStyle(ChatFormatting.GREEN),
                        true
                    );
                }
                return 1;
            }));
    }

    private static int executeExcludeAdd(CommandSourceStack source, String rawEntry) {
        SpawnTicksConfig.ExclusionAddResult result = SpawnTicksConfig.addExcludedBlock(rawEntry);
        switch (result.status()) {
            case INVALID_SYNTAX -> {
                source.sendFailure(Component.literal(
                    "[SpawnTicks] Invalid exclusion '" + rawEntry + "'. Use <namespace:block>, #<namespace:tag>, or <namespace:*>."
                ));
                return 0;
            }
            case ALREADY_PRESENT -> {
                source.sendSuccess(
                    () -> Component.literal("[SpawnTicks] '" + result.canonicalEntry() + "' is already in excludedBlocks.").withStyle(ChatFormatting.YELLOW),
                    false
                );
                return 1;
            }
            case ADDED -> {
                String suffix = (result.canonicalEntry().startsWith("#") || result.canonicalEntry().endsWith(":*"))
                    ? " (" + result.matchedBlocks() + " matching blocks)"
                    : "";
                source.sendSuccess(
                    () -> Component.literal("[SpawnTicks] Added '" + result.canonicalEntry() + "' to excludedBlocks" + suffix + ".").withStyle(ChatFormatting.GREEN),
                    true
                );
                return 1;
            }
            case ADDED_UNMATCHED -> {
                source.sendSuccess(
                    () -> Component.literal(
                        "[SpawnTicks] Added '" + result.canonicalEntry() + "' to excludedBlocks (note: 0 currently registered blocks or bound tags match)."
                    ).withStyle(ChatFormatting.YELLOW),
                    true
                );
                return 1;
            }
            default -> {
                return 0;
            }
        }
    }

    private static int executeExcludeRemove(CommandSourceStack source, String rawEntry) {
        String removed = SpawnTicksConfig.removeExcludedBlock(rawEntry);
        if (removed == null) {
            source.sendFailure(Component.literal("[SpawnTicks] '" + rawEntry + "' is not in excludedBlocks."));
            return 0;
        }
        source.sendSuccess(
            () -> Component.literal("[SpawnTicks] Removed '" + removed + "' from excludedBlocks.").withStyle(ChatFormatting.GREEN),
            true
        );
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestExcludableEntries(SuggestionsBuilder builder) {
        Stream<String> namespaces = BuiltInRegistries.BLOCK.keySet().stream()
            .map(ResourceLocation::getNamespace)
            .distinct()
            .map(ns -> ns + ":*");
        Stream<String> blockIds = BuiltInRegistries.BLOCK.keySet().stream()
            .map(ResourceLocation::toString);
        Stream<String> tagIds = BuiltInRegistries.BLOCK.getTagNames()
            .map(tag -> "#" + tag.location());
        Stream<String> all = Stream.concat(namespaces, Stream.concat(blockIds, tagIds));
        return SharedSuggestionProvider.suggest(all, builder);
    }

    private static MutableComponent buildPortalStatusLine(SpawnTicksConfig.UnattendedBlockFilter blockFilter) {
        if (!blockFilter.allowNetherPortalTicks()) {
            return Component.literal("Blocked").withStyle(ChatFormatting.GREEN);
        }
        if (blockFilter.isExplicitlyExcluded(Blocks.NETHER_PORTAL.defaultBlockState())) {
            return Component.literal("Blocked (via excludedBlocks)").withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal("Allowed").withStyle(ChatFormatting.YELLOW);
    }

    private static String formatExcludedBlocksSummary(List<String> entries) {
        if (entries.isEmpty()) {
            return "None";
        }
        int previewCount = Math.min(MAX_STATUS_EXCLUDED_PREVIEW, entries.size());
        String preview = String.join(", ", entries.subList(0, previewCount));
        int extra = entries.size() - previewCount;
        String suffix = extra > 0 ? ", +" + extra + " more; use /spawnticks exclude list" : "";
        return entries.size() + " (" + preview + suffix + ")";
    }

    private static MutableComponent buildStatusBadge(boolean enabled, boolean chunkHealthy, boolean levelHealthy) {
        if (!chunkHealthy) {
            return Component.literal("INACTIVE (MIXIN CONFLICT)").withStyle(ChatFormatting.GOLD);
        }
        if (!levelHealthy) {
            return enabled
                ? Component.literal("PARTIAL (KEEP-AWAKE CONFLICT)").withStyle(ChatFormatting.GOLD)
                : Component.literal("DISABLED (KEEP-AWAKE CONFLICT)").withStyle(ChatFormatting.RED);
        }
        return Component.literal(enabled ? "ENABLED" : "DISABLED")
            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static MutableComponent buildKeepAwakeLine(boolean enabled, boolean levelHealthy, int spawnRadius) {
        if (enabled && levelHealthy && spawnRadius > 0) {
            return Component.literal("Active").withStyle(ChatFormatting.GREEN);
        }
        String keepAwakeReason = !levelHealthy
            ? "Mixin conflict"
            : (!enabled ? "config disabled" : "spawnChunkRadius = 0");
        return Component.literal("Inactive (" + keepAwakeReason + ")").withStyle(ChatFormatting.YELLOW);
    }

    private static String formatHookConflictDetails() {
        List<String> mods = SpawnTicksRuntimeState.getConflictingMods();
        List<String> mixins = SpawnTicksRuntimeState.getConflictingMixins();
        String failureSummary = SpawnTicksRuntimeState.getFailureSummary();

        List<String> details = new ArrayList<>(2);
        if (!mods.isEmpty()) {
            details.add("Mod(s): " + String.join(", ", mods));
        } else if (!mixins.isEmpty()) {
            details.add("Mixin(s): " + String.join(", ", mixins));
        }
        if (failureSummary != null && !failureSummary.isBlank()) {
            details.add(failureSummary);
        }
        return details.isEmpty() ? "Conflict" : "Conflict [" + String.join(" | ", details) + "]";
    }

    private static String formatForcedChunks(ServerLevel level) {
        return level.getForcedChunks().size() + " vanilla /forceload, "
            + countModForcedChunks(level) + " NeoForge mod-forced";
    }

    private static int countModForcedChunks(ServerLevel level) {
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

    private static void collectTrackerChunks(ForcedChunkManager.TicketTracker<?> tracker, LongSet outChunks) {
        for (LongSet chunkSet : tracker.getChunks().values()) {
            outChunks.addAll(chunkSet);
        }
        for (LongSet chunkSet : tracker.getTickingChunks().values()) {
            outChunks.addAll(chunkSet);
        }
    }
}

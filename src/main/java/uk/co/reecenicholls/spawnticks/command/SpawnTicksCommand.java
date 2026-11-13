package uk.co.reecenicholls.spawnticks.command;

import com.mojang.brigadier.CommandDispatcher;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;
import uk.co.reecenicholls.spawnticks.runtime.SpawnTicksRuntimeState;

public class SpawnTicksCommand {
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
                .then(Commands.literal("status").executes(ctx -> {
                    boolean enabled = SpawnTicksConfig.isEnabled();
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

                    MutableComponent statusText = Component.literal("[SpawnTicks] Status: ")
                        .append(statusBadge)
                        .append(Component.literal("\n- Config Enabled: " + enabled))
                        .append(Component.literal("\n- Mixin Hooks: ")).append(hooksLine)
                        .append(Component.literal("\n- Overworld Keep-Awake: ")).append(keepAwakeLine)
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

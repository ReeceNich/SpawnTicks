package uk.co.reecenicholls.spawnticks.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;

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
                    ServerLevel overworld = ctx.getSource().getServer().overworld();
                    ServerLevel currentLevel = ctx.getSource().getLevel();
                    int spawnRadius = ctx.getSource().getServer().getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS);
                    int randomTickSpeed = ctx.getSource().getServer().getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
                    int entityTickingDiameter = Math.max(0, 2 * spawnRadius - 1);
                    int totalSpawnChunks = entityTickingDiameter * entityTickingDiameter;

                    BlockPos spawnPos = overworld.getSharedSpawnPos();
                    ChunkPos centerChunk = new ChunkPos(spawnPos);
                    int overworldForcedCount = overworld.getForcedChunks().size();

                    MutableComponent statusText = Component.literal("[SpawnTicks] Status: ")
                        .append(Component.literal(enabled ? "ENABLED" : "DISABLED").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                        .append(Component.literal("\n- World Spawn: (" + spawnPos.getX() + ", " + spawnPos.getY() + ", " + spawnPos.getZ() + ") [Chunk " + centerChunk.x + ", " + centerChunk.z + "]"))
                        .append(Component.literal("\n- gamerule spawnChunkRadius: " + spawnRadius))
                        .append(Component.literal("\n- Active Spawn Chunk Area: " + entityTickingDiameter + "x" + entityTickingDiameter + " (" + totalSpawnChunks + " entity-ticking chunks)"))
                        .append(Component.literal("\n- Overworld /forceload Chunks: " + overworldForcedCount));

                    if (currentLevel != overworld) {
                        statusText.append(Component.literal(
                            "\n- Current Dimension (" + currentLevel.dimension().location() + ") /forceload Chunks: "
                                + currentLevel.getForcedChunks().size()
                        ));
                    }

                    statusText.append(Component.literal("\n- gamerule randomTickSpeed: " + randomTickSpeed));

                    ctx.getSource().sendSuccess(() -> statusText, false);
                    return 1;
                }))
        );
    }
}

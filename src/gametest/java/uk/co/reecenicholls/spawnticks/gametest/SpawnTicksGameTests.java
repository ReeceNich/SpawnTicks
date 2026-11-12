package uk.co.reecenicholls.spawnticks.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import uk.co.reecenicholls.spawnticks.SpawnTicksMod;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;

@GameTestHolder(SpawnTicksMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpawnTicksGameTests {

    @GameTest(template = "empty", batch = "commands", timeoutTicks = 40)
    public static void commandTogglesConfigAndReportsStatus(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack();
        boolean originalEnabled = SpawnTicksConfig.isEnabled();

        try {
            int statusResult = server.getCommands().getDispatcher().execute("spawnticks status", source);
            helper.assertTrue(statusResult == 1, "Expected /spawnticks status to succeed");

            int disableResult = server.getCommands().getDispatcher().execute("spawnticks disable", source);
            helper.assertTrue(disableResult == 1, "Expected /spawnticks disable to succeed");
            helper.assertTrue(!SpawnTicksConfig.isEnabled(), "Expected SpawnTicksConfig.isEnabled() to be false after disable");

            int enableResult = server.getCommands().getDispatcher().execute("spawnticks enable", source);
            helper.assertTrue(enableResult == 1, "Expected /spawnticks enable to succeed");
            helper.assertTrue(SpawnTicksConfig.isEnabled(), "Expected SpawnTicksConfig.isEnabled() to be true after enable");

            helper.succeed();
        } catch (CommandSyntaxException e) {
            helper.fail("Command execution failed: " + e.getMessage());
        } finally {
            SpawnTicksConfig.setEnabled(originalEnabled);
        }
    }

    @GameTest(template = "empty", batch = "enabled_growth", timeoutTicks = 200)
    public static void unattendedSugarCaneGrowsWithoutIncreasingInhabitedTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players for unattended test");

        BlockPos caneRel = new BlockPos(2, 2, 2);
        BlockPos grownRel = new BlockPos(2, 3, 2);
        placeEnclosedSugarCane(helper, caneRel, grownRel);

        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(caneRel));
        boolean originalEnabled = SpawnTicksConfig.isEnabled();
        GameRules.IntegerValue randomTickRule = server.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
        int originalRandomTickSpeed = randomTickRule.get();
        boolean wasForced = level.getForcedChunks().contains(chunkPos.toLong());

        SpawnTicksConfig.setEnabled(true);
        level.setChunkForced(chunkPos.x, chunkPos.z, true);
        long initialInhabitedTime = level.getChunk(chunkPos.x, chunkPos.z).getInhabitedTime();
        randomTickRule.set(2000, server);

        final int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (helper.getBlockState(grownRel).is(Blocks.SUGAR_CANE)) {
                long finalInhabitedTime = level.getChunk(chunkPos.x, chunkPos.z).getInhabitedTime();
                restoreState(server, level, chunkPos, wasForced, randomTickRule, originalRandomTickSpeed, originalEnabled);
                helper.assertTrue(
                    finalInhabitedTime == initialInhabitedTime,
                    "Expected chunk inhabitedTime to stay unchanged (" + initialInhabitedTime + " vs " + finalInhabitedTime + ")"
                );
                helper.succeed();
            } else if (ticks[0] >= 180) {
                restoreState(server, level, chunkPos, wasForced, randomTickRule, originalRandomTickSpeed, originalEnabled);
                helper.fail("Unattended sugar cane failed to grow within 180 ticks at randomTickSpeed=2000");
            }
        });
    }

    @GameTest(template = "empty", batch = "disabled_noop", timeoutTicks = 60)
    public static void disabledConfigSkipsUnattendedRandomTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players for unattended test");

        BlockPos caneRel = new BlockPos(2, 2, 2);
        BlockPos grownRel = new BlockPos(2, 3, 2);
        placeEnclosedSugarCane(helper, caneRel, grownRel);

        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(caneRel));
        boolean originalEnabled = SpawnTicksConfig.isEnabled();
        GameRules.IntegerValue randomTickRule = server.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
        int originalRandomTickSpeed = randomTickRule.get();
        boolean wasForced = level.getForcedChunks().contains(chunkPos.toLong());

        SpawnTicksConfig.setEnabled(false);
        level.setChunkForced(chunkPos.x, chunkPos.z, true);
        randomTickRule.set(2000, server);

        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(
                    helper.getBlockState(caneRel).is(Blocks.SUGAR_CANE),
                    "Expected sugar cane block to remain planted, found " + helper.getBlockState(caneRel)
                );
                int age = helper.getBlockState(caneRel).getValue(SugarCaneBlock.AGE);
                boolean grown = helper.getBlockState(grownRel).is(Blocks.SUGAR_CANE);
                helper.assertTrue(age == 0 && !grown, "Expected 0 sugar cane growth while disabled, but age=" + age + ", grown=" + grown);
                helper.succeed();
            } finally {
                restoreState(server, level, chunkPos, wasForced, randomTickRule, originalRandomTickSpeed, originalEnabled);
            }
        });
    }

    private static void placeEnclosedSugarCane(GameTestHelper helper, BlockPos caneRel, BlockPos grownRel) {
        // Solid stone base under the water and soil so nothing falls or washes away.
        helper.setBlock(new BlockPos(1, 0, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        // Enclose water at (1, 1, 2) on all sides at Y=1.
        helper.setBlock(new BlockPos(0, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.SAND);
        helper.setBlock(caneRel, Blocks.SUGAR_CANE);
        helper.setBlock(grownRel, Blocks.AIR);
    }

    private static void restoreState(
        MinecraftServer server,
        ServerLevel level,
        ChunkPos chunkPos,
        boolean wasForced,
        GameRules.IntegerValue randomTickRule,
        int originalRandomTickSpeed,
        boolean originalEnabled
    ) {
        randomTickRule.set(originalRandomTickSpeed, server);
        if (!wasForced) {
            level.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
        SpawnTicksConfig.setEnabled(originalEnabled);
    }
}

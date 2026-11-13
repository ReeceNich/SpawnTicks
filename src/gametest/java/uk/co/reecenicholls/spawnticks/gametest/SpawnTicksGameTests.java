package uk.co.reecenicholls.spawnticks.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import uk.co.reecenicholls.spawnticks.SpawnTicksMod;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;
import uk.co.reecenicholls.spawnticks.runtime.EmptyTimeAccess;
import uk.co.reecenicholls.spawnticks.runtime.SpawnTicksRuntimeState;

@GameTestHolder(SpawnTicksMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpawnTicksGameTests {
    private static final TicketType<ChunkPos> TEST_KEEP_LOADED_TICKET = TicketType.create(
        "spawnticks_test_keep_loaded",
        Comparator.comparingLong(ChunkPos::toLong)
    );
    private static final TicketType<ChunkPos> TEST_FORCE_TICKS_TICKET = TicketType.create(
        "spawnticks_test_force_ticks",
        Comparator.comparingLong(ChunkPos::toLong)
    );
    private static final BlockPos CANE_REL = new BlockPos(2, 2, 2);
    private static final BlockPos GROWN_REL = new BlockPos(2, 3, 2);

    @GameTest(template = "empty", batch = "runtime_health", timeoutTicks = 20)
    public static void runtimeMixinHooksAreHealthy(GameTestHelper helper) {
        helper.assertTrue(
            SpawnTicksRuntimeState.isChunkTickHookHealthy(),
            "Expected ServerChunkCache#tickChunks hook to be healthy, failure: " + SpawnTicksRuntimeState.getFailureSummary()
        );
        helper.assertTrue(
            SpawnTicksRuntimeState.isLevelTickHookHealthy(),
            "Expected ServerLevel#tick hook to be healthy, failure: " + SpawnTicksRuntimeState.getFailureSummary()
        );
        helper.assertTrue(
            SpawnTicksRuntimeState.isFullyActive(),
            "Expected SpawnTicksRuntimeState.isFullyActive() to be true"
        );
        helper.assertTrue(
            SpawnTicksRuntimeState.getConflictingMixins().isEmpty(),
            "Expected no conflicting mixins, found: " + SpawnTicksRuntimeState.getConflictingMixins()
        );
        helper.assertTrue(
            helper.getLevel() instanceof EmptyTimeAccess,
            "Expected ServerLevel to implement EmptyTimeAccess"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "commands", timeoutTicks = 40)
    public static void commandTogglesConfigAndReportsStatus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();

        try (TestScope scope = new TestScope(server, level).saveEnabled()) {
            String statusOutput = executeStatusCommand(server);
            helper.assertTrue(statusOutput.contains("[SpawnTicks] Status:"), "Expected status header in: " + statusOutput);

            CommandSourceStack source = server.createCommandSourceStack();
            int disableResult = server.getCommands().getDispatcher().execute("spawnticks disable", source);
            helper.assertTrue(disableResult == 1, "Expected /spawnticks disable to succeed");
            helper.assertTrue(!SpawnTicksConfig.isEnabled(), "Expected SpawnTicksConfig.isEnabled() to be false after disable");

            int enableResult = server.getCommands().getDispatcher().execute("spawnticks enable", source);
            helper.assertTrue(enableResult == 1, "Expected /spawnticks enable to succeed");
            helper.assertTrue(SpawnTicksConfig.isEnabled(), "Expected SpawnTicksConfig.isEnabled() to be true after enable");

            scope.succeed(helper);
        } catch (CommandSyntaxException e) {
            helper.fail("Command execution failed: " + e.getMessage());
        }
    }

    @GameTest(template = "empty", batch = "enabled_growth", timeoutTicks = 200)
    public static void unattendedSugarCaneGrowsWithoutIncreasingInhabitedTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players for unattended test");

        placeEnclosedSugarCane(level, helper.absolutePos(BlockPos.ZERO), null);
        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(CANE_REL));

        TestScope scope = new TestScope(server, level)
            .saveEnabled()
            .forceChunk(chunkPos);
        GameRules.IntegerValue randomTickRule = scope.saveIntRule(GameRules.RULE_RANDOMTICKING);

        SpawnTicksConfig.setEnabled(true);
        long initialInhabitedTime = level.getChunk(chunkPos.x, chunkPos.z).getInhabitedTime();
        randomTickRule.set(2000, server);

        final int[] ticks = {0};
        helper.onEachTick(() -> scope.runGuarded(() -> {
            ticks[0]++;
            if (helper.getBlockState(GROWN_REL).is(Blocks.SUGAR_CANE)) {
                long finalInhabitedTime = level.getChunk(chunkPos.x, chunkPos.z).getInhabitedTime();
                helper.assertTrue(
                    finalInhabitedTime == initialInhabitedTime,
                    "Expected chunk inhabitedTime to stay unchanged (" + initialInhabitedTime + " vs " + finalInhabitedTime + ")"
                );
                scope.succeed(helper);
            } else if (ticks[0] >= 180) {
                scope.fail(helper, "Unattended sugar cane failed to grow within 180 ticks at randomTickSpeed=2000");
            }
        }));
    }

    @GameTest(template = "empty", batch = "disabled_noop", timeoutTicks = 60)
    public static void disabledConfigSkipsUnattendedRandomTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players for unattended test");

        placeEnclosedSugarCane(level, helper.absolutePos(BlockPos.ZERO), null);
        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(CANE_REL));

        TestScope scope = new TestScope(server, level)
            .saveEnabled()
            .forceChunk(chunkPos);
        GameRules.IntegerValue randomTickRule = scope.saveIntRule(GameRules.RULE_RANDOMTICKING);

        SpawnTicksConfig.setEnabled(false);
        randomTickRule.set(2000, server);

        helper.runAfterDelay(25, () -> scope.runGuarded(() -> {
            assertSugarCaneUnchanged(level, helper.absolutePos(CANE_REL), helper.absolutePos(GROWN_REL), "while disabled");
            scope.succeed(helper);
        }));
    }

    @GameTest(template = "empty", batch = "empty_time_keep_awake", timeoutTicks = 60)
    public static void serverLevelMixinResetsEmptyTimeWhenEnabled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players");
        EmptyTimeAccess emptyTimeAccess = (EmptyTimeAccess) level;

        TestScope scope = new TestScope(server, level)
            .saveEnabled()
            .suppressForcedChunksForEmptyTimeTest();
        GameRules.IntegerValue spawnRadiusRule = scope.saveIntRule(GameRules.RULE_SPAWN_CHUNK_RADIUS);

        helper.assertTrue(!ForcedChunkManager.hasForcedChunks(level), "Expected no remaining forced chunks during emptyTime test");
        spawnRadiusRule.set(Math.max(2, spawnRadiusRule.get()), server);
        SpawnTicksConfig.setEnabled(false);

        final boolean[] reenabled = {false};
        final int[] verifyTicks = {0};
        final int[] totalTicks = {0};

        helper.onEachTick(() -> scope.runGuarded(() -> {
            totalTicks[0]++;
            if (!reenabled[0]) {
                if (emptyTimeAccess.spawnticks$getEmptyTime() >= 5) {
                    reenabled[0] = true;
                    SpawnTicksConfig.setEnabled(true);
                } else if (totalTicks[0] >= 40) {
                    scope.fail(helper, "Expected ServerLevel emptyTime to reach >= 5 while disabled, got " + emptyTimeAccess.spawnticks$getEmptyTime());
                }
                return;
            }

            verifyTicks[0]++;
            if (verifyTicks[0] >= 2) {
                int finalEmptyTime = emptyTimeAccess.spawnticks$getEmptyTime();
                helper.assertTrue(
                    finalEmptyTime <= 1,
                    "Expected ServerLevelMixin to reset emptyTime to <= 1 after re-enabling, got " + finalEmptyTime
                );
                scope.succeed(helper);
            }
        }));
    }

    @GameTest(template = "empty", batch = "paused_empty_dim", timeoutTicks = 400)
    public static void pausedEmptyDimensionSkipsUnattendedRandomTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players");
        EmptyTimeAccess emptyTimeAccess = (EmptyTimeAccess) level;

        placeEnclosedSugarCane(level, helper.absolutePos(BlockPos.ZERO), null);
        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(CANE_REL));

        TestScope scope = new TestScope(server, level)
            .saveEnabled()
            .suppressForcedChunksForEmptyTimeTest()
            .addTicket(TEST_KEEP_LOADED_TICKET, chunkPos, 2, false);
        GameRules.IntegerValue randomTickRule = scope.saveIntRule(GameRules.RULE_RANDOMTICKING);
        GameRules.IntegerValue spawnRadiusRule = scope.saveIntRule(GameRules.RULE_SPAWN_CHUNK_RADIUS);

        spawnRadiusRule.set(0, server);
        randomTickRule.set(0, server);
        SpawnTicksConfig.setEnabled(false);

        final boolean[] pauseReached = {false};
        final int[] pausedTicks = {0};
        final int[] totalTicks = {0};

        helper.onEachTick(() -> scope.runGuarded(() -> {
            totalTicks[0]++;
            if (!pauseReached[0]) {
                if (emptyTimeAccess.spawnticks$getEmptyTime() >= 305) {
                    pauseReached[0] = true;
                    SpawnTicksConfig.setEnabled(true);
                    randomTickRule.set(2000, server);
                } else if (totalTicks[0] >= 350) {
                    scope.fail(helper, "Expected emptyTime to reach >= 305, got " + emptyTimeAccess.spawnticks$getEmptyTime());
                }
                return;
            }

            pausedTicks[0]++;
            if (pausedTicks[0] >= 25) {
                helper.assertTrue(
                    emptyTimeAccess.spawnticks$getEmptyTime() >= 300,
                    "Expected dimension to remain paused (emptyTime >= 300), got " + emptyTimeAccess.spawnticks$getEmptyTime()
                );
                assertSugarCaneUnchanged(
                    level,
                    helper.absolutePos(CANE_REL),
                    helper.absolutePos(GROWN_REL),
                    "while dimension is paused at emptyTime >= 300"
                );
                scope.succeed(helper);
            }
        }));
    }

    @GameTest(template = "empty", batch = "fluid_random_tick", timeoutTicks = 200)
    public static void unattendedFluidRandomTickRunsWithoutPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players");

        // Enclose a single lava block at Y=1 ringed by flammable planks with air above at Y=2.
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    helper.setBlock(new BlockPos(2, 1, 2), Blocks.LAVA);
                } else {
                    helper.setBlock(new BlockPos(2 + dx, 0, 2 + dz), Blocks.STONE);
                    helper.setBlock(new BlockPos(2 + dx, 1, 2 + dz), Blocks.OAK_PLANKS);
                }
                helper.setBlock(new BlockPos(2 + dx, 2, 2 + dz), Blocks.AIR);
            }
        }

        BlockPos lavaRel = new BlockPos(2, 1, 2);
        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(lavaRel));

        TestScope scope = new TestScope(server, level)
            .saveEnabled()
            .forceChunk(chunkPos);
        GameRules.IntegerValue randomTickRule = scope.saveIntRule(GameRules.RULE_RANDOMTICKING);
        GameRules.BooleanValue fireTickRule = scope.saveBooleanRule(GameRules.RULE_DOFIRETICK);

        SpawnTicksConfig.setEnabled(true);
        fireTickRule.set(true, server);
        randomTickRule.set(2000, server);

        final int[] ticks = {0};
        helper.onEachTick(() -> scope.runGuarded(() -> {
            ticks[0]++;
            boolean ignited = false;
            for (int dx = -1; dx <= 1 && !ignited; dx++) {
                for (int dz = -1; dz <= 1 && !ignited; dz++) {
                    if (helper.getBlockState(new BlockPos(2 + dx, 2, 2 + dz)).is(BlockTags.FIRE)) {
                        ignited = true;
                    }
                }
            }
            if (ignited) {
                scope.succeed(helper);
            } else if (ticks[0] >= 180) {
                scope.fail(helper, "Expected unattended lava FluidState#randomTick to ignite adjacent planks within 180 ticks");
            }
        }));
    }

    @GameTest(template = "empty", batch = "forced_ticket_inhabited", timeoutTicks = 40)
    public static void moddedForceTicksTicketPreservesVanillaInhabitedTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(new BlockPos(2, 1, 2)));

        TestScope scope = new TestScope(server, level)
            .saveEnabled()
            .addTicket(TEST_FORCE_TICKS_TICKET, chunkPos, 2, true);

        SpawnTicksConfig.setEnabled(false);
        long initialInhabitedTime = level.getChunk(chunkPos.x, chunkPos.z).getInhabitedTime();

        helper.runAfterDelay(10, () -> scope.runGuarded(() -> {
            long finalInhabitedTime = level.getChunk(chunkPos.x, chunkPos.z).getInhabitedTime();
            helper.assertTrue(
                finalInhabitedTime > initialInhabitedTime,
                "Expected forceTicks=true ticket to advance vanilla inhabitedTime (" + initialInhabitedTime + " -> " + finalInhabitedTime + ")"
            );
            scope.succeed(helper);
        }));
    }

    @GameTest(template = "empty", batch = "tick_freeze", timeoutTicks = 40)
    public static void tickFreezeSkipsUnattendedRandomTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players");

        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(CANE_REL));

        try (TestScope scope = new TestScope(server, level)
                .saveEnabled()
                .forceChunk(chunkPos)
                .freezeTickRate()) {
            placeEnclosedSugarCane(level, origin, scope);
            GameRules.IntegerValue randomTickRule = scope.saveIntRule(GameRules.RULE_RANDOMTICKING);
            SpawnTicksConfig.setEnabled(true);
            randomTickRule.set(2000, server);

            helper.assertTrue(
                !server.tickRateManager().runsNormally(),
                "Expected TickRateManager.runsNormally() to be false while frozen"
            );

            // GameTestTicker pauses while TickRateManager is frozen, so step chunk ticking synchronously.
            for (int i = 0; i < 25; i++) {
                level.getChunkSource().tick(() -> true, true);
            }

            helper.assertTrue(
                level.isNaturalSpawningAllowed(chunkPos),
                "Expected force-loaded test chunk to be in entity-ticking range during freeze check"
            );
            assertSugarCaneUnchanged(
                level,
                origin.offset(CANE_REL),
                origin.offset(GROWN_REL),
                "while /tick freeze is active"
            );
            scope.succeed(helper);
        }
    }

    @GameTest(template = "empty", batch = "border_exclusion", timeoutTicks = 200)
    public static void outerBorderBlockTickingChunkSkipsUnattendedRandomTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players");

        ChunkPos baseChunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ChunkPos borderChunk = new ChunkPos(baseChunk.x + 128, baseChunk.z + 128);
        BlockPos setupOrigin = new BlockPos(borderChunk.getMinBlockX(), 200, borderChunk.getMinBlockZ());

        // Pre-generate/load FULL status synchronously so radius-1 promotion does not race async worldgen on CI.
        level.getChunk(borderChunk.x, borderChunk.z);

        TestScope scope = new TestScope(server, level)
            .saveEnabled()
            .addTicket(TEST_KEEP_LOADED_TICKET, borderChunk, 1, false);
        GameRules.IntegerValue randomTickRule = scope.saveIntRule(GameRules.RULE_RANDOMTICKING);

        SpawnTicksConfig.setEnabled(true);
        randomTickRule.set(2000, server);

        final boolean[] setupDone = {false};
        final int[] loadTicks = {0};
        final int[] testTicks = {0};

        helper.onEachTick(() -> scope.runGuarded(() -> {
            if (!setupDone[0]) {
                loadTicks[0]++;
                ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(borderChunk.toLong());
                if (holder != null && holder.getTickingChunk() != null && level.shouldTickBlocksAt(borderChunk.toLong())) {
                    helper.assertTrue(
                        !level.isNaturalSpawningAllowed(borderChunk),
                        "Expected radius-1 ticket chunk to sit at BLOCK_TICKING border (!isNaturalSpawningAllowed)"
                    );
                    placeEnclosedSugarCane(level, setupOrigin, scope);
                    setupDone[0] = true;
                } else if (loadTicks[0] >= 120) {
                    scope.fail(helper, "Level-32 border chunk failed to reach BLOCK_TICKING state within 120 ticks");
                }
                return;
            }

            testTicks[0]++;
            if (testTicks[0] >= 25) {
                helper.assertTrue(
                    level.shouldTickBlocksAt(borderChunk.toLong()),
                    "Expected level-32 border chunk to remain in BLOCK_TICKING range"
                );
                helper.assertTrue(
                    !level.isNaturalSpawningAllowed(borderChunk),
                    "Expected level-32 border chunk to remain excluded from entity ticking"
                );
                assertSugarCaneUnchanged(
                    level,
                    setupOrigin.offset(CANE_REL),
                    setupOrigin.offset(GROWN_REL),
                    "in level-32 outer border chunk"
                );
                scope.succeed(helper);
            }
        }));
    }

    @GameTest(template = "empty", batch = "conflict_reporting", timeoutTicks = 240)
    public static void partialConflictKeepsChunkTickingActiveAndReportsDetails(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(level.players().isEmpty(), "GameTestServer must have 0 online players");

        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        placeEnclosedSugarCane(level, origin, null);
        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(CANE_REL));

        TestScope scope = new TestScope(server, level)
            .saveEnabled()
            .saveRuntimeState()
            .forceChunk(chunkPos);
        GameRules.IntegerValue randomTickRule = scope.saveIntRule(GameRules.RULE_RANDOMTICKING);

        SpawnTicksRuntimeState.setStateForTest(
            true,
            false,
            List.of("com.example.KeepAwakeConflictMixin"),
            "ServerLevel#tick was overwritten by com.example.KeepAwakeConflictMixin"
        );
        helper.assertTrue(SpawnTicksRuntimeState.isChunkTickHookHealthy(), "Expected chunk tick hook healthy under partial conflict");
        helper.assertTrue(!SpawnTicksRuntimeState.isLevelTickHookHealthy(), "Expected level tick hook unhealthy under partial conflict");
        helper.assertTrue(!SpawnTicksRuntimeState.isFullyActive(), "Expected isFullyActive() false under partial conflict");

        SpawnTicksConfig.setEnabled(true);
        String partialStatus = executeStatusCommand(server);
        helper.assertTrue(
            partialStatus.contains("PARTIAL (KEEP-AWAKE CONFLICT)"),
            "Expected PARTIAL (KEEP-AWAKE CONFLICT) badge in status: " + partialStatus
        );
        helper.assertTrue(
            partialStatus.contains("com.example.KeepAwakeConflictMixin")
                && partialStatus.contains("ServerLevel#tick was overwritten"),
            "Expected both conflicting mixin and failure summary in status: " + partialStatus
        );
        helper.assertTrue(
            partialStatus.contains("Overworld Keep-Awake: Inactive (Mixin conflict)"),
            "Expected keep-awake reported inactive due to Mixin conflict: " + partialStatus
        );

        randomTickRule.set(2000, server);
        final boolean[] phaseB = {false};
        final int[] phaseATicks = {0};
        final int[] phaseBTicks = {0};

        helper.onEachTick(() -> scope.runGuarded(() -> {
            if (!phaseB[0]) {
                phaseATicks[0]++;
                if (level.getBlockState(origin.offset(GROWN_REL)).is(Blocks.SUGAR_CANE)) {
                    phaseB[0] = true;
                    level.setBlock(origin.offset(CANE_REL), Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(origin.offset(GROWN_REL), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    SpawnTicksRuntimeState.setStateForTest(
                        false,
                        true,
                        List.of("com.example.ChunkTickConflictMixin"),
                        "ServerChunkCache#tickChunks was overwritten"
                    );
                    String fullConflictStatus = executeStatusCommand(server);
                    helper.assertTrue(
                        fullConflictStatus.contains("INACTIVE (MIXIN CONFLICT)")
                            && fullConflictStatus.contains("com.example.ChunkTickConflictMixin")
                            && fullConflictStatus.contains("ServerChunkCache#tickChunks was overwritten"),
                        "Expected full conflict status details in: " + fullConflictStatus
                    );
                } else if (phaseATicks[0] >= 180) {
                    scope.fail(helper, "Expected unattended sugar cane to still grow during partial keep-awake conflict");
                }
                return;
            }

            phaseBTicks[0]++;
            if (phaseBTicks[0] >= 25) {
                assertSugarCaneUnchanged(
                    level,
                    origin.offset(CANE_REL),
                    origin.offset(GROWN_REL),
                    "when chunk-tick hook is marked conflicted"
                );
                scope.succeed(helper);
            }
        }));
    }

    private static String executeStatusCommand(MinecraftServer server) {
        StringBuilder output = new StringBuilder();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                output.append(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        try {
            int result = server.getCommands().getDispatcher().execute(
                "spawnticks status",
                server.createCommandSourceStack().withSource(capture)
            );
            if (result != 1) {
                throw new AssertionError("Expected /spawnticks status to return 1, got " + result);
            }
        } catch (CommandSyntaxException ex) {
            throw new AssertionError("Failed to execute /spawnticks status", ex);
        }
        return output.toString();
    }

    private static void placeEnclosedSugarCane(ServerLevel level, BlockPos origin, TestScope recordBlocksScope) {
        // Solid stone base under the water and soil so nothing falls or washes away.
        setBlockWithOptionalRecord(level, origin.offset(1, 0, 2), Blocks.STONE.defaultBlockState(), recordBlocksScope);
        setBlockWithOptionalRecord(level, origin.offset(2, 0, 2), Blocks.STONE.defaultBlockState(), recordBlocksScope);
        // Enclose water at (1, 1, 2) on all sides at Y=1.
        setBlockWithOptionalRecord(level, origin.offset(0, 1, 2), Blocks.STONE.defaultBlockState(), recordBlocksScope);
        setBlockWithOptionalRecord(level, origin.offset(1, 1, 1), Blocks.STONE.defaultBlockState(), recordBlocksScope);
        setBlockWithOptionalRecord(level, origin.offset(1, 1, 3), Blocks.STONE.defaultBlockState(), recordBlocksScope);
        setBlockWithOptionalRecord(level, origin.offset(1, 1, 2), Blocks.WATER.defaultBlockState(), recordBlocksScope);
        setBlockWithOptionalRecord(level, origin.offset(2, 1, 2), Blocks.SAND.defaultBlockState(), recordBlocksScope);
        setBlockWithOptionalRecord(level, origin.offset(CANE_REL), Blocks.SUGAR_CANE.defaultBlockState(), recordBlocksScope);
        setBlockWithOptionalRecord(level, origin.offset(GROWN_REL), Blocks.AIR.defaultBlockState(), recordBlocksScope);
    }

    private static void setBlockWithOptionalRecord(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        TestScope recordBlocksScope
    ) {
        if (recordBlocksScope != null) {
            recordBlocksScope.recordOriginalBlock(pos);
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void assertSugarCaneUnchanged(
        ServerLevel level,
        BlockPos canePos,
        BlockPos grownPos,
        String context
    ) {
        BlockState caneState = level.getBlockState(canePos);
        if (!caneState.is(Blocks.SUGAR_CANE)) {
            throw new AssertionError("Expected sugar cane block to remain planted, found " + caneState);
        }
        int age = caneState.getValue(SugarCaneBlock.AGE);
        boolean grown = level.getBlockState(grownPos).is(Blocks.SUGAR_CANE);
        if (age != 0 || grown) {
            throw new AssertionError("Expected 0 sugar cane growth " + context + ", but age=" + age + ", grown=" + grown);
        }
    }

    private static final class TestScope implements AutoCloseable {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final List<Runnable> cleanups = new ArrayList<>();
        private final Map<BlockPos, BlockState> savedBlocks = new LinkedHashMap<>();
        private boolean closed = false;

        private TestScope(MinecraftServer server, ServerLevel level) {
            this.server = server;
            this.level = level;
        }

        private TestScope saveEnabled() {
            boolean originalEnabled = SpawnTicksConfig.isEnabled();
            this.cleanups.add(() -> SpawnTicksConfig.setEnabled(originalEnabled));
            return this;
        }

        private TestScope saveRuntimeState() {
            SpawnTicksRuntimeState.TestStateSnapshot snapshot = SpawnTicksRuntimeState.captureStateForTest();
            this.cleanups.add(() -> SpawnTicksRuntimeState.restoreStateForTest(snapshot));
            return this;
        }

        private GameRules.IntegerValue saveIntRule(GameRules.Key<GameRules.IntegerValue> key) {
            GameRules.IntegerValue rule = this.server.getGameRules().getRule(key);
            int originalValue = rule.get();
            this.cleanups.add(() -> rule.set(originalValue, this.server));
            return rule;
        }

        private GameRules.BooleanValue saveBooleanRule(GameRules.Key<GameRules.BooleanValue> key) {
            GameRules.BooleanValue rule = this.server.getGameRules().getRule(key);
            boolean originalValue = rule.get();
            this.cleanups.add(() -> rule.set(originalValue, this.server));
            return rule;
        }

        private TestScope forceChunk(ChunkPos chunkPos) {
            boolean wasForced = this.level.getForcedChunks().contains(chunkPos.toLong());
            this.level.setChunkForced(chunkPos.x, chunkPos.z, true);
            this.cleanups.add(() -> {
                if (!wasForced) {
                    this.level.setChunkForced(chunkPos.x, chunkPos.z, false);
                }
            });
            return this;
        }

        private TestScope addTicket(TicketType<ChunkPos> type, ChunkPos pos, int radius, boolean forceTicks) {
            this.level.getChunkSource().addRegionTicket(type, pos, radius, pos, forceTicks);
            this.cleanups.add(() -> this.level.getChunkSource().removeRegionTicket(type, pos, radius, pos, forceTicks));
            return this;
        }

        private TestScope freezeTickRate() {
            ServerTickRateManager tickRateManager = this.server.tickRateManager();
            boolean originallyFrozen = tickRateManager.isFrozen();
            tickRateManager.setFrozen(true);
            // Update runGameElements for the current tick just as MinecraftServer#tickServer does.
            tickRateManager.tick();
            this.cleanups.add(() -> {
                tickRateManager.setFrozen(originallyFrozen);
                tickRateManager.tick();
            });
            return this;
        }

        private TestScope suppressForcedChunksForEmptyTimeTest() {
            LongSet savedForced = new LongOpenHashSet(this.level.getForcedChunks());
            for (long packedPos : savedForced) {
                ChunkPos pos = new ChunkPos(packedPos);
                this.level.getChunkSource().addRegionTicket(TEST_KEEP_LOADED_TICKET, pos, 2, pos, false);
                this.level.setChunkForced(pos.x, pos.z, false);
            }
            this.cleanups.add(() -> {
                for (long packedPos : savedForced) {
                    ChunkPos pos = new ChunkPos(packedPos);
                    this.level.setChunkForced(pos.x, pos.z, true);
                    this.level.getChunkSource().removeRegionTicket(TEST_KEEP_LOADED_TICKET, pos, 2, pos, false);
                }
            });
            return this;
        }

        private void recordOriginalBlock(BlockPos pos) {
            BlockPos immutable = pos.immutable();
            if (this.savedBlocks.isEmpty()) {
                this.cleanups.add(() -> {
                    for (Map.Entry<BlockPos, BlockState> entry : this.savedBlocks.entrySet()) {
                        this.level.setBlock(
                            entry.getKey(),
                            entry.getValue(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                        );
                    }
                });
            }
            this.savedBlocks.putIfAbsent(immutable, this.level.getBlockState(immutable));
        }

        private void runGuarded(Runnable action) {
            if (this.closed) {
                return;
            }
            try {
                action.run();
            } catch (Throwable t) {
                close();
                throw t;
            }
        }

        private void succeed(GameTestHelper helper) {
            close();
            helper.succeed();
        }

        private void fail(GameTestHelper helper, String message) {
            close();
            helper.fail(message);
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            for (int i = this.cleanups.size() - 1; i >= 0; i--) {
                this.cleanups.get(i).run();
            }
        }
    }
}

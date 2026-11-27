package uk.co.reecenicholls.spawnticks;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import uk.co.reecenicholls.spawnticks.command.SpawnTicksCommand;
import uk.co.reecenicholls.spawnticks.platform.SpawnTicksPlatform;
import uk.co.reecenicholls.spawnticks.runtime.SpawnTicksRuntimeState;

public final class SpawnTicksCommon {
    public static final String MOD_ID = "spawnticks";

    private SpawnTicksCommon() {
    }

    public static String getModVersion() {
        return SpawnTicksPlatform.INSTANCE.getModVersion();
    }

    public static void onServerStarting() {
        SpawnTicksRuntimeState.verifyRuntimeIntegrity();
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        SpawnTicksCommand.register(dispatcher);
    }
}

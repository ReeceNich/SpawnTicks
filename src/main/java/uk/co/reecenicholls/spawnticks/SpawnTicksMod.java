package uk.co.reecenicholls.spawnticks;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import uk.co.reecenicholls.spawnticks.command.SpawnTicksCommand;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;
import uk.co.reecenicholls.spawnticks.runtime.SpawnTicksRuntimeState;

@Mod(SpawnTicksMod.MOD_ID)
public class SpawnTicksMod {
    public static final String MOD_ID = "spawnticks";
    private static volatile String modVersion = "unknown";

    public SpawnTicksMod(ModContainer modContainer, IEventBus modEventBus) {
        modVersion = modContainer.getModInfo().getVersion().toString();
        modContainer.registerConfig(ModConfig.Type.SERVER, SpawnTicksConfig.SPEC);
        // ModConfigEvent is non-abstract, so NeoForge EventBus requires subscribing to concrete subclasses.
        modEventBus.addListener(ModConfigEvent.Loading.class, SpawnTicksConfig::onConfigLoadOrReload);
        modEventBus.addListener(ModConfigEvent.Reloading.class, SpawnTicksConfig::onConfigLoadOrReload);
        modEventBus.addListener(ModConfigEvent.Unloading.class, SpawnTicksConfig::onConfigUnload);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    public static String getModVersion() {
        return modVersion;
    }

    private void onServerStarting(ServerStartingEvent event) {
        SpawnTicksRuntimeState.verifyRuntimeIntegrity();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        SpawnTicksCommand.register(event.getDispatcher());
    }
}

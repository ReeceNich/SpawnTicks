package uk.co.reecenicholls.spawnticks.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import uk.co.reecenicholls.spawnticks.SpawnTicksCommon;

@Mod(SpawnTicksCommon.MOD_ID)
public class SpawnTicksNeoForgeMod {
    private static volatile String modVersion = "unknown";

    public SpawnTicksNeoForgeMod(ModContainer modContainer, IEventBus modEventBus) {
        modVersion = modContainer.getModInfo().getVersion().toString();
        modContainer.registerConfig(ModConfig.Type.SERVER, NeoForgeConfig.SPEC);
        // ModConfigEvent is non-abstract, so NeoForge EventBus requires subscribing to concrete subclasses.
        modEventBus.addListener(ModConfigEvent.Loading.class, NeoForgeConfig::onConfigLoadOrReload);
        modEventBus.addListener(ModConfigEvent.Reloading.class, NeoForgeConfig::onConfigLoadOrReload);
        modEventBus.addListener(ModConfigEvent.Unloading.class, NeoForgeConfig::onConfigUnload);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    public static String getModVersion() {
        return modVersion;
    }

    private void onServerStarting(ServerStartingEvent event) {
        SpawnTicksCommon.onServerStarting();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        SpawnTicksCommon.registerCommands(event.getDispatcher());
    }
}

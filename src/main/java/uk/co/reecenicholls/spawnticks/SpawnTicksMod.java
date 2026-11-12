package uk.co.reecenicholls.spawnticks;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import uk.co.reecenicholls.spawnticks.command.SpawnTicksCommand;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;

@Mod(SpawnTicksMod.MOD_ID)
public class SpawnTicksMod {
    public static final String MOD_ID = "spawnticks";

    public SpawnTicksMod(ModContainer modContainer, IEventBus modEventBus) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SpawnTicksConfig.SPEC);
        // ModConfigEvent is non-abstract, so NeoForge EventBus requires subscribing to concrete subclasses.
        modEventBus.addListener(ModConfigEvent.Loading.class, SpawnTicksConfig::onConfigLoadOrReload);
        modEventBus.addListener(ModConfigEvent.Reloading.class, SpawnTicksConfig::onConfigLoadOrReload);
        modEventBus.addListener(ModConfigEvent.Unloading.class, SpawnTicksConfig::onConfigUnload);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        SpawnTicksCommand.register(event.getDispatcher());
    }
}

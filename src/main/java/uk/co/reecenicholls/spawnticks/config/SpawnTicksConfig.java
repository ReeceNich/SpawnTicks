package uk.co.reecenicholls.spawnticks.config;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-scoped configuration for SpawnTicks, cached in a volatile field for
 * lock-free hot-path reads during chunk ticking.
 */
public class SpawnTicksConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    private static volatile boolean cachedEnabled = true;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("SpawnTicks server configuration");
        ENABLED = builder
                .comment("Whether random block ticking is enabled in entity-ticking chunks without nearby players")
                .define("enabled", true);
        SPEC = builder.build();
    }

    public static boolean isEnabled() {
        return cachedEnabled;
    }

    public static void setEnabled(boolean enabled) {
        cachedEnabled = enabled;
        if (SPEC.isLoaded()) {
            ENABLED.set(enabled);
            SPEC.save();
        }
    }

    /** Synchronizes the cached flag when the server config loads or reloads from disk. */
    public static void onConfigLoadOrReload(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC && SPEC.isLoaded()) {
            cachedEnabled = ENABLED.get();
        }
    }

    /** Resets the cached flag to default when a world's server config unloads. */
    public static void onConfigUnload(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            cachedEnabled = true;
        }
    }
}

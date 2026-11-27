package uk.co.reecenicholls.spawnticks.neoforge;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import uk.co.reecenicholls.spawnticks.config.SpawnTicksConfig;

/**
 * NeoForge {@link ModConfigSpec} backing {@link SpawnTicksConfig}.
 */
public final class NeoForgeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue ALLOW_UNATTENDED_NETHER_PORTAL_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("SpawnTicks server configuration");
        ENABLED = builder
            .comment("Whether random block ticking is enabled in entity-ticking chunks without nearby players")
            .define("enabled", true);
        ALLOW_UNATTENDED_NETHER_PORTAL_TICKS = builder
            .comment(
                "Whether Nether portal blocks may receive unattended random ticks when no players are nearby.",
                "Vanilla NetherPortalBlock#randomTick spawns Zombified Piglins ignoring mob caps, and mobs",
                "never despawn when 0 players are in the dimension, causing Piglin buildup over time.",
                "Default: false (blocked while unattended; normal ticking resumes whenever a player is near)."
            )
            .define("allowUnattendedNetherPortalTicks", false);
        EXCLUDED_BLOCKS = builder
            .comment(
                "Additional blocks to skip during unattended random block & fluid ticking.",
                "Supported formats:",
                "  - Exact block ID: \"minecraft:ice\", \"somemod:custom_portal\"",
                "  - Block tag: \"#minecraft:leaves\"",
                "  - Namespace wildcard: \"somemod:*\""
            )
            .defineListAllowEmpty("excludedBlocks", List.<String>of(), () -> "", SpawnTicksConfig::isValidExclusionEntry);
        SPEC = builder.build();
    }

    private NeoForgeConfig() {
    }

    public static void saveEnabled(boolean enabled) {
        if (SPEC.isLoaded()) {
            ENABLED.set(enabled);
            SPEC.save();
        }
    }

    public static void saveBlockFilter(
        boolean allowPortalTicks,
        List<String> excludedBlocks,
        boolean persistPortals,
        boolean persistExcluded
    ) {
        if (SPEC.isLoaded()) {
            if (persistPortals) {
                ALLOW_UNATTENDED_NETHER_PORTAL_TICKS.set(allowPortalTicks);
            }
            if (persistExcluded) {
                EXCLUDED_BLOCKS.set(new ArrayList<>(excludedBlocks));
            }
            SPEC.save();
        }
    }

    public static void onConfigLoadOrReload(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC && SPEC.isLoaded()) {
            SpawnTicksConfig.applyLoadedConfig(
                ENABLED.get(),
                ALLOW_UNATTENDED_NETHER_PORTAL_TICKS.get(),
                EXCLUDED_BLOCKS.get()
            );
        }
    }

    public static void onConfigUnload(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            SpawnTicksConfig.resetToDefaults();
        }
    }
}

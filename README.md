# SpawnTicks

**Server-side-only NeoForge mod for Minecraft `1.21`–`1.21.4`** that backports Minecraft 1.21.5's unattended random block & fluid ticking to entity-ticking chunks—so crops, sugar cane, dripstone, and other random-tick mechanics keep growing 24/7 in spawn chunks and `/forceload` chunks even when zero players are online or nearby.

---

## Key Features

- **Server-Side Only**: Installed strictly on the server (`displayTest = "IGNORE_ALL_VERSION"`, `side = "SERVER"`). Vanilla and modded `1.21`–`1.21.4` clients can connect without installing the mod.
- **Broad `1.21`–`1.21.4` Coverage via Two Clean Era JARs**:
  - `spawnticks-<version>+mc1.21-1.21.1-neoforge.jar` — supports Minecraft `1.21.0` and `1.21.1` (NeoForge `21.0.x`–`21.1.x`).
  - `spawnticks-<version>+mc1.21.2-1.21.4-neoforge.jar` — supports Minecraft `1.21.2`, `1.21.3`, and `1.21.4` (NeoForge `21.2.x`–`21.4.x`).
- **24/7 Growth in Loaded Chunks**: Applies `randomTickSpeed` random block and fluid ticks to entity-ticking chunks (Overworld spawn chunks via `/gamerule spawnChunkRadius` and `/forceload` chunks) with no nearby players.
- **No Unwanted Side Effects**: Only random block/fluid ticks run while unattended—vanilla skips chunk `inhabitedTime` (regional difficulty), natural mob spawning (`NaturalSpawner`), weather/lightning strikes, skeleton horse traps, snow/ice accumulation, and unattended Nether Portal random ticks (preventing Zombified Piglin buildup when no players are in the dimension).
- **Configurable Block Exclusions**: Skip specific block IDs, block `#tags`, or entire `namespace:*` mods during unattended random block and fluid ticking via config or commands.
- **Zero Double-Ticking**: Chunks with a player within range use vanilla ticking normally and are never ticked twice.
- **Respects `/tick freeze`**: Only ticks while `tickRateManager().runsNormally()` is active.
- **Overworld Keep-Awake**: Prevents Minecraft's 300-tick (15-second) empty-world pause in the Overworld when `spawnChunkRadius > 0`.

---

## How It Works

In vanilla Minecraft `1.21`–`1.21.4`, chunks loaded by spawn chunks or `/forceload` continue ticking entities (iron farms, hoppers, redstone), **but** random block ticks only happen when a player is within range (`anyPlayerCloseEnoughForSpawning`) or forced via an unused flag.

SpawnTicks hooks into chunk ticking to:
1. Keep the Overworld ticking when 0 players are online (if `spawnChunkRadius > 0`).
2. Run random block and fluid ticks on unattended entity-ticking chunks (`isNaturalSpawningAllowed` & `shouldTickBlocksAt`) without triggering natural mob spawns, weather events, regional difficulty buildup, or unattended Nether Portal Piglin spawns (`allowUnattendedNetherPortalTicks = false` by default, plus any `excludedBlocks` rules).

> **Note on Vanilla Crop Rules:** Light-dependent crops (wheat, carrots, potatoes, etc.) still check light level (`>= 9`) in vanilla code, so underground farms need torches or sky access. Plants without light checks (sugar cane, bamboo, kelp, cactus, dripstone, copper oxidation) grow day and night as long as the chunk is ticking.

---

## Commands & Configuration

Requires **OP level 2** or server console access:

- `/spawnticks status` — Displays mod version, active/conflict status, Mixin hook health, Overworld keep-awake state, unattended Nether Portal policy, active `excludedBlocks` summary, world spawn coordinates and chunk, `spawnChunkRadius`, active entity-ticking spawn area (e.g., `3x3`), vanilla `/forceload` and NeoForge modded forced-chunk counts, and `randomTickSpeed`.
- `/spawnticks enable` — Enables unattended random block ticking and saves the setting.
- `/spawnticks disable` — Disables unattended random block ticking and saves the setting.
- `/spawnticks portals <allow|block>` — Allows or blocks unattended Nether Portal random ticks (`allowUnattendedNetherPortalTicks`). Blocking prevents Zombified Piglins from accumulating at portals when nobody is in the Overworld.
- `/spawnticks exclude <add|remove|list|clear>` — Manages `excludedBlocks` in-game with tab completion for block IDs (`minecraft:ice`), block tags (`#minecraft:leaves`), and namespace wildcards (`somemod:*`).

In NeoForge `21.0`–`21.4`, configuration is generated in `config/spawnticks-server.toml` (and supports optional per-world overrides if placed in `<world>/serverconfig/spawnticks-server.toml`). Edits to the active TOML file hot-reload automatically:

```toml
# SpawnTicks server configuration
# Whether random block ticking is enabled in entity-ticking chunks without nearby players
enabled = true
# Whether Nether portal blocks may receive unattended random ticks when no players are nearby
allowUnattendedNetherPortalTicks = false
# Additional blocks to skip during unattended random block & fluid ticking
# Supports exact block IDs ("minecraft:ice"), block tags ("#minecraft:leaves"), and namespace wildcards ("somemod:*")
excludedBlocks = []
```

---

## Installation

1. Download the JAR matching your server's Minecraft version from [Releases](https://github.com/ReeceNich/SpawnTicks/releases) and place it in your **NeoForge server's `mods/` folder**:
   - **MC `1.21` / `1.21.1`**: `spawnticks-<version>+mc1.21-1.21.1-neoforge.jar`
   - **MC `1.21.2` / `1.21.3` / `1.21.4`**: `spawnticks-<version>+mc1.21.2-1.21.4-neoforge.jar`
2. Restart the server (no client installation required).
3. *(Optional)* Run `/spawnticks status` to confirm your active spawn chunk radius and `/forceload` counts.

### Compatibility Notes
- **Minecraft / Loader**: Minecraft `1.21`–`1.21.4`, NeoForge `21.0.x`–`21.4.x` (CI-tested against `21.1.72` and `21.4.161`), Java `21`.
  - *Note for `1.21.2`/`1.21.3`:* NeoForge `21.2.x` and `21.3.x` have an upstream NeoForge bug ([#1702](https://github.com/neoforged/NeoForge/issues/1702), fixed in NeoForge `21.4.23-beta+`) affecting NeoForge's `forceTicks = true` region tickets when no players are nearby. SpawnTicks' own unattended random ticking does not rely on `forceTicks` on `1.21.2+`, and `1.21.4` includes NeoForge's upstream fix.
- **Vanilla & Modded Clients**: Unmodded and modded `1.21`–`1.21.4` clients can connect freely without installing SpawnTicks.
- **Content, Machine & Chunk-Loader Mods (Create, FTB Chunks, Chunk Loaders)**: Compatible out of the box. Blocks loaded via NeoForge `ForcedChunkManager` tickets receive unattended random ticks cleanly, while machines and entities keep working without triggering unintended item pileups in paused empty dimensions (`emptyTime >= 300`).
- **Optimization Mods & Profilers (EntityCulling, Lithium, ServerCore, Does It Tick?, Spark/JFR)**:
  - **EntityCulling** is client/render-side and unaffected.
  - **Lithium**, **ServerCore**, and **Does It Tick?** remain compatible; SpawnTicks hooks into chunk ticking at Mixin `priority = 1500` and preserves standard `"tickBlocks"` / `"randomTick"` profiler sections for Spark and JFR.
  - **Ksyxis**: Note that Ksyxis sets Overworld `spawnChunkRadius` to `0`, which disables vanilla spawn chunks and Overworld spawn-chunk keep-awake; chunks force-loaded with `/forceload` or NeoForge `ForcedChunkManager` chunk loaders keep their dimension awake and continue to receive unattended random ticks 24/7 even with `0` players online (or keep `spawnChunkRadius > 0` if you want vanilla spawn chunks kept awake).
- **Chunk-Overhaul Mods (e.g. Moonrise)**: If another mod completely replaces `ServerChunkCache#tickChunks` (or removes `shouldForceTicks` on `1.21`–`1.21.1`), SpawnTicks' Mixin guard detects the conflict, avoids crashing server startup, logs an error identifying the conflicting mixin/mod, disables only the affected feature(s), and reports `INACTIVE (MIXIN CONFLICT)` (or `PARTIAL (KEEP-AWAKE CONFLICT)` / `DISABLED (KEEP-AWAKE CONFLICT)` if only Overworld empty-world keep-awake is affected while `/forceload` and chunk-loader random ticking continue working) in `/spawnticks status`.
- **AutoModpack**: If your server uses AutoModpack and it picks up server-side mods from `mods/`, add `mods/spawnticks-*.jar` to your AutoModpack server exclusion rules so clients don't download it unnecessarily.

---

## Building from Source

Requires **Java 21** (or JDK 17+ to run the Gradle wrapper with Foojay JDK 21 toolchain provisioning):

```bash
git clone https://github.com/ReeceNich/SpawnTicks.git
cd SpawnTicks
./gradlew :neoforge:1.21.1-neoforge:build :neoforge:1.21.4-neoforge:build
```

On Windows PowerShell:

```powershell
.\gradlew.bat :neoforge:1.21.1-neoforge:build :neoforge:1.21.4-neoforge:build
```

Compiled JARs are collected in `build/libs/` (`spawnticks-<version>+mc1.21-1.21.1-neoforge.jar` and `spawnticks-<version>+mc1.21.2-1.21.4-neoforge.jar`).

---

## Project Scope & Support

SpawnTicks is a solo hobby project built and shared for the community. To keep maintenance sustainable:

- **Target Platform**: Built for **NeoForge** on Minecraft `1.21`–`1.21.4` (note: vanilla Minecraft `1.21.5+` includes unattended spawn/forced-chunk random ticking natively).
- **Other Loaders**: The codebase now separates loader-agnostic logic (`common/`) from platform bindings (`neoforge/`) via a `ServiceLoader` SPI so Fabric support could be added cleanly in the future, though no Fabric release schedule is promised yet. Under the [MIT License](LICENSE), you are welcome to fork or adapt the code.
- **Bug Reports**: Genuine bug reports and mod-compatibility issues are welcome via [GitHub Issues](https://github.com/ReeceNich/SpawnTicks/issues)—please include your `/spawnticks status` output and server log (`logs/latest.log`).
- **Modpacks**: You are free to include SpawnTicks in any modpack without asking for permission.

---

## Links & License

- **Source Code**: [github.com/ReeceNich/SpawnTicks](https://github.com/ReeceNich/SpawnTicks)
- **Bug Reports & Issues**: [github.com/ReeceNich/SpawnTicks/issues](https://github.com/ReeceNich/SpawnTicks/issues)
- **License**: MIT

# SpawnTicks

**Server-side-only NeoForge mod for Minecraft 1.21.1** that backports Minecraft 1.21.5's unattended random block & fluid ticking to entity-ticking chunks—so crops, sugar cane, dripstone, and other random-tick mechanics keep growing 24/7 in spawn chunks and `/forceload` chunks even when zero players are online or nearby.

---

## Key Features

- **Server-Side Only**: Installed strictly on the server (`displayTest = "IGNORE_ALL_VERSION"`, `side = "SERVER"`). Vanilla and modded 1.21.1 clients can connect without installing the mod.
- **24/7 Growth in Loaded Chunks**: Applies `randomTickSpeed` random block and fluid ticks to entity-ticking chunks (Overworld spawn chunks via `/gamerule spawnChunkRadius` and `/forceload` chunks) with no nearby players.
- **No Unwanted Side Effects**: Only random block/fluid ticks run while unattended—vanilla skips chunk `inhabitedTime` (regional difficulty), natural mob spawning (`NaturalSpawner`), weather/lightning strikes, skeleton horse traps, and snow/ice accumulation.
- **Zero Double-Ticking**: Chunks with a player within range use vanilla ticking normally and are never ticked twice.
- **Respects `/tick freeze`**: Only ticks while `tickRateManager().runsNormally()` is active.
- **Overworld Keep-Awake**: Prevents Minecraft's 300-tick (15-second) empty-world pause in the Overworld when `spawnChunkRadius > 0`.

---

## How It Works

In vanilla Minecraft 1.21.1, chunks loaded by spawn chunks or `/forceload` continue ticking entities (iron farms, hoppers, redstone), **but** random block ticks only happen when a player is within range (`anyPlayerCloseEnoughForSpawning`) or forced via an unused flag.

SpawnTicks hooks into chunk ticking to:
1. Keep the Overworld ticking when 0 players are online (if `spawnChunkRadius > 0`).
2. Run random block and fluid ticks on unattended entity-ticking chunks (`isNaturalSpawningAllowed` & `shouldTickBlocksAt`) without triggering mob spawns, weather events, or regional difficulty buildup.

> **Note on Vanilla Crop Rules:** Light-dependent crops (wheat, carrots, potatoes, etc.) still check light level (`>= 9`) in vanilla code, so underground farms need torches or sky access. Plants without light checks (sugar cane, bamboo, kelp, cactus, dripstone, copper oxidation) grow day and night as long as the chunk is ticking.

---

## Commands & Configuration

Requires **OP level 2** or server console access:

- `/spawnticks status` — Displays enabled state, Overworld spawn coordinates and chunk, `spawnChunkRadius`, active entity-ticking spawn area (e.g., `3x3`), `/forceload` chunk counts, and `randomTickSpeed`.
- `/spawnticks enable` — Enables unattended random block ticking and saves the setting.
- `/spawnticks disable` — Disables unattended random block ticking and saves the setting.

In NeoForge 1.21.1, configuration is generated in `config/spawnticks-server.toml` (and supports optional per-world overrides if placed in `<world>/serverconfig/spawnticks-server.toml`). Edits to the active TOML file hot-reload automatically:

```toml
# SpawnTicks server configuration
# Whether random block ticking is enabled in entity-ticking chunks without nearby players
enabled = true
```

---

## Installation

1. Download `spawnticks-<version>+mc<mc_version>.jar` from [Releases](https://github.com/ReeceNich/SpawnTicks/releases) and place it in your **NeoForge 1.21.1 server's `mods/` folder**.
2. Restart the server (no client installation required).
3. *(Optional)* Run `/spawnticks status` to confirm your active spawn chunk radius and `/forceload` counts.

### Compatibility Notes
- **Minecraft / Loader**: Minecraft `1.21.1`, NeoForge `21.1+` (built against `21.1.72`), Java `21`.
- **AutoModpack**: If your server uses AutoModpack and it picks up server-side mods from `mods/`, add `mods/spawnticks-*.jar` to your AutoModpack server exclusion rules so clients don't download it unnecessarily.

---

## Building from Source

Requires **Java 21** (or JDK 17+ to run the Gradle wrapper with Foojay JDK 21 toolchain provisioning):

```bash
git clone https://github.com/ReeceNich/SpawnTicks.git
cd SpawnTicks
./gradlew build
```

On Windows PowerShell:

```powershell
.\gradlew.bat build
```

The compiled mod will be generated at `build/libs/spawnticks-<version>+mc<mc_version>.jar`.

---

## Links & License

- **Source Code**: [github.com/ReeceNich/SpawnTicks](https://github.com/ReeceNich/SpawnTicks)
- **Bug Reports & Issues**: [github.com/ReeceNich/SpawnTicks/issues](https://github.com/ReeceNich/SpawnTicks/issues)
- **License**: MIT

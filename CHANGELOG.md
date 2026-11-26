# Changelog

All notable changes to **SpawnTicks** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-11-23

### Added
- `allowUnattendedNetherPortalTicks` boolean setting in `config/spawnticks-server.toml` (default: `false`) cached in an immutable `volatile` snapshot (`UnattendedBlockFilter`) to prevent unattended `NetherPortalBlock#randomTick` Zombified Piglin spawns while players are absent.
- `excludedBlocks` list setting in `config/spawnticks-server.toml` (default: `[]`) supporting exact block IDs (`"minecraft:ice"`), block tags (`"#minecraft:leaves"`), and namespace wildcards (`"somemod:*"`), skipping both block and fluid random ticks at matched positions.
- In-game operator subcommands `/spawnticks portals <allow|block>` and `/spawnticks exclude <add|remove|list|clear>` with live tab completion for block IDs, `#tags`, and `namespace:*` wildcards.
- Mod version in the `/spawnticks status` header (`[SpawnTicks v<version>] Status: ...`), plus unattended Nether Portal status and active `excludedBlocks` summary lines.
- 3 new automated NeoForge GameTests (14 total) covering unattended Nether Portal blocking/allowance, `excludedBlocks` matching (`id`, `#tag`, `namespace:*`, fluid random ticks, and precedence), and the new `/spawnticks portals` & `/spawnticks exclude` commands.

### Changed
- Synchronized `SpawnTicksConfig` server config updates and reload/unload handlers with lock-free `volatile` snapshot reads in `ServerChunkCacheMixin`.
- Updated `README.md` with unattended Nether Portal behavior, `excludedBlocks` syntax, and the new `/spawnticks portals` & `/spawnticks exclude` commands.

### Fixed
- Fixed Zombified Piglins accumulating around Overworld Nether Portals inside spawn chunks or force-loaded chunks when zero players are in the dimension (since portal `randomTick` spawns bypass `NaturalSpawner` and mobs do not despawn with no nearby players).

## [1.0.1] - 2026-11-13

### Added
- Automated Modrinth release publishing via `Kir-Antipov/mc-publish@v3` in `.github/workflows/release.yml` alongside GitHub Releases.
- Graceful Mixin conflict detection (`SpawnTicksMixinPlugin` and `SpawnTicksRuntimeState`) that logs an `[ERROR]` naming detected conflicting mods/mixins and safely disables only the affected hook(s) instead of crashing startup when chunk-overhaul mods replace `tickChunks` or Overworld empty-world keep-awake hooks.
- `/spawnticks status` now reports Mixin hook health (including `PARTIAL (KEEP-AWAKE CONFLICT)`, `DISABLED (KEEP-AWAKE CONFLICT)`, and `INACTIVE (MIXIN CONFLICT)` badges with both conflict attribution and failure details), Overworld empty-world keep-awake state, and deduplicated NeoForge modded forced-chunk counts (`ForcedChunksSavedData`) alongside vanilla `/forceload` counts.
- Spark/JFR `ProfilerFiller` `"tickBlocks"` and `"randomTick"` sections around unattended random block and fluid ticks.
- Expanded automated NeoForge GameTests (11 tests total) covering Mixin hook health, `emptyTime` keep-awake resets, `/tick freeze` pause safety, paused empty-dimension skip safety, unattended fluid random ticks, `forceTicks = true` region tickets, outer level-32 border-chunk exclusion, and partial/full conflict status reporting.

### Changed
- Added `authors`, `displayURL`, and `issueTrackerURL` metadata to `META-INF/neoforge.mods.toml`.
- Set `ServerChunkCacheMixin` to Mixin `priority = 1500`, removed `@Local`/`@Share` per-tick wrapper allocation in favor of `chunkMap.getVisibleChunkIfPresent(...)`, and cached `RULE_RANDOMTICKING` and empty-dimension skip checks once per game tick with fast early returns.
- Tightened Mixin conflict, bridge-method, and target-handler verification and cached mod JAR attribution lookups in `SpawnTicksMixinPlugin` and `SpawnTicksRuntimeState`; decoupled Overworld keep-awake conflicts from `/forceload` and chunk-loader random ticking.
- Ordered `ServerLevelMixin` guards to check `level.players().isEmpty()` before reading gamerules.
- Expanded `README.md` compatibility notes, project scope & support expectations, and `/spawnticks status` documentation, added structured GitHub issue templates (`.github/ISSUE_TEMPLATE/`), and updated `AGENTS.md` Mixin safety conventions.

### Fixed
- Prevented unintended item accumulation in paused empty dimensions (`emptyTime >= 300`, such as temporary non-`ForcedChunkManager` tickets or `spawnChunkRadius = 0`) by skipping unattended random ticks whenever dimension entities are paused.
- Fixed `/spawnticks status` conflict details formatting and false-healthy Mixin state edge cases when hooks fail to merge or only one hook is affected.

## [1.0.0] - 2026-11-12

### Added
- Server-side-only NeoForge mod for Minecraft `1.21.1` backporting unattended random block & fluid ticking to entity-ticking chunks (`ServerChunkCacheMixin`).
- Overworld empty-time keep-awake logic (`ServerLevelMixin`) when `spawnChunkRadius > 0` so spawn chunks keep ticking with 0 players online.
- Preserves vanilla behavior for regional difficulty (`inhabitedTime`), natural mob spawning, weather/lightning, and `/tick freeze`.
- Server configuration in `config/spawnticks-server.toml` (with optional `<world>/serverconfig/spawnticks-server.toml` per-world overrides).
- Operator command `/spawnticks <status|enable|disable>` (`OP` level 2+).

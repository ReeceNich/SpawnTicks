# Changelog

All notable changes to **SpawnTicks** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.0] - 2026-11-27

### Added
- Multi-version support covering Minecraft `1.21`–`1.21.4` on NeoForge via two era builds (`spawnticks-<version>+mc1.21-1.21.1-neoforge.jar` for `1.21`–`1.21.1` and `spawnticks-<version>+mc1.21.2-1.21.4-neoforge.jar` for `1.21.2`–`1.21.4`).
- Loader-agnostic `ServerChunkCacheMixin` (`TAIL` hook on `ServerChunkCache#tickChunks(ProfilerFiller, long, List)` iterating `DistanceManager#getTickingChunks()`) for Minecraft `1.21.2`–`1.21.4`.
- Platform abstraction (`SpawnTicksPlatform` `ServiceLoader` SPI) separating shared logic (`common/`) from NeoForge bindings (`neoforge/`) in preparation for potential future Fabric support.

### Changed
- Migrated repository to a single-branch Stonecutter `0.8.4` + ModDevGradle `2.0.148` + Gradle `9.4.1` build, retiring per-version `mc/*` git branches.
- Updated GitHub Actions `Build` and `Release` workflows to build and GameTest both targets in parallel and publish both JARs under a single `v<mod_version>` release tag.

## [1.1.0] - 2026-11-26

### Added
- `allowUnattendedNetherPortalTicks` config option (default: `false`) and `/spawnticks portals <allow|block>` command to control unattended Nether Portal ticking.
- `excludedBlocks` config list and `/spawnticks exclude <add|remove|list|clear>` command (with tab completion) to exclude specific blocks (`minecraft:ice`), block tags (`#minecraft:leaves`), or whole mods (`somemod:*`) from unattended random block & fluid ticks.
- Mod version in the `/spawnticks status` header (`[SpawnTicks v1.1.0] Status: ...`), plus Nether Portal and excluded-block status lines.

### Fixed
- Prevented Zombified Piglins from building up around Overworld Nether Portals when no players are online or nearby by blocking unattended Nether Portal random ticks by default.

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

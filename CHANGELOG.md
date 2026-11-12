# Changelog

All notable changes to **SpawnTicks** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-11-12

### Added
- Server-side-only NeoForge mod for Minecraft `1.21.1` backporting unattended random block & fluid ticking to entity-ticking chunks (`ServerChunkCacheMixin`).
- Overworld empty-time keep-awake logic (`ServerLevelMixin`) when `spawnChunkRadius > 0` so spawn chunks keep ticking with 0 players online.
- Preserves vanilla behavior for regional difficulty (`inhabitedTime`), natural mob spawning, weather/lightning, and `/tick freeze`.
- Server configuration in `config/spawnticks-server.toml` (with optional `<world>/serverconfig/spawnticks-server.toml` per-world overrides).
- Operator command `/spawnticks <status|enable|disable>` (`OP` level 2+).

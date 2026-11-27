# SpawnTicks Repository Conventions & Working Rules

Read and follow these rules for all work in this repository (`SpawnTicks/`). Ignore unrelated files in the parent workspace directory unless explicitly asked.

## 1. Architecture & Code Invariants
- **Single-branch Stonecutter + Multi-Loader layout:**
  - All supported Minecraft versions live on **`main`** using **Stonecutter `0.8.4`** + **ModDevGradle `2.0.148`** + **Gradle `9.4.1`** (local wrapper runs on JDK 17+; Java 21 toolchain compiles/runs Minecraft).
  - **`common/`** compiles in ModDevGradle Vanilla mode (`neoFormVersion`) and **must never import `net.neoforged.*`** or other loader-specific classes. Put loader-specific operations behind `SpawnTicksPlatform` (`ServiceLoader` SPI).
  - **`neoforge/`** owns the `@Mod` entrypoint, `ModConfigSpec` config wiring (`NeoForgeConfig`), `SpawnTicksPlatform` implementation (`NeoForgePlatform`), `<1.21.2` NeoForge `shouldForceTicks` Mixin (`NeoForgeServerChunkCacheMixin`), and NeoForge GameTests.
  - **Stonecutter active version:** Keep root `stonecutter.gradle` set to `stonecutter.active '1.21.1'` (`vcsVersion`) before committing so Git diffs stay clean. Avoid nested `/* ... */` comments inside `//? if ... { ... //?}` preprocessor blocks.
- **Server-side only:** `modId="spawnticks"`, Java 21. `neoforge/src/main/resources/META-INF/neoforge.mods.toml` requires `side="SERVER"` dependencies and `displayTest="IGNORE_ALL_VERSION"`. Do not introduce client-required classes or dependencies unless intentionally changing mod scope.
- **Configuration (`SpawnTicksConfig` & `NeoForgeConfig`):** Uses NeoForge `ModConfig.Type.SERVER` (`config/spawnticks-server.toml`, with optional `<world>/serverconfig/` overrides). Keep hot-path toggles and block filters cached in `volatile` fields synced via `ModConfigEvent.Loading`, `ModConfigEvent.Reloading`, and `ModConfigEvent.Unloading` — do not read `ModConfigSpec` directly on every chunk tick.
- **Mixin safety (`spawnticks.mixins.json` & `spawnticks.neoforge.mixins.json`):** Keep `"defaultRequire": 1` for fail-fast safety on any future mixins, while `ServerChunkCacheMixin` (`>=1.21.2`), `NeoForgeServerChunkCacheMixin` (`<1.21.2`), and `ServerLevelMixin` (`priority = 1500`) use explicit `require = 0` guarded by `SpawnTicksMixinPlugin` (`IMixinConfigPlugin`) and `SpawnTicksRuntimeState` runtime `@MixinMerged` verification so chunk-overhaul mod conflicts log cleanly and degrade safely instead of crashing startup. Mixins must preserve vanilla behavior for `inhabitedTime` (regional difficulty), natural mob spawning (`NaturalSpawner`), weather/lightning, `/tick freeze` (`tickRateManager().runsNormally()`), skip paused empty dimensions (`emptyTime >= 300`), and avoid double-ticking chunks with nearby players.
- **Automated tests (`neoforge/src/gametest/`):** GameTests live in the separate `gametest` sourceSet so test code and `empty.nbt` stay excluded from release `.jar`s. Use `@GameTestHolder(SpawnTicksCommon.MOD_ID)` and `@PrefixGameTestTemplate(false)`. New features or bug fixes should add or update GameTests, and tests must restore any mutated config, gamerules, or forceloaded chunks before finishing. Keep Stonecutter preprocessor directives out of `gametest` sources (put versioned helpers in `UnattendedChunkTicker` if needed).
- **Line endings & executable bit:** `.gitattributes` enforces LF (`gradlew`, `*.sh`, source/config files), CRLF (`*.bat`), and binary (`*.nbt`, `*.jar`, `*.png`). Keep `gradlew` at mode `100755` so Linux GitHub Actions runners can execute `./gradlew`. Verify Stonecutter has not introduced CRLF on Windows before committing.
- **Private local docs:** `personal_docs/` is gitignored. Never commit files in `personal_docs/`, and never store credentials, tokens, or personal emails in tracked files or commits.

## 2. Verification Before Committing or Pushing
After any Java, Mixin, Gradle, workflow, or mod-metadata change, ensure Stonecutter active version is reset to `1.21.1` and run:
```powershell
.\gradlew.bat clean :neoforge:1.21.1-neoforge:build :neoforge:1.21.1-neoforge:runGameTestServer :neoforge:1.21.4-neoforge:build :neoforge:1.21.4-neoforge:runGameTestServer --no-daemon --console=plain
```
Confirm `BUILD SUCCESSFUL` and all required GameTests pass on **every** active target (`1.21.1-neoforge` and `1.21.4-neoforge`) before committing or pushing.

## 3. Git Branching, PRs & Multi-Version Workflow
- **Trunk (`main`):** Single source of truth for all supported Minecraft versions (`1.21–1.21.1` via `1.21.1-neoforge` and `1.21.2–1.21.4` via `1.21.4-neoforge`). Do not create long-lived `mc/*` branches.
- **Strict branch-and-PR workflow:**
  - **Always** make edits on a short-lived `feature/<short-kebab-description>` or `fix/<short-kebab-description>` branch — **never** commit directly to or force-push `main` (not even for docs or release bumps).
  - Use strictly `feature/*` or `fix/*` prefixes (avoid `chore/*`, `docs/*`, etc.) so `.github/workflows/build.yml` triggers CI builds on every branch push as well as on PRs to `main`.
- **Push & tag permissions:**
  - After local build and GameTests pass across all targets, **auto-push** `feature/*` / `fix/*` branches to `origin` so the PR is ready on GitHub.
  - **Never** create or push `v*` release tags without explicit user confirmation (e.g., after the user merges the release PR on GitHub).
- **PR merges:** Prefer **Squash and merge** for PRs on GitHub.
- **Commit hygiene:**
  - Use Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`).
  - Commit and tag with a GitHub `users.noreply.github.com` email; never expose personal emails.

## 4. Versioning, `README.md` & `CHANGELOG.md`
- **Gradle properties:**
  - Root `gradle.properties` holds shared `mod_id`, `mod_group`, and `mod_version` (SemVer `MAJOR.MINOR.PATCH`).
  - `common/versions/<mc>/gradle.properties` holds `neoform_version`.
  - `neoforge/versions/<target>/gradle.properties` holds `minecraft_version`, `minecraft_version_range`, `neoforge_version`, `neoforge_version_range`, `jar_mc_label`, and `modrinth_game_versions`.
  - `neoforge/build.gradle` outputs `spawnticks-<mod_version>+mc<jar_mc_label>-neoforge.jar` (copied to root `build/libs/` via `collectJar`) and substitutes metadata into `META-INF/neoforge.mods.toml`.
- **`README.md`:** Keep download/build JAR filenames generic (e.g. `spawnticks-<version>+mc1.21-1.21.1-neoforge.jar`, `spawnticks-<version>+mc1.21.2-1.21.4-neoforge.jar`) instead of hardcoding specific patch numbers so `README.md` does not go stale on releases.
- **`CHANGELOG.md` (Keep a Changelog 1.1.0):**
  - Use standard subsections (`### Added`, `### Changed`, `### Deprecated`, `### Removed`, `### Fixed`, `### Security`).
  - Document every user-facing, config, workflow, or metadata change under `## [Unreleased]` as work happens.
  - When preparing a release PR, move `## [Unreleased]` entries under a heading formatted **exactly** as `## [<mod_version>] - YYYY-MM-DD` (matching `mod_version` in root `gradle.properties`, e.g. `## [1.2.0] - 2026-11-27`) and leave `## [Unreleased]` empty above it. `.github/workflows/release.yml` depends on matching `## [<mod_version>]` up to the next `## [`.

## 5. Releases & Workflows
- **`.github/workflows/build.yml` (`Build`):** Runs a parallel matrix (`build-matrix`) over `[1.21.1-neoforge, 1.21.4-neoforge]`, executing `./gradlew :neoforge:<target>:build :neoforge:<target>:runGameTestServer --no-daemon` and uploading `*-neoforge.jar` (excluding `*-sources.jar`), followed by a `build-and-test` aggregator job required by `main` branch protection.
- **`.github/workflows/release.yml` (`Release`):** Triggered by pushing a single annotated `v<mod_version>` tag (e.g. `v1.2.0`) after the release PR is merged to `main`. Do not create `*-release` branches. Builds and GameTests all targets, publishes a single GitHub Release attaching both JARs, and uploads each JAR to Modrinth via `Kir-Antipov/mc-publish@v3` with its target game versions (using `MODRINTH_ID` and `MODRINTH_TOKEN`).
- **Keep docs in sync:** Whenever repository rules or workflows change, update `AGENTS.md` (and `personal_docs/MULTI_VERSION_GIT_GUIDE.md` locally if present, without committing `personal_docs/`).

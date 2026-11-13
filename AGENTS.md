# SpawnTicks Repository Conventions & Working Rules

Read and follow these rules for all work in this repository (`SpawnTicks/`). Ignore unrelated files in the parent workspace directory unless explicitly asked.

## 1. Architecture & Code Invariants
- **Server-side only:** `modId="spawnticks"`, Java 21, ModDevGradle. `src/main/resources/META-INF/neoforge.mods.toml` requires `side="SERVER"` dependencies and `displayTest="IGNORE_ALL_VERSION"`. Do not introduce client-required classes or dependencies unless intentionally changing mod scope.
- **Configuration (`SpawnTicksConfig`):** Uses NeoForge `ModConfig.Type.SERVER` (`config/spawnticks-server.toml` default in NeoForge 21.1, with optional `<world>/serverconfig/` overrides). Keep the hot-path toggle cached in a `volatile` field synced via `ModConfigEvent.Loading`, `ModConfigEvent.Reloading`, and `ModConfigEvent.Unloading` — do not read `ModConfigSpec` directly on every chunk tick.
- **Mixin safety (`spawnticks.mixins.json`):** Uses `"defaultRequire": 1`. Mixins (`ServerChunkCacheMixin`, `ServerLevelMixin`) must preserve vanilla behavior for `inhabitedTime` (regional difficulty), natural mob spawning (`NaturalSpawner`), weather/lightning, `/tick freeze` (`tickRateManager().runsNormally()`), and avoid double-ticking chunks with nearby players.
- **Automated tests (`src/gametest/`):** GameTests live in the separate `gametest` sourceSet so test code and `empty.nbt` stay excluded from the release `.jar`. Use `@GameTestHolder(SpawnTicksMod.MOD_ID)` and `@PrefixGameTestTemplate(false)`. New features or bug fixes should add or update GameTests, and tests must restore any mutated config, gamerules, or forceloaded chunks before finishing.
- **Line endings & executable bit:** `.gitattributes` enforces LF (`gradlew`, `*.sh`, source/config files), CRLF (`*.bat`), and binary (`*.nbt`, `*.jar`, `*.png`). Keep `gradlew` at mode `100755` so Linux GitHub Actions runners can execute `./gradlew`.
- **Private local docs:** `personal_docs/` is gitignored. Never commit files in `personal_docs/`, and never store credentials, tokens, or personal emails in tracked files or commits.

## 2. Verification Before Committing or Pushing
After any Java, Mixin, Gradle, workflow, or mod-metadata change, run:
```powershell
.\gradlew.bat clean build runGameTestServer --no-daemon --console=plain
```
Confirm `BUILD SUCCESSFUL` and all required GameTests pass before committing or pushing.

## 3. Git Branching, PRs & Multi-MC Concurrency
- **Trunk (`main`):** Pinned to Minecraft **`1.21.1`** (primary server version).
  - Do **not** create an `mc/1.21.1` branch while `main` tracks `1.21.1` (only run `git branch mc/1.21.1 main` if `main` ever advances to a newer MC version later).
- **Secondary MC versions (`mc/<minecraft_version>`):** Long-lived branches for other supported MC versions (e.g. `mc/1.21`, `mc/1.21.2`, `mc/1.21.4`), each owning its own `version.properties`.
- **Strict branch-and-PR workflow:**
  - **Always** make edits on a short-lived `feature/<short-kebab-description>` or `fix/<short-kebab-description>` branch — **never** commit directly to or force-push `main` or `mc/*` (not even for docs or release bumps).
  - Use strictly `feature/*` or `fix/*` prefixes (avoid `chore/*`, `docs/*`, etc.) so `.github/workflows/build.yml` triggers CI builds on every branch push as well as on PRs to `main` / `mc/**`.
- **Push & tag permissions:**
  - After local build and GameTests pass, **auto-push** `feature/*` / `fix/*` branches to `origin` so the PR is ready on GitHub.
  - **Never** create or push `v*` release tags without explicit user confirmation (e.g., after the user merges the release PR on GitHub).
- **PR merges & cross-version porting:**
  - Prefer **Squash and merge** for PRs on GitHub so each merged change produces a single clean commit SHA.
  - **Never `git merge main` into `mc/<version>` branches** (that overwrites branch-specific `version.properties` and pulls incompatible MC changes). Instead, use `git cherry-pick <commit-sha>` (or a dedicated port PR), and keep code commits separate from `version.properties` edits so cherry-picks stay conflict-free.
- **Commit hygiene:**
  - Use Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`).
  - Commit and tag with a GitHub `users.noreply.github.com` email; never expose personal emails.

## 4. Versioning, `README.md` & `CHANGELOG.md`
- **`version.properties`:** Single source of truth per branch (`mod_version`, `minecraft_version`, `minecraft_version_range`, `neoforge_version`, `neoforge_version_range`). Keep `mod_version` (SemVer `MAJOR.MINOR.PATCH`) **synced** across active branches; `build.gradle` outputs `build/libs/spawnticks-<mod_version>+mc<minecraft_version>.jar` and substitutes properties into `META-INF/neoforge.mods.toml`.
- **`README.md`:** Keep download/build JAR filenames generic (e.g. `spawnticks-<version>+mc<mc_version>.jar`) instead of hardcoding specific patch numbers so `README.md` does not go stale on releases.
- **`CHANGELOG.md` (Keep a Changelog 1.1.0):**
  - Use standard subsections (`### Added`, `### Changed`, `### Deprecated`, `### Removed`, `### Fixed`, `### Security`).
  - Document every user-facing, config, workflow, or metadata change under `## [Unreleased]` as work happens.
  - When preparing a release PR, move `## [Unreleased]` entries under a heading formatted **exactly** as `## [<mod_version>] - YYYY-MM-DD` (matching `mod_version` in `version.properties`, e.g. `## [1.0.1] - 2026-11-13`) and leave `## [Unreleased]` empty above it. `.github/workflows/release.yml` depends on matching `## [<mod_version>]` up to the next `## [`.

## 5. Releases & Workflows
- **`.github/workflows/build.yml` (`Build`):** Runs `./gradlew build runGameTestServer --no-daemon` and uploads `build/libs/*.jar` (excluding `*-sources.jar`).
- **`.github/workflows/release.yml` (`Release`):** Triggered by pushing an annotated `v<mod_version>+mc<minecraft_version>` tag (e.g. `v1.0.1+mc1.21.1`) after the release PR is merged. Do not create `*-release` branches. Modrinth publishing remains commented out in `release.yml` until explicitly requested and configured with `MODRINTH_TOKEN`.
- **Keep docs in sync:** Whenever repository rules or workflows change, update `AGENTS.md` (and `personal_docs/MULTI_VERSION_GIT_GUIDE.md` locally if present, without committing `personal_docs/`).

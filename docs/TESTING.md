# Testing

Further modularization + automation program: [`PLAN_MODULAR_ANDROID_HARDENING.md`](PLAN_MODULAR_ANDROID_HARDENING.md).  
Module-local test notes live in each folder `README.md` (see [`MODULE_README_TEMPLATE.md`](MODULE_README_TEMPLATE.md)).

## Layers

| Layer | Command / location | Catches |
| :--- | :--- | :--- |
| JVM unit | `./gradlew testDebugUnitTest` | Logic / state bugs |
| Architecture-as-code | `:core:testing` Konsist / filesystem guards (part of unit tests) | Feature→feature deps, `getInstance` allowlist, data↛designsystem, module READMEs |
| Static analysis | `./gradlew detekt`; `./gradlew ktlintCheck` | New Kotlin quality/style issues beyond committed baselines |
| Android lint | `./gradlew lintDebug` (CI non-blocking) | Manifest / resource / API lint debt |
| Coverage | `./gradlew :koverVerifyMerged` (also in unit-tests CI) | Soft line-coverage gate |
| Compose UI | `androidTest` (P24) | Dead controls, nav wiring |
| Maestro | device flows (P25) + nightly workflow | Real-device glitches |
| Screenshots | optional (P26) — **not complete** | Visual regressions (no goldens / no Roborazzi yet) |

## Stack

- **JUnit 5** (+ Vintage during migration leftovers)
- **Turbine**, **MockWebServer**, **Robolectric**
- **Konsist** (architecture guards in `:core:testing`)
- **Kover** (merged reports for `:core:data`, `:core:domain`, `:feature:home`, `:core:analytics`, `:core:rss`, `:core:downloads`)
- Shared fixtures: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`)
- **B1 network contracts:** MockWebServer tests in `:core:network` (`BoxLoreApiContractTest`) — run `./gradlew :core:network:testDebugUnitTest`
- **B2/B3 hard slices:** Settings Turbine suite; Home `DiscoveryGreetingTest` + `PodcastAffinityLogicTest`; Info catalog/offline merge tests; domain local/offline port fakes; Learn `LearnDeckLogicTest` + `LearnCuriosityCard` UI model (no network DTO in UI state); Explore `ExploreBrowseLogicTest`; playback `HistoryRecommendationLogic` / `AutoVoiceSearchLogic` / `SmartQueueRefillPolicy` / `MixtapeResumePolicy` / `NightWindowLogic` / `ListeningHistoryUpsertLogic` tests; downloads `SmartDownloadCandidateLogicTest` + worker tests. Full Home/Info VMs still deferred (Application + heavy deps).
- **B4:** `:core:database` has minimal `PodcastDaoInMemoryTest` with `unitTests.isIncludeAndroidResources = true` — see `core/database/README.md` for residual AAPT pitfalls on dependents.
- **No MockK / Hilt**
- Compose **androidTest** uses **JUnit4** + `AndroidJUnitRunner` (androidx.test)

## Coverage (Kover)

Plugin applied on the root project plus `:core:data`, `:core:domain`, `:feature:home`, `:core:analytics`, `:core:rss`, and `:core:downloads`. Those modules contribute a shared Kover report variant `merged` (maps to each module’s `debug` unit tests). Root merges them and enforces a **modest** line-coverage floor (**15%**). Measured merged line coverage after this widen is ~22% (headroom for a later 20% floor in the end-state wave).

**Ratchet path:** 8 → 10 → 12 → **15** → 25 on the merged variant. Optional later: soft module-specific gates for `:core:ranking` once denser.

```bash
# Unit tests only (CI default)
./gradlew testDebugUnitTest

# Architecture guards only
./gradlew :core:testing:testDebugUnitTest

# Coverage verify (runs merged-module debug unit tests, then checks the gate)
./gradlew :koverVerifyMerged

# HTML / XML reports (merged variant at root)
./gradlew :koverHtmlReportMerged
./gradlew :koverXmlReportMerged
```

Reports land under `build/reports/kover/` (root). Raise the threshold only after more suites land — avoid an aggressive gate that flakes CI.

## Static analysis (detekt + ktlint)

Plain detekt runs from the root project against Android/Kotlin source directories. It uses `config/detekt/detekt.yml` and the committed baseline at `config/detekt/baseline.xml`, so CI fails only for new findings beyond the current debt.

ktlint uses `org.jlleitschuh.gradle.ktlint` from the root build and is applied to the root project plus real Android/Kotlin subprojects. The plugin generates one baseline per Gradle project under `config/ktlint/`, so `ktlintCheck` fails only for new style issues beyond the current debt. Format tasks exist by plugin convention but are not wired into build or CI.

```bash
./gradlew detekt
./gradlew ktlintCheck
```

### Android lint (optional / non-blocking)

`unit-tests.yml` runs `./gradlew lintDebug` with `continue-on-error: true` so lint debt does not fail the required Unit Tests check. Promote to fail-on-error once the backlog is clean.

```bash
./gradlew lintDebug
```

## Compose UI tests (P24)

Hosted in `:feature:home` so dialogs can be composed without app DI.

Stable `testTag`s:

- `home_settings_button` — home top-bar Settings
- `settings_add_rss_url` / `settings_add_rss_confirm` / `settings_add_rss_cancel` — Add RSS dialog

Instrumented coverage includes empty-url disable, typing enables confirm, cancel dismiss, error text display, and `isAdding` disables controls.

Compile instrumentation sources (no emulator required):

```bash
./gradlew :feature:home:compileDebugAndroidTestKotlin
```

Run on a connected device/emulator:

```bash
./gradlew :feature:home:connectedDebugAndroidTest
```

CI runs the same task via `android-instrumented-tests.yml` (API 34 emulator + KVM).

## Maestro smoke (P25)

See `maestro/README.md`.

```bash
./gradlew :app:installDebug
maestro test maestro/
```

**Nightly CI:** `.github/workflows/maestro-nightly.yml` (cron UTC + `workflow_dispatch`) always validates that `maestro/*.yaml` flows exist and pass lightweight syntax checks. Full remote device runs use `mobile-dev-inc/action-maestro-cloud` only when repository secrets `MAESTRO_CLOUD_API_KEY` and `MAESTRO_PROJECT_ID` are configured; otherwise the workflow documents the blocker and still passes on flow validation.

## Screenshot baselines (P26)

**P26 is not complete.** No PNG goldens are checked in; Roborazzi is not wired; screenshot capture is **not** a CI gate. Reserved path: `screenshots/baselines/` + [`docs/screenshots/README.md`](screenshots/README.md). `:feature:home` androidTest includes a **composition smoke** for Add RSS tags (`AddRssFeedDialogScreenshotStubTest`) — that is not a screenshot golden.

## CI

| Workflow | What it runs | When |
| :--- | :--- | :--- |
| `unit-tests.yml` | Architecture boundary script + detekt + ktlint + `testDebugUnitTest` (includes Konsist) + `:koverVerifyMerged` + non-blocking `lintDebug` | **`merge-ci` label** on the PR (and later pushes while labeled), or **Actions → Run workflow**. Merge queue is **not** available on this user-owned repo. |
| `android-instrumented-tests.yml` | `:feature:home:connectedDebugAndroidTest` on an API 34 emulator | Same merge-gate as unit tests |
| `maestro-nightly.yml` | Validate `maestro/*.yaml`; optional Maestro Cloud when secrets present | Nightly cron (UTC) / manual |

Architecture boundary: `scripts/ci/check-feature-no-boxlore-database.sh` fails if Home/Info ViewModels or assemblers re-introduce `BoxLoreDatabase`. Konsist/filesystem guards in `:core:testing` additionally enforce feature isolation, `getInstance` allowlist, `:core:data` ↛ designsystem, and module README presence.

**Merge CI (label gate, not a GitHub ruleset):** Unit + Instrumented run when the PR has the **`merge-ci`** label. Honor that process before merging.

```bash
# Ensures the merge-ci label exists and removes any master rulesets that
# accidentally block github-actions[bot] data pushes (user-owned repos cannot
# grant Actions a ruleset bypass).
./scripts/ci/configure-master-merge-queue.sh
```

> **Do not** put `required_status_checks` on `master` on this user-owned repo. GitHub Actions cannot bypass that ruleset, so scheduled bots (`new-episode-check`, sync jobs) fail with `GH013` when committing tracker state. Merge-queue is also unavailable here. After a move to an **organization**, enforced checks + Actions bypass become possible.

Maestro device-farm stays **optional** (needs `MAESTRO_CLOUD_API_KEY` + `MAESTRO_PROJECT_ID`). Screenshots stay local (manual capture).

Protected inputs:
- `app/google-services.json` is **gitignored** and must never be committed.
- The real file is a **release-environment** secret (`GOOGLE_SERVICES_JSON_BASE64` on GitHub Environment `release`). Ordinary CI jobs cannot read it.
- The unit-test workflow writes a non-secret CI stub only so the Google Services plugin can configure `:app`, then deletes it. Release workflows keep using the real secret.
- Locally, keep using your usual `.env` / local `app/google-services.json` — unchanged.

### Cursor Cloud Agents (stub-only build)

Cloud agents **do not** need Firebase, proxy, PostHog, signing, or Maestro secrets in the Cursor Secrets dashboard for compile / unit / instrumented work. Leave those empty.

A fresh clone has no `app/google-services.json` (gitignored). Before `./gradlew assembleDebug` or local tests on the VM, run:

```bash
./scripts/ci/write-cloud-agent-local-config.sh
```

That script writes the **same non-secret CI stub** as `.github/actions/write-ci-google-services` plus a minimal `local.properties` with `sdk.dir` only (no API keys). BuildConfig fields default to empty strings when keys are absent. If `app/google-services.json` or `local.properties` already exist, they are left alone (`FORCE=1` overwrites).

[`.cursor/environment.json`](../.cursor/environment.json) sets this as the environment `install` hook so new cloud VMs get the stub automatically. GitHub **`merge-ci`** checks still write the stub themselves on Actions runners — Cursor Secrets do not feed GitHub, and PRs do not fail merge checks for lack of laptop secrets.

## README checklist

Every Gradle module under `app/`, `core/*/`, and `feature/*/` must keep a folder `README.md` aligned with [`MODULE_README_TEMPLATE.md`](MODULE_README_TEMPLATE.md). Treat the template’s **Testing notes** section as mandatory for modules that own logic under test:

- Document the primary Gradle test command (`./gradlew :<module>:testDebugUnitTest` or instrumented equivalent).
- Note participation in Kover merged coverage when applicable.
- Link shared fixtures in `:core:testing` when used.
- Konsist CI fails if an included app/core/feature module is missing `README.md`.

Quick audit: open each module README’s Testing notes, then cross-check commands against this file’s Layers / CI tables.

## Conventions

- Prefer constructor injection + fakes over `getInstance` in new tests
- Hard ViewModels (Home/Settings/Info) use assemblers + thin ports from `:core:domain` (`RssSubscriptionPort`, `RankingResetPort`, `PodcastCatalogPort`) and Turbine
- Do not rewrite `feature/player` `v2/logic` behavior when migrating runners
- Keep DataStore name `user_preferences`, DB filename, and `rss:` / negative IDs stable in fixtures
- Room/Robolectric DAO tests need `unitTests.isIncludeAndroidResources = true` (enabled on `:core:database` for `PodcastDaoInMemoryTest`); watch AAPT/`includeAndroidResources` pitfalls when enabling on other modules — see `core/database/README.md`
- Workers that need listen history for recommendations use `HistoryRecommendationSource` / `DefaultSmartQueueSources`, not a second `PlaybackRepository`

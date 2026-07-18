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
| Screenshots | optional (P26) | Visual regressions |

## Stack

- **JUnit 5** (+ Vintage during migration leftovers)
- **Turbine**, **MockWebServer**, **Robolectric**
- **Konsist** (architecture guards in `:core:testing`)
- **Kover** (merged reports for `:core:data`, `:core:domain`, `:feature:home`)
- Shared fixtures: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`)
- **B1 network contracts:** MockWebServer tests in `:core:network` (`BoxLoreApiContractTest`) — run `./gradlew :core:network:testDebugUnitTest`
- **B2/B3 hard slices:** Settings Turbine suite; Home `DiscoveryGreetingTest` + `PodcastAffinityLogicTest`; Info catalog/offline merge tests; domain local/offline port fakes; Learn `LearnDeckLogicTest` + `LearnCuriosityCard` UI model (no network DTO in UI state); Explore `ExploreBrowseLogicTest`; playback `HistoryRecommendationLogic` / `AutoVoiceSearchLogic` / `SmartQueueRefillPolicy` / `MixtapeResumePolicy` / `NightWindowLogic` / `ListeningHistoryUpsertLogic` tests; downloads `SmartDownloadCandidateLogicTest` + worker tests. Full Home/Info VMs still deferred (Application + heavy deps).
- **B4:** `:core:database` has minimal `PodcastDaoInMemoryTest` with `unitTests.isIncludeAndroidResources = true` — see `core/database/README.md` for residual AAPT pitfalls on dependents.
- **No MockK / Hilt**
- Compose **androidTest** uses **JUnit4** + `AndroidJUnitRunner` (androidx.test)

## Coverage (Kover)

Plugin applied on the root project plus `:core:data`, `:core:domain`, and `:feature:home`. Those modules contribute a shared Kover report variant `merged` (maps to each module’s `debug` unit tests). Root merges them and enforces a **modest** line-coverage floor (**12%**).

**Ratchet path:** 8 → 10 → **12** → 15 → 25 on the merged variant. Optional later: soft module-specific gates for `:core:ranking` / `:core:downloads` once their suites are denser.

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

Optional, local-only. See `docs/screenshots/README.md` and `screenshots/baselines/`.
No Roborazzi/Papyrus plugin is required for the current scaffolding.

## CI

| Workflow | What it runs | When |
| :--- | :--- | :--- |
| `unit-tests.yml` | Architecture boundary script + detekt + ktlint + `testDebugUnitTest` (includes Konsist) + `:koverVerifyMerged` + non-blocking `lintDebug` | **Every PR** (`pull_request`), merge queue (`merge_group`), or **Actions → Run workflow** |
| `android-instrumented-tests.yml` | `:feature:home:connectedDebugAndroidTest` on an API 34 emulator | Merge queue / manual |
| `maestro-nightly.yml` | Validate `maestro/*.yaml`; optional Maestro Cloud when secrets present | Nightly cron (UTC) / manual |

Architecture boundary: `scripts/ci/check-feature-no-boxlore-database.sh` fails if Home/Info ViewModels or assemblers re-introduce `BoxLoreDatabase`. Konsist/filesystem guards in `:core:testing` additionally enforce feature isolation, `getInstance` allowlist, `:core:data` ↛ designsystem, and module README presence.

**Repo rules (owner only):** apply the merge queue + required checks with:

```bash
./scripts/ci/configure-master-merge-queue.sh
```

That script (needs `gh` admin on the repo) creates/updates a `master` ruleset: merge queue + required Unit/Instrumented checks, with bypass for GitHub Actions (data bots) and the repo owner so direct `[skip ci]` pushes still work. The Cursor agent token cannot apply this — run it locally as `ashwkun`.

Maestro device-farm stays **optional** (needs `MAESTRO_CLOUD_API_KEY` + `MAESTRO_PROJECT_ID`). Screenshots stay local (manual capture).

Protected inputs:
- `app/google-services.json` is **gitignored** and must never be committed.
- The real file is a **release-environment** secret (`GOOGLE_SERVICES_JSON_BASE64` on GitHub Environment `release`). Ordinary CI jobs cannot read it.
- The unit-test workflow writes a non-secret CI stub only so the Google Services plugin can configure `:app`, then deletes it. Release workflows keep using the real secret.
- Locally, keep using your usual `.env` / local `app/google-services.json` — unchanged.

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

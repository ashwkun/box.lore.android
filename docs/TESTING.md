# Testing

How Boxlore is tested: layers, commands, coverage, and what each layer currently covers.

## Status legend

| Status | Meaning |
| :--- | :--- |
| **Done** | Present and exercised |
| **WIP** | Exists but shallow or incomplete |
| **Yet to start** | Not implemented yet |

## Layers

| Layer | Command / location | Catches | Status |
| :--- | :--- | :--- | :--- |
| JVM unit | `./gradlew testDebugUnitTest` | Logic / state bugs | WIP |
| Architecture-as-code | `:core:testing` Konsist / filesystem guards | Feature isolation, allowlists, README presence | Done |
| Static analysis | `./gradlew detekt`; `./gradlew ktlintCheck` | New quality/style issues beyond baselines | Done |
| Android lint | `./gradlew lintDebug` | Manifest / resource / API lint | Done |
| Coverage (Kover) | `./gradlew :koverVerifyMerged` | Soft line-coverage floor on merged modules | WIP |
| Compose UI | `androidTest` (primarily `:feature:home`) | Dead controls, dialog wiring | WIP |
| Maestro | `maestro/` + nightly validate | Real-device flow regressions | WIP |
| Screenshots | `screenshots/baselines/` + Roborazzi | Visual regressions | Yet to start |

Architecture boundaries and module ownership that these guards enforce are described in [`ARCHITECTURE.md`](../ARCHITECTURE.md).

## Stack

- JUnit 5 (+ Vintage where leftovers remain)
- Turbine, MockWebServer, Robolectric
- Konsist (architecture guards in `:core:testing`)
- Kover merged report for `:core:catalog`, `:core:domain`, `:feature:home`, `:core:analytics`, `:core:rss`, `:core:downloads`, `:core:playback`
- Shared fixtures: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`)
- No MockK / Hilt
- Compose `androidTest` uses JUnit4 + `AndroidJUnitRunner`

## Coverage targets

| Target | Status |
| :--- | :--- |
| Merged Kover floor ≥ 20% on gated modules | Done |
| Include analytics / rss / downloads / playback in merge | Done |
| Ratchet floor toward 25% with measured headroom | Yet to start |
| Optional soft gates for `:core:ranking` | Yet to start |

```bash
./gradlew testDebugUnitTest
./gradlew :core:testing:testDebugUnitTest
./gradlew :koverVerifyMerged
./gradlew :koverHtmlReportMerged
./gradlew :koverXmlReportMerged
```

Reports: `build/reports/kover/`.

## Static analysis

```bash
./gradlew detekt
./gradlew ktlintCheck
./gradlew lintDebug
```

Detekt: `config/detekt/{detekt.yml,baseline.xml}`.  
ktlint: per-project baselines under `config/ktlint/`.

Dependency Guard baselines (`:app`, `:core:catalog`, `:core:playback`):

```bash
./gradlew :app:dependencyGuardBaseline :core:catalog:dependencyGuardBaseline :core:playback:dependencyGuardBaseline
```

## JVM / network / DAO coverage

| Area | Status |
| :--- | :--- |
| `:core:network` MockWebServer contracts (`BoxLoreApiContractTest`) | Done |
| Settings assembler / Turbine suites | Done |
| Home pure helpers (greeting, affinity, online) | Done |
| Info catalog / offline merge port tests | Done |
| Playback pure helpers (queue, night window, history upsert, etc.) | Done |
| Downloads worker + candidate logic tests | Done |
| `:core:database` minimal DAO in-memory test | Done |
| Full Application-backed `HomeViewModel` suite | Yet to start |
| Full Application-backed Info / Episode ViewModel suites | Yet to start |
| Hermetic alternative covering the same Home/Info behaviors without Application | Yet to start |

```bash
./gradlew :core:network:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
./gradlew :feature:info:testDebugUnitTest
./gradlew :core:playback:testDebugUnitTest
./gradlew :core:downloads:testDebugUnitTest
./gradlew :core:database:testDebugUnitTest
```

## Compose UI (`androidTest`)

| Target | Status |
| :--- | :--- |
| Hermetic Add RSS dialog tests in `:feature:home` | Done |
| Hermetic Downloads settings page tests | Done |
| Additional hermetic Settings / Home surfaces | WIP |
| Instrumented coverage in other feature modules | Yet to start |
| Full-app navigation instrumentation | Yet to start |

Stable `testTag`s (non-exhaustive):

- `home_settings_button`
- `settings_add_rss_url` / `settings_add_rss_confirm` / `settings_add_rss_cancel`
- `settings_downloads_*` (see `feature/home/README.md`)

```bash
./gradlew :feature:home:compileDebugAndroidTestKotlin
./gradlew :feature:home:connectedDebugAndroidTest
```

## Maestro

| Target | Status |
| :--- | :--- |
| Flow YAML present under `maestro/` | Done |
| Nightly workflow validates YAML without Cloud secrets | Done |
| At least one locally strict smoke flow | Done |
| Additional strict flows (subscribe→library, play→mini player, Learn, Settings RSS entry) | Yet to start |
| Maestro Cloud required CI | Out of scope (local-only; no Cloud secrets) |

```bash
./gradlew :app:installDebug
maestro test maestro/
```

See [`maestro/README.md`](../maestro/README.md).

## Screenshots

| Target | Status |
| :--- | :--- |
| Reserved `screenshots/baselines/` path | Done |
| Checked-in PNG goldens | Yet to start |
| Roborazzi/Paparazzi CI gate | Yet to start |

See [`docs/screenshots/README.md`](screenshots/README.md).

## CI

| Workflow | Runs | When | Status |
| :--- | :--- | :--- | :--- |
| `unit-tests.yml` | Architecture scripts + detekt + ktlint + unit tests + Kover + lint + Dependency Guard | `merge-ci` label or workflow_dispatch | Done |
| `android-instrumented-tests.yml` | `:feature:home:connectedDebugAndroidTest` (API 34 emulator) | Same merge gate | Done |
| `maestro-nightly.yml` | Validate Maestro YAML; optional Cloud if secrets present | Nightly / manual | Done |

Architecture scripts:

- `scripts/ci/check-feature-no-boxlore-database.sh`
- `scripts/ci/check-feature-no-posthog.sh`

**Merge gate:** add `merge-ci` only when ready to merge. Do not put `required_status_checks` on `master` on this user-owned repo (blocks Actions bots with `GH013`).

Protected inputs:

- `app/google-services.json` is gitignored.
- CI writes a non-secret stub via workflow action / `scripts/ci/write-cloud-agent-local-config.sh`.

## Conventions

- Prefer constructor injection + fakes over `getInstance` in new tests.
- Hard ViewModels use assemblers + ports from `:core:domain` and Turbine.
- Do not rewrite `feature/player` `v2/logic` behavior when migrating runners.
- Keep DataStore name `user_preferences`, DB filename, and `rss:` / negative IDs stable in fixtures.
- Room/Robolectric DAO tests need `unitTests.isIncludeAndroidResources = true` where required.
- Workers that need listen history use `HistoryRecommendationSource` / ports — not a second `PlaybackRepository`.

## Module README checklist

Every `app/`, `core/*/`, and `feature/*/` module must keep a folder README aligned with [`MODULE_README_TEMPLATE.md`](MODULE_README_TEMPLATE.md), including Testing notes and the primary Gradle test command. Konsist fails if an included module lacks `README.md`.

## Next phase

A later testing phase owns implementation of every **Yet to start** row above (Application-backed Home/Info suites or hermetic equivalents, broader androidTest, additional strict Maestro flows, screenshot goldens, Kover ratchet). This document is the aspirational map; do not claim those rows Done until verified.

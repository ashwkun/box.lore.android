# Testing

How Boxlore is tested: layers, commands, coverage floors, architecture gates, and the max-coverage checklist.

## Status legend

| Status | Meaning |
| :--- | :--- |
| **Done** | Present and exercised at the max-coverage bar |
| **WIP** | Exists but below the max-coverage bar |
| **Yet to start** | Not implemented yet |
| **Excluded** | Irreducible exclusion with an alternate coverage layer |

## Goal

Maximum achievable automated coverage: every testable production path is exercised by JVM, Compose `androidTest`, Maestro, and/or Roborazzi. High Kover floors fail CI on drop. Architecture guards fail `merge-ci` on graph drift.

**Strategy:** hermetic JVM — constructors, domain ports, shared fakes in `:core:testing`, assemblers, Turbine. No MockK/Hilt. No Application-backed Home/Info suites (hermetic `logic/` + assembler/port suites cover the same behaviors). Media3 service / `PlaybackRepository` stay out of the line gate; covered by policy unit tests + Maestro.

## Layers

| Layer | Command / location | Catches | Status |
| :--- | :--- | :--- | :--- |
| JVM unit | `./gradlew testDebugUnitTest` | Logic / state bugs | WIP |
| Architecture-as-code | `:core:testing` Konsist / scripts | Feature isolation, graph, allowlists, new-code tests | Done |
| Static analysis | `./gradlew detekt`; `./gradlew ktlintCheck` | Style / quality beyond baselines | Done |
| Android lint | `./gradlew lintDebug` | Manifest / resource / API lint | Done |
| Coverage (Kover) | `./gradlew :koverVerifyMerged` | Merged floor (ratchet toward 80%) | WIP |
| Compose UI | `androidTest` per feature (CI matrix) | Dead controls, empty/error UI | Done |
| Maestro | `maestro/` strict flows + nightly validate | Real-device flow regressions | Done |
| Screenshots | `screenshots/baselines/` + Roborazzi verify | Visual regressions | Done |

Architecture boundaries: [`ARCHITECTURE.md`](../ARCHITECTURE.md).

## Stack

- JUnit 5 (+ Vintage where leftovers remain)
- Turbine, MockWebServer, Robolectric
- Konsist (architecture guards in `:core:testing`)
- Shared fixtures / fakes: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`, `core.testing.fakes.*`)
- No MockK / Hilt
- Compose `androidTest` uses JUnit4 + `AndroidJUnitRunner`
- Roborazzi for JVM screenshot goldens

## Max-coverage bars

| Layer | Bar | CI fail |
| :--- | :--- | :--- |
| JVM | Every public behavior-owning type has a suite (happy/empty/error/branches) or an irreducible exclusion | `testDebugUnitTest` |
| ViewModels | Behaviors via hermetic `logic/` + Settings Turbine; AndroidViewModels allowlisted when logic suites exist | unit job |
| Instrumented | Every interactive feature: primary hosts in `androidTest` | instrumented matrix |
| App nav | Bottom-nav + deep-link smoke via Maestro | Maestro nightly / YAML validate |
| Maestro | Strict critical journeys | YAML validate on merge; device nightly |
| Screenshots | Home settings goldens + verify | Roborazzi verify |
| Kover merged | **≥ 80%** end state (ratchet **40 → 45 → 55 → 70 → 80**) | `:koverVerifyMerged` |
| Kover per-module | **≥ 70%** on logic-heavy modules (ratchet as suites land) | module verify |
| Architecture | ARCHITECTURE.md boundaries | scripts + `ArchitectureGuardTest` + dependencyGuard |
| New code | `*ViewModel` / `*Repository` need matching `*Test.kt` (allowlists for stubs / Media3 / hard AndroidViewModels) | `ArchitectureGuardTest` |

### Current Kover floor

| Target | Status |
| :--- | :--- |
| Merged floor ≥ **45%** on full gated set | Done (enforced by `:koverVerifyMerged`) |
| Measured merged line coverage | **≈ 47.9%** (13,358 / 27,869 lines) |
| Per-module ≥ 70% on logic modules | Yet to start |
| Merged floor ≥ 55% / 70% / **80%** | Yet to start (next ratchets) |

The CI floor is locked at **45** (never lower). Measured ≈ 47.9% leaves headroom in the 45–55 band. Reaching **55+** requires more hermetic suites on remaining pure helpers and/or instrumented coverage that feeds Kover; Media3-bound types (`PlaybackRepository`, queue/telemetry coordinators, `DownloadRepository`, `SmartDownloadManager`) and concrete non-open repos stay on alternate layers (policy tests, `logic/` packages, `androidTest`, Maestro).

`:app` Compose nav / FCM / survey chrome is excluded from the line gate (covered by Maestro / instrumented); see root [`build.gradle.kts`](../build.gradle.kts) `kover { }`.

Gated modules: `:core:catalog`, `:core:domain`, `:core:analytics`, `:core:rss`, `:core:downloads`, `:core:playback`, `:core:ranking`, `:core:prefs`, `:core:network`, `:core:database`, `:core:model`, `:feature:home`, `:feature:info`, `:feature:explore`, `:feature:library`, `:feature:onboarding`, `:feature:briefing`, `:feature:player`, `:app`.

```bash
./gradlew testDebugUnitTest
./gradlew :core:testing:testDebugUnitTest
./gradlew :koverVerifyMerged
./gradlew :koverHtmlReportMerged
./gradlew :koverXmlReportMerged
```

Reports: `build/reports/kover/`.

### Irreducible exclusions (line gate only)

| Exclusion | Alternate coverage |
| :--- | :--- |
| `PlaybackRepository` + `core.playback.service.*` / Auto | Policy unit tests + Maestro play/queue |
| `@Composable` / `@Preview` | `androidTest` and/or Roborazzi |
| `:app` `navigation.*` / `ui.*` / `fcm.*` / `surveys.*` | Maestro + instrumented |
| PostHog / Firebase SDK internals | Not our code; features must not import PostHog |
| Generated `R` / `BuildConfig` / databinding | Generated |

## Architecture CI (fail on deviate)

`merge-ci` (`unit-tests.yml`) fails when architecture drifts:

| Guard | What it enforces |
| :--- | :--- |
| `ArchitectureGuardTest` | No feature→feature Gradle deps or imports; catalog↛designsystem; catalog↛playback; catalog must not `api` analytics/ranking; module READMEs; `getInstance` allowlist; package=module (+ `core.data` stubs); no Hilt/Koin/Dagger/MockK; new `*ViewModel`/`*Repository` need matching `*Test.kt` |
| `scripts/ci/check-feature-no-posthog.sh` | Features never import/capture via PostHog |
| `scripts/ci/check-feature-no-boxlore-database.sh` | Home/Info VMs/assemblers do not take `BoxLoreDatabase` |
| `dependencyGuard` | Locked dependency lists for `:app`, `:core:catalog`, `:core:playback` |

```bash
bash scripts/ci/check-feature-no-boxlore-database.sh
bash scripts/ci/check-feature-no-posthog.sh
./gradlew :core:testing:testDebugUnitTest
./gradlew :app:dependencyGuard :core:catalog:dependencyGuard :core:playback:dependencyGuard
```

## Static analysis

```bash
./gradlew detekt
./gradlew ktlintCheck
./gradlew lintDebug
```

Detekt: `config/detekt/{detekt.yml,baseline.xml}`.  
ktlint: per-project baselines under `config/ktlint/`.

## Module × layer checklist

| Module | JVM | VM / logic | androidTest | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `:core:ranking` | Done | n/a | n/a | Repos, scorer, runtime controls |
| `:core:catalog` | WIP | n/a | n/a | Ports/consent/content Done; backup/Media3-adjacent WIP |
| `:core:playback` | WIP | n/a | n/a | Queue/mixtape/policy Done; service excluded |
| `:core:downloads` | WIP | n/a | n/a | Candidate logic Done; Media3 manager Excluded |
| `:core:prefs` | Done | n/a | n/a | DataStore + migrator |
| `:core:database` | Done | n/a | n/a | In-memory DAOs |
| `:core:rss` | Done | n/a | n/a | Feed fixtures + helpers |
| `:core:analytics` | Done | n/a | n/a | Tracks + glossary + facade |
| `:core:network` | Done | n/a | n/a | MockWebServer contracts |
| `:core:domain` | Done | n/a | n/a | Port contracts |
| `:core:model` | Done | n/a | n/a | Behavior helpers |
| `:feature:home` | Done | Done | Done | Settings Turbine + logic + UI tests + goldens |
| `:feature:info` | Done | Done | Done | Port/logic suites + episode rail UI |
| `:feature:explore` | Done | Done | Done | Logic + Learn store + cards UI |
| `:feature:library` | Done | Done | Done | Sort/filter + download models + chips UI |
| `:feature:onboarding` | Done | Done | Done | Logic + suggested row UI |
| `:feature:briefing` | Done | Done | Done | Story text + chip UI |
| `:feature:player` | Done | n/a | Done | v2 logic JVM + mini player UI |
| `:app` | WIP | n/a | Excluded | Worker/push allowlists; nav via Maestro |

Application-backed Home/Info suites are **not** pursued; hermetic `logic/` + assembler/port suites replace them.

## Compose UI (`androidTest`)

| Target | Status |
| :--- | :--- |
| Hermetic Add RSS / Downloads / Reset analytics in `:feature:home` | Done |
| Home `TopControlBar` settings CTA | Done |
| Instrumented coverage in info/player/explore/library/onboarding/briefing | Done |
| App bottom-nav / deep-link smoke | Done (Maestro) |

```bash
./gradlew :feature:home:connectedDebugAndroidTest
# CI matrix runs all feature modules with androidTest
```

## Maestro

| Target | Status |
| :--- | :--- |
| Flow YAML under `maestro/` | Done |
| Nightly YAML validate | Done |
| Strict smoke (launch/home) | Done |
| Strict flows: settings RSS, settings entry, Learn, briefing, play→mini | Done |
| Maestro Cloud required CI | Out of scope |

See [`maestro/README.md`](../maestro/README.md).

## Screenshots

| Target | Status |
| :--- | :--- |
| Reserved `screenshots/baselines/` | Done |
| Checked-in PNG goldens (Add RSS, Reset analytics, Downloads) | Done |
| Roborazzi CI gate (`:feature:home:verifyRoborazziDebug`) | Done |

See [`docs/screenshots/README.md`](screenshots/README.md).

## CI

| Workflow | Runs | When | Status |
| :--- | :--- | :--- | :--- |
| `unit-tests.yml` | Architecture guards + detekt + ktlint + unit + Roborazzi verify + Kover + lint + Dependency Guard | `merge-ci` / dispatch | Done |
| `android-instrumented-tests.yml` | Feature `connectedDebugAndroidTest` matrix (all interactive features) | Same merge gate | Done |
| `maestro-nightly.yml` | Validate Maestro YAML; optional Cloud | Nightly / manual | Done |

**Merge gate:** add `merge-ci` only when ready to merge.

Protected inputs: `app/google-services.json` is gitignored; CI writes a non-secret stub.

## Conventions

- Prefer constructor injection + fakes (`core.testing.fakes`) over `getInstance` in new tests.
- Hard ViewModels use assemblers + ports from `:core:domain` and Turbine + `MainDispatcherExtension` when constructible; otherwise exhaust `logic/` packages.
- Do not rewrite `feature/player` `v2/logic` behavior when migrating runners.
- Keep DataStore name `user_preferences`, DB filename, and `rss:` / negative IDs stable in fixtures.
- Room/Robolectric DAO tests need `unitTests.isIncludeAndroidResources = true` where required.
- Workers that need listen history use `HistoryRecommendationSource` / ports — not a second `PlaybackRepository`.

## Module README checklist

Every `app/`, `core/*/`, and `feature/*/` module keeps a folder README. Shape: [`MODULE_README_TEMPLATE.md`](MODULE_README_TEMPLATE.md). Konsist fails if an included module lacks `README.md`.

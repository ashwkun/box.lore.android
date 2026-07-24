# Testing

How Boxlore is tested: layers, commands, coverage floors, architecture gates, and the coverage checklist.

## Status legend

| Status | Meaning |
| :--- | :--- |
| **Done** | Present and exercised at the bar |
| **WIP** | Exists but below the bar |
| **Yet to start** | Not implemented yet |
| **Excluded** | Irreducible exclusion with an alternate coverage layer |

## Goal

Automated coverage focused on **hermetic JVM** for product logic (queue fill, ranking, catalog, prefs, feature `logic/`). High Kover floors fail CI on drop. Architecture guards fail the unit PR job on graph drift.

**Strategy:** constructors, domain ports, shared fakes in `:core:testing`, assemblers, Turbine. No MockK/Hilt. No Application-backed Home/Info suites. Media3 service / `PlaybackRepository` stay out of the line gate; covered by policy unit tests. Maestro YAML is validated nightly (no paid Maestro Cloud device runs).

## Layers

| Layer | Command / location | Catches | Status |
| :--- | :--- | :--- | :--- |
| JVM unit | `./gradlew testDebugUnitTest` | Logic / state bugs | WIP |
| Architecture-as-code | `:core:testing` Konsist / scripts | Feature isolation, graph, allowlists, new-code tests | Done |
| Static analysis | `./gradlew detekt` | Style / quality beyond baselines | Done |
| Android lint | `./gradlew lintDebug` | Manifest / resource / API lint | Done |
| Coverage (Kover) | `./gradlew :koverVerifyMerged` | Merged floor (ratchet toward 80%) | WIP |
| Screenshots | `screenshots/baselines/` + Roborazzi verify | Visual regressions | Done |
| Maestro | `maestro/` YAML validate | Flow file presence/syntax | Done |

Architecture boundaries: [`ARCHITECTURE.md`](../ARCHITECTURE.md).

## Stack

- JUnit 5 (+ Vintage where leftovers remain)
- Turbine, MockWebServer, Robolectric
- Konsist (architecture guards in `:core:testing`)
- Shared fixtures / fakes: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`, `core.testing.fakes.*`)
- No MockK / Hilt
- Roborazzi for JVM screenshot goldens

## Coverage bars

| Layer | Bar | CI fail |
| :--- | :--- | :--- |
| JVM | Every public behavior-owning type has a suite (happy/empty/error/branches) or an irreducible exclusion | `testDebugUnitTest` |
| ViewModels | Behaviors via hermetic `logic/` + Settings Turbine; AndroidViewModels allowlisted when logic suites exist | unit job |
| Screenshots | Home settings goldens + verify | Roborazzi verify |
| Maestro | YAML present and well-formed | nightly validate |
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

The CI floor is locked at **45** (never lower). Reaching **55+** requires more hermetic suites on remaining pure helpers. Media3-bound types (`PlaybackRepository`, queue/telemetry coordinators, `DownloadRepository`, `SmartDownloadManager`) stay on alternate layers (policy tests, `logic/` packages).

`:app` Compose nav / FCM / survey chrome is excluded from the line gate; see root [`build.gradle.kts`](../build.gradle.kts) `kover { }`.

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
| `PlaybackRepository` + `core.playback.service.*` / Auto | Policy unit tests |
| `@Composable` / `@Preview` | Roborazzi (home settings) + manual |
| `:app` `navigation.*` / `ui.*` / `fcm.*` / `surveys.*` | Manual / optional local Maestro |
| PostHog / Firebase SDK internals | Not our code; features must not import PostHog |
| Generated `R` / `BuildConfig` / databinding | Generated |

## Architecture CI (fail on deviate)

`unit-tests.yml` (PR / dispatch) fails when architecture drifts:

| Guard | What it enforces |
| :--- | :--- |
| `ArchitectureGuardTest` | No feature→feature Gradle deps or imports; catalog↛designsystem; catalog↛playback; catalog must not `api` analytics/ranking; module READMEs; `getInstance` allowlist; package=module (+ `core.data` stubs); no Hilt/Koin/Dagger/MockK; new `*ViewModel`/`*Repository` need matching `*Test.kt` |
| `GlossaryCoverageGuardTest` | `event_glossary.csv` ↔ `AnalyticsGlossary` allowlist; every event has `glossary_emission_coverage.csv` mode (`emission` / `sdk_backed` / `person_props_only`); no dual open/install volume |
| `GlossaryAllEventsEmissionTest` (`:core:analytics`) | Every `emission:` inventory row captures via façade; lifecycle helpers never emit `app_open` / `app_background` / `install_attributed` |
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

| Module | JVM | VM / logic | Notes |
| :--- | :--- | :--- | :--- |
| `:core:ranking` | Done | n/a | Repos, scorer, runtime controls |
| `:core:catalog` | WIP | n/a | Ports/consent/content Done; backup WIP |
| `:core:playback` | WIP | n/a | Queue/mixtape/policy Done; service excluded |
| `:core:downloads` | WIP | n/a | Candidate logic Done; Media3 manager Excluded |
| `:core:prefs` | Done | n/a | DataStore + migrator |
| `:core:database` | Done | n/a | In-memory DAOs |
| `:core:rss` | Done | n/a | Feed fixtures + helpers |
| `:core:analytics` | Done | n/a | Tracks + glossary + facade |
| `:core:network` | Done | n/a | MockWebServer contracts |
| `:core:domain` | Done | n/a | Port contracts |
| `:core:model` | Done | n/a | Behavior helpers |
| `:feature:home` | Done | Done | Settings Turbine + logic + Roborazzi goldens |
| `:feature:info` | Done | Done | Port/logic suites |
| `:feature:explore` | Done | Done | Logic + Learn store |
| `:feature:library` | Done | Done | Sort/filter + download models |
| `:feature:onboarding` | Done | Done | Logic suites |
| `:feature:briefing` | Done | Done | Story text helpers |
| `:feature:player` | Done | n/a | v2 logic JVM |
| `:app` | WIP | n/a | Worker/push allowlists |

Application-backed Home/Info suites are **not** pursued; hermetic `logic/` + assembler/port suites replace them.

## Maestro

| Target | Status |
| :--- | :--- |
| Flow YAML under `maestro/` | Done |
| Nightly YAML validate | Done |
| Maestro Cloud device runs | Out of scope (not subscribed) |

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
| `unit-tests.yml` | Architecture + detekt + ktlint + unit + Roborazzi + Kover + lint + Dependency Guard | PR / dispatch | Done |
| `coderabbit-threads-resolved.yml` | Fail unless all non-outdated CodeRabbit review threads are Resolved | PR / review | Done |
| `gitleaks.yml` | Secret scan | PR / push to master | Done |
| `maestro-nightly.yml` | Validate Maestro YAML | Nightly / manual | Done |

**Merge gate:** master uses a branch ruleset (no merge queue). Required checks: **`testDebugUnitTest`** and **`coderabbit-threads-resolved`**. SonarCloud / CodeRabbit / Gitleaks still run on PRs (fix Sonar new-code issues; resolve CodeRabbit threads — the bare `CodeRabbit` status only means the review finished). The unit suite cancels prior in-progress runs on each PR push (or via Actions → Run workflow). Put `[skip unit]` in the PR title to no-op that job for docs/chore-only changes (still reports green; `workflow_dispatch` always runs full). Bots push to master via **boxlore-master-pusher** (ruleset Integration bypass).

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

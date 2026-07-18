# Testing

Further modularization + automation program: [`PLAN_MODULAR_ANDROID_HARDENING.md`](PLAN_MODULAR_ANDROID_HARDENING.md).  
Module-local test notes live in each folder `README.md` (see [`MODULE_README_TEMPLATE.md`](MODULE_README_TEMPLATE.md)).

## Layers

| Layer | Command / location | Catches |
| :--- | :--- | :--- |
| JVM unit | `./gradlew testDebugUnitTest` | Logic / state bugs |
| Coverage | `./gradlew :koverVerifyMerged` (also in unit-tests CI) | Soft line-coverage gate |
| Compose UI | `androidTest` (P24) | Dead controls, nav wiring |
| Maestro | device flows (P25) | Real-device glitches |
| Screenshots | optional (P26) | Visual regressions |

## Stack

- **JUnit 5** (+ Vintage during migration leftovers)
- **Turbine**, **MockWebServer**, **Robolectric**
- **Kover** (merged reports for `:core:data`, `:core:domain`, `:feature:home`)
- Shared fixtures: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`)
- **B1 network contracts:** MockWebServer tests in `:core:network` (`BoxLoreApiContractTest`) — run `./gradlew :core:network:testDebugUnitTest`
- **B2 hard VM slice (started):** Settings via assembler + fakes (`SettingsViewModelTest`); Home greeting helper (`DiscoveryGreetingTest`); Info catalog port fakes (`InfoCatalogPortBehaviorTest`). Full `HomeViewModel` / Info VMs still deferred (Application + heavy deps).
- **No MockK / Hilt**
- Compose **androidTest** uses **JUnit4** + `AndroidJUnitRunner` (androidx.test)

## Coverage (Kover)

Plugin applied on the root project plus `:core:data`, `:core:domain`, and `:feature:home`. Those modules contribute a shared Kover report variant `merged` (maps to each module’s `debug` unit tests). Root merges them and enforces a **modest** line-coverage floor (8%).

```bash
# Unit tests only (CI default)
./gradlew testDebugUnitTest

# Coverage verify (runs merged-module debug unit tests, then checks the gate)
./gradlew :koverVerifyMerged

# HTML / XML reports (merged variant at root)
./gradlew :koverHtmlReportMerged
./gradlew :koverXmlReportMerged
```

Reports land under `build/reports/kover/` (root). Raising the threshold should wait until more suites land — do not set an aggressive gate that flakes CI.

## Compose UI tests (P24)

Hosted in `:feature:home` so dialogs can be composed without app DI.

Stable `testTag`s:

- `home_settings_button` — home top-bar Settings
- `settings_add_rss_url` / `settings_add_rss_confirm` / `settings_add_rss_cancel` — Add RSS dialog

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

## Screenshot baselines (P26)

Optional, local-only. See `docs/screenshots/README.md` and `screenshots/baselines/`.
No Roborazzi/Papyrus plugin is required for the current scaffolding.

## CI

| Workflow | What it runs | When |
| :--- | :--- | :--- |
| `unit-tests.yml` | `./gradlew testDebugUnitTest` + `:koverVerifyMerged` (JVM) | Merge queue (`merge_group`) or **Actions → Run workflow** |
| `android-instrumented-tests.yml` | `:feature:home:connectedDebugAndroidTest` on an API 34 emulator | Same |

These do **not** run on every PR push. Enable a **merge queue** on `master` and mark both workflows as required checks so merge is blocked if they fail. Use manual dispatch anytime you want a one-off run.

Maestro / screenshots stay **local** (need a full app install / manual capture).

Protected inputs:
- `app/google-services.json` is **gitignored** and must never be committed.
- The real file is a **release-environment** secret (`GOOGLE_SERVICES_JSON_BASE64` on GitHub Environment `release`). Ordinary CI jobs cannot read it.
- The unit-test workflow writes a non-secret CI stub only so the Google Services plugin can configure `:app`, then deletes it. Release workflows keep using the real secret.
- Locally, keep using your usual `.env` / local `app/google-services.json` — unchanged.

## Conventions

- Prefer constructor injection + fakes over `getInstance` in new tests
- Hard ViewModels (Home/Settings/Info) use assemblers + thin ports from `:core:domain` (`RssSubscriptionPort`, `RankingResetPort`, `PodcastCatalogPort`) and Turbine
- Do not rewrite `feature/player` `v2/logic` behavior when migrating runners
- Keep DataStore name `user_preferences`, DB filename, and `rss:` / negative IDs stable in fixtures
- Room/Robolectric DAO tests need `unitTests.isIncludeAndroidResources = true` and compileSdk ≥ 36 (blocked by current AAR metadata pins); RSS ID fixtures cover the RSS identity rules until then
- Workers that need listen history for recommendations use `HistoryRecommendationSource` / `DefaultSmartQueueSources`, not a second `PlaybackRepository`

# Testing

## Layers

| Layer | Command / location | Catches |
| :--- | :--- | :--- |
| JVM unit | `./gradlew testDebugUnitTest` | Logic / state bugs |
| Compose UI | `androidTest` (P24) | Dead controls, nav wiring |
| Maestro | device flows (P25) | Real-device glitches |
| Screenshots | optional (P26) | Visual regressions |

## Stack

- **JUnit 5** (+ Vintage during migration leftovers)
- **Turbine**, **MockWebServer**, **Robolectric**
- **Kover** plugin available (thresholds in harden phase)
- Shared fixtures: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`)
- **No MockK / Hilt**

## CI

GitHub Actions workflow `unit-tests.yml` runs `testDebugUnitTest` on PRs and pushes to feature branches.

Protected inputs:
- `app/google-services.json` is **gitignored** and must never be committed.
- The real file is a **release-environment** secret (`GOOGLE_SERVICES_JSON_BASE64` on GitHub Environment `release`). Ordinary PR/push jobs cannot read it.
- The unit-test workflow writes a non-secret CI stub only so the Google Services plugin can configure `:app`, then deletes it. Release workflows keep using the real secret.
- Locally, keep using your usual `.env` / local `app/google-services.json` — unchanged.

## Conventions

- Prefer constructor injection + fakes over `getInstance` in new tests
- Do not rewrite `feature/player` `v2/logic` behavior when migrating runners
- Keep DataStore name `user_preferences`, DB filename, and `rss:` / negative IDs stable in fixtures
- Room/Robolectric DAO tests need `unitTests.isIncludeAndroidResources = true` and compileSdk ≥ 36 (blocked by current AAR metadata pins); RSS ID fixtures cover the RSS identity rules until then

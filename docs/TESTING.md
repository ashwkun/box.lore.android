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
- CI materializes it from repo secret `GOOGLE_SERVICES_JSON_BASE64` (same secret as the release/changelog workflow). The workflow decodes via Python, writes mode `0600`, never prints file contents, and deletes the file in an `always()` cleanup step.
- Locally, keep using your normal `.env` / local `app/google-services.json` path — CI secret wiring does not change that.

## Conventions

- Prefer constructor injection + fakes over `getInstance` in new tests
- Do not rewrite `feature/player` `v2/logic` behavior when migrating runners
- Keep DataStore name `user_preferences`, DB filename, and `rss:` / negative IDs stable in fixtures
- Room/Robolectric DAO tests need `unitTests.isIncludeAndroidResources = true` and compileSdk ≥ 36 (blocked by current AAR metadata pins); RSS ID fixtures cover the RSS identity rules until then

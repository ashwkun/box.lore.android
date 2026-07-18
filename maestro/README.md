# Maestro smoke flows (P25)

Device / emulator UI smoke tests for Boxlore. Local runs need a connected device; CI has a **nightly scaffold** that always validates flow files and optionally runs Maestro Cloud when secrets are present.

## App id

`cx.aswin.boxlore`

## Prerequisites

1. Install [Maestro](https://maestro.mobile.dev/):
   ```bash
   curl -Ls "https://get.maestro.mobile.dev" | bash
   ```
2. Start an emulator or connect a device with a debug/release build installed:
   ```bash
   ./gradlew :app:installDebug
   ```

## Run

From the repo root:

```bash
maestro test maestro/
```

Or a single flow:

```bash
maestro test maestro/smoke_launch.yaml
maestro test maestro/smoke_settings_rss.yaml
maestro test maestro/smoke_home_visible.yaml
```

## Flows

| File | Intent |
| :--- | :--- |
| `smoke_launch.yaml` | Cold launch; assert Settings entry visible |
| `smoke_home_visible.yaml` | Soft assert home/nav affordances |
| `smoke_settings_rss.yaml` | Settings → Library → Add RSS (optional steps) |

Flows prefer Compose `testTag`s (`home_settings_button`, `settings_add_rss_*`) and fall back to visible text. Flaky first-run taps use `optional: true` so onboarding/consent does not hard-fail the suite on every machine.

## CI — Maestro nightlies

Workflow: [`.github/workflows/maestro-nightly.yml`](../.github/workflows/maestro-nightly.yml)

| Trigger | Behavior |
| :--- | :--- |
| Cron (nightly UTC) / `workflow_dispatch` | Always validates `maestro/*.yaml` presence + lightweight syntax (`appId`, `---`, non-empty steps) |
| Same workflow, optional job | Runs `mobile-dev-inc/action-maestro-cloud` against `:app:assembleDebug` **only if** secrets are set |

**Required for full device-farm runs (optional secrets):**

- `MAESTRO_CLOUD_API_KEY` — Maestro Cloud API key
- `MAESTRO_PROJECT_ID` — Maestro Cloud project id

Without those secrets the nightly still passes on flow validation and prints a blocker note. It is **not** a required PR check. See also `docs/TESTING.md`.

## Notes

- Complete or skip onboarding once on the test device for more stable Settings navigation.
- Do not commit secrets or production `google-services.json` for Maestro runs.

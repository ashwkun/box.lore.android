# Maestro smoke flows (P25)

Device / emulator UI smoke tests for Boxlore. Local runs need a connected device; CI has a **nightly scaffold** that always validates flow files and optionally runs Maestro Cloud when secrets are present.

## App id

`cx.aswin.boxlore`

## Prerequisites

1. Install [Maestro](https://maestro.mobile.dev/):
   ```bash
   curl -Ls "https://get.maestro.mobile.dev" | bash
   export PATH="$PATH:$HOME/.maestro/bin"
   ```
2. Prepare the repo stub config (no secrets required):
   ```bash
   ./scripts/ci/write-cloud-agent-local-config.sh
   export ANDROID_HOME="$HOME/Android/Sdk"
   export ANDROID_SDK_ROOT="$ANDROID_HOME"
   ```
3. Start an emulator or connect a device, then install a debug build:
   ```bash
   ./gradlew :app:installDebug
   ```
4. **First run only:** complete or skip onboarding on the device so the home shell is reachable. `smoke_launch.yaml` treats consent/onboarding taps as optional but **requires** `home_settings_button` after launch.

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
| `smoke_launch.yaml` | Cold launch; **strict** assert on `home_settings_button` |
| `smoke_home_visible.yaml` | Cold-start visibility; **strict** on `home_settings_button`, soft nav text |
| `smoke_settings_rss.yaml` | Settings → Library → Add RSS (optional steps) |

Flows prefer Compose `testTag`s (`home_settings_button`, `settings_add_rss_*`) and fall back to visible text where noted. Flaky first-run taps use `optional: true` so onboarding/consent does not hard-fail the suite on every machine.

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

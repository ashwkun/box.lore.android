# Maestro smoke flows

Maestro flows provide device or emulator smoke coverage for the installed Boxlore app. Local runs require a connected device or emulator. CI validates flow files and can run Maestro Cloud when the optional secrets are configured.

## App id

`cx.aswin.boxlore`

## Prerequisites

1. Install [Maestro](https://maestro.mobile.dev/):
   ```bash
   curl -Ls "https://get.maestro.mobile.dev" | bash
   export PATH="$PATH:$HOME/.maestro/bin"
   ```
2. Prepare local CI-style configuration:
   ```bash
   ./scripts/ci/write-cloud-agent-local-config.sh
   export ANDROID_HOME="$HOME/Android/Sdk"
   export ANDROID_SDK_ROOT="$ANDROID_HOME"
   ```
3. Start an emulator or connect a device, then install a debug build:
   ```bash
   ./gradlew :app:installDebug
   ```
4. Complete or skip onboarding on the device when a flow needs the home shell. The launch flow treats consent and onboarding taps as optional but requires the Home settings button after launch.

## Run

From the repository root:

```bash
maestro test maestro/
```

Run a single flow:

```bash
maestro test maestro/smoke_launch.yaml
maestro test maestro/settings_entry.yaml
maestro test maestro/learn_tab.yaml
```

## Flows

| File | Intent |
| :--- | :--- |
| `smoke_launch.yaml` | Cold launch with a strict assertion on `home_settings_button` |
| `smoke_home_visible.yaml` | Home visibility with strict assertions on `home_settings_button` and the `Home`/`Library` bottom-nav labels |
| `settings_entry.yaml` | Home Settings button opens the Settings hub (strict `Settings`/`Appearance`/`Privacy`) |
| `smoke_settings_rss.yaml` | Settings → Library → Add podcast by RSS, strict through the tagged `settings_add_rss_url` field and Cancel |
| `learn_tab.yaml` | The `Lore` bottom-nav tab navigates off the home shell (strict `home_settings_button` disappears) |
| `briefing_from_home.yaml` | The daily briefing card opens the full briefing screen (strict, needs network) |
| `play_mini_player.yaml` | Playing the briefing card raises the mini player (strict, needs network) |

Flows prefer Compose `testTag`s such as `home_settings_button` and `settings_add_rss_*`, and otherwise use stable `contentDescription`/label text (`Lore`, `Add podcast by RSS feed`, `The Boxlore Brief`, `Episode artwork`).

Every flow keeps a single optional line — the first-run consent/permission tap (`Accept|Allow|Continue|Skip|Not now`) — because that chrome varies by device state. All other steps are strict assertions. `briefing_from_home.yaml` and `play_mini_player.yaml` are strict but depend on a networked, onboarded device that has a region briefing available; run them against a device that has completed onboarding.

## CI

Workflow: [`.github/workflows/maestro-nightly.yml`](../.github/workflows/maestro-nightly.yml)

| Trigger | Behavior |
| :--- | :--- |
| Cron or `workflow_dispatch` | Validates `maestro/*.yaml` presence and basic syntax |
| Optional device-farm job | Builds `:app:assembleDebug` and runs Maestro Cloud when secrets are set |

Optional secrets for full device-farm runs:

- `MAESTRO_CLOUD_API_KEY`
- `MAESTRO_PROJECT_ID`

Without those secrets, the workflow still validates flow files and reports that the device-farm job is skipped.

## Notes

- Complete or skip onboarding once on a local test device for more stable Settings navigation.
- Do not commit secrets or production `google-services.json` for Maestro runs.
- See [`docs/TESTING.md`](../docs/TESTING.md) for the broader test strategy.

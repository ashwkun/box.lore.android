# Screenshot baselines (B7 / P26)

Lightweight visual-regression scaffolding. **Not required in CI.**

## Status: P26 not complete

Do **not** claim screenshot automation or P26 complete. Facts today:

- **Zero** PNG goldens under `screenshots/baselines/` (gitkeep only).
- Roborazzi **spike only** (PR5): `:feature:home` has `AddRssFeedDialogRoborazziSpikeTest` (Robolectric + `captureRoboImage`) — it writes ephemeral images under `feature/home/build/outputs/roborazzi/` locally and is **not** a CI gate. No compare/verify goldens are checked in.
- CI does **not** run image diffs.

What does run:

1. **Composition smoke** in `:feature:home` androidTest (`AddRssFeedDialogScreenshotStubTest`, `ResetAnalyticsDialogUiTest`) — asserts tagged dialog controls. Uses **testTags only** (no `onRoot()` — AlertDialog has multiple Compose roots).
2. Golden path **reserved** under `screenshots/baselines/` for a future Roborazzi adoption (record/compare tasks + committed PNGs).

### Roborazzi spike notes (AGP 9.3)

- Plugin `io.github.takahirom.roborazzi` **1.56.0+** applies and `:feature:home:testDebugUnitTest` can capture a Compose dialog via Robolectric (`AddRssFeedDialogRoborazziSpikeTest`).
- Blockers before P26 “done”: no `recordRoborazzi*` / `compareRoborazzi*` CI wiring, no committed baselines under `screenshots/baselines/`, dialog multi-root still prefers tagged nodes over full-dialog goldens.

## Layout

```
screenshots/baselines/     # PNG goldens (gitkeep until first capture)
docs/screenshots/          # this guide
```

Suggested naming when goldens exist:

```
screenshots/baselines/add_rss_feed_dialog.png
screenshots/baselines/home_settings_entry.png
```

## Capture (local, until Roborazzi CI)

1. Install a debug build or run the Roborazzi spike test:
   ```bash
   ./gradlew :feature:home:testDebugUnitTest --tests '*AddRssFeedDialogRoborazziSpikeTest'
   ```
   Outputs land under `feature/home/build/outputs/roborazzi/` (ephemeral).
2. For device captures: install debug on a fixed-density emulator, capture manually, drop PNGs into `screenshots/baselines/` when intentional.

Avoid relying on `onRoot()` for dialogs — prefer tagged nodes or Roborazzi once goldens are adopted.

## Compare

Until a tool is adopted in CI:

- Diff PNGs in PR review, or
- Use `git diff` / an image diff viewer locally.

Prefer **Roborazzi** (JVM, Compose-friendly) over Papyrus when goldens are wired. Keep committed goldens in `screenshots/baselines/`.

## Gradle

No screenshot Gradle **CI** task is wired yet. Local spike uses Roborazzi plugin on `:feature:home` only.

## Related

- Compose UI tests: `docs/TESTING.md` → androidTest
- Composition smoke: `feature/home/.../AddRssFeedDialogScreenshotStubTest.kt` (tags only; not a golden)
- Roborazzi spike: `feature/home/.../AddRssFeedDialogRoborazziSpikeTest.kt` (local capture only)

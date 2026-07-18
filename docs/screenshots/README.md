# Screenshot baselines (B7 / P26)

Lightweight visual-regression scaffolding. **Not required in CI.**

## Status: P26 not complete

Do **not** claim screenshot automation or P26 complete. Facts today:

- **Zero** PNG goldens under `screenshots/baselines/` (gitkeep only).
- Roborazzi (JVM Compose screenshots) is **not** wired on AGP 9.
- CI does **not** run image diffs.

What does run:

1. **Composition smoke** in `:feature:home` androidTest (`AddRssFeedDialogScreenshotStubTest`) — asserts tagged dialog controls. Uses **testTags only** (no `onRoot()` — AlertDialog has multiple Compose roots).
2. Golden path **reserved** under `screenshots/baselines/` for a future Roborazzi (or manual PNG) adoption.

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

## Capture (local, until Roborazzi)

1. Install a debug build on an emulator/device with a fixed density (e.g. Pixel 6 API 34, xxhdpi).
2. Capture the Add RSS dialog (or other target UI) manually from the running app at a fixed state.
3. Drop PNGs into `screenshots/baselines/` and commit when intentional.

Avoid relying on `onRoot()` for dialogs — prefer tagged nodes or a dedicated screenshot tool once Roborazzi is enabled.

## Compare

Until a tool is adopted:

- Diff PNGs in PR review, or
- Use `git diff` / an image diff viewer locally.

Prefer **Roborazzi** (JVM, Compose-friendly) over Papyrus when the AGP/Compose stack is ready. Keep goldens in `screenshots/baselines/`.

## Gradle

No screenshot Gradle task is wired yet (keeps CI green). A future Roborazzi task could update goldens under `screenshots/baselines/`.

## Related

- Compose UI tests: `docs/TESTING.md` → androidTest
- Composition smoke: `feature/home/.../AddRssFeedDialogScreenshotStubTest.kt` (tags only; not a golden)

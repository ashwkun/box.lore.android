# Screenshot baselines (B7 / P26)

Lightweight visual-regression scaffolding. **Not required in CI.**

## Status: staged Roborazzi adoption

Roborazzi (JVM Compose screenshots) is **not** wired on AGP 9 yet — plugin/engine setup is heavy for this pass. Until then:

1. **Composition smoke** runs in CI via `:feature:home` androidTest (`AddRssFeedDialogScreenshotStubTest`) — asserts tagged dialog controls compose (not `@Ignore`).
2. **Golden path reserved** under `screenshots/baselines/` for future Roborazzi (or manual PNG) captures.
3. When adopting Roborazzi, keep goldens in `screenshots/baselines/` so paths stay stable.

## Layout

```
screenshots/baselines/     # PNG goldens (gitkeep until first capture)
docs/screenshots/          # this guide
```

Suggested naming:

```
screenshots/baselines/add_rss_feed_dialog.png
screenshots/baselines/home_settings_entry.png
```

## Capture (local, until Roborazzi)

1. Install a debug build on an emulator/device with a fixed density (e.g. Pixel 6 API 34, xxhdpi).
2. Either:
   - Extend `AddRssFeedDialogScreenshotStubTest` temporarily with
     `composeRule.onRoot().captureToImage()` and write PNG under `screenshots/baselines/`, **or**
   - Capture manually from the running app at the same UI state.
3. Drop PNGs into `screenshots/baselines/` and commit when intentional.

## Compare

Until a tool is adopted:

- Diff PNGs in PR review, or
- Use `git diff` / an image diff viewer locally.

Prefer **Roborazzi** (JVM, Compose-friendly) over Papyrus when the AGP/Compose stack is ready. Keep goldens in `screenshots/baselines/`.

## Gradle

No screenshot Gradle task is wired yet (keeps CI green). A future Roborazzi task could update goldens under `screenshots/baselines/`.

## Related

- Compose UI tests: `docs/TESTING.md` → androidTest
- Smoke test: `feature/home/.../AddRssFeedDialogScreenshotStubTest.kt` (runs; not ignored)

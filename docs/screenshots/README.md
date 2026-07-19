# Screenshot baselines

This document describes the committed Roborazzi goldens and the visual-regression reference path. `:feature:home:verifyRoborazziDebug` runs in merge CI.

## Current coverage

`:feature:home` hosts JVM Roborazzi golden tests in `SettingsGoldenRoborazziTest`, and their committed baselines live under `screenshots/baselines/`:

```text
screenshots/baselines/add_rss_feed_dialog.png
screenshots/baselines/reset_analytics_dialog.png
screenshots/baselines/downloads_settings_page.png
```

Home instrumented tests additionally verify dialog composition through stable Compose test tags.

## Layout

```text
screenshots/baselines/     # committed PNG goldens (Roborazzi output dir)
docs/screenshots/          # this reference document
```

The output directory and file naming are configured in `feature/home/build.gradle.kts` (`roborazzi { outputDir = screenshots/baselines }`) and `gradle.properties` (`roborazzi.record.filePathStrategy=relativePathFromRoborazziContextOutputDirectory`), so the short names passed to `captureRoboImage()` resolve directly under `screenshots/baselines/`.

## Capture

Regenerate the goldens from the repository root and commit any intended changes:

```bash
./gradlew :feature:home:recordRoborazziDebug
```

Goldens are captured with a fixed `lightColorScheme()` so they stay deterministic across machines. Prefer tagged-node assertions for dialogs because Compose `AlertDialog` can produce multiple roots.

## Compare

```bash
./gradlew :feature:home:verifyRoborazziDebug
```

Verify re-renders each screen and compares it against the committed baseline; mismatches write `*_compare.png` under `feature/home/build/outputs/roborazzi/` and fail the task.

## Gradle

`:feature:home:verifyRoborazziDebug` is wired into `.github/workflows/unit-tests.yml` (merge CI). Roborazzi is applied through the `roborazzi` plugin alias in `feature/home/build.gradle.kts`.

## See also

- [`docs/TESTING.md`](../TESTING.md)
- [`feature/home/README.md`](../../feature/home/README.md)
- `feature/home/src/androidTest/java/cx/aswin/boxlore/feature/home/settings/`

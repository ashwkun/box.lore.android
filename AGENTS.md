# AGENTS.md

## Cursor Cloud specific instructions

boxlore is a single-product **Android app** (Kotlin, multi-module Gradle: `:app`, `:core:*`, `:feature:*`). There is no server to run — "running" the product means building the debug APK and launching it on an Android emulator. The "smart" backend (search/recommendations/briefing) is a private external service and is not in this repo; without it the app still works as a standard podcast client (offline library, RSS, OPML).

### Environment (already provisioned in the snapshot)
- JDK 17 at `/usr/lib/jvm/java-17-openjdk-amd64` (the repo requires 17; the base image also ships JDK 21 — do not let Gradle pick 21).
- Android SDK at `~/Android/Sdk` with `platform-tools`, `build-tools;36.0.0`, `platforms;android-36`, `emulator`, and system images `android-34;google_apis;x86_64` and `android-34;default;x86_64`.
- `~/.bashrc` exports `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `PATH`. New non-login shells may not source it — if `java -version` shows 21 or `sdkmanager` is missing, `source ~/.bashrc` first.
- The update script runs `scripts/ci/write-cloud-agent-local-config.sh`, which writes `local.properties` (`sdk.dir`) and a non-secret stub `app/google-services.json`. Both are gitignored and are NOT secrets.

### Build / test / lint (no device needed)
Standard commands, all via the Gradle wrapper (see also `.github/workflows/unit-tests.yml`):
- Build debug APK: `./gradlew assembleDebug` (first run downloads Gradle 9.6.1 + deps, ~4–5 min).
- Unit tests: `./gradlew testDebugUnitTest --continue`
- Lint: `./gradlew detekt ktlintCheck lintDebug`
- Coverage floor / screenshots / dep guard: `./gradlew :koverVerifyMerged :feature:home:verifyRoborazziDebug :app:dependencyGuard`
- Roborazzi renders real Compose screens on the JVM (no emulator): `./gradlew :feature:home:recordRoborazziDebug` → PNGs in `feature/home/build/intermediates/roborazzi/`.

### Running the app on the emulator (non-obvious gotchas)
- There is **no `/dev/kvm`** here, so the emulator runs with software CPU emulation (`-no-accel -gpu swiftshader_indirect -no-window`). It is usable but very slow: boot takes several minutes and the starved CPU triggers frequent system-wide "System UI / Process system isn't responding" ANR dialogs. These are environment slowness, not app bugs — dismiss with "Wait" and give screens 60–90s to settle.
- Prefer the lighter **AOSP image** (`system-images;android-34;default;x86_64`, AVD `boxlore_aosp`) over `google_apis`: Play Services background work on the google_apis image makes ANRs much worse.
- After install, run `adb shell cmd package compile -m speed -f cx.aswin.boxlore` to AOT-compile — this removes the runtime class-verification overhead that otherwise causes a playback-service ANR on the slow CPU.
- The launcher activity is `cx.aswin.boxlore/.MainActivity`.
- **The app requires a syntactically valid `BOXLORE_API_BASE_URL` to launch.** `PodcastRepository` eagerly builds a Retrofit client from it, so an empty value (the default in the stub config) crashes at startup with `IllegalArgumentException: Expected URL scheme 'http' or 'https'`. To launch the UI offline, add to `local.properties`: `BOXLORE_API_BASE_URL=https://api.boxlore.example` (and optionally `BOXLORE_PUBLIC_KEY=demo-placeholder-key`), then rebuild. With a placeholder URL, backend-dependent screens (Explore search, Lore/curiosity, briefing) show graceful "failed to load" states; offline features (onboarding, Library/Downloads, RSS/OPML) work normally. For real backend functionality, set the private `BOXLORE_API_BASE_URL`/`BOXLORE_PUBLIC_KEY` as secrets.

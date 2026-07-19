# AGENTS.md — Boxlore agent contract

Short entrypoint for Cursor / Codex / cloud agents. Prefer this over long essays.

## Non-negotiables

- Read [`ARCHITECTURE.md`](ARCHITECTURE.md) + the touched module `README.md` before editing; **ARCHITECTURE wins** on conflicts.
- No feature→feature deps/imports; no PostHog in features (use `:core:analytics`); no Hilt/Koin/MockK.
- Never break identity/storage contracts (`applicationId`, DataStore `user_preferences`, Room names, `rss:` IDs, single `PlaybackRepository`, smart-queue refill ownership). See ARCHITECTURE identity table.
- Update the touched module README in the same change (template: [`docs/MODULE_README_TEMPLATE.md`](docs/MODULE_README_TEMPLATE.md)).
- Extend JVM `src/test` for touched logic; **bug fix ⇒ regression test** (same failure mode app-wide when shared). No Compose `androidTest` / emulator CI.
- Commit / push / open a PR **only when the user asks**. Conventional Commits titles.
- Every PR needs **exactly one** user-impact label (`user-impact-high|medium|low` or `no-user-impact`); optional `backend-change`. Changelog / README upcoming workflows depend on these — see [`.cursor/rules/pr-impact-labels.mdc`](.cursor/rules/pr-impact-labels.mdc).
- Merge via **Merge when ready** (merge queue). Unit suite runs on every PR push (cancels prior in-progress runs) and again in the queue; use `[skip unit]` / `[skip changelog]` only when appropriate. No `merge-ci`.
- SonarCloud: **0 new-code issues** (quality gate must fail if any new issue). Resolve all CodeRabbit review threads before merge.
- Never commit secrets (`local.properties`, `.env`, keystores, `google-services.json`).
- Do **not** hand-edit `CHANGELOG.md` or README Upcoming — `changelog-on-merge` owns that.
- **boxlore-only:** do not change other `boxcreate` repos or org-wide bot settings unless asked. Keep proxy/backend internals out of public Android PR text.
- Product name in user-facing copy is **boxlore** (all lowercase), not “Boxlore” / “BoxLore”.
- Cards / panels: solid Material 3 surfaces only — no glassmorphism / translucent card backgrounds.

## Source of truth (priority order)

1. Latest user message (explicit overrides win)
2. This file + [`.cursor/rules/*.mdc`](.cursor/rules/)
3. [`ARCHITECTURE.md`](ARCHITECTURE.md)
4. Touched module `README.md`
5. [`docs/TESTING.md`](docs/TESTING.md) and [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md)

## Where to look

| Topic | Doc |
| :--- | :--- |
| Module graph, DI, identity | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| Unit / Kover / Konsist / CI | [`docs/TESTING.md`](docs/TESTING.md) |
| Always-on agent rules | [`.cursor/rules/`](.cursor/rules/) |
| PR body / merge queue checklist | [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md) |
| Impact labels + merge gate | [`.cursor/rules/pr-impact-labels.mdc`](.cursor/rules/pr-impact-labels.mdc) |

## Large refactors / P1 batches (hard stop)

For large refactors or multi-issue P1 batches: **propose in plain English → wait for explicit user OK → then branch and code**. Do not start implementation on approval-shaped silence. Details: [`.cursor/rules/p1-workflow.mdc`](.cursor/rules/p1-workflow.mdc).

## Default local loop

After UI / app-behavior changes: `./gradlew installDebug` on a connected device when available. Do not run device automation (taps, screenshots, layout dumps) unless the user asks. Gradle must use the real `GRADLE_USER_HOME` — see [`.cursor/rules/gradle-no-sandbox.mdc`](.cursor/rules/gradle-no-sandbox.mdc).

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

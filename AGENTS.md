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
- Merge via **Merge when ready** (merge queue). Unit suite runs there; use `[skip unit]` / `[skip changelog]` only when appropriate. No `merge-ci`.
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

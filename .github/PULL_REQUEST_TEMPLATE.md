## Title (required)

Use Conventional Commits. Examples from this repo:

- `feat(scope): short description`
- `fix(scope): short description`
- `chore: short description`
- `release: vX.Y.Z [skip changelog]`
- `docs: … [skip unit]` / `chore: … [skip unit]` — no-ops merge-queue unit suite (still reports green). **Only** for docs/chore with no logic risk.

Do **not** use sentence-case titles without a type prefix (e.g. avoid `Polish the announcement dialog`).

## Merge queue (required before merge)

Unit tests, detekt, ktlint, Roborazzi, and the Kover coverage gate run **only in the merge queue** (plus optional Actions → Run workflow). They do **not** run on every PR push.

Master uses a **merge queue**. Required checks before merge:

1. **`testDebugUnitTest`** — runs when the PR enters the merge queue (put `[skip unit]` in the PR title to no-op for safe docs/chore only; check still reports green)
2. **`SonarCloud Code Analysis`** — quality gate must pass (**0 new-code issues**)
3. **`CodeRabbit`** — review must be green; resolve all review threads

1. Open the PR and iterate as usual (Sonar + CodeRabbit still run).
2. Resolve CodeRabbit threads; wait for SonarCloud.
3. Use **Merge when ready** — the PR enters the merge queue (squash), which runs **`testDebugUnitTest`**.
4. Optional: Actions → Run workflow (`Unit Tests`) for a manual full gate.

Scheduled bots push to `master` via the **boxlore-master-pusher** GitHub App (ruleset Integration bypass).

## Summary

<!-- What changed and why. Release notes / changelog bullets are derived from this — be specific. -->

-

## Motivation

<!-- Why this change exists. What problem or gap does it address for listeners or maintainers? -->

-

## What changed

<!-- Concrete product / code changes. Prefer bullets over vague summaries. -->

-

## Behavior & compatibility

<!-- User-visible behavior before/after. Call out FCM/API/payload compatibility, defaults, and anything older clients still rely on. -->

-

## Impact (required)

### User impact — pick **exactly one**

| Label | Use when |
|:--|:--|
| `user-impact-high` | Listeners clearly notice (player, search, downloads, onboarding, major UX) |
| `user-impact-medium` | Noticeable but not headline (polish, secondary flows) |
| `user-impact-low` | Minor user-facing tweak |
| `no-user-impact` | CI, docs, tooling, internal-only — no listener-facing change |

- [ ] `user-impact-high`
- [ ] `user-impact-medium`
- [ ] `user-impact-low`
- [ ] `no-user-impact`

### Listener impact — **required when** `user-impact-high` or `user-impact-medium`

<!-- Write this for a listener, not an engineer. What is different in their day-to-day use of boxlore after this ships? -->
<!-- Skip only for `user-impact-low` or `no-user-impact`. -->

**What changes in the user’s life:**

-

### Backend — optional, **pairable** with any user-impact level

| Label | Use when |
|:--|:--|
| `backend-change` | Touches server / proxy / infra (can combine with high/medium/low/none) |

- [ ] `backend-change`

Examples: `user-impact-high` + `backend-change`, or `no-user-impact` + `backend-change`, or just `user-impact-medium`.

Add impact labels on the PR (`gh pr edit <n> --add-label user-impact-high --add-label backend-change`).

## Test plan

<!-- Checklist of concrete verification steps for this PR. Mark items done before merge when possible. -->

- [ ] Built / installed locally (`./gradlew installDebug`) when UI or app behavior changed
- [ ] Manual checks for the user-visible paths touched by this PR
- [ ] Required checks are green in the merge queue before merge completes
- [ ]

## Notes (optional)

<!-- Screenshots, rollout risks, follow-ups, related deploys (e.g. admin hosting), out of scope. -->

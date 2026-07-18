## Title (required)

Use Conventional Commits. Examples from this repo:

- `feat(scope): short description`
- `fix(scope): short description`
- `chore: short description`
- `release: vX.Y.Z [skip changelog]`

Do **not** use sentence-case titles without a type prefix (e.g. avoid `Polish the announcement dialog`).

## Merge CI (required before merge)

Unit tests, instrumented tests, detekt, ktlint, and the Kover coverage gate do **not** run on every push.

1. Open the PR and iterate as usual (no expensive CI yet).
2. When the PR is ready to merge, add the **`merge-ci`** label.
3. Wait until these required checks are green:
   - `testDebugUnitTest`
   - `feature:home connectedDebugAndroidTest`
4. Then merge.

```bash
gh pr edit <n> --add-label merge-ci
```

Do **not** leave `merge-ci` on while you are still force-pushing WIP — every later push while the label is present re-runs the full suite.

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
- [ ] Added **`merge-ci`** when ready, and required checks are green before merge
- [ ]

## Notes (optional)

<!-- Screenshots, rollout risks, follow-ups, related deploys (e.g. admin hosting), out of scope. -->

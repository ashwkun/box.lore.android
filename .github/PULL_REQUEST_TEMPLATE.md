## Summary

<!-- What changed and why (1–3 bullets). Keep it clear — release notes are derived from this. -->

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

### Backend — optional, **pairable** with any user-impact level

| Label | Use when |
|:--|:--|
| `backend-change` | Touches server / proxy / infra (can combine with high/medium/low/none) |

- [ ] `backend-change`

Examples: `user-impact-high` + `backend-change`, or `no-user-impact` + `backend-change`, or just `user-impact-medium`.

Add the labels on the PR (`gh pr edit <n> --add-label user-impact-high --add-label backend-change`).

## Test plan

- [ ] Built / installed locally (`./gradlew installDebug`) when UI or app behavior changed
- [ ] Manual checks for the user-visible paths touched by this PR

## Notes (optional)

<!-- Screenshots, rollout risks, follow-ups. -->

#!/usr/bin/env bash
# Configure master merge queue + required test checks for ashwkun/boxlore.
#
# Requires a token with admin:repo (or repo admin) — the Cursor cloud agent
# token cannot do this. Run locally as the repo owner:
#
#   gh auth login   # if needed, as ashwkun
#   ./scripts/ci/configure-master-merge-queue.sh
#
# Design:
# - PR merges into master must go through the merge queue
# - Unit Tests + Instrumented Tests are required for the queue
# - GitHub Actions (data sync bots) and the repo owner can bypass so
#   direct [skip ci] data pushes to master keep working
set -euo pipefail

OWNER="${OWNER:-ashwkun}"
REPO="${REPO:-boxlore}"
RULESET_NAME="${RULESET_NAME:-master-merge-queue}"

# https://api.github.com/apps/github-actions
GITHUB_ACTIONS_APP_ID=15368
# Repo owner (ashwkun) — keeps emergency direct pushes possible
OWNER_USER_ID=167635885

# Job `name:` values from the workflows (GitHub check contexts)
UNIT_CHECK="testDebugUnitTest"
INSTRUMENTED_CHECK="feature:home connectedDebugAndroidTest"

echo "Checking auth can administer ${OWNER}/${REPO}..."
if ! gh api "repos/${OWNER}/${REPO}" --jq '.permissions.admin' | grep -qx true; then
  echo "error: current gh token lacks admin on ${OWNER}/${REPO}" >&2
  echo "       Sign in as the owner: gh auth login" >&2
  exit 1
fi

existing_id="$(
  gh api "repos/${OWNER}/${REPO}/rulesets" \
    | jq -r --arg name "$RULESET_NAME" '.[] | select(.name == $name) | .id' \
    | head -n1
)"
if [[ "${existing_id}" == "null" ]]; then
  existing_id=""
fi

payload="$(cat <<EOF
{
  "name": "${RULESET_NAME}",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": ["refs/heads/master"],
      "exclude": []
    }
  },
  "bypass_actors": [
    {
      "actor_id": ${GITHUB_ACTIONS_APP_ID},
      "actor_type": "Integration",
      "bypass_mode": "always"
    },
    {
      "actor_id": ${OWNER_USER_ID},
      "actor_type": "User",
      "bypass_mode": "always"
    }
  ],
  "rules": [
    {
      "type": "merge_queue",
      "parameters": {
        "merge_method": "SQUASH",
        "max_entries_to_build": 5,
        "min_entries_to_merge": 1,
        "max_entries_to_merge": 5,
        "min_entries_to_merge_wait_minutes": 0,
        "check_response_timeout_minutes": 90,
        "grouping_strategy": "ALLGREEN"
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "do_not_enforce_on_create": true,
        "required_status_checks": [
          { "context": "${UNIT_CHECK}" },
          { "context": "${INSTRUMENTED_CHECK}" }
        ]
      }
    }
  ]
}
EOF
)"

if [[ -n "${existing_id}" ]]; then
  echo "Updating existing ruleset id=${existing_id}..."
  echo "${payload}" | gh api --method PUT "repos/${OWNER}/${REPO}/rulesets/${existing_id}" --input -
else
  echo "Creating ruleset ${RULESET_NAME}..."
  echo "${payload}" | gh api --method POST "repos/${OWNER}/${REPO}/rulesets" --input -
fi

echo
echo "Configured. Verify:"
echo "  https://github.com/${OWNER}/${REPO}/settings/rules"
echo "  gh api repos/${OWNER}/${REPO}/rulesets --jq '.[].name'"
echo
echo "PR merges → merge queue → ${UNIT_CHECK} + ${INSTRUMENTED_CHECK}."
echo "GitHub Actions + owner bypass keep direct master data pushes working."

#!/usr/bin/env bash
# Configure master required merge-gate checks for ashwkun/boxlore.
#
# Requires a token with admin:repo (or repo admin). Run locally as the owner:
#
#   gh auth login   # if needed, as ashwkun
#   ./scripts/ci/configure-master-merge-queue.sh
#
# Design:
# - PR merges into master require Unit + Instrumented status checks
# - CI only runs when the PR has the `merge-ci` label (see workflows) — not on
#   every intermediate push
# - No "require pull request" / merge_queue rule: this is a user-owned repo and
#   merge_queue is rejected by the API; data bots must keep pushing to master
# - Repo owner can bypass the ruleset for emergencies
set -euo pipefail

OWNER="${OWNER:-ashwkun}"
REPO="${REPO:-boxlore}"
RULESET_NAME="${RULESET_NAME:-master-required-checks}"

# Repo owner (ashwkun) — emergency ruleset bypass
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

# Ensure the merge-gate label exists (workflows key off this name).
echo "Ensuring label merge-ci..."
gh label create merge-ci \
  --repo "${OWNER}/${REPO}" \
  --description "Run merge-gate CI (unit + instrumented). Add when ready to merge." \
  --color "0E8A16" \
  --force

# Drop classic branch protection if present — it blocks non-admin direct pushes
# (breaks data bots). Rulesets below do not require a PR for direct pushes.
if gh api "repos/${OWNER}/${REPO}/branches/master/protection" >/dev/null 2>&1; then
  echo "Removing classic branch protection on master (bots need direct push)..."
  gh api --method DELETE "repos/${OWNER}/${REPO}/branches/master/protection"
fi

# Remove obsolete merge-queue ruleset name if left from earlier attempts.
old_mq_id="$(
  gh api "repos/${OWNER}/${REPO}/rulesets" \
    | jq -r '.[] | select(.name == "master-merge-queue") | .id' \
    | head -n1
)"
if [[ -n "${old_mq_id}" && "${old_mq_id}" != "null" ]]; then
  echo "Deleting obsolete ruleset master-merge-queue id=${old_mq_id}..."
  gh api --method DELETE "repos/${OWNER}/${REPO}/rulesets/${old_mq_id}"
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
      "actor_id": ${OWNER_USER_ID},
      "actor_type": "User",
      "bypass_mode": "always"
    }
  ],
  "rules": [
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
echo "Workflow:"
echo "  1. Open / update PR as usual (no Unit/Instrumented CI yet)"
echo "  2. Add label \`merge-ci\` when ready → Unit + Instrumented run"
echo "  3. Merge only after both checks are green"
echo "Direct master pushes (bots / chores) stay outside these gates."
echo "Owner bypass remains for emergency ruleset overrides."

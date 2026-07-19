#!/usr/bin/env bash
# Architecture boundary: feature modules must not import or call PostHog directly.
# Capture goes through :core:analytics (AnalyticsHelper / Analytics). :app may
# initialize the SDK and host survey UI that imports PostHog survey types.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FEATURE_ROOT="$ROOT/feature"

if [[ ! -d "$FEATURE_ROOT" ]]; then
  echo "FAIL: missing feature/ directory at $FEATURE_ROOT"
  exit 1
fi

# Match PostHog SDK imports and direct capture call sites under feature/*/src/main.
PATTERN='(import[[:space:]]+com\.posthog|com\.posthog\.PostHog|PostHog\.capture)'

failed=0
while IFS= read -r -d '' path; do
  if grep -nE "$PATTERN" "$path" >/dev/null 2>&1; then
    echo "FAIL: $path uses PostHog directly (use AnalyticsHelper / :core:analytics)"
    grep -nE "$PATTERN" "$path" || true
    failed=1
  fi
done < <(find "$FEATURE_ROOT" -type f -path '*/src/main/*' \( -name '*.kt' -o -name '*.java' \) -print0)

if [[ "$failed" -ne 0 ]]; then
  echo "Architecture boundary violated: zero feature-module PostHog.capture / com.posthog imports."
  exit 1
fi

echo "OK: feature modules do not import or capture via PostHog directly."

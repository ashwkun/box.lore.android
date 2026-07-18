#!/usr/bin/env bash
# Architecture boundary: Home / Info feature ViewModels and assemblers must not
# inject BoxLoreDatabase (use LocalCatalogPort / EpisodeOfflineLookupPort instead).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PATTERN='BoxLoreDatabase'
TARGETS=(
  "$ROOT/feature/home/src/main/java/cx/aswin/boxlore/feature/home/HomeViewModel.kt"
  "$ROOT/feature/home/src/main/java/cx/aswin/boxlore/feature/home/HomeViewModelAssembler.kt"
  "$ROOT/feature/home/src/main/java/cx/aswin/boxlore/feature/home/HomeScreen.kt"
  "$ROOT/feature/info/src/main/java/cx/aswin/boxlore/feature/info/PodcastInfoViewModel.kt"
  "$ROOT/feature/info/src/main/java/cx/aswin/boxlore/feature/info/EpisodeInfoViewModel.kt"
  "$ROOT/feature/info/src/main/java/cx/aswin/boxlore/feature/info/InfoViewModelAssembler.kt"
)

failed=0
for path in "${TARGETS[@]}"; do
  if grep -nE "$PATTERN" "$path" >/dev/null 2>&1; then
    echo "FAIL: $path still references $PATTERN"
    grep -nE "$PATTERN" "$path" || true
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  echo "Architecture boundary violated: inject LocalCatalogPort / EpisodeOfflineLookupPort instead of BoxLoreDatabase."
  exit 1
fi

echo "OK: Home/Info ViewModels do not reference BoxLoreDatabase."

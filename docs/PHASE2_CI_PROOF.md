# Phase 2 CI proof

Baseline verification for Boxlore Android Phase 2 (Final adherence).

- Master tip at proof time is recorded by the PR that lands this file.
- Required gate: GitHub Actions **Unit Tests** + **Instrumented** with the `merge-ci` label (not a master ruleset).
- Cloud agents use [`scripts/ci/write-cloud-agent-local-config.sh`](../scripts/ci/write-cloud-agent-local-config.sh); Actions write their own google-services stub.

If this PR’s merge-ci checks are green, Phase 2 may proceed to PR1 (critical bugs).

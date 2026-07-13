#!/usr/bin/env python3
"""Prepare and validate reviewable boxlore Android releases."""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import update_changelog

APP_GRADLE_PATH = Path("app/build.gradle.kts")
CHANGELOG_PATH = Path("CHANGELOG.md")
README_PATH = Path("README.md")
SEMVER_RE = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
VERSION_CODE_RE = re.compile(r"^(\s*versionCode\s*=\s*)(\d+)(\s*)$", re.MULTILINE)
VERSION_NAME_RE = re.compile(
    r'^(\s*versionName\s*=\s*")([^"]+)("\s*)$',
    re.MULTILINE,
)
CHANGELOG_VERSION_RE = re.compile(
    r"^## \[v((?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*))\]"
    r"\s*-\s*(\d{4}-\d{2}-\d{2})\s*$",
    re.MULTILINE,
)
README_VERSION_RE = re.compile(
    r"<summary><b>🎉 What's New \(v"
    r"((?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*))\)"
    r"\s*-\s*(\d{4}-\d{2}-\d{2})</b></summary>"
)
README_APK_URL_RE = re.compile(
    r"https://github\.com/[^/\"'\s]+/[^/\"'\s]+/"
    r"releases/latest/download/[^\"'\s]+\.apk"
)
UPCOMING_DETAILS_RE = re.compile(
    r"<details(?:\s+open)?>\s*"
    r"<summary><b>🔮 Upcoming in the Next Release</b></summary>"
    r"(.*?)</details>",
    re.DOTALL,
)
EXPECTED_FILES = {
    "CHANGELOG.md",
    "README.md",
    "app/build.gradle.kts",
}
GITHUB_RETRYABLE_STATUS = {429, 500, 502, 503, 504}
GITHUB_MAX_RETRIES = 5


@dataclass(frozen=True)
class AppVersion:
    name: str
    code: int

    @property
    def tag(self) -> str:
        return f"v{self.name}"

    @property
    def apk_asset(self) -> str:
        return f"boxlore-{self.tag}.apk"

    @property
    def aab_asset(self) -> str:
        return f"boxlore-{self.tag}.aab"


def fail(message: str) -> None:
    raise ValueError(message)


def require_file(path: Path) -> str:
    if not path.is_file():
        fail(f"Required file does not exist: {path}")
    return path.read_text(encoding="utf-8")


def read_app_version(content: str | None = None) -> AppVersion:
    source = content if content is not None else require_file(APP_GRADLE_PATH)
    code_matches = list(VERSION_CODE_RE.finditer(source))
    name_matches = list(VERSION_NAME_RE.finditer(source))
    if len(code_matches) != 1 or len(name_matches) != 1:
        fail("Expected exactly one versionCode and versionName in app/build.gradle.kts")

    name = name_matches[0].group(2)
    if not SEMVER_RE.fullmatch(name):
        fail(f"Gradle versionName is not strict semantic versioning: {name}")
    return AppVersion(name=name, code=int(code_matches[0].group(2)))


def bump_version(current: AppVersion, bump: str) -> AppVersion:
    match = SEMVER_RE.fullmatch(current.name)
    if not match:
        fail(f"Cannot bump malformed version: {current.name}")
    major, minor, patch = (int(part) for part in match.groups())
    if bump == "patch":
        patch += 1
    elif bump == "minor":
        minor += 1
        patch = 0
    elif bump == "major":
        major += 1
        minor = 0
        patch = 0
    else:
        fail(f"Unsupported version bump: {bump}")
    return AppVersion(name=f"{major}.{minor}.{patch}", code=current.code + 1)


def latest_changelog_version(content: str | None = None) -> str:
    source = content if content is not None else require_file(CHANGELOG_PATH)
    match = CHANGELOG_VERSION_RE.search(source)
    if not match:
        fail("CHANGELOG.md has no versioned release after [Unreleased]")
    return match.group(1)


def latest_readme_version(content: str | None = None) -> str:
    source = content if content is not None else require_file(README_PATH)
    match = README_VERSION_RE.search(source)
    if not match:
        fail("README.md has no versioned What's New block")
    return match.group(1)


def unreleased_block(content: str) -> str:
    header = re.search(r"^## \[Unreleased\]\s*$", content, flags=re.MULTILINE)
    if not header:
        fail("CHANGELOG.md is missing the [Unreleased] header")
    next_version = re.search(r"^## \[", content[header.end() :], flags=re.MULTILINE)
    end = header.end() + next_version.start() if next_version else len(content)
    return content[header.end() : end]


def validate_baseline(current: AppVersion, latest_tag: str) -> None:
    expected_tag = current.tag
    if latest_tag != expected_tag:
        fail(
            "Version baseline mismatch: "
            f"Gradle is {current.name} ({current.code}) but GitHub Latest is {latest_tag}"
        )

    changelog_version = latest_changelog_version()
    readme_version = latest_readme_version()
    if changelog_version != current.name or readme_version != current.name:
        fail(
            "Version baseline mismatch: "
            f"Gradle={current.name}, CHANGELOG={changelog_version}, README={readme_version}"
        )


def github_request(
    repository: str,
    token: str,
    path: str,
    *,
    allow_missing: bool = False,
    method: str = "GET",
    payload: dict[str, object] | None = None,
) -> object | None:
    url = f"https://api.github.com/repos/{repository}/{path.lstrip('/')}"
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")

    last_error: urllib.error.HTTPError | urllib.error.URLError | None = None
    for attempt in range(1, GITHUB_MAX_RETRIES + 1):
        request = urllib.request.Request(
            url,
            data=data,
            method=method,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "User-Agent": "boxlore-release/1.0",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                if response.status == 204:
                    return None
                body = response.read().decode("utf-8")
                if not body:
                    return None
                return json.loads(body)
        except urllib.error.HTTPError as exc:
            last_error = exc
            if allow_missing and exc.code == 404:
                return None
            if exc.code in GITHUB_RETRYABLE_STATUS and attempt < GITHUB_MAX_RETRIES:
                delay = min(2**attempt, 30)
                print(
                    f"GitHub API {path} returned {exc.code}; "
                    f"retrying in {delay}s (attempt {attempt}/{GITHUB_MAX_RETRIES})",
                    file=sys.stderr,
                )
                time.sleep(delay)
                continue
            fail(f"GitHub API request failed for {path} with status {exc.code}")
        except urllib.error.URLError as exc:
            last_error = exc
            if attempt < GITHUB_MAX_RETRIES:
                delay = min(2**attempt, 30)
                print(
                    f"GitHub API {path} failed: {exc.reason}; "
                    f"retrying in {delay}s (attempt {attempt}/{GITHUB_MAX_RETRIES})",
                    file=sys.stderr,
                )
                time.sleep(delay)
                continue
            fail(f"GitHub API request failed for {path}: {exc.reason}")

    if isinstance(last_error, urllib.error.HTTPError):
        fail(
            f"GitHub API request failed for {path} with status {last_error.code}"
        )
    if isinstance(last_error, urllib.error.URLError):
        fail(f"GitHub API request failed for {path}: {last_error.reason}")
    return None


def github_delete(repository: str, token: str, path: str) -> None:
    github_request(repository, token, path, method="DELETE")


def release_branch_name(target: AppVersion) -> str:
    return f"release/{target.tag}"


def release_target_artifacts(
    repository: str,
    token: str,
    target: AppVersion,
) -> dict[str, object | None]:
    encoded_tag = urllib.parse.quote(f"tags/{target.tag}", safe="/")
    encoded_branch = urllib.parse.quote(
        f"heads/{release_branch_name(target)}",
        safe="/",
    )
    return {
        "tag": github_request(
            repository,
            token,
            f"git/ref/{encoded_tag}",
            allow_missing=True,
        ),
        "branch": github_request(
            repository,
            token,
            f"git/ref/{encoded_branch}",
            allow_missing=True,
        ),
        "release": github_request(
            repository,
            token,
            f"releases/tags/{urllib.parse.quote(target.tag, safe='')}",
            allow_missing=True,
        ),
    }


def find_open_release_pull_request(
    repository: str,
    token: str,
    branch: str,
) -> dict[str, object] | None:
    owner, _repo = repository.split("/", 1)
    query = urllib.parse.urlencode({"head": f"{owner}:{branch}", "state": "open"})
    pulls = github_request(repository, token, f"pulls?{query}")
    if not isinstance(pulls, list) or not pulls:
        return None
    pull = pulls[0]
    return pull if isinstance(pull, dict) else None


def cleanup_stale_release_target(
    repository: str,
    token: str,
    target: AppVersion,
    *,
    abort_if_open_pr: bool = False,
) -> list[str]:
    branch = release_branch_name(target)
    artifacts = release_target_artifacts(repository, token, target)
    if artifacts["tag"] is not None or artifacts["release"] is not None:
        return []

    open_pr = find_open_release_pull_request(repository, token, branch)
    if open_pr is not None:
        number = open_pr.get("number")
        if abort_if_open_pr:
            fail(
                f"{target.tag} already has an open release PR "
                f"(#{number}); close or merge it before preparing again"
            )
        return []

    actions: list[str] = []
    if artifacts["branch"] is not None:
        encoded_branch = urllib.parse.quote(f"heads/{branch}", safe="/")
        github_delete(repository, token, f"git/refs/{encoded_branch}")
        actions.append(f"deleted stale branch {branch}")
    return actions


def ensure_remote_target_is_available(
    repository: str,
    token: str,
    target: AppVersion,
) -> None:
    for action in cleanup_stale_release_target(
        repository,
        token,
        target,
        abort_if_open_pr=True,
    ):
        print(action)

    artifacts = release_target_artifacts(repository, token, target)
    conflicts: list[str] = []
    if artifacts["tag"] is not None:
        conflicts.append("tag")
    if artifacts["branch"] is not None:
        conflicts.append("release branch")
    if artifacts["release"] is not None:
        conflicts.append("GitHub Release")
    if conflicts:
        fail(f"{target.tag} already has: {', '.join(conflicts)}")


def assert_remote_target_is_free(
    repository: str,
    token: str,
    target: AppVersion,
) -> None:
    ensure_remote_target_is_available(repository, token, target)


def pull_request_is_release_candidate(pull_request: dict[str, object]) -> bool:
    number = pull_request.get("number")
    title = str(pull_request.get("title") or "")
    base_ref = str((pull_request.get("base") or {}).get("ref") or "")
    head_ref = str((pull_request.get("head") or {}).get("ref") or "")
    return (
        number is not None
        and pull_request.get("merged_at") is not None
        and base_ref == "master"
        and "[skip changelog]" not in title
        and not head_ref.startswith("release/v")
    )


def pull_requests_between(
    repository: str,
    token: str,
    base_tag: str,
    head_sha: str,
) -> list[dict[str, object]]:
    base = urllib.parse.quote(base_tag, safe="")
    head = urllib.parse.quote(head_sha, safe="")
    comparison = github_request(repository, token, f"compare/{base}...{head}")
    if not isinstance(comparison, dict):
        fail("GitHub compare response was not an object")
    if comparison.get("status") not in {"ahead", "identical"}:
        fail(f"Release commit is not ahead of {base_tag}")

    commits = comparison.get("commits", [])
    if not isinstance(commits, list):
        fail("GitHub compare response did not include commits")
    total_commits = int(comparison.get("total_commits", len(commits)))
    if total_commits != len(commits):
        fail(
            f"Release range contains {total_commits} commits but GitHub returned "
            f"{len(commits)}; cut a smaller release range"
        )

    commit_shas = {
        str(commit["sha"])
        for commit in commits
        if isinstance(commit, dict) and commit.get("sha")
    }

    pull_requests: dict[int, dict[str, object]] = {}
    page = 1
    while page <= 20:
        query = urllib.parse.urlencode(
            {
                "state": "closed",
                "base": "master",
                "sort": "updated",
                "direction": "desc",
                "per_page": "100",
                "page": str(page),
            }
        )
        page_pulls = github_request(repository, token, f"pulls?{query}")
        if not isinstance(page_pulls, list) or not page_pulls:
            break

        matched_on_page = 0
        for pull_request in page_pulls:
            if not isinstance(pull_request, dict):
                continue
            merge_commit_sha = str(pull_request.get("merge_commit_sha") or "")
            if merge_commit_sha not in commit_shas:
                continue
            if not pull_request_is_release_candidate(pull_request):
                continue
            number = int(pull_request["number"])
            pull_requests[number] = pull_request
            matched_on_page += 1

        if matched_on_page == 0 and page > 1:
            break
        page += 1

    return sorted(
        pull_requests.values(),
        key=lambda item: (str(item.get("merged_at") or ""), int(item["number"])),
    )


def reconcile_changelog(
    repository: str,
    token: str,
    api_key: str,
    base_tag: str,
    head_sha: str,
) -> list[int]:
    pull_requests = pull_requests_between(repository, token, base_tag, head_sha)
    processed: list[int] = []

    for pull_request in pull_requests:
        number = int(pull_request["number"])
        current_unreleased = unreleased_block(require_file(CHANGELOG_PATH))
        if update_changelog._pr_already_present(current_unreleased, number):
            processed.append(number)
            continue

        changed = update_changelog.append_changelog(
            api_key,
            number,
            str(pull_request.get("title") or "").strip(),
            str(pull_request.get("body") or ""),
        )
        if not changed:
            fail(f"PR #{number} produced no changelog entry")
        processed.append(number)

    update_changelog.sync_changelog_unreleased(api_key)
    update_changelog.sync_readme_upcoming(api_key)

    final_unreleased = unreleased_block(require_file(CHANGELOG_PATH))
    missing = [
        number
        for number in processed
        if not update_changelog._pr_already_present(final_unreleased, number)
    ]
    if missing:
        fail(
            "Release reconciliation lost changelog coverage for PRs: "
            + ", ".join(f"#{number}" for number in missing)
        )
    return processed


def replace_gradle_version(
    content: str,
    current: AppVersion,
    target: AppVersion,
) -> str:
    if read_app_version(content) != current:
        fail("Gradle version changed while preparing the release")
    updated, code_count = VERSION_CODE_RE.subn(
        rf"\g<1>{target.code}\g<3>",
        content,
        count=1,
    )
    updated, name_count = VERSION_NAME_RE.subn(
        rf"\g<1>{target.name}\g<3>",
        updated,
        count=1,
    )
    if code_count != 1 or name_count != 1:
        fail("Could not update Gradle version fields exactly once")
    if read_app_version(updated) != target:
        fail("Gradle version update did not produce the requested target")
    return updated


def promote_changelog(content: str, target: AppVersion, release_date: str) -> str:
    header = re.search(r"^## \[Unreleased\]\s*$", content, flags=re.MULTILINE)
    if not header:
        fail("CHANGELOG.md is missing the [Unreleased] header")
    next_version = re.search(r"^## \[", content[header.end() :], flags=re.MULTILINE)
    end = header.end() + next_version.start() if next_version else len(content)
    release_body = content[header.end() : end].strip()
    if not release_body or not update_changelog._extract_unreleased_sections(content):
        fail("Cannot prepare a release with an empty [Unreleased] section")

    replacement = (
        f"## [Unreleased]\n\n"
        f"## [{target.tag}] - {release_date}\n\n"
        f"{release_body}\n\n"
    )
    updated = content[: header.start()] + replacement + content[end:]
    if latest_changelog_version(updated) != target.name:
        fail("CHANGELOG promotion produced an inconsistent latest version")
    return updated


def promote_readme(content: str, target: AppVersion, release_date: str) -> str:
    start = content.find(update_changelog.UPCOMING_CHANGES_START)
    end = content.find(update_changelog.UPCOMING_CHANGES_END)
    if start < 0 or end < 0 or end <= start:
        fail("README.md is missing valid upcoming-changes markers")
    end += len(update_changelog.UPCOMING_CHANGES_END)
    current_block = content[start:end]

    upcoming = UPCOMING_DETAILS_RE.search(current_block)
    if not upcoming:
        fail("README.md is missing the Upcoming in the Next Release block")
    release_body = upcoming.group(1).strip()
    if "currently in development" in release_body:
        fail("README Upcoming section is empty")

    # Drop older What's New blocks; CHANGELOG.md is the long-term history.
    new_release = (
        "<details open>\n"
        f"<summary><b>🎉 What's New ({target.tag}) - {release_date}</b></summary>\n"
        f"{release_body}\n"
        "</details>"
    )
    empty_upcoming = (
        "<details>\n"
        "<summary><b>🔮 Upcoming in the Next Release</b></summary>\n"
        '<p align="left">\n'
        "New features and improvements for the next release are currently in development.\n"
        "</p>\n"
        f"{update_changelog.README_AI_NOTICE}\n"
        "</details>"
    )
    replacement = (
        f"{update_changelog.UPCOMING_CHANGES_START}\n"
        '<div align="center">\n\n'
        f"{empty_upcoming}\n\n"
        "<br/>\n\n"
        f"{new_release}\n\n"
        "</div>\n"
        f"{update_changelog.UPCOMING_CHANGES_END}"
    )
    updated = content[:start] + replacement + content[end:]
    if latest_readme_version(updated) != target.name:
        fail("README promotion produced an inconsistent latest version")
    return updated


def release_apk_url(repository: str, version: AppVersion) -> str:
    return (
        f"https://github.com/{repository}/releases/latest/download/"
        f"{version.apk_asset}"
    )


def update_readme_download_url(
    content: str,
    repository: str,
    target: AppVersion,
) -> str:
    matches = list(README_APK_URL_RE.finditer(content))
    if not matches:
        fail("README.md must contain at least one GitHub latest-download APK URL")
    updated = README_APK_URL_RE.sub(
        release_apk_url(repository, target),
        content,
    )
    if release_apk_url(repository, target) not in updated:
        fail("README APK download URL did not update to the release asset")
    return updated


def write_outputs(values: dict[str, str | int]) -> None:
    output_path = os.environ.get("GITHUB_OUTPUT")
    if not output_path:
        for key, value in values.items():
            print(f"{key}={value}")
        return
    with Path(output_path).open("a", encoding="utf-8") as output:
        for key, value in values.items():
            text = str(value)
            if "\n" not in text:
                output.write(f"{key}={text}\n")
                continue
            delimiter = f"BOXL0RE_{key.upper()}_OUTPUT"
            while delimiter in text:
                delimiter += "_X"
            output.write(f"{key}<<{delimiter}\n{text}\n{delimiter}\n")


def prepare_release(args: argparse.Namespace) -> None:
    repository = os.environ.get("GITHUB_REPOSITORY", "").strip()
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    api_key = os.environ.get("GROQ_API_KEY", "").strip()
    if not repository or not token or not api_key:
        fail("GITHUB_REPOSITORY, GITHUB_TOKEN, and GROQ_API_KEY are required")

    current = read_app_version()
    validate_baseline(current, args.latest_tag)
    target = bump_version(current, args.bump)
    assert_remote_target_is_free(repository, token, target)

    processed = reconcile_changelog(
        repository,
        token,
        api_key,
        args.latest_tag,
        args.head_sha,
    )
    release_date = datetime.now(timezone.utc).date().isoformat()

    gradle_original = require_file(APP_GRADLE_PATH)
    changelog_original = require_file(CHANGELOG_PATH)
    readme_original = require_file(README_PATH)
    APP_GRADLE_PATH.write_text(
        replace_gradle_version(gradle_original, current, target),
        encoding="utf-8",
    )
    CHANGELOG_PATH.write_text(
        promote_changelog(changelog_original, target, release_date),
        encoding="utf-8",
    )
    promoted_readme = promote_readme(readme_original, target, release_date)
    README_PATH.write_text(
        update_readme_download_url(promoted_readme, repository, target),
        encoding="utf-8",
    )

    write_outputs(
        {
            "current_version": current.name,
            "current_code": current.code,
            "new_version": target.name,
            "new_code": target.code,
            "tag": target.tag,
            "apk_asset": target.apk_asset,
            "aab_asset": target.aab_asset,
            "branch": f"release/{target.tag}",
            "release_date": release_date,
            "reconciled_pr_count": len(processed),
        }
    )


def git_show(revision_path: str) -> str:
    try:
        return subprocess.check_output(
            ["git", "show", revision_path],
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        fail(f"Could not read git object {revision_path}: {exc}")
    raise AssertionError("unreachable")


def git_changed_files(base: str, head: str) -> set[str]:
    try:
        return {
            line
            for line in subprocess.check_output(
                ["git", "diff", "--name-only", base, head],
                text=True,
            ).splitlines()
            if line
        }
    except subprocess.CalledProcessError as exc:
        fail(f"Could not inspect git diff {base}..{head}: {exc}")
    raise AssertionError("unreachable")


def find_release_prepare_commit(version: AppVersion) -> str:
    needle = f"release: prepare {version.tag}"
    try:
        sha = subprocess.check_output(
            [
                "git",
                "log",
                "-1",
                "--format=%H",
                "--fixed-strings",
                f"--grep={needle}",
            ],
            text=True,
        ).strip()
    except subprocess.CalledProcessError as exc:
        fail(f"Could not search for release prepare commit: {exc}")
    if not sha:
        fail(f"No commit found matching '{needle}'")
    return sha


def verify_release_diff(current: AppVersion, commit: str = "HEAD") -> None:
    parent = f"{commit}^1"
    changed_files = git_changed_files(parent, commit)
    if changed_files != EXPECTED_FILES:
        fail(
            f"Release commit {commit[:12]} changed {sorted(changed_files)}; "
            f"expected exactly {sorted(EXPECTED_FILES)}"
        )

    previous = read_app_version(git_show(f"{parent}:app/build.gradle.kts"))
    commit_version = read_app_version(git_show(f"{commit}:app/build.gradle.kts"))
    if commit_version != current:
        fail(
            f"Release commit {commit[:12]} has {commit_version.name} "
            f"({commit_version.code}) but workspace is {current.name} "
            f"({current.code})"
        )

    allowed_targets = {
        bump_version(previous, bump)
        for bump in ("patch", "minor", "major")
    }
    if current not in allowed_targets:
        fail(
            f"Release version {current.name} ({current.code}) is not one valid "
            f"bump after {previous.name} ({previous.code})"
        )

    commit_gradle = git_show(f"{commit}:app/build.gradle.kts")
    previous_gradle = git_show(f"{parent}:app/build.gradle.kts")
    if replace_gradle_version(commit_gradle, current, previous) != previous_gradle:
        fail("Release merge changed app/build.gradle.kts beyond version fields")


def verify_release_metadata(current: AppVersion) -> None:
    changelog_version = latest_changelog_version()
    readme_version = latest_readme_version()
    if changelog_version != current.name or readme_version != current.name:
        fail(
            "Release metadata mismatch: "
            f"Gradle={current.name}, CHANGELOG={changelog_version}, README={readme_version}"
        )
    if update_changelog._extract_unreleased_sections(require_file(CHANGELOG_PATH)):
        fail("Release commit still contains [Unreleased] entries")
    repository = os.environ.get(
        "GITHUB_REPOSITORY",
        update_changelog.DEFAULT_GITHUB_REPOSITORY,
    ).strip()
    expected_download_url = release_apk_url(repository, current)
    if expected_download_url not in require_file(README_PATH):
        fail(f"README APK link must point to {current.apk_asset}")


def write_release_verify_outputs(
    current: AppVersion,
    *,
    merge_sha: str,
) -> None:
    write_outputs(
        {
            "version": current.name,
            "version_code": current.code,
            "tag": current.tag,
            "apk_asset": current.apk_asset,
            "aab_asset": current.aab_asset,
            "merge_sha": merge_sha,
        }
    )


def verify_release(args: argparse.Namespace) -> None:
    current = read_app_version()
    verify_release_diff(current)
    verify_release_metadata(current)
    expected_branch = f"release/{current.tag}"
    expected_title = f"release: {current.tag} [skip changelog]"
    if args.branch != expected_branch:
        fail(f"Release branch must be {expected_branch}, got {args.branch}")
    if args.title != expected_title:
        fail(f"Release PR title must be exactly: {expected_title}")
    write_release_verify_outputs(
        current,
        merge_sha=subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            text=True,
        ).strip(),
    )


def write_release_notes(args: argparse.Namespace) -> None:
    current = read_app_version()
    content = require_file(CHANGELOG_PATH)
    match = re.search(
        rf"^## \[{re.escape(current.tag)}\]\s*-\s*(\d{{4}}-\d{{2}}-\d{{2}})\s*$",
        content,
        flags=re.MULTILINE,
    )
    if not match:
        fail(f"CHANGELOG.md has no section for {current.tag}")
    next_header = re.search(r"^## \[", content[match.end() :], flags=re.MULTILINE)
    end = match.end() + next_header.start() if next_header else len(content)
    body = content[match.end() : end].strip()
    if not body:
        fail(f"CHANGELOG section for {current.tag} is empty")
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        f"# boxlore {current.tag}\n\n"
        "> **AI-generated summary:** May contain mistakes. "
        "Verify details against the linked pull requests.\n\n"
        f"{body}\n",
        encoding="utf-8",
    )


def plain_text(fragment: str) -> str:
    without_badges = re.sub(
        r"<a\b[^>]*>\s*<img\b[^>]*>\s*</a>",
        "",
        fragment,
        flags=re.DOTALL | re.IGNORECASE,
    )
    without_images = re.sub(
        r"<img\b[^>]*>",
        "",
        without_badges,
        flags=re.IGNORECASE,
    )
    without_tags = re.sub(r"<[^>]+>", "", without_images)
    return re.sub(r"\s+", " ", html.unescape(without_tags)).strip()


def shorten_notification_line(text: str, limit: int = 180) -> str:
    if len(text) <= limit:
        return text
    shortened = text[: limit - 1].rsplit(" ", 1)[0].rstrip(" ,;:-")
    return f"{shortened}…"


def notification_bullets(content: str, version: AppVersion) -> list[str]:
    release_match = re.search(
        r"<details(?:\s+open)?>\s*"
        rf"<summary><b>🎉 What's New \({re.escape(version.tag)}\)"
        r"\s*-\s*\d{4}-\d{2}-\d{2}</b></summary>"
        r"(.*?)</details>",
        content,
        flags=re.DOTALL,
    )
    if not release_match:
        fail(f"README.md has no What's New block for {version.tag}")
    release_body = release_match.group(1)

    candidates = [
        plain_text(item)
        for item in re.findall(
            r"<li\b[^>]*>(.*?)</li>",
            release_body,
            flags=re.DOTALL | re.IGNORECASE,
        )
    ]
    if not any(candidates):
        candidates = [
            plain_text(item)
            for item in re.split(
                r"<br\s*/?>",
                release_body,
                flags=re.IGNORECASE,
            )
        ]

    bullets: list[str] = []
    for candidate in candidates:
        if (
            not candidate
            or candidate in bullets
            or candidate.lower().startswith("for more details")
        ):
            continue
        bullets.append(shorten_notification_line(candidate))
        if len(bullets) == 3:
            break
    if not bullets:
        fail(f"README What's New block for {version.tag} has no notification text")
    return bullets


def write_notification_outputs() -> None:
    current = read_app_version()
    repository = os.environ.get(
        "GITHUB_REPOSITORY",
        update_changelog.DEFAULT_GITHUB_REPOSITORY,
    ).strip()
    if not repository or "/" not in repository:
        fail("GITHUB_REPOSITORY is malformed")
    bullets = notification_bullets(require_file(README_PATH), current)
    write_outputs(
        {
            "notification_title": f"boxlore {current.tag} is here",
            "notification_body": (
                "\n".join(f"- {bullet}" for bullet in bullets)
                + "\n\n*AI-generated summary; may contain mistakes.*"
            ),
            "notification_route": (
                release_apk_url(repository, current)
            ),
        }
    )


def verify_merged_commit() -> None:
    current = read_app_version()
    merge_sha = find_release_prepare_commit(current)
    verify_release_diff(current, commit=merge_sha)
    verify_release_metadata(current)
    print(f"Publishing release prepare commit {merge_sha}")
    write_release_verify_outputs(current, merge_sha=merge_sha)


def cleanup_stale_release(args: argparse.Namespace) -> None:
    repository = os.environ.get("GITHUB_REPOSITORY", "").strip()
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not repository or not token:
        fail("GITHUB_REPOSITORY and GITHUB_TOKEN are required")

    tag = args.tag.strip()
    if not tag.startswith("v"):
        fail(f"Release tag must start with v: {tag}")
    version_name = tag[1:]
    if not SEMVER_RE.fullmatch(version_name):
        fail(f"Release tag is not strict semantic versioning: {tag}")

    target = AppVersion(name=version_name, code=0)
    for action in cleanup_stale_release_target(repository, token, target):
        print(action)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    prepare_parser = subparsers.add_parser(
        "prepare",
        help="Reconcile release notes and prepare version/docs changes",
    )
    prepare_parser.add_argument(
        "--bump",
        required=True,
        choices=("patch", "minor", "major"),
    )
    prepare_parser.add_argument("--latest-tag", required=True)
    prepare_parser.add_argument("--head-sha", required=True)

    verify_parser = subparsers.add_parser(
        "verify",
        help="Verify merged release metadata before building",
    )
    verify_parser.add_argument("--branch", required=True)
    verify_parser.add_argument("--title", required=True)

    subparsers.add_parser(
        "verify-merged",
        help="Verify the checked-out master commit is a merged release",
    )

    notes_parser = subparsers.add_parser(
        "notes",
        help="Extract release notes for the current Gradle version",
    )
    notes_parser.add_argument("--output", required=True)

    subparsers.add_parser(
        "notification",
        help="Create concise in-app release-announcement outputs",
    )

    cleanup_parser = subparsers.add_parser(
        "cleanup-stale",
        help="Delete orphan release branch left by a failed prepare-release run",
    )
    cleanup_parser.add_argument("--tag", required=True)

    args = parser.parse_args()
    try:
        if args.command == "prepare":
            prepare_release(args)
        elif args.command == "verify":
            verify_release(args)
        elif args.command == "verify-merged":
            verify_merged_commit()
        elif args.command == "notes":
            write_release_notes(args)
        elif args.command == "notification":
            write_notification_outputs()
        elif args.command == "cleanup-stale":
            cleanup_stale_release(args)
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
        print(f"Release preparation failed: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

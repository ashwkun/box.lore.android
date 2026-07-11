#!/usr/bin/env python3
"""Append merged-PR release notes to CHANGELOG.md and sync README Upcoming Changes."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

CHANGELOG_PATH = Path("CHANGELOG.md")
README_PATH = Path("README.md")
UPCOMING_CHANGES_START = "<!-- upcoming-changes:start -->"
UPCOMING_CHANGES_END = "<!-- upcoming-changes:end -->"
GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
GROQ_MODEL = "openai/gpt-oss-120b"
GROQ_USER_AGENT = "boxlore-changelog/1.2"
CATEGORY_ORDER = ("Added", "Changed", "Fixed", "Deprecated", "Removed", "Security")
README_GROUP_ORDER = ("New features", "Improvements", "Fixes", "Security", "Other")
DEFAULT_GITHUB_REPOSITORY = "ashwkun/boxlore"


def _github_repository() -> str:
    return os.environ.get("GITHUB_REPOSITORY", DEFAULT_GITHUB_REPOSITORY).strip() or DEFAULT_GITHUB_REPOSITORY


def _pr_suffix(pr_number: int) -> str:
    repo = _github_repository()
    return f"([#{pr_number}](https://github.com/{repo}/pull/{pr_number}))"


def _pr_already_present(content: str, pr_number: int) -> bool:
    if f"(#{pr_number})" in content:
        return True
    return bool(
        re.search(
            rf"\[#{pr_number}\]\(https://github\.com/[^/]+/[^/]+/pull/{pr_number}\)",
            content,
        )
    )


def _require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        print(f"Missing required environment variable: {name}", file=sys.stderr)
        sys.exit(1)
    return value


def _groq_entries(api_key: str, pr_number: int, pr_title: str, pr_body: str) -> dict[str, list[str]]:
    system_prompt = """You write Keep a Changelog entries for an Android podcast app (boxlore).
Return ONLY valid JSON with these optional keys: Added, Changed, Fixed, Deprecated, Removed, Security.
Each value is an array of bullet strings WITHOUT leading dashes.

Rules:
- Use plain English, user-facing wording, matching existing changelog tone.
- Merge related bullets into one line per feature area (e.g. all queue UX → one Added bullet).
- Omit analytics-only, test-only, or CI-only changes from the changelog.
- Use "Fixed ..." phrasing for bug fixes, "Added ..." for new features, "Changed ..." for behavior/UI updates.
- Do not invent changes not supported by the PR title/body.
- Omit empty categories entirely.
- Do not include version headers, dates, PR numbers, or markdown formatting in bullets.
- Aim for 3–8 bullets total per PR, not one bullet per commit.
"""

    user_prompt = f"""PR #{pr_number}
Title: {pr_title}

Description:
{pr_body or "(no description)"}

Generate changelog bullets for the [Unreleased] section."""

    parsed = _groq_chat_json(
        api_key,
        system_prompt,
        user_prompt,
        "Groq API error",
    )

    normalized: dict[str, list[str]] = {}
    for category in CATEGORY_ORDER:
        raw = parsed.get(category, [])
        if not isinstance(raw, list):
            continue
        bullets = [str(item).strip() for item in raw if str(item).strip()]
        if bullets:
            normalized[category] = bullets
    return normalized


def _extract_pr_number(bullet: str) -> int | None:
    match = re.search(r"\(#(\d+)\)", bullet)
    if match:
        return int(match.group(1))
    match = re.search(r"\[#(\d+)\]\(https://github\.com/[^/]+/[^/]+/pull/\1\)", bullet)
    if match:
        return int(match.group(1))
    return None


@dataclass
class ChangelogCluster:
    pr_number: int | None
    items: list[tuple[str, str]] = field(default_factory=list)
    importance: int = 50
    theme: str = "general"

    def text_blob(self) -> str:
        return " ".join(text for _, text in self.items).lower()


def _cluster_theme(text: str) -> str:
    if any(k in text for k in ("queue", "refill", "auto-fill", "auto‑fill", "reorder", "skip memory", "lore queue")):
        return "queue & playback"
    if any(k in text for k in ("nps", "survey", "play review", "play store", "engagement", "prompt", "posthog")):
        return "feedback & surveys"
    if any(k in text for k in ("home tab", "scroll", "lag", "shimmer", "staggered", "recomposition")):
        return "home performance"
    if any(k in text for k in ("gitignore", "pycache", "__pycache__", "bytecode")):
        return "developer tooling"
    if any(k in text for k in ("analytics", "telemetry", "event")):
        return "analytics"
    return "general"


def _cluster_importance(theme: str, text: str, categories: set[str]) -> int:
    if theme == "developer tooling" or theme == "analytics":
        return 10
    if theme == "queue & playback":
        return 95
    if theme == "home performance":
        return 80
    if theme == "feedback & surveys":
        return 42
    if "fixed" in {c.lower() for c in categories} and theme == "general":
        return 55
    return 50


def _cluster_sections_by_pr(sections: dict[str, list[str]]) -> list[ChangelogCluster]:
    grouped: dict[int | None, ChangelogCluster] = {}

    for category, bullets in sections.items():
        for bullet in bullets:
            if not bullet.strip():
                continue
            pr_number = _extract_pr_number(bullet)
            cleaned = _strip_pr_links(bullet)
            cluster = grouped.setdefault(pr_number, ChangelogCluster(pr_number=pr_number))
            cluster.items.append((category, cleaned))

    clusters: list[ChangelogCluster] = []
    for cluster in grouped.values():
        categories = {cat for cat, _ in cluster.items}
        blob = cluster.text_blob()
        cluster.theme = _cluster_theme(blob)
        cluster.importance = _cluster_importance(cluster.theme, blob, categories)
        clusters.append(cluster)

    clusters.sort(key=lambda c: (-c.importance, c.pr_number or 0))
    return clusters


def _render_clustered_changelog(clusters: list[ChangelogCluster]) -> str:
    lines: list[str] = []
    for cluster in clusters:
        if cluster.importance < 40:
            continue
        label = f"PR #{cluster.pr_number}" if cluster.pr_number is not None else "Unlinked"
        lines.append(
            f"## {label} | importance {cluster.importance} | theme: {cluster.theme}"
        )
        by_category: dict[str, list[str]] = defaultdict(list)
        for category, text in cluster.items:
            by_category[category].append(text)
        for category in CATEGORY_ORDER:
            for text in by_category.get(category, []):
                lines.append(f"- [{category}] {text}")
        lines.append("")
    return "\n".join(lines).strip()


def _strip_pr_links(text: str) -> str:
    return re.sub(r"\s*\(\[#\d+\]\([^)]+\)\)\s*$", "", text).strip()


def _groq_chat_json(api_key: str, system_prompt: str, user_prompt: str, error_label: str) -> dict:
    payload = {
        "model": GROQ_MODEL,
        "temperature": 0.2,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }

    request = urllib.request.Request(
        GROQ_API_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "User-Agent": GROQ_USER_AGENT,
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        print(f"{error_label} ({exc.code}): {detail}", file=sys.stderr)
        sys.exit(1)

    content = body["choices"][0]["message"]["content"]
    parsed = json.loads(content)
    if not isinstance(parsed, dict):
        raise ValueError(f"{error_label}: Groq response was not a JSON object")
    return parsed


def _groq_curate_readme_upcoming(
    api_key: str, sections: dict[str, list[str]]
) -> list[dict[str, list[str]]]:
    """Curate all [Unreleased] entries into grouped, importance-sorted README bullets."""
    if not any(sections.values()):
        return []

    clusters = _cluster_sections_by_pr(sections)
    clustered_input = _render_clustered_changelog(clusters)
    if not clustered_input:
        return []

    system_prompt = """You curate clustered changelog entries for boxlore (Android podcast app) into a README "Upcoming Changes" section for listeners — not developers.

Input is pre-grouped by PR/theme with an importance score (higher = more user-visible). Each ## block is ONE feature area — merge its bullets into as few README lines as possible.

Return ONLY valid JSON:
{"groups": [{"heading": "...", "bullets": ["...", ...]}, ...]}

Allowed headings (exactly one per group, omit empty):
- "New features" — new capabilities (from Added clusters)
- "Improvements" — behavior/UI polish (from Changed clusters)
- "Fixes" — bugs resolved (from Fixed clusters)
- "Security" — privacy/security only

MANDATORY clustering rules:
1. ONE README bullet per input ## block in most cases. Never split a single PR/theme across multiple bullets.
2. For importance 90+ themes (queue, playback, discovery): allow at most TWO bullets if they describe clearly distinct user wins; prefer ONE strong sentence.
3. For importance 40–55 themes (NPS, surveys, review prompts): exactly ONE bullet, never lead the list.
4. Drop clusters with importance below 40 entirely (analytics, gitignore, CI, internal plumbing).
5. Merge all bullets inside a ## block before writing — e.g. four queue bullets → one: "Smart queue auto-refills, shows why items appear, supports drag reorder, and undo remove."
6. Sort bullets within each group by the cluster importance score (highest first). Queue/playback MUST appear before NPS/survey lines.
7. Cap at 8 bullets total across all groups.

Importance guidance (respect the scores in input):
- 90–100: core listening (queue, player, search, discovery) — headline features
- 70–89: home/browse performance — Improvements section
- 40–55: optional feedback surveys — one merged line, never prioritized over queue
- below 40: omit from README

Rewrite in plain English; no PR numbers, Compose, modules, or developer jargon. Under 120 chars per bullet."""

    user_prompt = f"""Curate these PR/theme clusters into grouped README bullets:

{clustered_input}"""

    parsed = _groq_chat_json(
        api_key,
        system_prompt,
        user_prompt,
        "Groq README curation error",
    )
    raw_groups = parsed.get("groups", [])
    if not isinstance(raw_groups, list):
        return []

    groups: list[dict[str, list[str]]] = []
    for item in raw_groups:
        if not isinstance(item, dict):
            continue
        heading = str(item.get("heading", "")).strip()
        bullets_raw = item.get("bullets", [])
        if not heading or not isinstance(bullets_raw, list):
            continue
        bullets = [str(b).strip() for b in bullets_raw if str(b).strip()]
        if bullets:
            groups.append({"heading": heading, "bullets": bullets})

    return _sort_readme_groups(groups)


def _sort_readme_groups(groups: list[dict[str, list[str]]]) -> list[dict[str, list[str]]]:
    order = {name: index for index, name in enumerate(README_GROUP_ORDER)}

    def rank(group: dict[str, list[str]]) -> tuple[int, str]:
        heading = group["heading"]
        return (order.get(heading, len(README_GROUP_ORDER)), heading.lower())

    return sorted(groups, key=rank)


def _groq_readme_summary(api_key: str, sections: dict[str, list[str]]) -> list[str]:
    """Flat bullet fallback when grouped curation returns nothing."""
    if not any(sections.values()):
        return []

    changelog_text = _render_unreleased(sections)
    system_prompt = """You rewrite changelog entries as short README bullets for end users of boxlore, a podcast Android app.
Return ONLY valid JSON: {"bullets": ["...", ...]}.

Rules:
- Write for listeners using the app, not developers.
- One bullet per user-visible change; merge related technical items into one line.
- Never mention: Compose, recomposition, lazy grid, PerfLog, CI, Groq, CodeRabbit, Sonar, refactoring, parameters, modules, workflows, or internal code names.
- Describe what users notice: smoother scrolling, faster load, calmer animations, new screens, fixed bugs in plain terms.
- Keep each bullet under 120 characters.
- No leading dashes, PR numbers, or markdown links."""

    user_prompt = f"""Convert these [Unreleased] changelog entries into user-facing README bullets:

{changelog_text}"""

    parsed = _groq_chat_json(
        api_key,
        system_prompt,
        user_prompt,
        "Groq README summary error",
    )
    raw = parsed.get("bullets", [])
    if not isinstance(raw, list):
        return []
    return [str(item).strip() for item in raw if str(item).strip()]


def _parse_unreleased_sections(unreleased_block: str) -> dict[str, list[str]]:
    sections: dict[str, list[str]] = {}
    current: str | None = None
    for line in unreleased_block.splitlines():
        header = re.match(r"^### (Added|Changed|Fixed|Deprecated|Removed|Security)\s*$", line.strip())
        if header:
            current = header.group(1)
            sections.setdefault(current, [])
            continue
        if current and line.startswith("- "):
            sections[current].append(line[2:].strip())
    return sections


def _render_unreleased(sections: dict[str, list[str]]) -> str:
    lines: list[str] = []
    for category in CATEGORY_ORDER:
        bullets = sections.get(category, [])
        if not bullets:
            continue
        lines.append(f"### {category}")
        for bullet in bullets:
            lines.append(f"- {bullet}")
    return "\n".join(lines)


def _merge_entries(
    existing: dict[str, list[str]],
    incoming: dict[str, list[str]],
    pr_number: int,
) -> dict[str, list[str]]:
    merged = {key: list(values) for key, values in existing.items()}
    suffix = _pr_suffix(pr_number)

    for category, bullets in incoming.items():
        merged.setdefault(category, [])
        seen = set(merged[category])
        for bullet in bullets:
            tagged = bullet if _pr_already_present(bullet, pr_number) else f"{bullet} {suffix}".strip()
            if tagged not in seen:
                merged[category].append(tagged)
                seen.add(tagged)
    return merged


def _extract_unreleased_sections(content: str) -> dict[str, list[str]]:
    match = re.search(r"^## \[Unreleased\]\s*$", content, flags=re.MULTILINE)
    if not match:
        return {}

    start = match.end()
    next_version = re.search(r"^## \[", content[start:], flags=re.MULTILINE)
    end = start + next_version.start() if next_version else len(content)
    return _parse_unreleased_sections(content[start:end])


def _render_readme_upcoming_block(groups: list[dict[str, list[str]]] | None = None, bullets: list[str] | None = None) -> str:
    if groups:
        visible = [g for g in groups if g.get("bullets")]
        if not visible:
            body = "<p><em>Nothing queued yet.</em></p>"
        else:
            parts: list[str] = []
            for group in visible:
                parts.append(f"<b>{group['heading']}</b>")
                parts.append("")
                parts.extend(f"- {bullet}" for bullet in group["bullets"])
                parts.append("")
            body = "\n".join(parts).rstrip()
    elif bullets:
        body = "\n".join(f"- {bullet}" for bullet in bullets)
    else:
        body = "<p><em>Nothing queued yet.</em></p>"

    return (
        f"{UPCOMING_CHANGES_START}\n"
        "<details>\n"
        "<summary><b>✨ Upcoming in the next release</b></summary>\n"
        "<br/>\n\n"
        f"{body}\n\n"
        "<br/>\n"
        '<p align="center"><sub>Technical details in <a href="CHANGELOG.md">CHANGELOG.md</a></sub></p>\n'
        "</details>\n"
        f"{UPCOMING_CHANGES_END}"
    )


def _update_readme(content: str, groups: list[dict[str, list[str]]] | None = None, bullets: list[str] | None = None) -> str:
    block = _render_readme_upcoming_block(groups=groups, bullets=bullets)
    pattern = re.compile(
        re.escape(UPCOMING_CHANGES_START) + r".*?" + re.escape(UPCOMING_CHANGES_END),
        flags=re.DOTALL,
    )
    if pattern.search(content):
        updated = pattern.sub(block, content, count=1)
    else:
        anchor = re.search(r"^(<!-- upcoming-changes:start -->|<h2 id=\"features\">)", content, flags=re.MULTILINE)
        if not anchor:
            raise ValueError(
                "Could not find Upcoming Changes markers or insertion anchor in README.md"
            )
        updated = content[: anchor.start()] + block + "\n\n" + content[anchor.start() :]

    if not updated.endswith("\n"):
        updated += "\n"
    return updated


def _update_changelog(content: str, entries: dict[str, list[str]], pr_number: int) -> tuple[str, bool]:
    if _pr_already_present(content, pr_number):
        print(f"CHANGELOG already contains entry for PR #{pr_number}; skipping merge.")
        return content, False

    match = re.search(r"^## \[Unreleased\]\s*$", content, flags=re.MULTILINE)
    if not match:
        raise ValueError("Could not find '## [Unreleased]' header in CHANGELOG.md")

    start = match.end()
    next_version = re.search(r"^## \[", content[start:], flags=re.MULTILINE)
    end = start + next_version.start() if next_version else len(content)

    unreleased_block = content[start:end]
    existing = _parse_unreleased_sections(unreleased_block)
    merged = _merge_entries(existing, entries, pr_number)
    rendered = _render_unreleased(merged)

    if rendered:
        replacement = f"\n{rendered}\n"
    else:
        replacement = "\n"

    updated = content[:start] + replacement + content[end:]
    if not updated.endswith("\n"):
        updated += "\n"
    return updated, True


def append_changelog(api_key: str, pr_number: int, pr_title: str, pr_body: str) -> bool:
    if not CHANGELOG_PATH.exists():
        print("CHANGELOG.md not found", file=sys.stderr)
        sys.exit(1)

    changelog_original = CHANGELOG_PATH.read_text(encoding="utf-8")
    entries = _groq_entries(api_key, pr_number, pr_title, pr_body)
    if not entries:
        print("Groq returned no changelog entries.")
        return False

    changelog_updated, changelog_changed = _update_changelog(
        changelog_original, entries, pr_number
    )
    if changelog_changed:
        CHANGELOG_PATH.write_text(changelog_updated, encoding="utf-8")
        print(f"Updated CHANGELOG.md for PR #{pr_number}.")
    else:
        print(f"No CHANGELOG changes for PR #{pr_number}.")
    return changelog_changed


def sync_readme_upcoming(api_key: str) -> bool:
    if not CHANGELOG_PATH.exists():
        print("CHANGELOG.md not found", file=sys.stderr)
        sys.exit(1)
    if not README_PATH.exists():
        print("README.md not found", file=sys.stderr)
        sys.exit(1)

    readme_original = README_PATH.read_text(encoding="utf-8")
    changelog_content = CHANGELOG_PATH.read_text(encoding="utf-8")
    unreleased = _extract_unreleased_sections(changelog_content)

    if not unreleased:
        print("No [Unreleased] entries; clearing README upcoming section.")
        readme_updated = _update_readme(readme_original)
    else:
        print("Curating README from all [Unreleased] changelog entries...")
        groups = _groq_curate_readme_upcoming(api_key, unreleased)
        if groups:
            readme_updated = _update_readme(readme_original, groups=groups)
        else:
            print("Grouped curation empty; falling back to flat bullet summary.")
            bullets = _groq_readme_summary(api_key, unreleased)
            readme_updated = _update_readme(readme_original, bullets=bullets)

    if readme_updated != readme_original:
        README_PATH.write_text(readme_updated, encoding="utf-8")
        print("Synced README.md Upcoming Changes section.")
        return True

    print("No README changes written.")
    return False


def main() -> None:
    parser = argparse.ArgumentParser(description="Update CHANGELOG and README on PR merge.")
    parser.add_argument(
        "command",
        nargs="?",
        default="all",
        choices=("all", "append", "sync-readme"),
        help="append: write PR to CHANGELOG; sync-readme: curate full Unreleased into README; all: both",
    )
    args = parser.parse_args()

    api_key = _require_env("GROQ_API_KEY")
    changelog_changed = False
    readme_changed = False

    if args.command in ("all", "append"):
        pr_number = int(_require_env("PR_NUMBER"))
        pr_title = _require_env("PR_TITLE")
        pr_body = os.environ.get("PR_BODY", "")
        changelog_changed = append_changelog(api_key, pr_number, pr_title, pr_body)

    if args.command in ("all", "sync-readme"):
        readme_changed = sync_readme_upcoming(api_key)

    if not changelog_changed and not readme_changed:
        print("No CHANGELOG or README changes written.")


if __name__ == "__main__":
    main()

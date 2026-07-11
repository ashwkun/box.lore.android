#!/usr/bin/env python3
"""Append merged-PR release notes to CHANGELOG.md under [Unreleased]."""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

CHANGELOG_PATH = Path("CHANGELOG.md")
GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
GROQ_MODEL = "openai/gpt-oss-120b"
CATEGORY_ORDER = ("Added", "Changed", "Fixed", "Deprecated", "Removed", "Security")
PR_MARKER_RE = re.compile(r"\(#(\d+)\)")


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
- One concise bullet per meaningful change; merge tiny related edits into one bullet.
- Use "Fixed ..." phrasing for bug fixes, "Added ..." for new features, "Changed ..." for behavior/UI updates.
- Do not invent changes not supported by the PR title/body.
- Omit empty categories entirely.
- Do not include version headers, dates, PR numbers, or markdown formatting in bullets.
"""

    user_prompt = f"""PR #{pr_number}
Title: {pr_title}

Description:
{pr_body or "(no description)"}

Generate changelog bullets for the [Unreleased] section."""

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
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        print(f"Groq API error ({exc.code}): {detail}", file=sys.stderr)
        sys.exit(1)

    content = body["choices"][0]["message"]["content"]
    parsed = json.loads(content)
    if not isinstance(parsed, dict):
        raise ValueError("Groq response was not a JSON object")

    normalized: dict[str, list[str]] = {}
    for category in CATEGORY_ORDER:
        raw = parsed.get(category, [])
        if not isinstance(raw, list):
            continue
        bullets = [str(item).strip() for item in raw if str(item).strip()]
        if bullets:
            normalized[category] = bullets
    return normalized


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
    marker = f"(#{pr_number})"

    for category, bullets in incoming.items():
        merged.setdefault(category, [])
        seen = set(merged[category])
        for bullet in bullets:
            tagged = bullet if marker in bullet else f"{bullet} {marker}".strip()
            if tagged not in seen:
                merged[category].append(tagged)
                seen.add(tagged)
    return merged


def _update_changelog(content: str, entries: dict[str, list[str]], pr_number: int) -> str:
    marker = f"(#{pr_number})"
    if marker in content:
        print(f"CHANGELOG already contains entry for PR #{pr_number}; skipping.")
        return content

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
    return updated


def main() -> None:
    api_key = _require_env("GROQ_API_KEY")
    pr_number = int(_require_env("PR_NUMBER"))
    pr_title = _require_env("PR_TITLE")
    pr_body = os.environ.get("PR_BODY", "")

    if not CHANGELOG_PATH.exists():
        print("CHANGELOG.md not found", file=sys.stderr)
        sys.exit(1)

    entries = _groq_entries(api_key, pr_number, pr_title, pr_body)
    if not entries:
        print("Groq returned no changelog entries; nothing to update.")
        return

    original = CHANGELOG_PATH.read_text(encoding="utf-8")
    updated = _update_changelog(original, entries, pr_number)
    if updated != original:
        CHANGELOG_PATH.write_text(updated, encoding="utf-8")
        print(f"Updated CHANGELOG.md for PR #{pr_number}.")
    else:
        print("No CHANGELOG changes written.")


if __name__ == "__main__":
    main()

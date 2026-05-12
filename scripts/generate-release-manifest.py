#!/usr/bin/env python3
"""
Generate releases.stable.json for DrainQ.ONE update manifest.

Reads CHANGELOG.md to extract release notes for the current version,
computes APK size, and writes the manifest in the format expected by
HttpUpdateService / UpdateModels.kt.
"""

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone


MIN_SDK = 26
PROXY_BASE_URL = "https://updates.drainq.de/one/"
MANIFEST_FILE_STABLE = "releases.stable.json"


def extract_changelog_notes(version: str) -> str:
    """
    Extract the release notes block for `version` from CHANGELOG.md.
    Looks for a section starting with '## [version]' or '## v{version}'.
    Returns plain-text bullet list (first block only).
    """
    changelog_path = os.path.join(os.path.dirname(__file__), "..", "CHANGELOG.md")
    if not os.path.exists(changelog_path):
        return f"DrainQ.ONE {version}"

    with open(changelog_path, encoding="utf-8") as fh:
        content = fh.read()

    # Match header like: ## [0.4.0] or ## v0.4.0 or ## 0.4.0
    pattern = re.compile(
        r"^##\s+(?:\[?" + re.escape(version) + r"\]?|v" + re.escape(version) + r")\b.*?$",
        re.MULTILINE,
    )
    match = pattern.search(content)
    if not match:
        return f"DrainQ.ONE {version}"

    start = match.end()
    # Find next ## header or end of file
    next_section = re.search(r"^##\s+", content[start:], re.MULTILINE)
    block = content[start : start + next_section.start()] if next_section else content[start:]

    lines = [line.strip() for line in block.splitlines() if line.strip()]
    return "\n".join(lines) if lines else f"DrainQ.ONE {version}"


def load_existing_manifest(output_path: str) -> list:
    """Return the history array from an existing manifest, if present."""
    if os.path.exists(output_path):
        with open(output_path, encoding="utf-8") as fh:
            try:
                data = json.load(fh)
                latest = data.get("latest", {})
                history = data.get("history", [])
                # Move current latest into history (skip if same version)
                if latest and latest.get("version"):
                    history_entry = {
                        "version": latest["version"],
                        "versionCode": latest.get("versionCode", 0),
                        "releasedAt": latest.get("releasedAt", ""),
                    }
                    if not any(h["version"] == latest["version"] for h in history):
                        history.insert(0, history_entry)
                return history
            except json.JSONDecodeError:
                return []
    return []


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate DrainQ.ONE release manifest")
    parser.add_argument("--version", required=True, help="SemVer string, e.g. 0.4.0")
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--sha256", required=True, help="Hex SHA256 of APK file")
    parser.add_argument("--apk-path", required=True, help="Path to APK file (for size)")
    parser.add_argument("--output", default=MANIFEST_FILE_STABLE)
    parser.add_argument("--channel", default="stable")
    parser.add_argument("--mandatory", action="store_true", default=False)
    args = parser.parse_args()

    apk_size = os.path.getsize(args.apk_path)
    apk_name = os.path.basename(args.apk_path)
    apk_url = f"{PROXY_BASE_URL}{apk_name}"

    notes = extract_changelog_notes(args.version)
    released_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    history = load_existing_manifest(args.output)

    manifest = {
        "channel": args.channel,
        "latest": {
            "version": args.version,
            "versionCode": args.version_code,
            "minSdk": MIN_SDK,
            "url": apk_url,
            "sha256": args.sha256,
            "size": apk_size,
            "releasedAt": released_at,
            "notes": notes,
            "mandatory": args.mandatory,
        },
        "history": history,
    }

    with open(args.output, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    print(f"Manifest written to {args.output}")
    print(f"  version={args.version}  versionCode={args.version_code}  size={apk_size}B")
    print(f"  sha256={args.sha256[:16]}...")


if __name__ == "__main__":
    main()

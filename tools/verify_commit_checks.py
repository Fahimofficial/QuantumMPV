#!/usr/bin/env python3
"""Fail unless the required GitHub check runs succeeded on a commit.

Release workflows call this before building, so a tag can only be published
from a commit that already passed the repository's merge gates.

Environment: GITHUB_TOKEN (needs checks:read), GITHUB_REPOSITORY,
GITHUB_API_URL (optional). Waits for in-progress checks up to --timeout
seconds; a missing, failed, cancelled or timed-out check fails the run.

Usage:
  python3 tools/verify_commit_checks.py --sha <commit> \
      --check "Lint, test, and build debug APK" --check "ktlint + detekt"
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def fetch_check_runs(api: str, repo: str, sha: str, token: str) -> list[dict]:
    runs: list[dict] = []
    page = 1
    while True:
        url = f"{api}/repos/{repo}/commits/{sha}/check-runs?filter=latest&per_page=100&page={page}"
        request = urllib.request.Request(
            url,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "quantummpv-release-gate",
            },
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.load(response)
        batch = payload.get("check_runs", [])
        runs.extend(batch)
        if len(batch) < 100:
            return runs
        page += 1


def evaluate(runs: list[dict], required: list[str]) -> tuple[list[str], list[str], list[str]]:
    by_name: dict[str, list[dict]] = {}
    for run in runs:
        by_name.setdefault(run.get("name", ""), []).append(run)
    passed, pending, failed = [], [], []
    for name in required:
        candidates = by_name.get(name, [])
        if not candidates:
            pending.append(f"{name}: not reported yet")
            continue
        # Several workflows may report a check with the same name; all of them must pass.
        statuses = [(c.get("status"), c.get("conclusion"), c.get("html_url", "")) for c in candidates]
        if any(status != "completed" for status, _, _ in statuses):
            pending.append(f"{name}: in progress")
        elif all(conclusion == "success" for _, conclusion, _ in statuses):
            passed.append(name)
        else:
            details = ", ".join(f"{conclusion} ({url})" for _, conclusion, url in statuses if conclusion != "success")
            failed.append(f"{name}: {details}")
    return passed, pending, failed


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify required check runs on a commit.")
    parser.add_argument("--sha", required=True)
    parser.add_argument("--check", action="append", required=True, dest="checks")
    parser.add_argument("--timeout", type=int, default=1800, help="seconds to wait for pending checks")
    parser.add_argument("--interval", type=int, default=60)
    args = parser.parse_args()

    if not SHA_RE.fullmatch(args.sha):
        print(f"::error::Expected a full 40-character commit SHA, got {args.sha!r}")
        return 1
    token = os.environ.get("GITHUB_TOKEN", "")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    api = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    if not token or not repo:
        print("::error::GITHUB_TOKEN and GITHUB_REPOSITORY must be set.")
        return 1

    deadline = time.monotonic() + args.timeout
    while True:
        try:
            runs = fetch_check_runs(api, repo, args.sha, token)
        except urllib.error.HTTPError as error:
            print(f"::error::GitHub API returned HTTP {error.code} while reading check runs.")
            return 1
        passed, pending, failed = evaluate(runs, args.checks)
        for name in passed:
            print(f"PASS     {name}")
        for item in failed:
            print(f"FAIL     {item}")
        for item in pending:
            print(f"PENDING  {item}")
        if failed:
            print(f"::error::Required checks failed on {args.sha}. Fix them on master before releasing.")
            return 1
        if not pending:
            print(f"All {len(passed)} required checks passed on {args.sha}.")
            return 0
        if time.monotonic() >= deadline:
            print(
                f"::error::Timed out waiting for required checks on {args.sha}. A check that never ran "
                "(for example because its run was cancelled) must be re-run on that commit."
            )
            return 1
        print(f"Waiting {args.interval}s for pending checks...")
        time.sleep(args.interval)


if __name__ == "__main__":
    sys.exit(main())

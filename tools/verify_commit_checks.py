#!/usr/bin/env python3
"""
Fail unless the required GitHub check runs succeeded on a commit.

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
# Errors that retrying cannot fix: bad token, missing permission, unknown commit.
FATAL_HTTP_CODES = (401, 403, 404, 422)


class GateError(Exception):
    """The gate cannot reach a verdict; the message explains why."""


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
        with urllib.request.urlopen(request, timeout=30) as response:  # nosec B310 - https scheme enforced in read_settings()
            payload = json.load(response)
        batch = payload.get("check_runs", [])
        runs.extend(batch)
        if len(batch) < 100:
            return runs
        page += 1


def classify(name: str, candidates: list[dict]) -> tuple[str, str]:
    """Return ("passed" | "pending" | "failed", detail) for one required check."""
    if not candidates:
        return "pending", f"{name}: not reported yet"
    # Several workflows may report a check with the same name; all of them must pass.
    if any(c.get("status") != "completed" for c in candidates):
        return "pending", f"{name}: in progress"
    failures = [c for c in candidates if c.get("conclusion") != "success"]
    if not failures:
        return "passed", name
    details = ", ".join(f"{c.get('conclusion')} ({c.get('html_url', '')})" for c in failures)
    return "failed", f"{name}: {details}"


def evaluate(runs: list[dict], required: list[str]) -> tuple[list[str], list[str], list[str]]:
    by_name: dict[str, list[dict]] = {}
    for run in runs:
        by_name.setdefault(run.get("name", ""), []).append(run)
    results: dict[str, list[str]] = {"passed": [], "pending": [], "failed": []}
    for name in required:
        state, detail = classify(name, by_name.get(name, []))
        results[state].append(detail)
    return results["passed"], results["pending"], results["failed"]


def read_settings(sha: str) -> tuple[str, str, str]:
    """Validate the inputs and return (api, repo, token)."""
    if not SHA_RE.fullmatch(sha):
        raise GateError(f"Expected a full 40-character commit SHA, got {sha!r}")
    token = os.environ.get("GITHUB_TOKEN", "")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    api = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    if not api.startswith("https://"):
        raise GateError(f"GITHUB_API_URL must be an https:// URL, got {api!r}.")
    if not token or not repo:
        raise GateError("GITHUB_TOKEN and GITHUB_REPOSITORY must be set.")
    return api, repo, token


def fetch_with_retry(settings: tuple[str, str, str], sha: str, deadline: float, interval: int) -> list[dict]:
    """Fetch check runs, retrying transient API and network errors until the deadline."""
    api, repo, token = settings
    while True:
        try:
            return fetch_check_runs(api, repo, sha, token)
        except urllib.error.HTTPError as error:
            if error.code in FATAL_HTTP_CODES:
                raise GateError(
                    f"GitHub API returned HTTP {error.code} while reading check runs "
                    "(check the token's checks:read permission and the commit SHA)."
                ) from error
            retry_message = f"GitHub API returned HTTP {error.code}"
            give_up_message = f"GitHub API kept failing (last: HTTP {error.code}); giving up."
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            retry_message = f"Network error reading check runs ({error})"
            give_up_message = f"Could not reach the GitHub API ({error}); giving up."
        if time.monotonic() >= deadline:
            raise GateError(give_up_message)
        print(f"{retry_message}; retrying in {interval}s.")
        time.sleep(interval)


def print_results(passed: list[str], pending: list[str], failed: list[str]) -> None:
    for name in passed:
        print(f"PASS     {name}")
    for item in failed:
        print(f"FAIL     {item}")
    for item in pending:
        print(f"PENDING  {item}")


def wait_for_checks(args: argparse.Namespace, settings: tuple[str, str, str]) -> int:
    deadline = time.monotonic() + args.timeout
    while True:
        runs = fetch_with_retry(settings, args.sha, deadline, args.interval)
        passed, pending, failed = evaluate(runs, args.checks)
        print_results(passed, pending, failed)
        if failed:
            raise GateError(f"Required checks failed on {args.sha}. Fix them on master before releasing.")
        if not pending:
            print(f"All {len(passed)} required checks passed on {args.sha}.")
            return 0
        if time.monotonic() >= deadline:
            raise GateError(
                f"Timed out waiting for required checks on {args.sha}. A check that never ran "
                "(for example because its run was cancelled) must be re-run on that commit."
            )
        print(f"Waiting {args.interval}s for pending checks...")
        time.sleep(args.interval)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Verify required check runs on a commit.")
    parser.add_argument("--sha", required=True)
    parser.add_argument("--check", action="append", required=True, dest="checks")
    parser.add_argument("--timeout", type=int, default=1800, help="seconds to wait for pending checks")
    parser.add_argument("--interval", type=int, default=60)
    args = parser.parse_args(argv)
    try:
        return wait_for_checks(args, read_settings(args.sha))
    except GateError as error:
        print(f"::error::{error}")
        return 1


if __name__ == "__main__":
    sys.exit(main())

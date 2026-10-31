#!/usr/bin/env python3
"""
Regression tests for tools/verify_commit_checks.py (the release check gate).

Run: python3 tools/test_verify_commit_checks.py
"""
from __future__ import annotations

import contextlib
import io
import json
import os
import sys
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify_commit_checks as gate  # noqa: E402

SHA = "0123456789abcdef0123456789abcdef01234567"
ENV = {"GITHUB_TOKEN": "test-token", "GITHUB_REPOSITORY": "owner/repo", "GITHUB_API_URL": "https://api.github.com"}


def run(name: str, status: str = "completed", conclusion: str | None = "success") -> dict:
    return {"name": name, "status": status, "conclusion": conclusion, "html_url": f"https://example.test/{name}"}


def http_error(code: int) -> urllib.error.HTTPError:
    return urllib.error.HTTPError("https://api.github.com", code, "error", {}, None)


class FakeClock:
    """Deterministic replacement for time.monotonic/time.sleep."""

    def __init__(self) -> None:
        self.now = 0.0
        self.sleeps: list[float] = []

    def monotonic(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)
        self.now += seconds


class EvaluateTest(unittest.TestCase):
    def test_classification_table(self) -> None:
        cases = [
            ("missing", [], "pending"),
            ("queued", [run("queued", status="queued", conclusion=None)], "pending"),
            ("success", [run("success")], "passed"),
            ("failure", [run("failure", conclusion="failure")], "failed"),
            ("cancelled", [run("cancelled", conclusion="cancelled")], "failed"),
            ("skipped", [run("skipped", conclusion="skipped")], "failed"),
            ("timed_out", [run("timed_out", conclusion="timed_out")], "failed"),
            ("dup-ok", [run("dup-ok"), run("dup-ok")], "passed"),
            ("dup-mixed", [run("dup-mixed"), run("dup-mixed", conclusion="failure")], "failed"),
            ("dup-pending", [run("dup-pending", conclusion="failure"), run("dup-pending", status="in_progress", conclusion=None)], "pending"),
        ]
        for name, runs, expected in cases:
            with self.subTest(name=name):
                passed, pending, failed = gate.evaluate(runs, [name])
                actual = {"passed": passed, "pending": pending, "failed": failed}
                self.assertEqual([state for state, items in actual.items() if items], [expected])

    def test_failure_details_list_only_unsuccessful_runs(self) -> None:
        runs = [run("lint"), run("lint", conclusion="failure")]
        _, _, failed = gate.evaluate(runs, ["lint"])
        self.assertEqual(failed, ["lint: failure (https://example.test/lint)"])

    def test_unrelated_checks_are_ignored(self) -> None:
        passed, pending, failed = gate.evaluate([run("lint"), run("other", conclusion="failure")], ["lint"])
        self.assertEqual((passed, pending, failed), (["lint"], [], []))


class MainTest(unittest.TestCase):
    def call(self, responses: list, *, checks=("lint", "detekt"), sha: str = SHA, env: dict | None = None, timeout: int = 300) -> tuple[int, str, int]:
        """Run main() with fetch_check_runs returning (or raising) each response in turn."""
        clock = FakeClock()
        fetch = mock.Mock(side_effect=responses)
        argv = ["verify_commit_checks.py", "--sha", sha, "--timeout", str(timeout), "--interval", "60"]
        for check in checks:
            argv += ["--check", check]
        output = io.StringIO()
        with contextlib.ExitStack() as stack:
            stack.enter_context(mock.patch.dict(os.environ, ENV if env is None else env, clear=True))
            stack.enter_context(mock.patch.object(sys, "argv", argv))
            stack.enter_context(mock.patch.object(gate, "fetch_check_runs", fetch))
            stack.enter_context(mock.patch.object(gate.time, "monotonic", clock.monotonic))
            stack.enter_context(mock.patch.object(gate.time, "sleep", clock.sleep))
            stack.enter_context(contextlib.redirect_stdout(output))
            code = gate.main()
        return code, output.getvalue(), fetch.call_count

    def test_all_required_checks_pass(self) -> None:
        code, out, calls = self.call([[run("lint"), run("detekt")]])
        self.assertEqual((code, calls), (0, 1))
        self.assertIn("All 2 required checks passed", out)

    def test_failed_check_blocks_immediately(self) -> None:
        code, out, calls = self.call([[run("lint"), run("detekt", conclusion="failure"), run("pending", status="queued")]])
        self.assertEqual((code, calls), (1, 1))
        self.assertIn("FAIL     detekt: failure", out)
        self.assertIn("::error::Required checks failed", out)

    def test_waits_for_pending_checks_then_passes(self) -> None:
        responses = [[run("lint")], [run("lint"), run("detekt", status="in_progress", conclusion=None)], [run("lint"), run("detekt")]]
        code, out, calls = self.call(responses)
        self.assertEqual((code, calls), (0, 3))
        self.assertIn("PENDING  detekt: not reported yet", out)
        self.assertIn("PENDING  detekt: in progress", out)

    def test_times_out_when_a_check_never_reports(self) -> None:
        code, out, calls = self.call([[run("lint")]] * 10, timeout=120)
        self.assertEqual(code, 1)
        self.assertEqual(calls, 3)
        self.assertIn("::error::Timed out waiting for required checks", out)

    def test_retries_transient_http_and_network_errors(self) -> None:
        responses = [http_error(502), urllib.error.URLError("reset"), TimeoutError("slow"), [run("lint"), run("detekt")]]
        code, out, calls = self.call(responses)
        self.assertEqual((code, calls), (0, 4))
        self.assertIn("GitHub API returned HTTP 502; retrying in 60s.", out)
        self.assertIn("Network error reading check runs", out)

    def test_gives_up_on_persistent_transient_errors(self) -> None:
        code, out, _ = self.call([http_error(503)] * 10, timeout=120)
        self.assertEqual(code, 1)
        self.assertIn("::error::GitHub API kept failing (last: HTTP 503); giving up.", out)
        code, out, _ = self.call([urllib.error.URLError("down")] * 10, timeout=120)
        self.assertEqual(code, 1)
        self.assertIn("::error::Could not reach the GitHub API", out)

    def test_permission_and_lookup_errors_are_fatal_without_retry(self) -> None:
        for status in (401, 403, 404, 422):
            with self.subTest(status=status):
                code, out, calls = self.call([http_error(status), [run("lint"), run("detekt")]])
                self.assertEqual((code, calls), (1, 1))
                self.assertIn(f"HTTP {status} while reading check runs", out)

    def test_rejects_invalid_inputs_before_calling_the_api(self) -> None:
        cases = {
            "short sha": ({"sha": "abc123"}, "full 40-character commit SHA"),
            "uppercase sha": ({"sha": SHA.upper()}, "full 40-character commit SHA"),
            "http api": ({"env": {**ENV, "GITHUB_API_URL": "http://api.github.com"}}, "must be an https:// URL"),
            "file api": ({"env": {**ENV, "GITHUB_API_URL": "file:///etc/passwd"}}, "must be an https:// URL"),
            "no token": ({"env": {"GITHUB_REPOSITORY": "owner/repo"}}, "GITHUB_TOKEN and GITHUB_REPOSITORY must be set"),
            "no repo": ({"env": {"GITHUB_TOKEN": "t"}}, "GITHUB_TOKEN and GITHUB_REPOSITORY must be set"),
        }
        for label, (kwargs, message) in cases.items():
            with self.subTest(label):
                code, out, calls = self.call([[run("lint"), run("detekt")]], **kwargs)
                self.assertEqual((code, calls), (1, 0))
                self.assertIn(message, out)


class FetchTest(unittest.TestCase):
    def test_follows_pagination_and_sends_auth_header(self) -> None:
        pages = [{"check_runs": [run(f"c{i}") for i in range(100)]}, {"check_runs": [run("last")]}]
        requests = []

        def fake_urlopen(request, timeout):
            requests.append(request)
            response = mock.MagicMock()
            response.__enter__.return_value = io.StringIO(json.dumps(pages[len(requests) - 1]))
            return response

        with mock.patch.object(gate.urllib.request, "urlopen", fake_urlopen):
            runs = gate.fetch_check_runs("https://api.github.com", "owner/repo", SHA, "secret")
        self.assertEqual(len(runs), 101)
        self.assertEqual(len(requests), 2)
        self.assertTrue(requests[1].full_url.endswith("&page=2"))
        self.assertEqual(requests[0].get_header("Authorization"), "Bearer secret")


if __name__ == "__main__":
    unittest.main(verbosity=2)

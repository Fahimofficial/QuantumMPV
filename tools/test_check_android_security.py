#!/usr/bin/env python3
"""
Regression tests for tools/check_android_security.py.

Each test builds a small, self-contained fixture repository that passes the
policy, applies one change and checks which findings the gate reports.

Run: python3 tools/test_check_android_security.py
"""
from __future__ import annotations

import contextlib
import io
import json
import os
import subprocess  # nosec B404 - fixed git commands on a temporary fixture
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_android_security as security  # noqa: E402

MANIFEST = "app/src/main/AndroidManifest.xml"
XML_DIR = "app/src/main/res/xml"
APP_GRADLE = "app/build.gradle.kts"
SOURCE = "app/src/main/java/app/Feature.kt"

POLICY = {
    "manifest": MANIFEST,
    "exported_components": {".MainActivity": "Launcher entry point."},
    "permissions": {"android.permission.INTERNET": "Network playback."},
    "accepted_exceptions": {},
    "backup_exclusions": {
        f"{XML_DIR}/backup_rules.xml": [["file", "secure/"]],
        f"{XML_DIR}/data_extraction_rules.xml": [["file", "secure/"]],
    },
}

CLEAN_FILES = {
    MANIFEST: """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:networkSecurityConfig="@xml/network_security_config">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter><action android:name="android.intent.action.MAIN" /></intent-filter>
        </activity>
        <service android:name=".PlaybackService" android:exported="false" />
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="app.provider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/provider_paths" />
        </provider>
    </application>
</manifest>
""",
    f"{XML_DIR}/provider_paths.xml": '<paths><cache-path name="logs" path="logs/" /></paths>\n',
    f"{XML_DIR}/network_security_config.xml": """<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
    <debug-overrides>
        <trust-anchors><certificates src="user" /></trust-anchors>
    </debug-overrides>
</network-security-config>
""",
    f"{XML_DIR}/backup_rules.xml": '<full-backup-content><exclude domain="file" path="secure/" /></full-backup-content>\n',
    f"{XML_DIR}/data_extraction_rules.xml": """<data-extraction-rules>
    <cloud-backup><exclude domain="file" path="secure/" /></cloud-backup>
    <device-transfer><exclude domain="file" path="secure/" /></device-transfer>
</data-extraction-rules>
""",
    "build.gradle.kts": "plugins {}\n",
    "gradle.properties": "org.gradle.jvmargs=-Xmx2g\n",
    APP_GRADLE: """android {
    signingConfigs {
        create("release") { storePassword = System.getenv("KS_PASS") ?: "" }
    }
    buildTypes {
        named("release") {
            isMinifyEnabled = true
        }
        debug { isDebuggable = true }
    }
}
""",
    "app/proguard-rules.pro": """-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
""",
    SOURCE: """package app

class Feature(private val context: Context) {
    private val trustManager: javax.net.ssl.X509TrustManager? = null

    fun start(receiver: BroadcastReceiver) {
        PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.registerReceiver(receiver, IntentFilter("a"), Context.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(context, receiver, IntentFilter("b"), ContextCompat.RECEIVER_NOT_EXPORTED)
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun registerReceiver(value: Int) = value
}
""",
}


class FixtureTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.write("config/security/android-security-policy.json", json.dumps(POLICY))
        for rel, content in CLEAN_FILES.items():
            self.write(rel, content)
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)  # nosec B603 B607
        subprocess.run(["git", "add", "-A"], cwd=self.root, check=True)  # nosec B603 B607

    def write(self, rel: str, content: str) -> None:
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def replace(self, rel: str, old: str, new: str) -> None:
        text = (self.root / rel).read_text(encoding="utf-8")
        self.assertIn(old, text, f"fixture {rel} no longer contains {old!r}")
        self.write(rel, text.replace(old, new, 1))

    def policy(self, **changes) -> None:
        self.write("config/security/android-security-policy.json", json.dumps({**POLICY, **changes}))

    def check(self) -> tuple[list[tuple[str, str]], int]:
        """Run every check; return ((rule, severity) findings, exit code)."""
        checker = security.Checker(self.root)
        checker.check_manifest()
        checker.check_build()
        checker.check_sources()
        with mock.patch.dict(os.environ, {}, clear=True), contextlib.redirect_stdout(io.StringIO()):
            code = checker.report()
        return [(f.rule, f.severity) for f in checker.findings], code

    def assert_blocks(self, rule: str, severity: str) -> None:
        findings, code = self.check()
        self.assertIn((rule, severity), findings)
        self.assertEqual(code, 1)


class CleanFixtureTest(FixtureTest):
    def test_clean_fixture_has_no_findings(self) -> None:
        # Also covers false-positive guards: X509TrustManager type annotations,
        # user CAs inside <debug-overrides>, debuggable debug builds, sticky
        # registerReceiver(null, ...), RECEIVER_NOT_EXPORTED and a
        # function named registerReceiver.
        self.assertEqual(self.check(), ([], 0))

    def test_alternative_build_type_syntax_is_understood(self) -> None:
        self.replace(APP_GRADLE, 'named("release") {', 'getByName("release") {')
        self.assertEqual(self.check(), ([], 0))


class XmlSafetyTest(FixtureTest):
    def test_doctype_and_entities_are_rejected_before_parsing(self) -> None:
        payloads = {
            "external entity": '<!DOCTYPE paths [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><paths>&xxe;</paths>',
            "entity expansion": '<!DOCTYPE paths [<!ENTITY a "aaaa"><!ENTITY b "&a;&a;&a;&a;">]><paths>&b;</paths>',
            "bare doctype": '<!DOCTYPE paths SYSTEM "http://example.invalid/x.dtd"><paths />',
        }
        for label, payload in payloads.items():
            with self.subTest(label):
                self.write(f"{XML_DIR}/provider_paths.xml", '<?xml version="1.0"?>' + payload)
                checker = security.Checker(self.root)
                self.assertIsNone(checker.parse_xml(f"{XML_DIR}/provider_paths.xml"))
                self.assertEqual([(f.rule, f.severity) for f in checker.findings], [("XML001", "high")])

    def test_unreadable_xml_fails_closed(self) -> None:
        cases = {"malformed": b"<paths>", "not utf-8": b"<paths name='\xff\xfe' />", "missing": None}
        for label, content in cases.items():
            with self.subTest(label):
                path = self.root / XML_DIR / "provider_paths.xml"
                if content is None:
                    path.unlink(missing_ok=True)
                else:
                    path.write_bytes(content)
                self.assert_blocks("XML000", "high")


class ManifestTest(FixtureTest):
    def test_application_flags(self) -> None:
        cases = [
            ('android:allowBackup="true"', 'android:allowBackup="true" android:debuggable="true"', "MAN001", "critical"),
            ('android:allowBackup="true"', 'android:allowBackup="true" android:usesCleartextTraffic="true"', "MAN005", "medium"),
            ('android:fullBackupContent="@xml/backup_rules"', "", "MAN006", "high"),
            ('android:networkSecurityConfig="@xml/network_security_config"', "", "NSC000", "medium"),
        ]
        for old, new, rule, severity in cases:
            with self.subTest(rule):
                self.setUp()
                self.replace(MANIFEST, old, new)
                self.assert_blocks(rule, severity)

    def test_backup_rules_are_not_required_when_backup_is_disabled(self) -> None:
        self.replace(MANIFEST, 'android:allowBackup="true"', 'android:allowBackup="false"')
        self.replace(MANIFEST, 'android:fullBackupContent="@xml/backup_rules"', "")
        self.assertEqual(self.check(), ([], 0))

    def test_component_changes(self) -> None:
        cases = {
            "new exported activity": ('<activity android:name=".Share" android:exported="true" />', "MAN002", "medium"),
            "implicit export": ('<service android:name=".Sync"><intent-filter><action android:name="a" /></intent-filter></service>', "MAN002", "high"),
            "exported provider": ('<provider android:name=".Data" android:authorities="d" android:exported="true" />', "MAN004", "high"),
        }
        for label, (element, rule, severity) in cases.items():
            with self.subTest(label):
                self.setUp()
                self.replace(MANIFEST, "</application>", element + "</application>")
                self.assert_blocks(rule, severity)

    def test_allowlisted_exports_are_accepted_and_stale_entries_reported(self) -> None:
        self.policy(exported_components={**POLICY["exported_components"], ".Removed": "Old entry."})
        self.assertEqual(self.check(), ([("MAN002", "low")], 0))

    def test_permission_changes(self) -> None:
        self.replace(MANIFEST, "<application", '<uses-permission-sdk-23 android:name="android.permission.CAMERA" /><application')
        self.assert_blocks("MAN003", "medium")
        self.setUp()
        self.policy(permissions={**POLICY["permissions"], "android.permission.WAKE_LOCK": "Old entry."})
        self.assertEqual(self.check(), ([("MAN003", "low")], 0))

    def test_file_provider(self) -> None:
        self.replace(MANIFEST, 'android:grantUriPermissions="true"', 'android:grantUriPermissions="false"')
        self.assert_blocks("MAN004", "medium")
        for paths in ('<root-path name="r" path="x" />', '<files-path name="f" path="." />', '<cache-path name="c" />'):
            with self.subTest(paths):
                self.setUp()
                self.write(f"{XML_DIR}/provider_paths.xml", f"<paths>{paths}</paths>")
                self.assert_blocks("MAN004", "high")


class NetworkAndBackupTest(FixtureTest):
    NSC = f"{XML_DIR}/network_security_config.xml"

    def test_user_certificates_and_pin_overrides(self) -> None:
        self.replace(self.NSC, '<certificates src="system" />', '<certificates src="user" overridePins="true" />')
        findings, code = self.check()
        self.assertEqual((sorted(findings), code), ([("NSC001", "high"), ("NSC003", "medium")], 1))

    def test_domain_config_user_certificates(self) -> None:
        domain = '<domain-config><domain>example.com</domain><trust-anchors><certificates src="user" /></trust-anchors></domain-config>'
        self.replace(self.NSC, "<debug-overrides>", domain + "<debug-overrides>")
        self.assert_blocks("NSC001", "high")

    def test_global_cleartext_requires_an_accepted_exception(self) -> None:
        self.replace(self.NSC, 'cleartextTrafficPermitted="false"', 'cleartextTrafficPermitted="true"')
        self.assert_blocks("NSC002", "medium")
        self.policy(accepted_exceptions={"nsc-base-cleartext": "Reviewed."})
        self.assertEqual(self.check(), ([("NSC002", "info")], 0))

    def test_missing_backup_exclusions(self) -> None:
        for rel, element in [("backup_rules.xml", "full-backup-content"), ("data_extraction_rules.xml", "device-transfer")]:
            with self.subTest(rel):
                self.setUp()
                self.replace(f"{XML_DIR}/{rel}", f'<{element}><exclude domain="file" path="secure/" />', f"<{element}>")
                self.assert_blocks("BAK001", "high")


class BuildTest(FixtureTest):
    def test_debuggable_non_debug_build_types(self) -> None:
        for change in ("isMinifyEnabled = true\n            isDebuggable = true", "isMinifyEnabled = true\n            isJniDebuggable = true"):
            with self.subTest(change):
                self.setUp()
                self.replace(APP_GRADLE, "isMinifyEnabled = true", change)
                self.assert_blocks("BLD001", "critical")
        self.setUp()
        self.replace(APP_GRADLE, "debug { isDebuggable = true }", 'create("staging") { isDebuggable = true }')
        self.assert_blocks("BLD001", "critical")

    def test_release_must_be_minified(self) -> None:
        for old, new in [("isMinifyEnabled = true", "isMinifyEnabled = false"), ('named("release")', 'named("beta")')]:
            with self.subTest(new):
                self.setUp()
                self.replace(APP_GRADLE, old, new)
                self.assert_blocks("BLD002", "medium")

    def test_hardcoded_signing_passwords(self) -> None:
        self.replace(APP_GRADLE, 'System.getenv("KS_PASS") ?: ""', '"hunter2"')
        self.assert_blocks("BLD004", "critical")
        self.setUp()
        self.write("gradle.properties", 'keyPassword = "hunter2"\n')
        self.assert_blocks("BLD004", "critical")
        self.setUp()
        self.replace(APP_GRADLE, 'System.getenv("KS_PASS") ?: ""', '"$ksPass"')
        self.assertEqual(self.check(), ([], 0))

    def test_release_log_stripping(self) -> None:
        self.replace("app/proguard-rules.pro", "    public static int d(...);\n", "")
        self.assert_blocks("BLD003", "medium")

    def test_committed_keystore(self) -> None:
        self.write("signing/release.JKS", "not a real keystore")
        subprocess.run(["git", "add", "signing/release.JKS"], cwd=self.root, check=True)  # nosec B603 B607
        self.assert_blocks("REPO001", "critical")


class SourceTest(FixtureTest):
    def test_insecure_source_patterns(self) -> None:
        cases = {
            "SRC001": 'web.addJavascriptInterface(bridge, "app")',
            "SRC002": "web.settings.allowUniversalAccessFromFileURLs = true",
            "SRC003": "val tm = object : X509TrustManager { }",
            "SRC004": 'session.setConfig("StrictHostKeyChecking", "no")',
            "SRC005": 'Cipher.getInstance("AES/ECB/PKCS5Padding")',
            "SRC006": "openFileOutput(name, MODE_WORLD_READABLE)",
            "SRC007": "DexClassLoader(path, dir, null, loader)",
            "SRC008": "PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE)",
            "SRC009": 'context.registerReceiver(receiver, IntentFilter("x"))',
        }
        for rule, line in cases.items():
            with self.subTest(rule):
                self.setUp()
                self.write("app/src/main/java/app/Bad.kt", f"package app\nfun bad() {{\n    {line}\n}}\n")
                findings, code = self.check()
                self.assertEqual([rule], sorted({r for r, _ in findings}))
                self.assertEqual(code, 1)

    def test_hostname_verifier_and_java_trust_manager(self) -> None:
        self.write("app/src/main/java/app/Bad.java", "class Bad implements javax.net.ssl.X509TrustManager {}\n")
        self.write("app/src/main/java/app/Bad.kt", "val verifier = HostnameVerifier { _, _ -> true }\n")
        findings, _ = self.check()
        self.assertEqual(findings.count(("SRC003", "critical")), 2)

    def test_exported_dynamic_receiver(self) -> None:
        self.write("app/src/main/java/app/Bad.kt", 'fun f() = context.registerReceiver(r, IntentFilter("x"), Context.RECEIVER_EXPORTED)\n')
        self.assert_blocks("SRC009", "medium")

    def test_reviewed_exceptions_are_honored(self) -> None:
        self.write(
            "app/src/main/java/app/Reviewed.kt",
            """package app
fun reviewed() {
    // security-policy: allow SRC001 -- bridge only exposes a read-only version string
    web.addJavascriptInterface(bridge, "app")
    context.registerReceiver(r, IntentFilter("x"))  // security-policy: allow SRC009 -- protected system broadcast
}
""",
        )
        self.assertEqual(self.check(), ([], 0))

    def test_exception_requires_matching_rule_and_reason(self) -> None:
        for marker in ("// security-policy: allow SRC002 -- wrong rule", "// security-policy: allow SRC001"):
            with self.subTest(marker):
                self.setUp()
                self.write("app/src/main/java/app/Bad.kt", f'{marker}\nweb.addJavascriptInterface(bridge, "app")\n')
                self.assert_blocks("SRC001", "high")


class ReportTest(FixtureTest):
    def test_actions_annotations_and_step_summary(self) -> None:
        self.replace(MANIFEST, 'android:allowBackup="true"', 'android:allowBackup="true" android:debuggable="true"')
        self.policy(permissions={**POLICY["permissions"], "android.permission.WAKE_LOCK": "Old entry."})
        checker = security.Checker(self.root)
        checker.check_manifest()
        summary = self.root / "summary.md"
        output = io.StringIO()
        env = {"GITHUB_ACTIONS": "true", "GITHUB_STEP_SUMMARY": str(summary)}
        with mock.patch.dict(os.environ, env, clear=True), contextlib.redirect_stdout(output):
            code = checker.report()
        self.assertEqual(code, 1)
        text = output.getvalue()
        self.assertIn(f"::error file={MANIFEST},title=MAN001 (critical)::", text)
        self.assertIn(f"::warning file={MANIFEST},title=MAN003 (low)::", text)
        self.assertIn("2 finding(s), 1 blocking.", text)
        self.assertIn("| critical | MAN001 |", summary.read_text(encoding="utf-8"))
        self.assertIn("Blocking findings (critical/high/medium): **1**", summary.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main(verbosity=2)

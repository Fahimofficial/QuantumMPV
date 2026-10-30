#!/usr/bin/env python3
"""Android application security policy gate for QuantumMPV.

Checks the source manifest, XML security configuration, Gradle build types and
Kotlin/Java sources against the reviewed baseline in
config/security/android-security-policy.json.

Findings at severity critical, high or medium fail the run. Low and info
findings are reported but do not fail it. A source-code finding can be accepted
by putting a reviewed justification on the same line or the line above:

    // security-policy: allow <rule-id> -- <reason>

Usage: python3 tools/check_android_security.py [--root PATH]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
BLOCKING = {"critical", "high", "medium"}
SEVERITY_ORDER = ["critical", "high", "medium", "low", "info"]


@dataclass
class Finding:
    rule: str
    severity: str
    message: str
    path: str | None = None
    line: int | None = None


class Checker:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.findings: list[Finding] = []
        policy_path = root / "config/security/android-security-policy.json"
        self.policy = json.loads(policy_path.read_text(encoding="utf-8"))

    def add(self, rule: str, severity: str, message: str, path: str | None = None, line: int | None = None) -> None:
        self.findings.append(Finding(rule, severity, message, path, line))

    def parse_xml(self, rel: str) -> ET.Element | None:
        try:
            return ET.parse(self.root / rel).getroot()
        except (ET.ParseError, OSError) as error:
            self.add("XML000", "high", f"Could not parse security-relevant XML: {error}", rel)
            return None

    # ------------------------------------------------------------------ manifest
    def check_manifest(self) -> None:
        manifest_rel = self.policy["manifest"]
        root = self.parse_xml(manifest_rel)
        if root is None:
            return
        app = root.find("application")
        if app is None:
            self.add("MAN000", "high", "Manifest has no <application> element.", manifest_rel)
            return

        if app.get(ANDROID + "debuggable") == "true":
            self.add("MAN001", "critical", "android:debuggable=\"true\" is hardcoded in the manifest.", manifest_rel)
        if app.get(ANDROID + "usesCleartextTraffic") == "true":
            self.add(
                "MAN005",
                "medium",
                "android:usesCleartextTraffic=\"true\" bypasses the network security config; "
                "configure cleartext in network_security_config.xml instead.",
                manifest_rel,
            )
        if app.get(ANDROID + "allowBackup", "true") == "true":
            if not app.get(ANDROID + "dataExtractionRules") or not app.get(ANDROID + "fullBackupContent"):
                self.add(
                    "MAN006",
                    "high",
                    "Backup is enabled but dataExtractionRules and fullBackupContent are not both set.",
                    manifest_rel,
                )

        # Exported components must match the reviewed allowlist exactly.
        allowed = self.policy["exported_components"]
        exported: set[str] = set()
        for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
            for component in app.iter(tag):
                name = component.get(ANDROID + "name", "")
                flag = component.get(ANDROID + "exported")
                has_filters = component.find("intent-filter") is not None
                if flag is None and has_filters:
                    self.add(
                        "MAN002",
                        "high",
                        f"{tag} {name} has intent filters but no explicit android:exported value.",
                        manifest_rel,
                    )
                if flag == "true":
                    exported.add(name)
                if tag == "provider" and flag == "true":
                    self.add("MAN004", "high", f"Content provider {name} is exported.", manifest_rel)
        for name in sorted(exported - allowed.keys()):
            self.add(
                "MAN002",
                "medium",
                f"New exported component {name} is not in the reviewed allowlist. Review its intent "
                "handling and add a justification to config/security/android-security-policy.json.",
                manifest_rel,
            )
        for name in sorted(allowed.keys() - exported):
            self.add(
                "MAN002",
                "low",
                f"Allowlisted component {name} is no longer exported; remove it from the policy file.",
                manifest_rel,
            )

        # Permissions must match the reviewed allowlist.
        allowed_permissions = self.policy["permissions"]
        declared = {
            p.get(ANDROID + "name", "")
            for p in list(root.iter("uses-permission")) + list(root.iter("uses-permission-sdk-23"))
        }
        for name in sorted(declared - allowed_permissions.keys()):
            self.add(
                "MAN003",
                "medium",
                f"New permission {name} is not in the reviewed allowlist. Confirm it is necessary, "
                "update PRIVACY.md, and add a justification to the policy file.",
                manifest_rel,
            )
        for name in sorted(allowed_permissions.keys() - declared):
            self.add("MAN003", "low", f"Allowlisted permission {name} is no longer declared; remove it from the policy file.", manifest_rel)

        # FileProvider configuration.
        for provider in app.iter("provider"):
            if provider.get(ANDROID + "name") != "androidx.core.content.FileProvider":
                continue
            if provider.get(ANDROID + "grantUriPermissions") != "true":
                self.add("MAN004", "medium", "FileProvider must set android:grantUriPermissions=\"true\".", manifest_rel)
            for meta in provider.iter("meta-data"):
                resource = meta.get(ANDROID + "resource", "")
                if resource.startswith("@xml/"):
                    self.check_file_provider_paths(f"app/src/main/res/xml/{resource[5:]}.xml")

        nsc = app.get(ANDROID + "networkSecurityConfig", "")
        if nsc.startswith("@xml/"):
            self.check_network_security_config(f"app/src/main/res/xml/{nsc[5:]}.xml")
        else:
            self.add("NSC000", "medium", "No networkSecurityConfig is declared.", manifest_rel)

        self.check_backup_rules()

    def check_file_provider_paths(self, rel: str) -> None:
        root = self.parse_xml(rel)
        if root is None:
            return
        for element in root:
            path = (element.get("path") or "").strip()
            if element.tag == "root-path":
                self.add("MAN004", "high", "FileProvider exposes root-path.", rel)
            elif path in ("", ".", "/"):
                self.add("MAN004", "high", f"FileProvider <{element.tag}> shares the whole directory (path=\"{path}\").", rel)

    def check_network_security_config(self, rel: str) -> None:
        root = self.parse_xml(rel)
        if root is None:
            return
        for section in ("base-config", "domain-config"):
            for config in root.iter(section):
                for cert in config.iter("certificates"):
                    if cert.get("src") == "user":
                        self.add(
                            "NSC001",
                            "high",
                            f"{section} trusts user-installed CAs; restrict them to <debug-overrides>.",
                            rel,
                        )
                for cert in config.iter("certificates"):
                    if cert.get("overridePins") == "true":
                        self.add("NSC003", "medium", f"{section} overrides certificate pins.", rel)
        base = root.find("base-config")
        if base is not None and base.get("cleartextTrafficPermitted") == "true":
            if "nsc-base-cleartext" in self.policy.get("accepted_exceptions", {}):
                self.add("NSC002", "info", "Cleartext traffic is permitted globally (accepted exception nsc-base-cleartext).", rel)
            else:
                self.add("NSC002", "medium", "base-config permits cleartext traffic without an accepted exception.", rel)

    def check_backup_rules(self) -> None:
        for rel, required in self.policy["backup_exclusions"].items():
            root = self.parse_xml(rel)
            if root is None:
                continue
            sections = list(root.findall("cloud-backup")) + list(root.findall("device-transfer"))
            if not sections:
                sections = [root]
            for section in sections:
                excluded = {(e.get("domain"), e.get("path")) for e in section.iter("exclude")}
                label = section.tag
                for domain, path in required:
                    if (domain, path) not in excluded:
                        self.add("BAK001", "high", f"{label} does not exclude {domain}:{path}.", rel)

    # --------------------------------------------------------------------- build
    def check_build(self) -> None:
        rel = "app/build.gradle.kts"
        text = (self.root / rel).read_text(encoding="utf-8")
        for match in re.finditer(r"\bis(?:Jni)?Debuggable\s*=\s*true", text):
            preceding = text[: match.start()]
            blocks = re.findall(r'(?:named|create|getByName)\("([^"]+)"\)', preceding)
            block = blocks[-1] if blocks else "<unknown>"
            if block != "debug":
                self.add("BLD001", "critical", f"Build type '{block}' is debuggable.", rel, text.count("\n", 0, match.start()) + 1)
        release = re.search(r'named\("release"\)\s*\{(?P<body>.*?)\n    \}', text, re.DOTALL)
        if not release or "isMinifyEnabled = true" not in release.group("body"):
            self.add("BLD002", "medium", "Release build type must keep isMinifyEnabled = true.", rel)

        for gradle_file in [self.root / "build.gradle.kts", self.root / "app/build.gradle.kts", self.root / "gradle.properties"]:
            content = gradle_file.read_text(encoding="utf-8")
            for match in re.finditer(r'\b(storePassword|keyPassword)\s*=\s*"[^"$]', content):
                self.add(
                    "BLD004",
                    "critical",
                    f"Hardcoded {match.group(1)} in Gradle configuration.",
                    str(gradle_file.relative_to(self.root)),
                    content.count("\n", 0, match.start()) + 1,
                )

        proguard_rel = "app/proguard-rules.pro"
        proguard = (self.root / proguard_rel).read_text(encoding="utf-8")
        log_block = re.search(r"-assumenosideeffects class android\.util\.Log\s*\{(?P<body>[^}]*)\}", proguard)
        if not log_block or not all(f" {level}(" in log_block.group("body") for level in ("v", "d")):
            self.add("BLD003", "medium", "Release R8 rules must strip Log.v and Log.d calls (they log stream URLs and paths).", proguard_rel)

        tracked = subprocess.run(["git", "ls-files"], cwd=self.root, capture_output=True, text=True, check=True).stdout.splitlines()
        for path in tracked:
            if re.search(r"\.(jks|keystore|p12|pfx)$", path, re.IGNORECASE):
                self.add("REPO001", "critical", "A signing keystore is committed to the repository. Remove it and rotate the key.", path)

    # -------------------------------------------------------------------- source
    SOURCE_RULES = [
        ("SRC001", "high", r"\baddJavascriptInterface\s*\(", "WebView JavaScript bridge exposes app code to web content."),
        ("SRC002", "high", r"(?:setAllow(?:Universal|File)AccessFromFileURLs\s*\(\s*true|allow(?:Universal|File)AccessFromFileURLs\s*=\s*true)", "WebView allows file:// pages to read other origins."),
        ("SRC003", "critical", r"(?::\s*(?:[\w.]+\.)?X509(?:Extended)?TrustManager\b|implements\s+(?:[\w.]+\.)?X509(?:Extended)?TrustManager\b)", "Custom X509TrustManager implementation; this commonly disables certificate validation."),
        ("SRC003", "critical", r"(?:HostnameVerifier\s*\{\s*\w+\s*,\s*\w+\s*->\s*true\s*\}|hostnameVerifier\s*\{\s*\w+\s*,\s*\w+\s*->\s*true\s*\}|ALLOW_ALL_HOSTNAME_VERIFIER|NoopHostnameVerifier)", "Hostname verification is disabled."),
        ("SRC004", "high", r"StrictHostKeyChecking\"\s*,\s*\"no\"", "SSH host-key checking is disabled."),
        ("SRC005", "high", r"Cipher\.getInstance\(\s*\"(?:AES\"|AES/ECB|DES|DESede|RC4|Blowfish)", "Weak cipher or ECB mode."),
        ("SRC006", "high", r"\bMODE_WORLD_(?:READABLE|WRITEABLE)\b", "World-accessible file mode."),
        ("SRC007", "high", r"\b(?:DexClassLoader|InMemoryDexClassLoader)\b", "Dynamic code loading."),
    ]

    def check_sources(self) -> None:
        source_root = self.root / "app/src/main"
        files = sorted(list(source_root.rglob("*.kt")) + list(source_root.rglob("*.java")))
        for path in files:
            rel = str(path.relative_to(self.root))
            text = path.read_text(encoding="utf-8", errors="replace")
            lines = text.splitlines()
            for rule, severity, pattern, message in self.SOURCE_RULES:
                for match in re.finditer(pattern, text):
                    line = text.count("\n", 0, match.start()) + 1
                    if not self.allowed(lines, line, rule):
                        self.add(rule, severity, message, rel, line)
            self.check_pending_intents(rel, text, lines)
            self.check_receivers(rel, text, lines)

    @staticmethod
    def call_arguments(text: str, open_paren: int) -> str:
        depth = 0
        for index in range(open_paren, min(len(text), open_paren + 2000)):
            char = text[index]
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    return text[open_paren + 1 : index]
        return text[open_paren + 1 : open_paren + 2000]

    def check_pending_intents(self, rel: str, text: str, lines: list[str]) -> None:
        for match in re.finditer(r"PendingIntent\.get(?:Activity|Activities|Service|ForegroundService|Broadcast)\s*\(", text):
            args = self.call_arguments(text, match.end() - 1)
            line = text.count("\n", 0, match.start()) + 1
            if "FLAG_IMMUTABLE" in args:
                continue
            if self.allowed(lines, line, "SRC008"):
                continue
            detail = "uses FLAG_MUTABLE" if "FLAG_MUTABLE" in args else "does not pass FLAG_IMMUTABLE"
            self.add("SRC008", "medium", f"PendingIntent {detail}; mutable PendingIntents can be hijacked.", rel, line)

    def check_receivers(self, rel: str, text: str, lines: list[str]) -> None:
        for match in re.finditer(r"\bregisterReceiver\s*\(", text):
            prefix = text[max(0, match.start() - 4) : match.start()]
            if prefix.endswith("fun "):
                continue
            args = self.call_arguments(text, match.end() - 1).strip()
            line = text.count("\n", 0, match.start()) + 1
            first = re.split(r",", args, maxsplit=1)[0].strip()
            # registerReceiver(null, filter) only reads a sticky broadcast.
            if first == "null" or re.match(r"^\w+\s*,\s*null\s*,", args):
                continue
            if "RECEIVER_NOT_EXPORTED" in args or "RECEIVER_EXPORTED" in args:
                if "RECEIVER_EXPORTED" in args and "RECEIVER_NOT_EXPORTED" not in args and not self.allowed(lines, line, "SRC009"):
                    self.add("SRC009", "medium", "Dynamic receiver is registered as RECEIVER_EXPORTED.", rel, line)
                continue
            if self.allowed(lines, line, "SRC009"):
                continue
            self.add("SRC009", "medium", "Dynamic receiver registered without RECEIVER_NOT_EXPORTED.", rel, line)

    @staticmethod
    def allowed(lines: list[str], line: int, rule: str) -> bool:
        marker = re.compile(rf"security-policy:\s*allow\s+{re.escape(rule)}\b\s*--\s*\S")
        candidates = [lines[line - 1]] if 0 < line <= len(lines) else []
        if line >= 2:
            candidates.append(lines[line - 2])
        return any(marker.search(candidate) for candidate in candidates)

    # -------------------------------------------------------------------- report
    def report(self) -> int:
        ordered = sorted(self.findings, key=lambda f: (SEVERITY_ORDER.index(f.severity), f.rule, f.path or "", f.line or 0))
        blocking = [f for f in ordered if f.severity in BLOCKING]
        in_actions = os.environ.get("GITHUB_ACTIONS") == "true"
        for finding in ordered:
            location = finding.path or "-"
            if finding.line:
                location += f":{finding.line}"
            print(f"[{finding.severity.upper():8}] {finding.rule} {location}: {finding.message}")
            if in_actions:
                level = "error" if finding.severity in BLOCKING else ("warning" if finding.severity == "low" else "notice")
                props = []
                if finding.path:
                    props.append(f"file={finding.path}")
                if finding.line:
                    props.append(f"line={finding.line}")
                props.append(f"title={finding.rule} ({finding.severity})")
                print(f"::{level} {','.join(props)}::{finding.message}")

        summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary_path:
            with open(summary_path, "a", encoding="utf-8") as summary:
                summary.write("## Android security policy\n\n")
                if not ordered:
                    summary.write("No findings.\n")
                else:
                    summary.write("| Severity | Rule | Location | Finding |\n| --- | --- | --- | --- |\n")
                    for finding in ordered:
                        location = (finding.path or "-") + (f":{finding.line}" if finding.line else "")
                        summary.write(f"| {finding.severity} | {finding.rule} | `{location}` | {finding.message} |\n")
                summary.write(f"\nBlocking findings (critical/high/medium): **{len(blocking)}**\n")

        print(f"\n{len(ordered)} finding(s), {len(blocking)} blocking.")
        if blocking:
            print("Android security policy gate FAILED. Fix the findings above or, for a reviewed exception, update "
                  "config/security/android-security-policy.json / add a justified 'security-policy: allow' comment.")
            return 1
        print("Android security policy gate passed.")
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    checker = Checker(args.root.resolve())
    checker.check_manifest()
    checker.check_build()
    checker.check_sources()
    return checker.report()


if __name__ == "__main__":
    sys.exit(main())

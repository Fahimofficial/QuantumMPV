#!/usr/bin/env python3
"""Android application security policy gate for QuantumMPV."""
# Checks the source manifest, XML security configuration, Gradle build types and
# Kotlin/Java sources against the reviewed baseline in
# config/security/android-security-policy.json.
#
# Findings at severity critical, high or medium fail the run. Low and info
# findings are reported but do not fail it. A source-code finding can be accepted
# by putting a reviewed justification on the same line or the line above:
#
#     // security-policy: allow <rule-id> -- <reason>
#
# Usage: python3 tools/check_android_security.py [--root PATH]
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess  # nosec B404 - only runs a fixed `git ls-files` command
import sys
from xml.etree import ElementTree  # nosec B405 - input is guarded against DTDs/entities in parse_xml
from dataclasses import dataclass
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
BLOCKING = {"critical", "high", "medium"}
SEVERITY_ORDER = ["critical", "high", "medium", "low", "info"]
# The spellings AAPT2 compiles to true (ResourceUtils::ParseBool), after trimming whitespace.
ANDROID_TRUE_VALUES = frozenset(("true", "TRUE", "True"))


def is_android_true(value: str | None, default: bool = False) -> bool:
    """Return whether an XML boolean attribute is true as Android reads it; ``default`` if absent."""
    if value is None:
        return default
    return value.strip() in ANDROID_TRUE_VALUES


@dataclass
class Finding:
    rule: str
    severity: str
    message: str
    path: str | None = None
    line: int | None = None


class Checker:
    def __init__(self, root: Path) -> None:
        """Load the reviewed policy baseline from the repository at ``root``."""
        self.root = root
        self.findings: list[Finding] = []
        policy_path = root / "config/security/android-security-policy.json"
        self.policy = json.loads(policy_path.read_text(encoding="utf-8"))

    def add(self, rule: str, severity: str, message: str, path: str | None = None, line: int | None = None) -> None:
        self.findings.append(Finding(rule, severity, message, path, line))

    def parse_xml(self, rel: str) -> ElementTree.Element | None:
        try:
            text = (self.root / rel).read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as error:
            self.add("XML000", "high", f"Could not read security-relevant XML: {error}", rel)
            return None
        # Android resource XML never needs a DTD. Rejecting DOCTYPE/ENTITY
        # declarations rules out entity-expansion and external-entity attacks
        # from a crafted pull request without adding a dependency.
        if "<!DOCTYPE" in text or "<!ENTITY" in text:
            self.add("XML001", "high", "XML declares a DOCTYPE or ENTITY; Android resource XML must not.", rel)
            return None
        try:
            return ElementTree.fromstring(text)  # nosec B314 # nosemgrep: use-defused-xml-parse - DTD/entities rejected above
        except ElementTree.ParseError as error:
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
        self.check_application_flags(app, manifest_rel)
        self.check_backup_references(app, manifest_rel)
        self.check_exported_components(app, manifest_rel)
        self.check_permissions(root, manifest_rel)
        self.check_file_providers(app, manifest_rel)
        self.check_network_security_reference(app, manifest_rel)
        self.check_backup_rules()

    def check_application_flags(self, app: ElementTree.Element, rel: str) -> None:
        if is_android_true(app.get(ANDROID + "debuggable")):
            self.add("MAN001", "critical", "android:debuggable=\"true\" is hardcoded in the manifest.", rel)
        if is_android_true(app.get(ANDROID + "usesCleartextTraffic")):
            self.add(
                "MAN005",
                "medium",
                "android:usesCleartextTraffic=\"true\" bypasses the network security config; "
                "configure cleartext in network_security_config.xml instead.",
                rel,
            )
        backup_enabled = is_android_true(app.get(ANDROID + "allowBackup"), default=True)
        backup_rules_set = bool(app.get(ANDROID + "dataExtractionRules")) and bool(app.get(ANDROID + "fullBackupContent"))
        if backup_enabled and not backup_rules_set:
            self.add(
                "MAN006",
                "high",
                "Backup is enabled but dataExtractionRules and fullBackupContent are not both set.",
                rel,
            )

    def check_backup_references(self, app: ElementTree.Element, rel: str) -> None:
        """The backup rules the manifest uses must be the ones the policy checks."""
        # Checked even with allowBackup="false": on Android 12+ dataExtractionRules
        # still governs device-to-device transfer.
        for attribute in ("dataExtractionRules", "fullBackupContent"):
            value = app.get(ANDROID + attribute, "")
            if not value.startswith("@xml/"):
                continue
            target = f"app/src/main/res/xml/{value[5:]}.xml"
            if target not in self.policy["backup_exclusions"]:
                self.add(
                    "BAK002",
                    "high",
                    f"android:{attribute} points to {target}, which has no backup_exclusions entry in the policy file.",
                    rel,
                )

    def check_exported_components(self, app: ElementTree.Element, rel: str) -> None:
        """Exported components must match the reviewed allowlist exactly."""
        allowed = self.policy["exported_components"]
        exported = self.collect_exported_components(app, rel)
        for name in sorted(exported - allowed.keys()):
            self.add(
                "MAN002",
                "medium",
                f"New exported component {name} is not in the reviewed allowlist. Review its intent "
                "handling and add a justification to config/security/android-security-policy.json.",
                rel,
            )
        for name in sorted(allowed.keys() - exported):
            self.add(
                "MAN002",
                "low",
                f"Allowlisted component {name} is no longer exported; remove it from the policy file.",
                rel,
            )

    def collect_exported_components(self, app: ElementTree.Element, rel: str) -> set[str]:
        exported: set[str] = set()
        for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
            for component in app.iter(tag):
                name = component.get(ANDROID + "name", "")
                flag = component.get(ANDROID + "exported")
                if flag is None and component.find("intent-filter") is not None:
                    self.add(
                        "MAN002",
                        "high",
                        f"{tag} {name} has intent filters but no explicit android:exported value.",
                        rel,
                    )
                if is_android_true(flag):
                    exported.add(name)
                    if tag == "provider":
                        self.add("MAN004", "high", f"Content provider {name} is exported.", rel)
        return exported

    def check_permissions(self, root: ElementTree.Element, rel: str) -> None:
        """Declared permissions must match the reviewed allowlist."""
        allowed = self.policy["permissions"]
        declared = {
            p.get(ANDROID + "name", "")
            for p in list(root.iter("uses-permission")) + list(root.iter("uses-permission-sdk-23"))
        }
        for name in sorted(declared - allowed.keys()):
            self.add(
                "MAN003",
                "medium",
                f"New permission {name} is not in the reviewed allowlist. Confirm it is necessary, "
                "update PRIVACY.md, and add a justification to the policy file.",
                rel,
            )
        for name in sorted(allowed.keys() - declared):
            self.add("MAN003", "low", f"Allowlisted permission {name} is no longer declared; remove it from the policy file.", rel)

    def check_file_providers(self, app: ElementTree.Element, rel: str) -> None:
        for provider in app.iter("provider"):
            if provider.get(ANDROID + "name") != "androidx.core.content.FileProvider":
                continue
            if not is_android_true(provider.get(ANDROID + "grantUriPermissions")):
                self.add("MAN004", "medium", "FileProvider must set android:grantUriPermissions=\"true\".", rel)
            for meta in provider.iter("meta-data"):
                resource = meta.get(ANDROID + "resource", "")
                if resource.startswith("@xml/"):
                    self.check_file_provider_paths(f"app/src/main/res/xml/{resource[5:]}.xml")

    def check_network_security_reference(self, app: ElementTree.Element, rel: str) -> None:
        nsc = app.get(ANDROID + "networkSecurityConfig", "")
        if nsc.startswith("@xml/"):
            self.check_network_security_config(f"app/src/main/res/xml/{nsc[5:]}.xml")
        else:
            self.add("NSC000", "medium", "No networkSecurityConfig is declared.", rel)

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
                self.check_trust_anchors(config, section, rel)
        self.check_base_cleartext(root, rel)

    def check_trust_anchors(self, config: ElementTree.Element, section: str, rel: str) -> None:
        certificates = list(config.iter("certificates"))
        for cert in certificates:
            if cert.get("src") == "user":
                self.add(
                    "NSC001",
                    "high",
                    f"{section} trusts user-installed CAs; restrict them to <debug-overrides>.",
                    rel,
                )
        for cert in certificates:
            if is_android_true(cert.get("overridePins")):
                self.add("NSC003", "medium", f"{section} overrides certificate pins.", rel)

    def check_base_cleartext(self, root: ElementTree.Element, rel: str) -> None:
        base = root.find("base-config")
        if base is None or not is_android_true(base.get("cleartextTrafficPermitted")):
            return
        if "nsc-base-cleartext" in self.policy.get("accepted_exceptions", {}):
            self.add("NSC002", "info", "Cleartext traffic is permitted globally (accepted exception nsc-base-cleartext).", rel)
        else:
            self.add("NSC002", "medium", "base-config permits cleartext traffic without an accepted exception.", rel)

    def check_backup_rules(self) -> None:
        for rel, required in self.policy["backup_exclusions"].items():
            root = self.parse_xml(rel)
            if root is None:
                continue
            for section in self.backup_sections(root, rel):
                excluded = {(e.get("domain"), e.get("path")) for e in section.iter("exclude")}
                label = section.tag
                for domain, path in required:
                    if (domain, path) not in excluded:
                        self.add("BAK001", "high", f"{label} does not exclude {domain}:{path}.", rel)

    def backup_sections(self, root: ElementTree.Element, rel: str) -> list[ElementTree.Element]:
        """Return the sections whose exclusions must be checked, reporting missing ones."""
        if root.tag != "data-extraction-rules":
            return [root]
        sections = []
        for tag in ("cloud-backup", "device-transfer"):
            found = root.findall(tag)
            if not found:
                # Android applies no rules, i.e. includes all app data, to a missing section.
                self.add("BAK001", "high", f"No <{tag}> section, so {tag} includes all app data.", rel)
            sections.extend(found)
        return sections

    # --------------------------------------------------------------------- build
    @staticmethod
    def build_type_blocks(text: str) -> dict[str, str]:
        """Return the body of each build type in the android.buildTypes block, by brace matching."""
        # Accepts named("x") { }, getByName("x") { }, create("x") { }, register("x") { } and x { }.
        start = re.search(r"\bbuildTypes\s*\{", text)
        if not start:
            return {}

        def block_end(open_index: int) -> int:
            depth = 0
            for index in range(open_index, len(text)):
                if text[index] == "{":
                    depth += 1
                elif text[index] == "}":
                    depth -= 1
                    if depth == 0:
                        return index
            return len(text)

        outer_open = start.end() - 1
        outer_close = block_end(outer_open)
        blocks: dict[str, str] = {}
        cursor = outer_open + 1
        pattern = re.compile(r'(?:(?:named|getByName|create|register|maybeCreate)\(\s*"(\w+)"\s*\)|\b(debug|release)\b)\s*\{')
        while True:
            match = pattern.search(text, cursor, outer_close)
            if not match:
                return blocks
            open_index = match.end() - 1
            close_index = block_end(open_index)
            blocks[match.group(1) or match.group(2)] = text[open_index + 1 : close_index]
            cursor = close_index + 1

    def check_build(self) -> None:
        self.check_build_types("app/build.gradle.kts")
        self.check_signing_passwords()
        self.check_log_stripping("app/proguard-rules.pro")
        self.check_committed_keystores()

    def check_build_types(self, rel: str) -> None:
        text = (self.root / rel).read_text(encoding="utf-8")
        blocks = self.build_type_blocks(text)
        for name, body in blocks.items():
            if name != "debug" and re.search(r"\bis(?:Jni)?Debuggable\s*=\s*true", body):
                self.add("BLD001", "critical", f"Build type '{name}' is debuggable.", rel)
        if "release" not in blocks:
            self.add("BLD002", "medium", "Could not find the release build type in android.buildTypes.", rel)
        elif not re.search(r"\bisMinifyEnabled\s*=\s*true", blocks["release"]):
            self.add("BLD002", "medium", "Release build type must keep isMinifyEnabled = true.", rel)

    # Kotlin DSL: a string literal that is not a template, e.g. storePassword = "secret".
    KTS_PASSWORD = re.compile(r'\b(storePassword|keyPassword)\s*=\s*"[^"$]')
    # Properties files: every value is a literal, e.g. storePassword=secret or
    # android.injected.signing.store.password=secret.
    PROPERTIES_PASSWORD = re.compile(r"^[ \t]*([\w.-]*(?:store|key)[\w.-]*password)[ \t]*[=:][ \t]*\S", re.IGNORECASE | re.MULTILINE)

    def check_signing_passwords(self) -> None:
        sources = [("build.gradle.kts", self.KTS_PASSWORD), ("app/build.gradle.kts", self.KTS_PASSWORD), ("gradle.properties", self.PROPERTIES_PASSWORD)]
        for rel, pattern in sources:
            content = (self.root / rel).read_text(encoding="utf-8")
            for match in pattern.finditer(content):
                self.add(
                    "BLD004",
                    "critical",
                    f"Hardcoded {match.group(1)} in Gradle configuration.",
                    rel,
                    content.count("\n", 0, match.start()) + 1,
                )

    def check_log_stripping(self, rel: str) -> None:
        # Drop comments first, so a commented-out rule does not count.
        proguard = re.sub(r"#[^\n]*", "", (self.root / rel).read_text(encoding="utf-8"))
        log_block = re.search(r"-assumenosideeffects class android\.util\.Log\s*\{(?P<body>[^}]*)\}", proguard)
        if not log_block or not all(f" {level}(" in log_block.group("body") for level in ("v", "d")):
            self.add("BLD003", "medium", "Release R8 rules must strip Log.v and Log.d calls (they log stream URLs and paths).", rel)

    def check_committed_keystores(self) -> None:
        tracked = subprocess.run(  # nosec B603 B607 - fixed argument list, no shell
            ["git", "ls-files"], cwd=self.root, capture_output=True, text=True, check=True
        ).stdout.splitlines()
        for path in tracked:
            if re.search(r"\.(jks|keystore|p12|pfx)$", path, re.IGNORECASE):
                self.add("REPO001", "critical", "A signing keystore is committed to the repository. Remove it and rotate the key.", path)

    # -------------------------------------------------------------------- source
    SOURCE_RULES = [
        ("SRC001", "high", r"\baddJavascriptInterface\s*\(", "WebView JavaScript bridge exposes app code to web content."),
        (
            "SRC002",
            "high",
            r"(?:setAllow(?:Universal|File)AccessFromFileURLs\s*\(\s*true"
            r"|allow(?:Universal|File)AccessFromFileURLs\s*=\s*true)",
            "WebView allows file:// pages to read other origins.",
        ),
        # Implementations only (object expressions, class supertypes, Java implements), not type annotations.
        (
            "SRC003",
            "critical",
            r"(?:\bobject\s*:\s*(?:[\w.]+\.)?X509(?:Extended)?TrustManager\b"
            r"|\bclass\s+\w+[^{\n]*?\)?\s*:\s*(?:[\w.]+\.)?X509(?:Extended)?TrustManager\b"
            r"|\bimplements\s+(?:[\w.]+\.)?X509(?:Extended)?TrustManager\b)",
            "Custom X509TrustManager implementation; this commonly disables certificate validation.",
        ),
        (
            "SRC003",
            "critical",
            r"(?:HostnameVerifier\s*\{\s*\w+\s*,\s*\w+\s*->\s*true\s*\}"
            r"|hostnameVerifier\s*\{\s*\w+\s*,\s*\w+\s*->\s*true\s*\}"
            r"|ALLOW_ALL_HOSTNAME_VERIFIER|NoopHostnameVerifier)",
            "Hostname verification is disabled.",
        ),
        ("SRC004", "high", r"StrictHostKeyChecking\"\s*,\s*\"no\"", "SSH host-key checking is disabled."),
        ("SRC005", "high", r"Cipher\.getInstance\(\s*\"(?:AES\"|AES/ECB|DES|DESede|RC4|Blowfish)", "Weak cipher or ECB mode."),
        ("SRC006", "high", r"\bMODE_WORLD_(?:READABLE|WRITEABLE)\b", "World-accessible file mode."),
        ("SRC007", "high", r"\b(?:DexClassLoader|InMemoryDexClassLoader)\b", "Dynamic code loading."),
    ]

    # Gradle test source sets: test, androidTest, and their variants (testDebug,
    # androidTestStandard, testFixtures). Names such as "testing" are not tests.
    TEST_SOURCE_SET = re.compile(r"^(?:test|androidTest)(?:[A-Z]\w*)?$")

    def source_files(self) -> list[Path]:
        """Kotlin/Java files of every source set that can be packaged (all but test sets)."""
        files: list[Path] = []
        for source_set in sorted((self.root / "app/src").iterdir()):
            if source_set.is_dir() and not self.TEST_SOURCE_SET.match(source_set.name):
                files.extend(source_set.rglob("*.kt"))
                files.extend(source_set.rglob("*.java"))
        return sorted(files)

    def check_sources(self) -> None:
        for path in self.source_files():
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
            problem = self.receiver_problem(self.call_arguments(text, match.end() - 1).strip())
            line = text.count("\n", 0, match.start()) + 1
            if problem and not self.allowed(lines, line, "SRC009"):
                self.add("SRC009", "medium", problem, rel, line)

    @staticmethod
    def receiver_problem(args: str) -> str | None:
        """Return why a registerReceiver(...) call is unsafe, or None if it is fine."""
        first = args.split(",", 1)[0].strip()
        # registerReceiver(null, filter) only reads a sticky broadcast.
        if first == "null" or re.match(r"^\w+\s*,\s*null\s*,", args):
            return None
        if "RECEIVER_NOT_EXPORTED" in args:
            return None
        if "RECEIVER_EXPORTED" in args:
            return "Dynamic receiver is registered as RECEIVER_EXPORTED."
        return "Dynamic receiver registered without RECEIVER_NOT_EXPORTED."

    @staticmethod
    def allowed(lines: list[str], line: int, rule: str) -> bool:
        marker = re.compile(rf"security-policy:\s*allow\s+{re.escape(rule)}\b\s*--\s*\S")
        candidates = [lines[line - 1]] if 0 < line <= len(lines) else []
        if line >= 2:
            candidates.append(lines[line - 2])
        return any(marker.search(candidate) for candidate in candidates)

    # -------------------------------------------------------------------- report
    @staticmethod
    def location(finding: Finding) -> str:
        return (finding.path or "-") + (f":{finding.line}" if finding.line else "")

    @staticmethod
    def annotation(finding: Finding) -> str:
        """Format a finding as a GitHub Actions workflow command."""
        level = "error" if finding.severity in BLOCKING else ("warning" if finding.severity == "low" else "notice")
        props = [f"file={finding.path}"] if finding.path else []
        if finding.line:
            props.append(f"line={finding.line}")
        props.append(f"title={finding.rule} ({finding.severity})")
        return f"::{level} {','.join(props)}::{finding.message}"

    def write_summary(self, summary_path: str, ordered: list[Finding], blocking: list[Finding]) -> None:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write("## Android security policy\n\n")
            if not ordered:
                summary.write("No findings.\n")
            else:
                summary.write("| Severity | Rule | Location | Finding |\n| --- | --- | --- | --- |\n")
                for finding in ordered:
                    summary.write(f"| {finding.severity} | {finding.rule} | `{self.location(finding)}` | {finding.message} |\n")
            summary.write(f"\nBlocking findings (critical/high/medium): **{len(blocking)}**\n")

    def report(self) -> int:
        ordered = sorted(self.findings, key=lambda f: (SEVERITY_ORDER.index(f.severity), f.rule, f.path or "", f.line or 0))
        blocking = [f for f in ordered if f.severity in BLOCKING]
        in_actions = os.environ.get("GITHUB_ACTIONS") == "true"
        for finding in ordered:
            print(f"[{finding.severity.upper():8}] {finding.rule} {self.location(finding)}: {finding.message}")
            if in_actions:
                print(self.annotation(finding))

        summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary_path:
            self.write_summary(summary_path, ordered, blocking)

        print(f"\n{len(ordered)} finding(s), {len(blocking)} blocking.")
        if blocking:
            print("Android security policy gate FAILED. Fix the findings above or, for a reviewed exception, update "
                  "config/security/android-security-policy.json / add a justified 'security-policy: allow' comment.")
            return 1
        print("Android security policy gate passed.")
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    checker = Checker(args.root.resolve())
    checker.check_manifest()
    checker.check_build()
    checker.check_sources()
    return checker.report()


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Fix LocalContext.getString calls reported by Compose lint in affected UI files."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
REPORT = Path("/tmp/quantummpv-lint/intermediates/lint_intermediate_text_report/standardDebug/lintReportStandardDebug/lint-results-standardDebug.txt")

if not REPORT.exists():
    raise SystemExit(f"Lint report not found: {REPORT}")

files = set()
for line in REPORT.read_text(encoding="utf-8").splitlines():
    if "LocalContextGetResourceValueCall" not in line or ": Error:" not in line:
        continue
    match = re.search(r"/app/src/main/java/(.+?\.kt):\d+: Error:", line)
    if match:
        files.add(ROOT / "app/src/main/java" / match.group(1))

changed = []
for path in sorted(files):
    if not path.exists():
        raise SystemExit(f"Affected file does not exist: {path}")
    text = path.read_text(encoding="utf-8")
    updated = text.replace("context.getString(", "stringResource(")
    if updated == text:
        continue
    if "import androidx.compose.ui.res.stringResource" not in updated:
        lines = updated.splitlines()
        import_indices = [i for i, line in enumerate(lines) if line.startswith("import ")]
        if not import_indices:
            raise SystemExit(f"Could not locate imports in {path}")
        lines.insert(import_indices[-1] + 1, "import androidx.compose.ui.res.stringResource")
        updated = "\n".join(lines) + ("\n" if text.endswith("\n") else "")
    path.write_text(updated, encoding="utf-8")
    changed.append(str(path.relative_to(ROOT)))

print(f"Updated {len(changed)} files")
for item in changed:
    print(item)

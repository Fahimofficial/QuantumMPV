#!/usr/bin/env bash
set -euo pipefail

# Report-only audit. Review every candidate before deleting it.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

./gradlew :app:lintStandardRelease \
  :app:dependencies --configuration standardReleaseRuntimeClasspath \
  :app:assembleStandardRelease \
  --no-daemon --max-workers=1

REPORT_DIR="app/build/reports"
mkdir -p "$REPORT_DIR/optimization"

if [[ -f app/build/outputs/mapping/standardRelease/mapping.txt ]]; then
  cp app/build/outputs/mapping/standardRelease/mapping.txt "$REPORT_DIR/optimization/r8-mapping.txt"
fi

if [[ -f app/build/reports/lint-results-standardRelease.xml ]]; then
  cp app/build/reports/lint-results-standardRelease.xml "$REPORT_DIR/optimization/lint-results-standardRelease.xml"
  echo "Unused/deprecated findings:"
  grep -E 'issue id="(UnusedResources|ObsoleteSdkInt|Deprecated|NewApi|UnusedIds|PrivateResource)"' \
    "$REPORT_DIR/optimization/lint-results-standardRelease.xml" || true
fi

if [[ -f app/build/reports/lint-results-standardRelease.html ]]; then
  cp app/build/reports/lint-results-standardRelease.html "$REPORT_DIR/optimization/lint-results-standardRelease.html"
fi

cat <<'EOF'

Audit complete. Review reports under app/build/reports/optimization/.
Do not delete a class solely because a text search found no references:
AndroidManifest entries, reflection, JNI, serializers, Room, Koin, and XML can keep code alive.
EOF

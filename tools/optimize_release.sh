#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

GRADLE_ARGS=(--no-daemon --max-workers="${GRADLE_MAX_WORKERS:-1}")

run_gradle() {
  ./gradlew "$@" "${GRADLE_ARGS[@]}"
}

printf '%s\n' '== Clean release analysis outputs =='
rm -rf app/build/outputs/mapping/standardRelease \
       app/build/reports/lint-results-standardRelease.* \
       app/build/reports/resources_config_map_file/standardRelease

printf '%s\n' '== Unit tests =='
run_gradle :app:testStandardDebugUnitTest

printf '%s\n' '== Kotlin style checks =='
run_gradle ktlintCheck

printf '%s\n' '== Release lint =='
# This is intentionally report-only while the project’s existing release lint debt is triaged.
run_gradle :app:lintStandardRelease || {
  printf '%s\n' 'Release lint reported findings; continuing so R8 and APK reports are still produced.' >&2
}

printf '%s\n' '== Optimized release APK =='
run_gradle :app:assembleStandardRelease

printf '%s\n' '== APK sizes =='
find app/build/outputs/apk -type f -name '*.apk' -printf '%s %p\n' \
  | sort -nr \
  | numfmt --field=1 --to=iec

printf '%s\n' '== R8 reports =='
find app/build/outputs/mapping/standardRelease -maxdepth 1 -type f \
  \( -name 'mapping.txt' -o -name 'usage.txt' -o -name 'seeds.txt' -o -name 'configuration.txt' \) \
  -printf '%p %s bytes\n' | sort

printf '%s\n' '== Largest APK entries =='
APK="$(find app/build/outputs/apk/standard/release -maxdepth 1 -type f -name '*.apk' | head -1)"
if [[ -n "${APK:-}" ]]; then
  unzip -l "$APK" | sort -k1,1nr | head -40
fi

printf '%s\n' '== Native library sizes =='
find app/src/main/jniLibs -type f -name '*.so' -printf '%s %p\n' \
  | sort -nr \
  | numfmt --field=1 --to=iec || true

printf '%s\n' '== Diff hygiene =='
git diff --check

echo 'Optimization analysis completed. No source or resource files were deleted automatically.'

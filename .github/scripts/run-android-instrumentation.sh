#!/usr/bin/env bash
set -Eeuo pipefail

dump_diagnostics() {
  local rc=$?
  if [[ "$rc" -ne 0 ]]; then
    echo "=== Emulator diagnostics after instrumentation failure ==="
    adb devices -l || true
    timeout 15s adb shell getprop ro.build.version.sdk || true
    timeout 15s adb shell getprop sys.boot_completed || true
    timeout 20s adb logcat -d -t 400 || true
    timeout 15s adb shell dumpsys meminfo || true
  fi
  exit "$rc"
}
trap dump_diagnostics EXIT

echo "Waiting for Android API 30 emulator..."
timeout 120s adb wait-for-device
api_level=""
boot_completed=""
for attempt in $(seq 1 24); do
  api_level="$(timeout 15s adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
  boot_completed="$(timeout 15s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$api_level" == "30" && "$boot_completed" == "1" ]]; then
    echo "Emulator is responsive (API $api_level)."
    break
  fi
  echo "Emulator not ready (attempt $attempt/24; API=$api_level; boot=$boot_completed)."
  sleep 5
done
test "$api_level" = "30"
test "$boot_completed" = "1"

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
APKANALYZER="$(find "$ANDROID_HOME" -type f -name apkanalyzer | head -n 1)"
test -s "$APP_APK"
test -s "$TEST_APK"
test -n "$APKANALYZER"

INSTRUMENTATION_INFO="$(python3 - "$APKANALYZER" "$TEST_APK" <<'PY'
import subprocess
import sys
import xml.etree.ElementTree as ET

analyzer, apk = sys.argv[1], sys.argv[2]
manifest = subprocess.check_output(
    [analyzer, "manifest", "print", apk], text=True
)
root = ET.fromstring(manifest)
ns = "{http://schemas.android.com/apk/res/android}"
instrumentation = root.find("instrumentation")
if instrumentation is None:
    raise SystemExit("Android test APK has no instrumentation declaration")
print("|".join((
    root.attrib["package"],
    instrumentation.attrib["targetPackage"],
    instrumentation.attrib[ns + "name"],
)))
PY
)"
IFS='|' read -r TEST_PACKAGE TARGET_PACKAGE TEST_RUNNER <<< "$INSTRUMENTATION_INFO"
APP_PACKAGE="$("$APKANALYZER" manifest application-id "$APP_APK")"
test "$TARGET_PACKAGE" = "$APP_PACKAGE"
test -n "$TEST_PACKAGE"
test -n "$TEST_RUNNER"

echo "Installing prebuilt app ($APP_PACKAGE) and test APK ($TEST_PACKAGE)..."
adb install -r "$APP_APK"
adb install -r -t "$TEST_APK"

run_instrumentation() {
  local label="$1"
  shift
  local output
  local status
  output="$(mktemp)"
  echo "=== $label ==="
  set +e
  timeout 900s adb shell am instrument -w -r "$@" "$TEST_PACKAGE/$TEST_RUNNER" >"$output" 2>&1
  status=$?
  set -e
  cat "$output"
  if [[ "$status" -ne 0 ]] || ! grep -q "INSTRUMENTATION_CODE: 0" "$output"; then
    echo "Instrumentation did not report a successful completion (exit=$status)."
    rm -f "$output"
    return 1
  fi
  rm -f "$output"
}

if [[ "${GITHUB_REF:-}" == "refs/heads/feature/open-source-document-engine" ]] || [[ "${GITHUB_REF:-}" == "refs/heads/feature/wps-style-variable-width-pdf-edit" ]] || [[ "${GITHUB_REF:-}" == "refs/heads/fix/direct-page-inline-pdf-text-edit" ]] || [[ "${GITHUB_REF:-}" == "refs/heads/feature/pdfcraft-native-text-engine" ]]; then
  run_instrumentation "Focused Bengali PDF text replacement test" -e class com.example.engine.pdfbox.PdfNativeBengaliTextEditInstrumentedTest
fi
run_instrumentation "Complete Android instrumentation suite"

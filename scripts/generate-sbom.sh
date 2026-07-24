#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 path/to/app.apk path/to/sbom.cdx.json" >&2
  exit 2
fi

apk="$1"
output="$2"
if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  exit 1
fi
if ! command -v syft >/dev/null 2>&1; then
  echo "Syft is required to generate the release SBOM." >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to validate the release SBOM." >&2
  exit 1
fi

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
project_dir="$(CDPATH= cd -- "$script_dir/.." && pwd)"
gradle_lock="$project_dir/android/app/gradle.lockfile"
if [[ ! -f "$gradle_lock" ]]; then
  echo "Release runtime lockfile not found: $gradle_lock" >&2
  exit 1
fi

mkdir -p "$(dirname -- "$output")"
scan_root="$(mktemp -d)"
cleanup() {
  find "$scan_root" -depth -delete
}
trap cleanup EXIT

mkdir -p "$scan_root/apk"
unzip -qq "$apk" 'lib/*.so' -d "$scan_root/apk"
cp "$gradle_lock" "$scan_root/gradle.lockfile"

SYFT_CHECK_FOR_APP_UPDATE=false syft scan "dir:$scan_root" \
  --source-name TONNET-Browser-Mobile \
  --output "cyclonedx-json=$output"

if [[ ! -s "$output" ]]; then
  echo "SBOM was not generated: $output" >&2
  exit 1
fi
component_count="$(jq -r '.components | length' "$output")"
if [[ "$component_count" -eq 0 ]]; then
  echo "SBOM contains no components." >&2
  exit 1
fi
echo "SBOM components: $component_count"
shasum -a 256 "$output"

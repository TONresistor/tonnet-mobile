#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 path/to/app.apk [expected-certificate-sha256]" >&2
  exit 2
fi

apk="$1"
expected_certificate_sha256="${2:-}"
if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  exit 1
fi

if [[ -z "${ANDROID_SDK_ROOT:-}" && -z "${ANDROID_HOME:-}" ]]; then
  echo "Set ANDROID_SDK_ROOT or ANDROID_HOME." >&2
  exit 1
fi
sdk_root="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
build_tools_version="${BUILD_TOOLS_VERSION:-36.0.0}"
ndk_version="${NDK_VERSION:-28.2.13676358}"
build_tools="$sdk_root/build-tools/$build_tools_version"
if [[ ! -x "$build_tools/zipalign" || ! -x "$build_tools/apksigner" ]]; then
  echo "Android build tools $build_tools_version are unavailable at $build_tools." >&2
  exit 1
fi

"$build_tools/zipalign" -c -P 16 4 "$apk"
signature_report="$("$build_tools/apksigner" verify --verbose --print-certs "$apk")"
printf '%s\n' "$signature_report"
if [[ -n "$expected_certificate_sha256" ]]; then
  certificate_sha256="$(
    sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$signature_report"
  )"
  if [[ "$certificate_sha256" != "$expected_certificate_sha256" ]]; then
    echo "Unexpected APK signing certificate: $certificate_sha256" >&2
    exit 1
  fi
fi

ndk_root="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$ndk_root" ]]; then
  ndk_root="$sdk_root/ndk/$ndk_version"
fi
readelf="$(find "$ndk_root/toolchains/llvm/prebuilt" -path '*/bin/llvm-readelf' -print | head -1)"
if [[ -z "$readelf" || ! -x "$readelf" ]]; then
  echo "llvm-readelf is unavailable in Android NDK: $ndk_root" >&2
  exit 1
fi

verify_root="$(mktemp -d)"
trap 'rm -rf -- "$verify_root"' EXIT
unzip -qq "$apk" 'lib/*.so' -d "$verify_root"

native_libraries="$(find "$verify_root/lib" -type f -name '*.so' | sort)"
if [[ -z "$native_libraries" ]]; then
  echo "APK contains no native libraries." >&2
  exit 1
fi

while IFS= read -r library; do
  relative_path="${library#"$verify_root/"}"
  echo "Checking ELF LOAD alignment: $relative_path"
  alignments="$("$readelf" -lW "$library" | awk '$1 == "LOAD" { print $NF }')"
  if [[ -z "$alignments" ]]; then
    echo "$relative_path has no ELF LOAD segments." >&2
    exit 1
  fi
  while IFS= read -r alignment; do
    if (( alignment < 0x4000 )); then
      echo "$relative_path has LOAD alignment $alignment; expected at least 0x4000." >&2
      exit 1
    fi
  done <<< "$alignments"

  while IFS= read -r dependency; do
    if [[ "$dependency" == */* ]]; then
      echo "$relative_path embeds a non-portable ELF dependency: $dependency" >&2
      exit 1
    fi
  done < <("$readelf" -dW "$library" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p')
done <<< "$native_libraries"

shasum -a 256 "$apk"

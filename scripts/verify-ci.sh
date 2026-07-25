#!/usr/bin/env bash
set -euo pipefail

readonly ACTIONLINT_VERSION="1.7.12"
readonly ACTIONLINT_DARWIN_ARM64_SHA256="aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f"
readonly ACTIONLINT_LINUX_AMD64_SHA256="8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8"
readonly GOVULNCHECK_VERSION="v1.6.0"
readonly GOOGLE_MAVEN_BASE_URL="https://dl.google.com/dl/android/maven2"

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
project_dir="$(CDPATH= cd -- "$script_dir/.." && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [[ -z "$sdk_root" ]]; then
  echo "Set ANDROID_SDK_ROOT or ANDROID_HOME." >&2
  exit 1
fi

case "$(uname -s):$(uname -m)" in
  Darwin:arm64)
    actionlint_platform="darwin_arm64"
    actionlint_sha256="$ACTIONLINT_DARWIN_ARM64_SHA256"
    system_image_abi="arm64-v8a"
    ;;
  Linux:x86_64)
    actionlint_platform="linux_amd64"
    actionlint_sha256="$ACTIONLINT_LINUX_AMD64_SHA256"
    system_image_abi="x86_64"
    ;;
  *)
    echo "Unsupported CI verification host: $(uname -s) $(uname -m)" >&2
    exit 1
    ;;
esac

readonly verification_root="$(mktemp -d)"
cleanup() {
  chmod -R u+w "$verification_root"
  find "$verification_root" -depth -delete
}
trap cleanup EXIT

tools_dir="$verification_root/tools"
mkdir -p \
  "$tools_dir" \
  "$verification_root/gradle-home" \
  "$verification_root/go-mod-cache" \
  "$verification_root/go-build-cache" \
  "$verification_root/tool-mod-cache" \
  "$verification_root/tool-build-cache"

verify_sha256() {
  local expected="$1"
  local file="$2"
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s  %s\n' "$expected" "$file" | sha256sum --check -
  else
    printf '%s  %s\n' "$expected" "$file" | shasum -a 256 --check
  fi
}

actionlint_archive="$verification_root/actionlint.tar.gz"
curl --fail-with-body --silent --show-error --location \
  --output "$actionlint_archive" \
  "https://github.com/rhysd/actionlint/releases/download/v${ACTIONLINT_VERSION}/actionlint_${ACTIONLINT_VERSION}_${actionlint_platform}.tar.gz"
verify_sha256 "$actionlint_sha256" "$actionlint_archive"
tar -xzf "$actionlint_archive" -C "$tools_dir" actionlint

verification_metadata="$project_dir/android/gradle/verification-metadata.xml"
aapt2_component="$(
  awk '
    /<component group="com.android.tools.build" name="aapt2" version="/ { capture = 1 }
    capture { print }
    capture && /<\/component>/ { exit }
  ' "$verification_metadata"
)"
aapt2_version="$(
  printf '%s\n' "$aapt2_component" |
    sed -n 's/.*name="aapt2" version="\([^"]*\)".*/\1/p'
)"
if [[ -z "$aapt2_version" ]]; then
  echo "AAPT2 verification metadata is missing." >&2
  exit 1
fi
for platform in linux osx; do
  artifact="aapt2-${aapt2_version}-${platform}.jar"
  expected_sha256="$(
    printf '%s\n' "$aapt2_component" |
      awk -v artifact="$artifact" '
        index($0, "<artifact name=\"" artifact "\"") { capture = 1; next }
        capture && /<sha256 value="/ { print; exit }
        capture && /<\/artifact>/ { exit }
      ' |
      sed -n 's/.*<sha256 value="\([^"]*\)".*/\1/p'
  )"
  if [[ -z "$expected_sha256" ]]; then
    echo "Missing verification metadata for $artifact." >&2
    exit 1
  fi
  downloaded_artifact="$verification_root/$artifact"
  curl --fail-with-body --silent --show-error --location \
    --output "$downloaded_artifact" \
    "$GOOGLE_MAVEN_BASE_URL/com/android/tools/build/aapt2/$aapt2_version/$artifact"
  verify_sha256 "$expected_sha256" "$downloaded_artifact"
done

if ! command -v govulncheck >/dev/null 2>&1 ||
  ! govulncheck -version 2>/dev/null | grep -q "Scanner: govulncheck@${GOVULNCHECK_VERSION}"; then
  GOBIN="$tools_dir" \
  GOMODCACHE="$verification_root/tool-mod-cache" \
  GOCACHE="$verification_root/tool-build-cache" \
  GOTOOLCHAIN=go1.25.12 \
    go install "golang.org/x/vuln/cmd/govulncheck@${GOVULNCHECK_VERSION}"
fi

if [[ ! -d "$sdk_root/system-images/android-36/default/$system_image_abi" ]]; then
  echo "Android 36 default $system_image_abi system image is unavailable." >&2
  exit 1
fi

emulator="$sdk_root/emulator/emulator"
if [[ ! -x "$emulator" ]]; then
  echo "Android Emulator is unavailable at $emulator." >&2
  exit 1
fi
if ! acceleration="$("$emulator" -accel-check 2>&1)"; then
  printf '%s\n' "$acceleration" >&2
  echo "Android hardware acceleration is unavailable." >&2
  exit 1
fi
printf '%s\n' "$acceleration"
if ! grep -qx '0' <<< "$acceleration"; then
  echo "Android hardware acceleration did not report a usable hypervisor." >&2
  exit 1
fi

export PATH="$tools_dir:$PATH"
export ANDROID_HOME="$sdk_root"
export ANDROID_SDK_ROOT="$sdk_root"
export BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-36.0.0}"
export NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
export GRADLE_USER_HOME="$verification_root/gradle-home"
export GOMODCACHE="$verification_root/go-mod-cache"
export GOCACHE="$verification_root/go-build-cache"
export GOTOOLCHAIN=local
export TONNET_RUN_GOVULNCHECK=1

cd "$project_dir"
actionlint -shellcheck=""
./scripts/verify-repository-hygiene.sh
(
  cd android
  ./gradlew --no-daemon clean
  ./gradlew --no-daemon qualityGate
)
./scripts/verify-apk.sh android/app/build/outputs/apk/beta/debug/app-beta-debug.apk

#!/usr/bin/env bash
set -euo pipefail

readonly PROXY_REPOSITORY="https://github.com/TONresistor/Tonutils-Proxy.git"
readonly PROXY_REVISION="43c104b25ec8ddd82b38ace3f1ebb239ac88ad21"
readonly ADNL_TUNNEL_MODULE="github.com/ton-blockchain/adnl-tunnel"
readonly ADNL_TUNNEL_VERSION="v0.2.0"
readonly X_NET_VERSION="v0.55.0"
readonly X_TEXT_VERSION="v0.39.0"
readonly GO_TOOLCHAIN="go1.25.12"
readonly NDK_VERSION="28.2.13676358"
readonly ANDROID_API="28"

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
project_dir="$(CDPATH= cd -- "$script_dir/.." && pwd)"
wrapper_source="$project_dir/native/tonproxy/main.go"
upstream_tests="$project_dir/native/tonproxy/upstream-tests"
proxy_patch="$script_dir/patches/tonutils-mobile.patch"
proxy_lifecycle_patch="$script_dir/patches/tonutils-lifecycle.patch"
proxy_hardening_patch="$script_dir/patches/tonutils-mobile-hardening.patch"
adnl_tunnel_patch="$script_dir/patches/adnl-tunnel-v0.2.0-mobile.patch"
output_root="${TONNET_JNI_OUTPUT_ROOT:-$project_dir/android/ton-proxy-android/build/generated/jniLibs}"

if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
  ndk_dir="$ANDROID_NDK_HOME"
elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  ndk_dir="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  ndk_dir="$ANDROID_HOME/ndk/$NDK_VERSION"
else
  echo "Set ANDROID_SDK_ROOT, ANDROID_HOME or ANDROID_NDK_HOME." >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin) host_tag="darwin-x86_64" ;;
  Linux) host_tag="linux-x86_64" ;;
  *) echo "Unsupported build host" >&2; exit 1 ;;
esac

toolchain_bin="$ndk_dir/toolchains/llvm/prebuilt/$host_tag/bin"
llvm_readelf="$toolchain_bin/llvm-readelf"
if [[ ! -x "$llvm_readelf" ]]; then
  echo "Android NDK $NDK_VERSION not found at $ndk_dir" >&2
  exit 1
fi

build_dir="$(mktemp -d)"
cleanup() {
  find "$build_dir" -depth -delete
}
trap cleanup EXIT

git clone --quiet --filter=blob:none --no-checkout "$PROXY_REPOSITORY" "$build_dir/source"
git -C "$build_dir/source" checkout --quiet "$PROXY_REVISION"
test "$(git -C "$build_dir/source" rev-parse HEAD)" = "$PROXY_REVISION"
git -C "$build_dir/source" apply --check "$proxy_patch"
git -C "$build_dir/source" apply "$proxy_patch"
git -C "$build_dir/source" apply --check "$proxy_lifecycle_patch"
git -C "$build_dir/source" apply "$proxy_lifecycle_patch"
git -C "$build_dir/source" apply --check "$proxy_hardening_patch"
git -C "$build_dir/source" apply "$proxy_hardening_patch"

resolved_adnl_tunnel_version="$(
  cd "$build_dir/source"
  GOTOOLCHAIN="$GO_TOOLCHAIN" go list -m -f '{{.Version}}' "$ADNL_TUNNEL_MODULE"
)"
if [[ "$resolved_adnl_tunnel_version" != "$ADNL_TUNNEL_VERSION" ]]; then
  echo "Unexpected adnl-tunnel version: $resolved_adnl_tunnel_version" >&2
  exit 1
fi
(
  cd "$build_dir/source"
  GOTOOLCHAIN="$GO_TOOLCHAIN" go mod download \
    "$ADNL_TUNNEL_MODULE@$ADNL_TUNNEL_VERSION"
)
adnl_tunnel_source="$(
  cd "$build_dir/source"
  GOTOOLCHAIN="$GO_TOOLCHAIN" go list -m -f '{{.Dir}}' "$ADNL_TUNNEL_MODULE"
)"
if [[ -z "$adnl_tunnel_source" || ! -d "$adnl_tunnel_source" ]]; then
  echo "adnl-tunnel source is unavailable: $ADNL_TUNNEL_MODULE@$ADNL_TUNNEL_VERSION" >&2
  exit 1
fi
adnl_tunnel_build_source="$build_dir/source/third_party/adnl-tunnel"
mkdir -p "$adnl_tunnel_build_source"
cp -R "$adnl_tunnel_source/." "$adnl_tunnel_build_source"
chmod -R u+w "$adnl_tunnel_build_source"
git -C "$build_dir/source" apply --check --directory=third_party/adnl-tunnel "$adnl_tunnel_patch"
git -C "$build_dir/source" apply --directory=third_party/adnl-tunnel "$adnl_tunnel_patch"
cp "$upstream_tests/adnl_tunnel_lifecycle_test.go" "$adnl_tunnel_build_source/tunnel/mobile_lifecycle_test.go"

(
  cd "$build_dir/source"
  GOTOOLCHAIN="$GO_TOOLCHAIN" go mod edit -replace="$ADNL_TUNNEL_MODULE=./third_party/adnl-tunnel"
  GOTOOLCHAIN="$GO_TOOLCHAIN" go get \
    "golang.org/x/net@$X_NET_VERSION" \
    "golang.org/x/text@$X_TEXT_VERSION"
)

mkdir -p "$build_dir/source/cmd/mobile"
cp "$wrapper_source" "$build_dir/source/cmd/mobile/main.go"
cp "$project_dir/native/tonproxy/main_test.go" "$build_dir/source/cmd/mobile/main_test.go"
cp "$upstream_tests/ton_only_test.go" "$build_dir/source/proxy/ton_only_test.go"
cp "$upstream_tests/client_lifecycle_test.go" "$build_dir/source/proxy/transport/client_lifecycle_test.go"
gofmt -w \
  "$adnl_tunnel_build_source/tunnel/setup.go" \
  "$adnl_tunnel_build_source/tunnel/mobile_lifecycle_test.go" \
  "$build_dir/source/cmd/mobile/main.go" \
  "$build_dir/source/cmd/mobile/main_test.go" \
  "$build_dir/source/proxy/ton_only_test.go" \
  "$build_dir/source/proxy/transport/client_lifecycle_test.go"

(
  cd "$build_dir/source"
  GOTOOLCHAIN="$GO_TOOLCHAIN" go mod tidy
  GOTOOLCHAIN="$GO_TOOLCHAIN" go mod verify
)

for module_version in \
  "golang.org/x/net:$X_NET_VERSION" \
  "golang.org/x/text:$X_TEXT_VERSION"; do
  module="${module_version%%:*}"
  expected_version="${module_version#*:}"
  resolved_version="$(
    cd "$build_dir/source"
    GOTOOLCHAIN="$GO_TOOLCHAIN" go list -m -f '{{.Version}}' "$module"
  )"
  if [[ "$resolved_version" != "$expected_version" ]]; then
    echo "Unexpected $module version: $resolved_version" >&2
    exit 1
  fi
done

(
  cd "$adnl_tunnel_build_source"
  GOTOOLCHAIN="$GO_TOOLCHAIN" go test ./tunnel
)

(
  cd "$build_dir/source"
  GOTOOLCHAIN="$GO_TOOLCHAIN" go test ./proxy/... ./cmd/mobile
  GOTOOLCHAIN="$GO_TOOLCHAIN" go vet ./proxy/... ./cmd/mobile
)

if [[ "${TONNET_RUN_GOVULNCHECK:-0}" == "1" ]]; then
  if ! command -v govulncheck >/dev/null 2>&1; then
    echo "TONNET_RUN_GOVULNCHECK requires govulncheck in PATH." >&2
    exit 1
  fi
  (
    cd "$build_dir/source"
    GOTOOLCHAIN="$GO_TOOLCHAIN" govulncheck ./proxy/... ./cmd/mobile
  )
fi

if [[ -n "${SOURCE_BUNDLE_OUTPUT:-}" ]]; then
  if [[ -n "$(git -C "$project_dir" status --porcelain)" ]]; then
    echo "Corresponding Source bundles require a clean Git worktree." >&2
    exit 1
  fi
  source_bundle_dir="$(dirname -- "$SOURCE_BUNDLE_OUTPUT")"
  mkdir -p "$source_bundle_dir"
  source_bundle="$(CDPATH= cd -- "$source_bundle_dir" && pwd)/$(basename -- "$SOURCE_BUNDLE_OUTPUT")"
  bundle_root="$build_dir/corresponding-source/tonnet-mobile-source"
  mkdir -p "$bundle_root/tonnet-mobile" "$bundle_root/third_party/Tonutils-Proxy"
  printf '%s\n' \
    "TONNET Mobile revision: $(git -C "$project_dir" rev-parse HEAD)" \
    "Tonutils-Proxy revision: $PROXY_REVISION" \
    "adnl-tunnel version: $ADNL_TUNNEL_VERSION with TONNET mobile lifecycle patch" \
    "golang.org/x/net version: $X_NET_VERSION" \
    "golang.org/x/text version: $X_TEXT_VERSION" \
    "Go toolchain: $GO_TOOLCHAIN" \
    > "$bundle_root/SOURCE_PROVENANCE.txt"

  git -C "$project_dir" archive HEAD | tar -xf - -C "$bundle_root/tonnet-mobile"
  (
    cd "$build_dir/source"
    GOTOOLCHAIN="$GO_TOOLCHAIN" go mod vendor
    tar --exclude=.git -cf - .
  ) | tar -xf - -C "$bundle_root/third_party/Tonutils-Proxy"

  tar -czf "$source_bundle" -C "$build_dir/corresponding-source" tonnet-mobile-source
  shasum -a 256 "$source_bundle"
fi

build_abi() {
  local abi="$1"
  local go_arch="$2"
  local clang_prefix="$3"
  local cc="$toolchain_bin/${clang_prefix}${ANDROID_API}-clang"
  local output_dir="$output_root/$abi"
  local output="$output_dir/libtonutils-proxy.so"

  if [[ ! -x "$cc" ]]; then
    echo "Android compiler not found: $cc" >&2
    exit 1
  fi

  mkdir -p "$output_dir"
  (
    cd "$build_dir/source"
    CC="$cc" CGO_ENABLED=1 GOOS=android GOARCH="$go_arch" GOTOOLCHAIN="$GO_TOOLCHAIN" \
      go build -buildmode=c-shared -trimpath \
        -ldflags="-w -s -X main.GitCommit=$PROXY_REVISION -extldflags '-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,-z,relro -Wl,-z,now'" \
        -o "$output" ./cmd/mobile
  )
  if GOTOOLCHAIN="$GO_TOOLCHAIN" go version -m "$output" |
    grep -Eq 'github.com/ethereum/go-ethereum|github.com/gagliardetto/solana-go|go.mongodb.org/mongo-driver'; then
    echo "$abi proxy contains disabled multi-chain resolver dependencies." >&2
    exit 1
  fi

  find "$output_dir" -maxdepth 1 -type f -name '*.h' -delete
  if "$llvm_readelf" -lW "$output" | awk '/LOAD/ { if ($NF != "0x4000" && $NF != "0x10000") bad=1 } END { exit bad }'; then
    :
  else
    echo "ELF LOAD segments are not 16 KiB aligned: $output" >&2
    exit 1
  fi
  shasum -a 256 "$output"
}

build_abi "arm64-v8a" "arm64" "aarch64-linux-android"
build_abi "x86_64" "amd64" "x86_64-linux-android"

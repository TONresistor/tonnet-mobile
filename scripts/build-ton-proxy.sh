#!/usr/bin/env bash

# Reproducibly build the arm64 TON proxy shared library used by Android.

set -Eeuo pipefail

readonly PROXY_REPOSITORY='https://github.com/TONresistor/Tonutils-Proxy.git'
readonly PROXY_VERSION='v1.9.4'
readonly PROXY_COMMIT='c071d9e40521cd92b99aecc58d3337ea50f51bc2'
readonly PROXY_PATCH_ID='tonnet-mobile.1'
readonly NDK_VERSION='27.1.12297006'
readonly GO_VERSION='1.25.5'
readonly GO_EXPERIMENT='nodwarf5'
readonly REQUIRED_ANDROID_API=28
readonly EXPECTED_OUTPUT_SHA256='59ef357356cc3720a3854c3ec32e79834b8c0c59fd7c4ed3404fa4258ada643d'

# Never let Go silently download or select a different toolchain.
export GOTOOLCHAIN=local
readonly GOTOOLCHAIN
export GOEXPERIMENT="$GO_EXPERIMENT"
readonly GOEXPERIMENT
export GOENV=off
readonly GOENV
export GOFLAGS=''
readonly GOFLAGS

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
readonly PROJECT_DIR
readonly SOURCE_PATCH="$SCRIPT_DIR/patches/tonutils-proxy-v1.9.4-mobile.patch"
readonly BUILD_ROOT="${TON_PROXY_BUILD_DIR:-${TMPDIR:-/tmp}/tonnet-ton-proxy}"
readonly BUILD_MARKER="$BUILD_ROOT/.tonnet-ton-proxy-build-root"
readonly BUILD_MARKER_CONTENT='tonnet-mobile native proxy build root v1'
readonly SOURCE_DIR="$BUILD_ROOT/source"
readonly BUILD_OUTPUT="$BUILD_ROOT/output/libtonutils-proxy.so"
readonly OUTPUT_DIR="$PROJECT_DIR/android/app/libs/jniLibs/arm64-v8a"
readonly OUTPUT_FILE="$OUTPUT_DIR/libtonutils-proxy.so"

ANDROID_API="${ANDROID_API:-$REQUIRED_ANDROID_API}"
readonly ANDROID_API
CLEAN=false

usage() {
    cat <<'EOF'
Usage: ./scripts/build-ton-proxy.sh [--clean]

Builds TONresistor/Tonutils-Proxy v1.9.4 for Android arm64 and writes:
  android/app/libs/jniLibs/arm64-v8a/libtonutils-proxy.so

Options:
  --clean  Remove the isolated temporary checkout before building.
  --help   Show this help.

Environment:
  ANDROID_SDK_ROOT      Android SDK root (ANDROID_HOME is accepted as fallback).
  ANDROID_NDK_HOME      NDK root (defaults to NDK 27.1 below the SDK root).
  ANDROID_API           Native API level; when set, it must be exactly 28.
  TON_PROXY_BUILD_DIR   Isolated temporary checkout/build directory.
EOF
}

fail() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

check_go_version() {
    local experiment version
    version="$(go env GOVERSION)"
    version="${version%% *}"
    [[ "$version" == "go$GO_VERSION" ]] || \
        fail "Go $GO_VERSION is required exactly (found ${version#go})"
    experiment="$(go env GOEXPERIMENT)"
    [[ "$experiment" == "$GO_EXPERIMENT" ]] || \
        fail "GOEXPERIMENT must be $GO_EXPERIMENT (found ${experiment:-none})"
}

resolve_ndk_host_tag() {
    case "$(uname -s)" in
        Linux) printf 'linux-x86_64' ;;
        Darwin) printf 'darwin-x86_64' ;;
        MINGW*|MSYS*|CYGWIN*) printf 'windows-x86_64' ;;
        *) fail "unsupported build host: $(uname -s)" ;;
    esac
}

prepare_source() {
    [[ ! -L "$SOURCE_DIR" ]] || fail "source directory must not be a symbolic link: $SOURCE_DIR"
    mkdir -p "$SOURCE_DIR"

    [[ ! -L "$SOURCE_DIR/.git" ]] || fail "source Git metadata must not be a symbolic link"
    if [[ -e "$SOURCE_DIR/.git" && ! -d "$SOURCE_DIR/.git" ]]; then
        fail "source Git metadata must be a directory: $SOURCE_DIR/.git"
    fi

    if [[ ! -d "$SOURCE_DIR/.git" ]]; then
        git -C "$SOURCE_DIR" init --quiet
        git -C "$SOURCE_DIR" remote add origin "$PROXY_REPOSITORY"
    else
        git -C "$SOURCE_DIR" remote set-url origin "$PROXY_REPOSITORY"
    fi

    git -C "$SOURCE_DIR" fetch --quiet --force --depth 1 \
        origin "refs/tags/$PROXY_VERSION:refs/tags/$PROXY_VERSION"
    git -C "$SOURCE_DIR" checkout --quiet --detach --force "$PROXY_VERSION"
    git -C "$SOURCE_DIR" clean --quiet --force --force -d -x

    local actual_commit
    actual_commit="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
    if [[ "$actual_commit" != "$PROXY_COMMIT" ]]; then
        fail "source verification failed: expected $PROXY_COMMIT, got $actual_commit"
    fi

    [[ -f "$SOURCE_PATCH" ]] || fail "source patch not found: $SOURCE_PATCH"
    git -C "$SOURCE_DIR" apply --check "$SOURCE_PATCH"
    git -C "$SOURCE_DIR" apply "$SOURCE_PATCH"
}

initialize_build_root() {
    [[ ! -L "$BUILD_ROOT" ]] || fail "build directory must not be a symbolic link: $BUILD_ROOT"
    [[ ! -L "$BUILD_MARKER" ]] || fail "build marker must not be a symbolic link: $BUILD_MARKER"

    if [[ -e "$BUILD_ROOT" && ! -d "$BUILD_ROOT" ]]; then
        fail "build path is not a directory: $BUILD_ROOT"
    fi

    if [[ -f "$BUILD_MARKER" ]]; then
        local marker_content
        marker_content="$(<"$BUILD_MARKER")"
        [[ "$marker_content" == "$BUILD_MARKER_CONTENT" ]] || \
            fail "build directory contains an invalid marker: $BUILD_ROOT"
        return
    fi

    if [[ -d "$BUILD_ROOT" ]]; then
        local -a existing_entries
        shopt -s dotglob nullglob
        existing_entries=("$BUILD_ROOT"/*)
        shopt -u dotglob nullglob
        ((${#existing_entries[@]} == 0)) || \
            fail "refusing to use unmarked non-empty directory: $BUILD_ROOT"
    fi

    mkdir -p "$BUILD_ROOT"
    printf '%s\n' "$BUILD_MARKER_CONTENT" >"$BUILD_MARKER"
}

clean_build_root() {
    [[ -e "$BUILD_ROOT" ]] || return 0
    [[ ! -L "$BUILD_ROOT" && -d "$BUILD_ROOT" ]] || \
        fail "refusing to clean unsafe build path: $BUILD_ROOT"
    [[ ! -L "$BUILD_MARKER" && -f "$BUILD_MARKER" ]] || \
        fail "refusing to clean unmarked directory: $BUILD_ROOT"

    local marker_content
    marker_content="$(<"$BUILD_MARKER")"
    [[ "$marker_content" == "$BUILD_MARKER_CONTENT" ]] || \
        fail "refusing to clean directory with invalid marker: $BUILD_ROOT"

    rm -rf "$BUILD_ROOT"
}

print_checksum() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$OUTPUT_FILE"
    else
        shasum -a 256 "$OUTPUT_FILE"
    fi
}

verify_checksum() {
    local actual_checksum
    if command -v sha256sum >/dev/null 2>&1; then
        actual_checksum="$(sha256sum "$OUTPUT_FILE")"
    else
        actual_checksum="$(shasum -a 256 "$OUTPUT_FILE")"
    fi
    actual_checksum="${actual_checksum%% *}"
    [[ "$actual_checksum" == "$EXPECTED_OUTPUT_SHA256" ]] || \
        fail "proxy checksum mismatch: expected $EXPECTED_OUTPUT_SHA256, got $actual_checksum"
}

while (($# > 0)); do
    case "$1" in
        --clean)
            CLEAN=true
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            usage >&2
            fail "unknown option: $1"
            ;;
    esac
    shift
done

require_command git
require_command go
require_command install
require_command awk
check_go_version

[[ "$ANDROID_API" =~ ^[0-9]+$ ]] || fail "ANDROID_API must be an integer"
((ANDROID_API == REQUIRED_ANDROID_API)) || \
    fail "ANDROID_API must be exactly $REQUIRED_ANDROID_API"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$SDK_ROOT" ]] || fail 'set ANDROID_SDK_ROOT (or ANDROID_HOME)'
NDK_ROOT="${ANDROID_NDK_HOME:-$SDK_ROOT/ndk/$NDK_VERSION}"
NDK_PROPERTIES="$NDK_ROOT/source.properties"
[[ -f "$NDK_PROPERTIES" ]] || fail "NDK metadata not found: $NDK_PROPERTIES"
NDK_REVISION="$(awk -F= '/^Pkg.Revision/ {gsub(/[[:space:]]/, "", $2); print $2}' "$NDK_PROPERTIES")"
[[ "$NDK_REVISION" == "$NDK_VERSION" ]] || \
    fail "NDK $NDK_VERSION is required (found ${NDK_REVISION:-unknown})"
NDK_HOST_TAG="$(resolve_ndk_host_tag)"
CLANG="$NDK_ROOT/toolchains/llvm/prebuilt/$NDK_HOST_TAG/bin/aarch64-linux-android${ANDROID_API}-clang"
[[ -x "$CLANG" ]] || fail "Android arm64 compiler not found: $CLANG"

if [[ "$CLEAN" == true ]]; then
    clean_build_root
fi

initialize_build_root
prepare_source
mkdir -p "$(dirname "$BUILD_OUTPUT")" "$OUTPUT_DIR"

printf 'Building Tonutils-Proxy %s (%s) for Android arm64 API %s...\n' \
    "$PROXY_VERSION" "$PROXY_COMMIT" "$ANDROID_API"
printf 'Applying deterministic mobile lifecycle patch: %s\n' "$PROXY_PATCH_ID"

(
    cd "$SOURCE_DIR"
    go mod verify
    CC="$CLANG" \
    CGO_ENABLED=1 \
    GOOS=android \
    GOARCH=arm64 \
        go build \
            -mod=readonly \
            -buildmode=c-shared \
            -buildvcs=false \
            -trimpath \
            -gcflags=all=-l \
            -ldflags="-w -s -X main.GitCommit=$PROXY_VERSION+$PROXY_PATCH_ID -extldflags=-Wl,-z,max-page-size=16384" \
            -o "$BUILD_OUTPUT" \
            cmd/lib/main.go
)

install -m 0644 "$BUILD_OUTPUT" "$OUTPUT_FILE"
verify_checksum
printf 'Built %s\nSHA-256: ' "$OUTPUT_FILE"
print_checksum

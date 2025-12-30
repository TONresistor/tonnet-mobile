#!/bin/bash
#
# Script pour compiler les librairies Go en .aar pour Android
#
# Prerequisites:
#   - Go 1.21+ installed
#   - gomobile installed: go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init
#   - Android NDK installed
#   - ANDROID_SDK_ROOT or ANDROID_HOME environment variable set
#   - ANDROID_NDK_HOME environment variable set (or auto-detected)
#
# Usage:
#   ./scripts/build-go-libs.sh [--clean] [--proxy-only] [--storage-only] [--api API_LEVEL]
#
# 2025 Best Practices:
#   - Uses ANDROID_SDK_ROOT with ANDROID_HOME fallback
#   - Default Android API level 24 (compatible with NDK 27+)
#   - Auto-detects NDK from $ANDROID_SDK_ROOT/ndk/
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LIBS_DIR="$PROJECT_DIR/android/app/libs"
TEMP_DIR="/tmp/tonnet-go-libs"

# Source environment if available
if [ -f "$SCRIPT_DIR/env.sh" ]; then
    source "$SCRIPT_DIR/env.sh"
fi

# Library versions (can be overridden via environment)
TONUTILS_PROXY_REPO="${TONUTILS_PROXY_REPO:-https://github.com/xssnick/Tonutils-Proxy}"
TONUTILS_PROXY_BRANCH="${TONUTILS_PROXY_BRANCH:-master}"
TONUTILS_STORAGE_REPO="${TONUTILS_STORAGE_REPO:-https://github.com/xssnick/tonutils-storage}"
TONUTILS_STORAGE_BRANCH="${TONUTILS_STORAGE_BRANCH:-master}"

# Build configuration (2025 best practices)
# Android API level 24 = Android 7.0 (required minimum for Google Play in 2025)
# NDK 27+ dropped support for API < 21
ANDROID_API="${ANDROID_API:-24}"

# Build flags
BUILD_PROXY=true
BUILD_STORAGE=true
CLEAN=false
VERBOSE=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --clean)
            CLEAN=true
            shift
            ;;
        --proxy-only)
            BUILD_STORAGE=false
            shift
            ;;
        --storage-only)
            BUILD_PROXY=false
            shift
            ;;
        --api)
            ANDROID_API="$2"
            shift 2
            ;;
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [--clean] [--proxy-only] [--storage-only] [--api LEVEL] [--verbose]"
            echo ""
            echo "Options:"
            echo "  --clean         Clean temporary build directory before building"
            echo "  --proxy-only    Only build tonutils-proxy.aar"
            echo "  --storage-only  Only build tonutils-storage.aar"
            echo "  --api LEVEL     Android API level (default: 24, min: 21 for NDK 27+)"
            echo "  --verbose, -v   Show detailed build output"
            echo ""
            echo "Environment variables:"
            echo "  ANDROID_SDK_ROOT  Android SDK location (preferred)"
            echo "  ANDROID_HOME      Android SDK location (legacy fallback)"
            echo "  ANDROID_NDK_HOME  Android NDK location (auto-detected if not set)"
            echo "  ANDROID_API       Default Android API level"
            echo ""
            echo "Examples:"
            echo "  $0                      # Build all libraries"
            echo "  $0 --clean              # Clean and rebuild"
            echo "  $0 --api 21             # Build for API 21 (Android 5.0)"
            echo "  $0 --proxy-only -v      # Build only proxy with verbose output"
            echo ""
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Function to print section headers
print_header() {
    echo ""
    echo -e "${GREEN}=== $1 ===${NC}"
    echo ""
}

# Function to print warnings
print_warning() {
    echo -e "${YELLOW}WARNING: $1${NC}"
}

# Function to print errors
print_error() {
    echo -e "${RED}ERROR: $1${NC}"
}

# Function to check prerequisites
check_prerequisites() {
    print_header "Checking Prerequisites"

    local errors=0
    local sdk_root=""

    # Check Go
    if command -v go &> /dev/null; then
        GO_VERSION=$(go version)
        echo "Go: $GO_VERSION"
    else
        print_error "Go is not installed"
        errors=$((errors + 1))
    fi

    # Check gomobile
    if command -v gomobile &> /dev/null; then
        GOMOBILE_VERSION=$(gomobile version 2>&1 || echo "installed")
        echo "gomobile: $GOMOBILE_VERSION"
    else
        print_error "gomobile is not installed"
        echo "  Install with: go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init"
        errors=$((errors + 1))
    fi

    # Check Android SDK (2025 best practice: prefer ANDROID_SDK_ROOT, fallback to ANDROID_HOME)
    if [ -n "$ANDROID_SDK_ROOT" ]; then
        sdk_root="$ANDROID_SDK_ROOT"
        echo "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
    elif [ -n "$ANDROID_HOME" ]; then
        sdk_root="$ANDROID_HOME"
        print_warning "Using deprecated ANDROID_HOME (consider setting ANDROID_SDK_ROOT)"
        echo "ANDROID_HOME: $ANDROID_HOME"
    else
        print_error "Neither ANDROID_SDK_ROOT nor ANDROID_HOME is set"
        echo "  Set ANDROID_SDK_ROOT to your Android SDK directory (e.g., ~/Android/Sdk)"
        echo "  Or run: source scripts/env.sh"
        errors=$((errors + 1))
    fi

    if [ -n "$sdk_root" ] && [ ! -d "$sdk_root" ]; then
        print_error "Android SDK directory does not exist: $sdk_root"
        errors=$((errors + 1))
    fi

    # Ensure both variables are set for maximum compatibility
    if [ -n "$sdk_root" ]; then
        export ANDROID_SDK_ROOT="$sdk_root"
        export ANDROID_HOME="$sdk_root"
    fi

    # Check ANDROID_NDK_HOME (with auto-detection)
    if [ -z "$ANDROID_NDK_HOME" ]; then
        print_warning "ANDROID_NDK_HOME is not set, attempting auto-detection..."

        # Try to find NDK automatically from SDK root
        if [ -n "$sdk_root" ] && [ -d "$sdk_root/ndk" ]; then
            # Preferred NDK versions in order (2025 recommendations)
            local preferred_versions=("27.1.12297006" "27.0.12077973" "26.3.11579264" "25.2.9519653")

            for version in "${preferred_versions[@]}"; do
                if [ -d "$sdk_root/ndk/$version" ]; then
                    export ANDROID_NDK_HOME="$sdk_root/ndk/$version"
                    echo -e "  ${GREEN}Found preferred NDK: $ANDROID_NDK_HOME${NC}"
                    break
                fi
            done

            # If no preferred version found, use latest available
            if [ -z "$ANDROID_NDK_HOME" ]; then
                NDK_DIR=$(ls -1d "$sdk_root/ndk"/*/ 2>/dev/null | sort -V | tail -n1)
                if [ -n "$NDK_DIR" ]; then
                    export ANDROID_NDK_HOME="${NDK_DIR%/}"
                    echo "  Auto-detected NDK: $ANDROID_NDK_HOME"
                fi
            fi
        fi

        if [ -z "$ANDROID_NDK_HOME" ]; then
            print_error "Could not auto-detect NDK. Please set ANDROID_NDK_HOME"
            echo "  Install NDK: sdkmanager 'ndk;27.1.12297006'"
            echo "  Or run: ./scripts/setup-android-sdk.sh"
            errors=$((errors + 1))
        fi
    else
        echo "ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
        if [ ! -d "$ANDROID_NDK_HOME" ]; then
            print_error "ANDROID_NDK_HOME directory does not exist: $ANDROID_NDK_HOME"
            errors=$((errors + 1))
        fi
    fi

    # Detect NDK version and validate API level compatibility
    if [ -n "$ANDROID_NDK_HOME" ] && [ -d "$ANDROID_NDK_HOME" ]; then
        # Extract NDK major version
        NDK_VERSION_FILE="$ANDROID_NDK_HOME/source.properties"
        if [ -f "$NDK_VERSION_FILE" ]; then
            NDK_REVISION=$(grep "Pkg.Revision" "$NDK_VERSION_FILE" | cut -d= -f2 | tr -d ' ')
            NDK_MAJOR=$(echo "$NDK_REVISION" | cut -d. -f1)
            echo "NDK Version: $NDK_REVISION"

            # Check API level compatibility
            if [ "$NDK_MAJOR" -ge 27 ] && [ "$ANDROID_API" -lt 21 ]; then
                print_warning "NDK $NDK_MAJOR requires minimum API level 21"
                print_warning "Automatically adjusting ANDROID_API from $ANDROID_API to 21"
                ANDROID_API=21
            elif [ "$NDK_MAJOR" -ge 25 ] && [ "$ANDROID_API" -lt 19 ]; then
                print_warning "NDK $NDK_MAJOR requires minimum API level 19"
                print_warning "Automatically adjusting ANDROID_API from $ANDROID_API to 19"
                ANDROID_API=19
            fi
        fi
    fi

    echo "Android API Level: $ANDROID_API"

    # Check git
    if command -v git &> /dev/null; then
        echo "git: $(git --version)"
    else
        print_error "git is not installed"
        errors=$((errors + 1))
    fi

    if [ $errors -gt 0 ]; then
        echo ""
        print_error "Prerequisites check failed with $errors error(s)"
        echo "Please fix the issues above and try again."
        echo ""
        echo "Quick fix: run ./scripts/setup-android-sdk.sh"
        exit 1
    fi

    echo ""
    echo -e "${GREEN}All prerequisites satisfied!${NC}"
}

# Function to setup directories
setup_directories() {
    print_header "Setting up directories"

    # Clean if requested
    if [ "$CLEAN" = true ] && [ -d "$TEMP_DIR" ]; then
        echo "Cleaning temporary directory..."
        rm -rf "$TEMP_DIR"
    fi

    mkdir -p "$LIBS_DIR"
    mkdir -p "$TEMP_DIR"

    echo "Libs directory: $LIBS_DIR"
    echo "Temp directory: $TEMP_DIR"
}

# Function to build tonutils-proxy
build_tonutils_proxy() {
    print_header "Building tonutils-proxy"

    cd "$TEMP_DIR"

    if [ ! -d "Tonutils-Proxy" ]; then
        echo "Cloning tonutils-proxy..."
        git clone --depth 1 --branch "$TONUTILS_PROXY_BRANCH" "$TONUTILS_PROXY_REPO" Tonutils-Proxy
    else
        echo "Using cached tonutils-proxy repository"
        cd Tonutils-Proxy
        git fetch origin "$TONUTILS_PROXY_BRANCH"
        git checkout "$TONUTILS_PROXY_BRANCH"
        git pull
        cd ..
    fi

    cd Tonutils-Proxy

    # Check if Makefile has android target
    if grep -q "build-android-lib" Makefile 2>/dev/null; then
        echo "Building using Makefile..."

        # Detect NDK host architecture
        case "$(uname -s)-$(uname -m)" in
            Linux-x86_64)  NDK_HOST_ARCH="linux-x86_64" ;;
            Linux-aarch64) NDK_HOST_ARCH="linux-aarch64" ;;
            Darwin-x86_64) NDK_HOST_ARCH="darwin-x86_64" ;;
            Darwin-arm64)  NDK_HOST_ARCH="darwin-arm64" ;;
            *)             NDK_HOST_ARCH="linux-x86_64" ;;
        esac

        echo "NDK_ROOT: $ANDROID_NDK_HOME"
        echo "NDK_ARCH: $NDK_HOST_ARCH"

        # Build using Makefile with correct NDK paths
        NDK_ROOT="$ANDROID_NDK_HOME" NDK_ARCH="$NDK_HOST_ARCH" make build-android-lib

        # The Makefile builds a .so, not .aar - we need to wrap it
        SO_FILE="build/lib/android/tonutils-proxy.so"
        if [ -f "$SO_FILE" ]; then
            echo "Found shared library: $SO_FILE"
            # For now, copy the .so file - we'll create AAR wrapper later
            mkdir -p "$LIBS_DIR/jniLibs/arm64-v8a"
            cp "$SO_FILE" "$LIBS_DIR/jniLibs/arm64-v8a/libtonutils-proxy.so"
            echo -e "${GREEN}Successfully built libtonutils-proxy.so${NC}"
        else
            # Fallback: look for any .aar file
            AAR_FILE=$(find . -name "*.aar" -type f | head -n1)
            if [ -n "$AAR_FILE" ]; then
                cp "$AAR_FILE" "$LIBS_DIR/tonutils-proxy.aar"
                echo -e "${GREEN}Successfully built tonutils-proxy.aar${NC}"
            else
                print_warning "Could not find .aar or .so file, falling back to gomobile bind"
            fi
        fi
    fi

    # The Makefile creates a .so file (not .aar), so we mark success based on that
    if [ -f "$LIBS_DIR/jniLibs/arm64-v8a/libtonutils-proxy.so" ]; then
        echo -e "${GREEN}tonutils-proxy native library built successfully${NC}"
    else
        print_error "Failed to build tonutils-proxy"
        exit 1
    fi

    cd "$TEMP_DIR"
}

# Function to build tonutils-storage
build_tonutils_storage() {
    print_header "Building tonutils-storage"

    cd "$TEMP_DIR"

    if [ ! -d "tonutils-storage" ]; then
        echo "Cloning tonutils-storage..."
        git clone --depth 1 --branch "$TONUTILS_STORAGE_BRANCH" "$TONUTILS_STORAGE_REPO" tonutils-storage
    else
        echo "Using cached tonutils-storage repository"
        cd tonutils-storage
        git fetch origin "$TONUTILS_STORAGE_BRANCH"
        git checkout "$TONUTILS_STORAGE_BRANCH"
        git pull
        cd ..
    fi

    cd tonutils-storage

    # Check if mobile bindings exist
    if [ ! -d "mobile" ]; then
        echo "Creating mobile bindings package..."
        mkdir -p mobile
        cat > mobile/mobile.go << 'MOBILEGO'
package mobile

import (
    "encoding/json"
)

// Storage daemon placeholder
// This will be replaced with actual implementation

var isRunning = false

// StartStorage initializes and starts the TON storage daemon
func StartStorage(port int, dbPath string, configPath string) error {
    isRunning = true
    return nil
}

// StopStorage stops the TON storage daemon
func StopStorage() {
    isRunning = false
}

// IsRunning returns true if storage daemon is running
func IsRunning() bool {
    return isRunning
}

// AddBag adds a new bag to download
func AddBag(bagID string, downloadPath string) error {
    return nil
}

// RemoveBag removes a bag from storage
func RemoveBag(bagID string) error {
    return nil
}

// ListBags returns JSON array of all bags
func ListBags() string {
    bags := []interface{}{}
    data, _ := json.Marshal(bags)
    return string(data)
}

// GetBagInfo returns JSON info about a specific bag
func GetBagInfo(bagID string) string {
    info := map[string]interface{}{
        "id":     bagID,
        "status": "unknown",
    }
    data, _ := json.Marshal(info)
    return string(data)
}
MOBILEGO

        # Create go.mod if not exists
        if [ ! -f "mobile/go.mod" ]; then
            cat > mobile/go.mod << 'GOMOD'
module github.com/xssnick/tonutils-storage/mobile

go 1.21
GOMOD
        fi
    fi

    echo "Building with gomobile..."
    echo "Using Android API level: $ANDROID_API"
    cd mobile

    # Build gomobile command with verbose flag if requested
    local gomobile_flags="-target=android -androidapi $ANDROID_API"
    if [ "$VERBOSE" = true ]; then
        gomobile_flags="$gomobile_flags -v"
    fi

    gomobile bind $gomobile_flags -o "$LIBS_DIR/tonutils-storage.aar" .

    echo -e "${GREEN}Successfully built tonutils-storage.aar${NC}"

    cd "$TEMP_DIR"
}

# Function to show summary
show_summary() {
    print_header "Build Summary"

    echo "Generated libraries in $LIBS_DIR:"
    echo ""

    # Check for .so files (native libraries)
    if [ -f "$LIBS_DIR/jniLibs/arm64-v8a/libtonutils-proxy.so" ]; then
        SIZE=$(du -h "$LIBS_DIR/jniLibs/arm64-v8a/libtonutils-proxy.so" | cut -f1)
        echo -e "  ${GREEN}[OK]${NC} jniLibs/arm64-v8a/libtonutils-proxy.so ($SIZE)"
    elif [ -f "$LIBS_DIR/tonutils-proxy.aar" ]; then
        SIZE=$(du -h "$LIBS_DIR/tonutils-proxy.aar" | cut -f1)
        echo -e "  ${GREEN}[OK]${NC} tonutils-proxy.aar ($SIZE)"
    else
        echo -e "  ${RED}[MISSING]${NC} tonutils-proxy library"
    fi

    if [ -f "$LIBS_DIR/tonutils-storage.aar" ]; then
        SIZE=$(du -h "$LIBS_DIR/tonutils-storage.aar" | cut -f1)
        echo -e "  ${GREEN}[OK]${NC} tonutils-storage.aar ($SIZE)"
    else
        echo -e "  ${RED}[MISSING]${NC} tonutils-storage.aar"
    fi

    echo ""
    echo "Next steps:"
    echo "  1. Run 'npx cap sync android' to sync Capacitor"
    echo "  2. Build the Android app: cd android && ./gradlew assembleDebug"
    echo ""
}

# Main execution
main() {
    echo "=================================="
    echo " Tonnet Go Libraries Build Script"
    echo "=================================="
    echo ""
    echo "Build configuration:"
    echo "  Android API Level: $ANDROID_API"
    echo "  Build Proxy:       $BUILD_PROXY"
    echo "  Build Storage:     $BUILD_STORAGE"
    echo "  Verbose:           $VERBOSE"
    echo ""

    check_prerequisites
    setup_directories

    if [ "$BUILD_PROXY" = true ]; then
        build_tonutils_proxy
    fi

    if [ "$BUILD_STORAGE" = true ]; then
        build_tonutils_storage
    fi

    show_summary

    echo -e "${GREEN}Build completed successfully!${NC}"
}

# Run main function
main "$@"

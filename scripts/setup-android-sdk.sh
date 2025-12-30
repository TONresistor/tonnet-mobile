#!/bin/bash
#
# Android SDK/NDK Setup Script for Tonnet Mobile
#
# This script installs Android SDK command-line tools and required components
# without requiring Android Studio.
#
# Based on 2025 best practices:
# - Uses ANDROID_SDK_ROOT (with ANDROID_HOME fallback for compatibility)
# - Correct cmdline-tools/latest directory structure
# - NDK 27.x for gomobile and React Native New Architecture compatibility
#
# Usage:
#   ./scripts/setup-android-sdk.sh [options]
#
# Options:
#   --sdk-root PATH    Custom SDK installation path (default: ~/Android/Sdk)
#   --ndk-version VER  NDK version to install (default: 27.1.12297006)
#   --skip-ndk         Skip NDK installation
#   --skip-emulator    Skip emulator installation
#   --minimal          Minimal installation (no emulator, no system images)
#   --help             Show this help message
#
# References:
#   - https://developer.android.com/tools
#   - https://developer.android.com/tools/sdkmanager
#

set -e

# =============================================================================
# Configuration
# =============================================================================

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Default values
DEFAULT_SDK_ROOT="$HOME/Android/Sdk"
DEFAULT_NDK_VERSION="27.1.12297006"
DEFAULT_BUILD_TOOLS_VERSION="35.0.0"
DEFAULT_PLATFORM_VERSION="35"

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Command-line tools download URL (updated regularly by Google)
# Check https://developer.android.com/studio#command-tools for latest version
CMDLINE_TOOLS_VERSION="13114758"  # Latest as of December 2025
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

# Options
SDK_ROOT="$DEFAULT_SDK_ROOT"
NDK_VERSION="$DEFAULT_NDK_VERSION"
SKIP_NDK=false
SKIP_EMULATOR=false
MINIMAL=false

# =============================================================================
# Functions
# =============================================================================

print_header() {
    echo ""
    echo -e "${BLUE}==============================================================================${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}==============================================================================${NC}"
    echo ""
}

print_step() {
    echo -e "${GREEN}>>> $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}WARNING: $1${NC}"
}

print_error() {
    echo -e "${RED}ERROR: $1${NC}"
}

print_success() {
    echo -e "${GREEN}SUCCESS: $1${NC}"
}

show_help() {
    cat << EOF
Android SDK/NDK Setup Script for Tonnet Mobile

Usage: $0 [options]

Options:
    --sdk-root PATH    Custom SDK installation path (default: $DEFAULT_SDK_ROOT)
    --ndk-version VER  NDK version to install (default: $DEFAULT_NDK_VERSION)
    --skip-ndk         Skip NDK installation
    --skip-emulator    Skip emulator installation
    --minimal          Minimal installation (platform-tools, build-tools, platform only)
    --help             Show this help message

Examples:
    # Default installation
    $0

    # Custom SDK location
    $0 --sdk-root /opt/android-sdk

    # Minimal installation for CI
    $0 --minimal

    # Use specific NDK version
    $0 --ndk-version 26.3.11579264

Environment Variables (set after installation):
    ANDROID_SDK_ROOT   Primary Android SDK path (2025 standard)
    ANDROID_HOME       Legacy SDK path (for compatibility)
    ANDROID_NDK_HOME   Path to NDK installation

After installation, run:
    source scripts/env.sh

EOF
    exit 0
}

check_dependencies() {
    print_step "Checking dependencies..."

    local missing=()

    # Required tools
    if ! command -v curl &> /dev/null && ! command -v wget &> /dev/null; then
        missing+=("curl or wget")
    fi

    if ! command -v unzip &> /dev/null; then
        missing+=("unzip")
    fi

    if ! command -v java &> /dev/null; then
        print_warning "Java not found. JDK 17+ is required for Android development."
        print_warning "Install with: sudo dnf install java-17-openjdk-devel (Fedora)"
        print_warning "           or: sudo apt install openjdk-17-jdk (Ubuntu/Debian)"
    else
        JAVA_VERSION=$(java -version 2>&1 | head -n 1)
        echo "  Java: $JAVA_VERSION"
    fi

    if [ ${#missing[@]} -gt 0 ]; then
        print_error "Missing required tools: ${missing[*]}"
        echo ""
        echo "Install on Fedora/RHEL:"
        echo "  sudo dnf install curl unzip"
        echo ""
        echo "Install on Ubuntu/Debian:"
        echo "  sudo apt install curl unzip"
        echo ""
        exit 1
    fi

    echo "  All dependencies satisfied"
}

detect_platform() {
    print_step "Detecting platform..."

    case "$(uname -s)" in
        Linux*)
            PLATFORM="linux"
            ;;
        Darwin*)
            PLATFORM="mac"
            CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-mac-${CMDLINE_TOOLS_VERSION}_latest.zip"
            ;;
        MINGW*|CYGWIN*|MSYS*)
            PLATFORM="win"
            CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-win-${CMDLINE_TOOLS_VERSION}_latest.zip"
            ;;
        *)
            print_error "Unsupported platform: $(uname -s)"
            exit 1
            ;;
    esac

    echo "  Platform: $PLATFORM"
    echo "  Architecture: $(uname -m)"
}

download_cmdline_tools() {
    # Note: This function sets DOWNLOAD_TEMP_DIR global variable instead of echoing
    # to avoid capturing print_step output in the return value

    print_step "Downloading Android command-line tools..." >&2

    DOWNLOAD_TEMP_DIR="/tmp/android-sdk-setup-$$"
    local zip_file="$DOWNLOAD_TEMP_DIR/cmdline-tools.zip"

    mkdir -p "$DOWNLOAD_TEMP_DIR"

    if command -v curl &> /dev/null; then
        curl -L -o "$zip_file" "$CMDLINE_TOOLS_URL" --progress-bar
    else
        wget -O "$zip_file" "$CMDLINE_TOOLS_URL" --show-progress
    fi

    echo "  Downloaded to: $zip_file" >&2
}

setup_sdk_directory() {
    print_step "Setting up SDK directory structure..."

    # Create the correct directory structure
    # The cmdline-tools must be in $SDK_ROOT/cmdline-tools/latest/
    mkdir -p "$SDK_ROOT/cmdline-tools"

    local temp_dir="$1"
    local zip_file="$temp_dir/cmdline-tools.zip"

    # Extract to temp location first
    unzip -q "$zip_file" -d "$temp_dir"

    # Check if already installed
    if [ -d "$SDK_ROOT/cmdline-tools/latest" ]; then
        print_warning "cmdline-tools/latest already exists, backing up..."
        mv "$SDK_ROOT/cmdline-tools/latest" "$SDK_ROOT/cmdline-tools/latest.backup.$(date +%Y%m%d%H%M%S)"
    fi

    # Move to correct location (cmdline-tools -> latest)
    mv "$temp_dir/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"

    # Cleanup
    rm -rf "$temp_dir"

    echo "  SDK root: $SDK_ROOT"
    echo "  cmdline-tools: $SDK_ROOT/cmdline-tools/latest"

    # Verify structure
    if [ -f "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
        print_success "Directory structure is correct"
    else
        print_error "sdkmanager not found at expected location"
        exit 1
    fi
}

accept_licenses() {
    print_step "Accepting SDK licenses..."

    local sdkmanager="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

    # Accept all licenses automatically
    yes | "$sdkmanager" --licenses > /dev/null 2>&1 || true

    echo "  Licenses accepted"
}

install_sdk_components() {
    print_step "Installing SDK components..."

    local sdkmanager="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

    # Core components (always installed)
    local components=(
        "platform-tools"
        "platforms;android-$DEFAULT_PLATFORM_VERSION"
        "build-tools;$DEFAULT_BUILD_TOOLS_VERSION"
    )

    # NDK (unless skipped)
    if [ "$SKIP_NDK" = false ]; then
        components+=("ndk;$NDK_VERSION")
    fi

    # Emulator and system images (unless minimal or skipped)
    if [ "$MINIMAL" = false ] && [ "$SKIP_EMULATOR" = false ]; then
        components+=("emulator")
        # System image for emulator (x86_64 for better performance)
        if [ "$(uname -m)" = "x86_64" ]; then
            components+=("system-images;android-$DEFAULT_PLATFORM_VERSION;google_apis;x86_64")
        fi
    fi

    echo "  Components to install:"
    for comp in "${components[@]}"; do
        echo "    - $comp"
    done
    echo ""

    # Install components
    "$sdkmanager" --sdk_root="$SDK_ROOT" "${components[@]}"

    print_success "SDK components installed"
}

generate_env_file() {
    print_step "Generating environment file..."

    local env_file="$SCRIPT_DIR/env.sh"

    cat > "$env_file" << ENVFILE
#!/bin/bash
#
# Android SDK Environment Variables for Tonnet Mobile
#
# Generated by setup-android-sdk.sh on $(date)
#
# Usage:
#   source scripts/env.sh
#
# For permanent setup, add to your ~/.bashrc or ~/.zshrc:
#   source /path/to/tonnet-mobile/scripts/env.sh
#

# =============================================================================
# Android SDK Configuration (2025 Best Practices)
# =============================================================================

# Primary SDK path (2025 standard - replaces ANDROID_HOME)
export ANDROID_SDK_ROOT="$SDK_ROOT"

# Legacy SDK path (for compatibility with older tools like Cordova, some CI systems)
# Many tools still check ANDROID_HOME first, so we set both
export ANDROID_HOME="\$ANDROID_SDK_ROOT"

# NDK path (required for gomobile, React Native with C++ code)
export ANDROID_NDK_HOME="\$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"

# Alternative NDK variable (some tools use this)
export NDK_HOME="\$ANDROID_NDK_HOME"

# =============================================================================
# PATH Configuration
# =============================================================================

# Add SDK tools to PATH (order matters - more specific first)
# cmdline-tools: sdkmanager, avdmanager, etc.
export PATH="\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$PATH"

# platform-tools: adb, fastboot
export PATH="\$ANDROID_SDK_ROOT/platform-tools:\$PATH"

# emulator
export PATH="\$ANDROID_SDK_ROOT/emulator:\$PATH"

# build-tools (for aapt, zipalign, etc.)
export PATH="\$ANDROID_SDK_ROOT/build-tools/$DEFAULT_BUILD_TOOLS_VERSION:\$PATH"

# =============================================================================
# Go/gomobile Configuration
# =============================================================================

# Ensure Go binaries are in PATH (for gomobile)
if command -v go &> /dev/null; then
    export PATH="\$PATH:\$(go env GOPATH)/bin"
fi

# =============================================================================
# Java Configuration (optional - uncomment if needed)
# =============================================================================

# If you have multiple Java versions, set JAVA_HOME explicitly:
# export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
# export PATH="\$JAVA_HOME/bin:\$PATH"

# =============================================================================
# Verification Functions
# =============================================================================

# Function to verify Android SDK setup
verify_android_sdk() {
    echo "Android SDK Configuration:"
    echo "  ANDROID_SDK_ROOT: \$ANDROID_SDK_ROOT"
    echo "  ANDROID_HOME:     \$ANDROID_HOME"
    echo "  ANDROID_NDK_HOME: \$ANDROID_NDK_HOME"
    echo ""

    if [ -d "\$ANDROID_SDK_ROOT" ]; then
        echo "SDK Status: OK"
    else
        echo "SDK Status: NOT FOUND"
        return 1
    fi

    if [ -d "\$ANDROID_NDK_HOME" ]; then
        echo "NDK Status: OK"
    else
        echo "NDK Status: NOT FOUND (run: sdkmanager 'ndk;$NDK_VERSION')"
    fi

    echo ""
    echo "Available tools:"
    command -v sdkmanager &> /dev/null && echo "  sdkmanager: \$(which sdkmanager)"
    command -v adb &> /dev/null && echo "  adb:        \$(which adb)"
    command -v avdmanager &> /dev/null && echo "  avdmanager: \$(which avdmanager)"
    command -v emulator &> /dev/null && echo "  emulator:   \$(which emulator)"
}

# Export the function for interactive use
export -f verify_android_sdk 2>/dev/null || true

# =============================================================================
# Auto-verification (optional - comment out for faster shell startup)
# =============================================================================

# Uncomment to verify SDK on every source:
# verify_android_sdk

ENVFILE

    chmod +x "$env_file"

    echo "  Generated: $env_file"
    print_success "Environment file created"
}

generate_local_properties() {
    print_step "Generating local.properties for Android project..."

    local local_props="$PROJECT_DIR/android/local.properties"

    if [ -d "$PROJECT_DIR/android" ]; then
        cat > "$local_props" << PROPS
## This file must *NOT* be checked into Version Control Systems,
## as it contains information specific to your local configuration.
##
## Generated by setup-android-sdk.sh on $(date)

sdk.dir=$SDK_ROOT
PROPS

        echo "  Generated: $local_props"

        # Add to .gitignore if not already present
        local gitignore="$PROJECT_DIR/.gitignore"
        if [ -f "$gitignore" ]; then
            if ! grep -q "android/local.properties" "$gitignore"; then
                echo "android/local.properties" >> "$gitignore"
                echo "  Added to .gitignore"
            fi
        fi
    else
        print_warning "android/ directory not found, skipping local.properties"
    fi
}

create_direnv_config() {
    print_step "Creating .envrc for direnv (optional)..."

    local envrc="$PROJECT_DIR/.envrc"

    if [ ! -f "$envrc" ]; then
        cat > "$envrc" << 'ENVRC'
# Tonnet Mobile - direnv configuration
#
# If you use direnv (https://direnv.net/), this file will automatically
# load the Android SDK environment when you enter the project directory.
#
# To enable: direnv allow

# Source the Android SDK environment
source_env scripts/env.sh
ENVRC

        echo "  Created: $envrc"
        echo "  To enable direnv: direnv allow"
    else
        print_warning ".envrc already exists, not overwriting"
    fi
}

show_summary() {
    print_header "Installation Summary"

    echo "Android SDK installed successfully!"
    echo ""
    echo "Installation details:"
    echo "  SDK root:      $SDK_ROOT"
    echo "  NDK version:   $NDK_VERSION"
    echo "  Platform:      android-$DEFAULT_PLATFORM_VERSION"
    echo "  Build tools:   $DEFAULT_BUILD_TOOLS_VERSION"
    echo ""
    echo "Directory structure:"
    echo "  $SDK_ROOT/"

    # Show actual directories created
    for dir in cmdline-tools platform-tools platforms build-tools ndk emulator; do
        if [ -d "$SDK_ROOT/$dir" ]; then
            echo "    [OK] $dir/"
        fi
    done

    echo ""
    echo "Environment files created:"
    echo "  scripts/env.sh          - Environment variables"
    [ -f "$PROJECT_DIR/android/local.properties" ] && echo "  android/local.properties - Android project config"
    [ -f "$PROJECT_DIR/.envrc" ] && echo "  .envrc                   - direnv config (optional)"

    echo ""
    print_header "Next Steps"

    echo "1. Load the environment variables:"
    echo ""
    echo "   source scripts/env.sh"
    echo ""
    echo "2. Verify the installation:"
    echo ""
    echo "   verify_android_sdk"
    echo "   sdkmanager --list_installed"
    echo ""
    echo "3. (Optional) Add to your shell profile for permanent setup:"
    echo ""
    echo "   echo 'source $SCRIPT_DIR/env.sh' >> ~/.bashrc"
    echo ""
    echo "4. Build the Go libraries:"
    echo ""
    echo "   ./scripts/build-go-libs.sh"
    echo ""

    print_success "Android SDK setup complete!"
}

# =============================================================================
# Main Script
# =============================================================================

main() {
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --sdk-root)
                SDK_ROOT="$2"
                shift 2
                ;;
            --ndk-version)
                NDK_VERSION="$2"
                shift 2
                ;;
            --skip-ndk)
                SKIP_NDK=true
                shift
                ;;
            --skip-emulator)
                SKIP_EMULATOR=true
                shift
                ;;
            --minimal)
                MINIMAL=true
                SKIP_EMULATOR=true
                shift
                ;;
            --help|-h)
                show_help
                ;;
            *)
                print_error "Unknown option: $1"
                echo "Use --help for usage information"
                exit 1
                ;;
        esac
    done

    print_header "Android SDK/NDK Setup for Tonnet Mobile"

    echo "Configuration:"
    echo "  SDK root:     $SDK_ROOT"
    echo "  NDK version:  $NDK_VERSION"
    echo "  Skip NDK:     $SKIP_NDK"
    echo "  Skip emulator: $SKIP_EMULATOR"
    echo "  Minimal:      $MINIMAL"
    echo ""

    # Check if SDK already exists
    if [ -f "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
        print_warning "Android SDK already installed at $SDK_ROOT"
        echo ""
        read -p "Do you want to continue and potentially reinstall? [y/N] " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Aborted."
            exit 0
        fi
    fi

    # Run setup steps
    check_dependencies
    detect_platform

    download_cmdline_tools
    setup_sdk_directory "$DOWNLOAD_TEMP_DIR"

    accept_licenses
    install_sdk_components

    generate_env_file
    generate_local_properties
    create_direnv_config

    show_summary
}

# Run main function
main "$@"

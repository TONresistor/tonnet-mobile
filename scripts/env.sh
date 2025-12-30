#!/bin/bash
#
# Android SDK Environment Variables for Tonnet Mobile
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
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"

# Legacy SDK path (for compatibility with older tools like Cordova, some CI systems)
# Many tools still check ANDROID_HOME first, so we set both
export ANDROID_HOME="$ANDROID_SDK_ROOT"

# NDK path (required for gomobile, React Native with C++ code)
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/27.1.12297006"

# Alternative NDK variable (some tools use this)
export NDK_HOME="$ANDROID_NDK_HOME"

# =============================================================================
# PATH Configuration
# =============================================================================

# Add SDK tools to PATH (order matters - more specific first)
# cmdline-tools: sdkmanager, avdmanager, etc.
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

# platform-tools: adb, fastboot
export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"

# emulator
export PATH="$ANDROID_SDK_ROOT/emulator:$PATH"

# build-tools (for aapt, zipalign, etc.)
export PATH="$ANDROID_SDK_ROOT/build-tools/35.0.0:$PATH"

# =============================================================================
# Go/gomobile Configuration
# =============================================================================

# Ensure Go binaries are in PATH (for gomobile)
if command -v go &> /dev/null; then
    export PATH="$PATH:$(go env GOPATH)/bin"
fi

# =============================================================================
# Java Configuration
# =============================================================================

# JDK 21 for gomobile and Android builds
if [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# =============================================================================
# Verification Functions
# =============================================================================

# Function to verify Android SDK setup
verify_android_sdk() {
    echo "Android SDK Configuration:"
    echo "  ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
    echo "  ANDROID_HOME:     $ANDROID_HOME"
    echo "  ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
    echo ""

    if [ -d "$ANDROID_SDK_ROOT" ]; then
        echo "SDK Status: OK"
    else
        echo "SDK Status: NOT FOUND"
        return 1
    fi

    if [ -d "$ANDROID_NDK_HOME" ]; then
        echo "NDK Status: OK"
    else
        echo "NDK Status: NOT FOUND (run: sdkmanager 'ndk;27.1.12297006')"
    fi

    echo ""
    echo "Available tools:"
    command -v sdkmanager &> /dev/null && echo "  sdkmanager: $(which sdkmanager)"
    command -v adb &> /dev/null && echo "  adb:        $(which adb)"
    command -v avdmanager &> /dev/null && echo "  avdmanager: $(which avdmanager)"
    command -v emulator &> /dev/null && echo "  emulator:   $(which emulator)"
}

# Export the function for interactive use
export -f verify_android_sdk 2>/dev/null || true

# =============================================================================
# Auto-verification (optional - comment out for faster shell startup)
# =============================================================================

# Uncomment to verify SDK on every source:
# verify_android_sdk


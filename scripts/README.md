# Tonnet Mobile - Build Scripts

This directory contains scripts for building the Go libraries required by Tonnet Browser Mobile.

## Go Libraries Overview

Tonnet Browser Mobile requires the following Go libraries compiled as Android AAR files:

| Library | Repository | Purpose |
|---------|------------|---------|
| tonutils-proxy.aar | [xssnick/Tonutils-Proxy](https://github.com/xssnick/Tonutils-Proxy) | TON Proxy for .ton domain resolution |
| tonutils-storage.aar | [xssnick/tonutils-storage](https://github.com/xssnick/tonutils-storage) | TON Storage for decentralized file storage |

---

## Prerequisites

### Quick Setup (Recommended)

Use the automated setup script:

```bash
# Make executable
chmod +x scripts/setup-android-sdk.sh

# Run the setup
./scripts/setup-android-sdk.sh

# Source the environment variables
source scripts/env.sh
```

### Manual Setup

#### 1. Install Go (1.21+)

**Fedora/RHEL:**
```bash
sudo dnf install golang
```

**Ubuntu/Debian:**
```bash
sudo apt install golang-go
```

**macOS:**
```bash
brew install go
```

**Verify installation:**
```bash
go version
# Expected: go version go1.21.x or higher
```

#### 2. Install gomobile

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
```

**Verify installation:**
```bash
gomobile version
```

**Troubleshooting:** If `gomobile` command is not found, add Go bin to PATH:
```bash
export PATH=$PATH:$(go env GOPATH)/bin
# Add this to your ~/.bashrc or ~/.zshrc for persistence
```

#### 3. Install Android SDK (Without Android Studio)

See [Android SDK Best Practices 2025](#android-sdk-best-practices-2025) below for detailed instructions.

**Quick Command Line Setup:**
```bash
# Download command-line tools from https://developer.android.com/studio#command-tools
# Extract and set up correct directory structure

mkdir -p ~/Android/Sdk/cmdline-tools
unzip commandlinetools-linux-*.zip -d /tmp/
mv /tmp/cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# Set environment variables (or use scripts/env.sh)
source scripts/env.sh

# Accept licenses and install required components
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.1.12297006"
```

#### 4. Install Android NDK

**Recommended NDK Version (2025):** `27.1.12297006`

This version is compatible with:
- gomobile (with `-androidapi 21` or higher)
- React Native New Architecture
- Capacitor 8+

```bash
# Using sdkmanager
sdkmanager "ndk;27.1.12297006"
```

**Verify NDK:**
```bash
ls $ANDROID_NDK_HOME
# Should show: build, meta, ndk-build, prebuilt, etc.
```

---

## Android SDK Best Practices 2025

### Directory Structure

The recommended Android SDK directory structure in 2025:

```
$ANDROID_HOME/                          # ~/Android/Sdk
├── build-tools/
│   └── 35.0.0/
├── cmdline-tools/
│   └── latest/                         # IMPORTANT: use "latest" subfolder
│       └── bin/
│           ├── sdkmanager
│           ├── avdmanager
│           └── ...
├── emulator/
├── licenses/
├── ndk/
│   └── 27.1.12297006/                  # NDK installed via sdkmanager
├── platform-tools/
│   ├── adb
│   ├── fastboot
│   └── ...
├── platforms/
│   └── android-35/
└── system-images/                      # For emulators (optional)
```

### Environment Variables

**Important:** `ANDROID_HOME` is deprecated but still widely used. `ANDROID_SDK_ROOT` is the replacement, but many tools still rely on `ANDROID_HOME`.

**Recommendation (2025):** Set BOTH variables for maximum compatibility.

```bash
# Primary (new standard)
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"

# Legacy (for compatibility with older tools)
export ANDROID_HOME="$ANDROID_SDK_ROOT"

# NDK location (required for gomobile)
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/27.1.12297006"

# PATH additions
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
export PATH="$ANDROID_SDK_ROOT/emulator:$PATH"
```

Use `scripts/env.sh` for a ready-to-use configuration.

### NDK Version Selection Guide

| Use Case | Recommended NDK | Notes |
|----------|-----------------|-------|
| gomobile (2025) | 27.1.12297006 | Use `-androidapi 21` or higher |
| React Native 0.76+ | 27.1.12297006 | Required for New Architecture |
| Capacitor 8+ | 26.x or 27.x | Generally more flexible |
| Legacy projects | 25.2.9519653 | For API 19 support |

**Note:** NDK 27+ requires minimum API level 21 (Android 5.0 Lollipop). This is fine for modern apps as Google Play requires API 24+ for new apps.

### gomobile Compatibility Notes

Modern gomobile versions have been updated to work with newer NDKs. Key points:

1. **API Level:** Use `-androidapi 21` or higher (NDK 27+ dropped support for API < 21)
2. **NDK Path:** Modern gomobile auto-detects NDK from `$ANDROID_SDK_ROOT/ndk/<version>` or `$ANDROID_NDK_HOME`
3. **Architecture:** Build for all ABIs by default, or specify `-target=android/arm64` for arm64-only

```bash
# Recommended gomobile command for 2025
gomobile bind -target=android -androidapi 24 -o output.aar ./package
```

---

## Capacitor Configuration

### local.properties

The `android/local.properties` file is auto-generated and should NOT be committed to version control. It contains machine-specific paths:

```properties
sdk.dir=/home/username/Android/Sdk
```

To generate it manually:
```bash
echo "sdk.dir=$ANDROID_SDK_ROOT" > android/local.properties
```

### variables.gradle

The `android/variables.gradle` file controls SDK versions for Capacitor:

```gradle
ext {
    minSdkVersion = 24          # Minimum Android 7.0
    compileSdkVersion = 35      # Latest stable SDK
    targetSdkVersion = 35       # Target latest for Google Play compliance
    // ... other versions
}
```

**Note:** Capacitor major versions are tied to target SDK versions. Only update target SDK with Capacitor upgrades.

---

## Building Libraries

### Automated Build

```bash
# Source environment first
source scripts/env.sh

# Run the build script
./scripts/build-go-libs.sh
```

**Options:**
```bash
./scripts/build-go-libs.sh --help         # Show help
./scripts/build-go-libs.sh --clean        # Clean temp directory before build
./scripts/build-go-libs.sh --proxy-only   # Build only tonutils-proxy.aar
./scripts/build-go-libs.sh --storage-only # Build only tonutils-storage.aar
```

### Manual Build

#### tonutils-proxy

```bash
# Clone repository
git clone https://github.com/xssnick/Tonutils-Proxy
cd Tonutils-Proxy

# Build using Makefile (if available)
make build-android-lib

# Or using gomobile directly (API 24 for NDK 27+ compatibility)
gomobile bind -target=android -androidapi 24 -o tonutils-proxy.aar ./mobile

# Copy to project
cp tonutils-proxy.aar /path/to/tonnet-mobile/android/app/libs/
```

#### tonutils-storage

```bash
# Clone repository
git clone https://github.com/xssnick/tonutils-storage
cd tonutils-storage

# Build mobile bindings
cd mobile  # or create mobile package if not exists
gomobile bind -target=android -androidapi 24 -o tonutils-storage.aar .

# Copy to project
cp tonutils-storage.aar /path/to/tonnet-mobile/android/app/libs/
```

---

## Output

After successful build, you will find the AAR files in:
```
android/app/libs/
  tonutils-proxy.aar
  tonutils-storage.aar
```

## Verifying AAR Files

```bash
# List contents of AAR
unzip -l android/app/libs/tonutils-proxy.aar

# Check size (typically 10-50 MB each)
du -h android/app/libs/*.aar
```

---

## Integration with Android Project

The `android/app/build.gradle` is already configured to include AAR files:

```gradle
dependencies {
    implementation fileTree(include: ['*.aar'], dir: 'libs')
}
```

After building the libraries:

```bash
# Sync Capacitor
npx cap sync android

# Build Android app
cd android && ./gradlew assembleDebug
```

---

## Troubleshooting

### gomobile: command not found

```bash
export PATH=$PATH:$(go env GOPATH)/bin
```

### NDK not found

```bash
# Check available NDKs
ls $ANDROID_SDK_ROOT/ndk/

# Install specific version
sdkmanager "ndk;27.1.12297006"

# Update ANDROID_NDK_HOME
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/27.1.12297006
```

### "unsupported API version 16 (not in 21..35)"

Modern NDKs (25+) dropped support for older API levels. Fix:
```bash
# Use API 21 or higher
gomobile bind -target=android -androidapi 21 -o output.aar ./package
```

### Build fails with "cannot find package"

```bash
# Update Go modules
cd /tmp/tonnet-go-libs/Tonutils-Proxy
go mod tidy
go mod download
```

### AAR too large

The AAR files include native libraries for multiple architectures. To reduce size for testing:

```bash
# Build for specific architecture only
gomobile bind -target=android/arm64 -androidapi 24 -o output.aar ./...
```

### Permission denied on build script

```bash
chmod +x scripts/build-go-libs.sh
chmod +x scripts/setup-android-sdk.sh
```

### ANDROID_HOME vs ANDROID_SDK_ROOT confusion

Both variables point to the same location. Set both for maximum compatibility:
```bash
source scripts/env.sh
```

---

## CI/CD Integration

For GitHub Actions, see the workflow in `.github/workflows/build-android.yml`.

Required secrets:
- None for unsigned APK
- `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` for signed release

### CI Environment Variables

```yaml
env:
  ANDROID_SDK_ROOT: ${{ runner.temp }}/android-sdk
  ANDROID_HOME: ${{ runner.temp }}/android-sdk
  ANDROID_NDK_HOME: ${{ runner.temp }}/android-sdk/ndk/27.1.12297006
```

---

## Links and References

### Official Documentation
- [Android Command-line Tools](https://developer.android.com/tools)
- [sdkmanager Documentation](https://developer.android.com/tools/sdkmanager)
- [Environment Variables](https://developer.android.com/tools/variables)
- [NDK Downloads](https://developer.android.com/ndk/downloads)
- [gomobile Documentation](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile)

### Project Dependencies
- [tonutils-proxy](https://github.com/xssnick/Tonutils-Proxy)
- [tonutils-storage](https://github.com/xssnick/tonutils-storage)

### Capacitor & React Native
- [Capacitor Android Documentation](https://capacitorjs.com/docs/android)
- [Capacitor Environment Setup](https://capacitorjs.com/docs/getting-started/environment-setup)
- [React Native New Architecture](https://docs.expo.dev/guides/new-architecture/)

---

## Version History

| Date | Changes |
|------|---------|
| 2025-12 | Updated for 2025 best practices: NDK 27.x, ANDROID_SDK_ROOT, API 24+ |
| 2025-01 | Initial version with NDK 25.x |

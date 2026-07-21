# Android native build

The Android application embeds a single native dependency: the arm64 shared library from
[`TONresistor/Tonutils-Proxy`](https://github.com/TONresistor/Tonutils-Proxy). The build does not
use `gomobile`, AAR wrappers, or `tonutils-storage`.

## Supported toolchain

| Tool | Required version |
| --- | --- |
| Node.js | 22 or newer |
| Go | 1.25.5 exactly (`GOTOOLCHAIN=local`, `GOEXPERIMENT=nodwarf5`) |
| Java | 21 or newer |
| Android platform / target SDK | 36 |
| Android min SDK | 28 |
| Android NDK | 27.1.12297006 |
| CMake | 3.18.1 |

The app ships only `arm64-v8a`. The proxy source is fixed to `v1.9.4` at commit
`c071d9e40521cd92b99aecc58d3337ea50f51bc2`; the build fails if the tag resolves to anything
else. After that verification, the repository-owned
`scripts/patches/tonutils-proxy-v1.9.4-mobile.patch` is applied. It synchronizes the process-wide
native lifecycle, reserves startup tokens atomically, makes an interrupted startup return
`CANCELLED`, exposes a health check, and bounds native shutdown to ten seconds. This prevents a
timed-out JNI call from hanging or stopping a newer proxy generation.

## Environment setup

For a command-line installation of SDK 36, Build Tools 36.0.0, NDK 27.1, and CMake 3.18.1:

```bash
./scripts/setup-android-sdk.sh --minimal
source scripts/env.sh
verify_android_sdk
```

The setup script writes machine-specific SDK paths to the ignored
`scripts/env.local.sh`. The tracked `scripts/env.sh` remains unchanged and loads that local file
when present. The optional `.envrc` created for direnv is also ignored by Git.

If the SDK is already installed, export the paths directly:

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.1.12297006"
```

`android/local.properties` is machine-specific and must not be committed.

## Build the proxy

```bash
./scripts/build-ton-proxy.sh
```

The script:

- fetches the pinned tag into an isolated directory below `${TMPDIR:-/tmp}`;
- verifies the exact Git commit before compilation;
- applies the deterministic, version-specific mobile lifecycle patch;
- verifies Go modules, then cross-compiles only Android arm64 API 28 with CGO;
- links native LOAD segments for 16 KiB Android memory pages;
- installs `android/app/libs/jniLibs/arm64-v8a/libtonutils-proxy.so`;
- requires the reproducible SHA-256
  `59ef357356cc3720a3854c3ec32e79834b8c0c59fd7c4ed3404fa4258ada643d`.

Use `--clean` to discard the isolated checkout first. Cleanup is allowed only when the directory
contains the build-root marker created by this script, so an accidental `TON_PROXY_BUILD_DIR`
cannot delete an unrelated directory. The variable can relocate the checkout without changing the
output path. The `.so` is generated and ignored by Git.

## Build and verify the app

```bash
npm ci
npm run build
npm run cap:sync
./scripts/build-ton-proxy.sh
cd android
./gradlew spotlessCheck testProdDebugUnitTest lintProdDebug assembleProdDebug
```

For the current pre-release, use `lintBetaRelease assembleBetaRelease`. Stable versions use
`lintProdRelease assembleProdRelease`. Gradle reads `versionName` directly from the root
`package.json`; `versionCode` remains an explicit Android value in `android/app/build.gradle`.

## Publish a release

Use `v<package-version>` for the Git tag; the APK itself is named
`tonnet-browser-<package-version>.apk`. A beta tag such as `v1.1.1-beta` must be published as a
GitHub pre-release and must point to a commit on the default branch.

Before the first release signed by a new key, run the `Build Release APK` workflow manually with
the matching channel. If its certificate repository variable is absent, the workflow signs the
APK, prints the public SHA-256 fingerprint, and stops before upload. Save that fingerprint as
`ANDROID_BETA_SIGNING_CERT_SHA256` or `ANDROID_PROD_SIGNING_CERT_SHA256`, then rerun. Later builds
fail closed if the signer changes.

## Troubleshooting

- `Android arm64 compiler not found`: install NDK `27.1.12297006` and verify
  `ANDROID_NDK_HOME`.
- `Go 1.25.5 is required exactly`: install that toolchain; `gomobile` is not needed.
- `source verification failed`: remove the isolated checkout with
  `./scripts/build-ton-proxy.sh --clean`. Do not bypass the commit check.
- CMake cannot find `libtonutils-proxy.so`: run the proxy build before Gradle.

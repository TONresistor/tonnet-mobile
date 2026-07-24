<h1 align="center">Tonnet Browser Mobile</h1>

<p align="center">
  <strong>TON Network Browser for Android</strong>
</p>

<p align="center">
  <a href="https://github.com/TONresistor/tonnet-mobile/releases/tag/v2.0.0-beta">
    <img src="https://img.shields.io/badge/Download_Beta-FF9800?style=for-the-badge&logo=android&logoColor=white" alt="Download beta">
  </a>
  &nbsp;
  <a href="https://tonnet.resistance.dog">
    <img src="https://img.shields.io/badge/tonnet.resistance.dog-0088cc?style=for-the-badge&logo=globe&logoColor=white" alt="Website">
  </a>
</p>

---

## About

Native Android browser for TON Sites. Browse `.ton`, `.adnl` and `.t.me` sites directly over TON or through an optional encrypted 2-hop tunnel.

## Features

- `.ton`, `.adnl` and `.t.me` TON Sites
- Direct TON access or encrypted 2-hop ADNL tunnel
- TON DHT relay discovery and automatic tunnel rerouting
- Third-party cookies blocked, Referer stripped and client hints removed
- JavaScript control, GPC/DNT and fingerprinting protections
- Tabs, bookmarks, custom homepage and navigation gestures

## Install

| Stable | Pre-release |
|:------:|:-----------:|
| v1.0 | v2.0.0-beta |
| [![Stable](https://img.shields.io/badge/Download_APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/TONresistor/tonnet-mobile/releases/latest) | [![Beta](https://img.shields.io/badge/Download_Beta-FF9800?style=for-the-badge&logo=android&logoColor=white)](https://github.com/TONresistor/tonnet-mobile/releases/tag/v2.0.0-beta) |

Enable installation from unknown sources, then install the APK. Android 9 or later, an ARM64 device and an up-to-date Android System WebView are required.

## Build

Requires JDK 17, Go 1.25.12, Android SDK 36, Android NDK 28.2.13676358 and CMake 3.22.1.

```bash
git clone https://github.com/TONresistor/tonnet-mobile.git
cd tonnet-mobile/android
./gradlew assembleBetaDebug assembleProdRelease
```

## Stack

| Component | Technology |
|-----------|------------|
| Application | Native Android, Kotlin, XML Views, AndroidX |
| Browser | Android System WebView, AndroidX WebKit |
| Proxy | [tonutils-proxy](https://github.com/TONresistor/Tonutils-Proxy) and [tonutils-go](https://github.com/xssnick/tonutils-go) |
| Tunnel | [adnl-tunnel](https://github.com/ton-blockchain/adnl-tunnel) |
| Transport | RLDP over ADNL over UDP |
| Build | Gradle, Android Gradle Plugin, Go and Android NDK |

## Links

- [Website](https://tonnet.resistance.dog)
- [Desktop version](https://github.com/TONresistor/Tonnet-Browser)
- [Telegram](https://t.me/zkproof)
- [Issues](https://github.com/TONresistor/tonnet-mobile/issues)

## Acknowledgements

[tonutils-proxy](https://github.com/xssnick/tonutils-proxy),
[tonutils-go](https://github.com/xssnick/tonutils-go),
[adnl-tunnel](https://github.com/ton-blockchain/adnl-tunnel).

## License

MIT

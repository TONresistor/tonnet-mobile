<h1 align="center">Tonnet Browser Mobile</h1>

<p align="center">
  <strong>TON Network Browser for Android</strong>
</p>

<p align="center">
  <a href="https://github.com/TONresistor/tonnet-mobile/releases/latest">
    <img src="https://img.shields.io/badge/Download_APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Download">
  </a>
  &nbsp;
  <a href="https://tonnet.resistance.dog">
    <img src="https://img.shields.io/badge/tonnet.resistance.dog-0088cc?style=for-the-badge&logo=globe&logoColor=white" alt="Website">
  </a>
</p>

---

## About

Android browser for the TON Network. Browse `.ton` sites directly or anonymously through a 3-hop encrypted tunnel.

Anonymous mode routes traffic through a 3-hop garlic circuit via ADNL tunnel. Relay nodes are discovered from the TON DHT. Outbound and inbound paths use independent circuits. Payload is encrypted end-to-end between your device and the exit gateway.

## Features

- `.ton`, `.adnl` and `.t.me` domains
- Anonymous mode: 3-hop ADNL tunnel, garlic routing, relay discovery via TON DHT
- End-to-end encrypted payload, automatic circuit rerouting
- Third-party cookies blocked, tracker blocking, Referer/Origin stripping
- JavaScript toggle, GPC/DNT headers

## Install

| Stable | Pre-release |
|:------:|:-----------:|
| v1.0 | v1.1.0-beta02 |
| [![Stable](https://img.shields.io/badge/Download_APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/TONresistor/tonnet-mobile/releases/latest) | [![Beta](https://img.shields.io/badge/Download_Beta-FF9800?style=for-the-badge&logo=android&logoColor=white)](https://github.com/TONresistor/tonnet-mobile/releases/tag/v1.1.0-beta02) |

Enable "Install from unknown sources", then install. Requires Android 9.0+ (API 28).

## Build

```bash
# Node.js 22+, Go 1.24+, Android SDK, NDK 27.1, Java 21

git clone https://github.com/TONresistor/tonnet-mobile.git
cd tonnet-mobile
npm install
npm run build
npx cap sync android
cd android && ./gradlew assembleRelease
```

## Stack

| Component | Technology |
|-----------|------------|
| Framework | Capacitor 8 |
| Frontend | React 19, TypeScript 5.9, Tailwind 4, Zustand |
| Build | Vite 7 |
| Proxy | [tonutils-proxy](https://github.com/TONresistor/Tonutils-Proxy) + [ADNL tunnel](https://github.com/ton-blockchain/adnl-tunnel) |
| Transport | RLDP over ADNL over UDP |

## Links

- [Website](https://tonnet.resistance.dog)
- [Desktop version](https://github.com/TONresistor/Tonnet-Browser-stable)
- [Telegram](https://t.me/zkproof)
- [Issues](https://github.com/TONresistor/tonnet-mobile/issues)

## Acknowledgements

[tonutils-proxy](https://github.com/xssnick/tonutils-proxy),
[tonutils-go](https://github.com/xssnick/tonutils-go),
[adnl-tunnel](https://github.com/ton-blockchain/adnl-tunnel)

## License

MIT

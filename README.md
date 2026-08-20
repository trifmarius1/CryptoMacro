# CryptoMacro — Android

Native **Crypto Portfolio & Market Intelligence** app for phones, foldables, tablets, and DeX.

This repository is **only the Android app**. The website lives in a separate repo: [trifmarius1/CryptoDashboard](https://github.com/trifmarius1/CryptoDashboard).

This folder is an Android Studio / Gradle project. The previous React web dashboard was replaced by a Jetpack Compose app that keeps the same market coverage (BTC, ETH, SOL, ADA, S&P 500, gold, TOTAL/BTC.D, Fear & Greed, Shemitah cycles) and adds portfolio, security, and adaptive multi-pane layouts from `docs/SPEC.md`.

## Open & run

1. Install **Android Studio** (Ladybug / 2024.2+ or newer) with **JDK 17** (the bundled JBR is fine).
2. Open this folder as an Android project.
3. Let Gradle sync, then run the `app` configuration on a device or emulator (**API 26+**).

Command line (requires Android SDK + JDK 17):

```bash
./gradlew :app:assembleDebug
```

On Windows: `gradlew.bat :app:assembleDebug`

Create `local.properties` if Studio does not:

```
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## What’s in the app

| Destination | Features |
|---|---|
| **Markets** | Live Binance WebSocket + REST failover, Top 200 crypto / Top 50 stocks, pin extra charts, interactive Canvas chart (pinch/pan/crosshair, SMA/EMA/volume) |
| **Portfolio** | Local holdings & transactions (buy/sell/transfer/staking) from top 200 coins, USD/EUR, allocation donut, DCA vs weekly/monthly BTC |
| **Learn** | Fear & Greed, Bitcoin halving countdown, mempool fees, Shemitah 7-year overlay, education modules (cycles, risk, self-custody, 2FA, leverage) |
| **Settings** | Dark/light/system, biometric lock, `FLAG_SECURE` privacy shield, encrypted read-only exchange API keys (Binance/Coinbase/Kraken) |

Adaptive UI:

- **Compact** (`< 600dp`) — bottom navigation, single pane
- **Medium** (`600–839dp`) — navigation rail, list-detail
- **Expanded** (`≥ 840dp`) — drawer + triple-pane workbench
- **Tabletop fold** — chart on top, portfolio controls below

Also: Glance home-screen widgets, Quick Settings tiles (BTC/ETH price + privacy), Add-transaction shortcut. Keyboard: Ctrl+1–4 tabs, Ctrl+F Markets, Ctrl+R refresh.

How it was built (beginner walkthrough + line-by-line): `docs/CryptoMacro_Technical_Build_Guide.pdf`.

## Architecture

Kotlin 2 · Jetpack Compose · Material 3 · Hilt · Room · DataStore · EncryptedSharedPreferences (AES-256-GCM via Android Keystore) · OkHttp + WebSocket · WorkManager · Glance

Data never leaves the device: no backend, backups disabled, API secrets stay in Keystore-backed prefs.

## Disclaimer

Not financial advice. Market data may be delayed. Shemitah overlays are educational / historical-cycle context only.

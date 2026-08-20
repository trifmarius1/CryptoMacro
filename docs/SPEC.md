# Product & Architecture Requirements Document: Multi-Screen Android Crypto Dashboard Application

---

## 1. Executive Summary & Vision

### 1.1 Overview
The **Crypto Portfolio & Market Intelligence App** is a native, production-grade Android application designed to transition the core value propositions of the web platform—accessible retail market context, long-term portfolio tracking (DCA/HODL), real-time pricing feeds, educational insights, and robust asset security—into a deeply responsive, fluid Android experience.

### 1.2 Target Devices & Form Factors
The app must provide first-class adaptive user experiences across the entire spectrum of Android screen sizes and configurations:
* **Compact Phones:** Small/Standard portrait displays (< 600dp width) such as Pixel 4a, Pixel 8.
* **Large Phones & Phablets:** High-resolution vertical screens (e.g., Galaxy S24 Ultra, Pixel Pro models).
* **Foldables (Fold & Flip):** Dual-state transition handling (Outer Cover Screen ~400dp vs. Unfolded Inner Screen ~840dp+), posture-aware layouts (tabletop, half-open).
* **Small Tablets:** 7–8.4 inch displays (e.g., Galaxy Tab A, 600dp–839dp width in portrait/landscape).
* **Large Tablets:** 10–14.6 inch widescreen displays (e.g., Pixel Tablet, Galaxy Tab S9 Ultra, >= 840dp width).
* **Android Desktop Mode / Samsung DeX / ChromeOS:** Multi-window, resizable free-form windows with keyboard/mouse navigation.

---

## 2. Adaptive Layouts & Window Size Classes (Material Design 3)

The application leverages **Jetpack Compose** and Android's official **Window Size Classes** (`Compact`, `Medium`, `Expanded`) across both width and height to dynamically re-architect navigation and content presentation without duplicating business logic.

```
+-----------------------------------------------------------------------------------------+
|                               WINDOW SIZE CLASS ADAPTATION MATRIX                      |
+-------------------+----------------------+-----------------------+----------------------+
| Dimension         | Compact (< 600dp)    | Medium (600dp - 839dp)| Expanded (>= 840dp)  |
+-------------------+----------------------+-----------------------+----------------------+
| Width             | Standard Phone       | Foldable Unfolded,    | Large Tablet,        |
|                   | Portrait             | Small Tablet Portrait | Landscape Tablet, DeX|
+-------------------+----------------------+-----------------------+----------------------+
| Navigation        | Bottom Navigation    | Navigation Rail       | Permanent / Modal    |
| UI Pattern        | Bar (NavigationBar)  | (NavigationRail)      | Navigation Drawer    |
+-------------------+----------------------+-----------------------+----------------------+
| Layout Strategy   | Single-pane column;  | Two-column grid /     | Dual/Triple Pane     |
|                   | Drill-down full-page | List-Detail layout    | Canonical Layouts;   |
|                   | sheet / navigation   | with modal inspector  | Supporting Pane View |
+-------------------+----------------------+-----------------------+----------------------+
| Chart & Analytics | Stacked vertical     | Side-by-side metric & | Full interactive     |
| Presentation      | cards; scrollable    | interactive chart     | multi-pane financial |
|                   | candlestick viewport | pane                  | workbench with stats |
+-------------------+----------------------+-----------------------+----------------------+
```

### 2.1 Screen Size Breakdown & Responsive Behavior

#### A. Compact Width (`< 600dp` — Standard Phones & Foldable Cover Screens)
* **Navigation:** Fixed bottom `NavigationBar` with 4 primary destinations (Dashboard/Markets, Portfolio, Learn/Insights, Settings/Security).
* **Dashboard Structure:** Single-column LazyColumn containing:
  * Total Net Worth & 24h PnL banner.
  * Quick Actions row (Deposit, Withdraw, Add Transaction, Scan QR).
  * Horizontal asset chip selector (BTC, ETH, SOL, S&P 500 benchmark).
  * Compact interactive chart (1D, 1W, 1M, 1Y, ALL toggle).
  * Vertical list of watchlist/holdings with real-time mini sparklines.
* **Detail Navigation:** Screen-to-screen full transitions using Compose Navigation with predictive back gesture animations.

#### B. Medium Width (`600dp - 839dp` — Small Tablets, Phablets in Landscape, Foldable Inner Displays)
* **Navigation:** Left-aligned `NavigationRail` to maximize vertical viewport space.
* **Layout Structure (List-Detail Pane Scaffold):**
  * Left 40% column: Watchlist, recent transactions, and portfolio asset allocation breakdown.
  * Right 60% column: Focused asset deep-dive (TradingView-style interactive candlestick chart, depth chart, order book summary, and asset metrics).
* **Foldable Posture Support:** Integrates `androidx.window.layout.FoldingFeature`. When in tabletop / half-folded posture:
  * Top half: Live Charting & Price Tickers.
  * Bottom half: Transaction forms, DCA calculator, or order execution controls.

#### C. Expanded Width (`>= 840dp` — 10”+ Tablets, Landscape Slates, Samsung DeX, ChromeOS)
* **Navigation:** Permanent left-hand `NavigationDrawer` with extended action items and account switcher.
* **Layout Structure (Triple-Pane Financial Workbench):**
  * **Pane 1 (Navigation & Markets List, 25%):** Searchable crypto list, categorization tabs (Top 100, DeFi, Layer 1/2, Memes, Custom Watchlist).
  * **Pane 2 (Main Chart & Analytics Canvas, 50%):** High-framerate Canvas chart with technical indicators (RSI, MACD, Volume, Bollinger Bands), timeline slider, benchmark overlay (vs. S&P 500 / Gold).
  * **Pane 3 (Context, Portfolio & DCA Insights, 25%):** Portfolio holdings for selected asset, average cost basis calculator, live order flow, recent news feed, and educational risk disclosures.

---

## 3. Core Functional Modules & Feature Specifications

### 3.1 Live Market Dashboard & Benchmarking
* **Real-time Price Engine:** WebSocket integration with automatic fallback to high-efficiency polling (exponential backoff on backgrounding).
* **Multi-Asset Intelligence:** Real-time tracking for Bitcoin (BTC), Ethereum (ETH), top altcoins, stablecoins (USDC, USDT), and macro reference assets (S&P 500, Gold) for macro correlation analysis.
* **Interactive Charting Engine:**
  * Rendered via custom Jetpack Compose Canvas or native hardware-accelerated charting library.
  * Support for gestures: pinch-to-zoom, horizontal pan, long-press tooltip crosshairs, and timeframe selectors (1H, 24H, 7D, 30D, 90D, 1Y, ALL).
  * Indicator overlays: Simple Moving Average (SMA), Exponential Moving Average (EMA), Volume histograms.

### 3.2 Portfolio Management & DCA/HODL Tracker
* **Transaction Entry & Sync:**
  * Manual transaction logging (Buy, Sell, Transfer, Staking Reward).
  * Read-only Exchange API integration via encrypted API key storage (Kraken, Coinbase, Binance).
  * Public blockchain address watch-only tracking (Bitcoin xPub/zPub, Ethereum ERC-20 addresses).
* **Metrics & Analytics:**
  * Total Value (USD / EUR / Local Fiat), Unrealized/Realized Profit & Loss.
  * Dollar-Cost Averaging (DCA) performance calculator: Compares actual user purchases vs. recurring benchmark buys.
  * Asset Allocation visualizer (Interactive donut chart with drilldown).

### 3.3 Security, Privacy & Self-Sovereignty First
* **Biometric Authentication:** Hardware-backed `BiometricPrompt` (Fingerprint, Class 3 3D Face Unlock) with Android Keystore integration.
* **Privacy Shield:** Automatic app-switcher blur/blanking (`FLAG_SECURE` togglable in settings to prevent unauthorized screen captures or shoulder surfing).
* **Hardware Token Support:** FIDO2 / WebAuthn and YubiKey NFC support for transaction signing and account unlock.
* **Zero-Knowledge Architecture:** No central server storage of user portfolios or private keys; all portfolio data is encrypted locally using AES-256-GCM via `EncryptedSharedPreferences` / Android SQLCipher.

### 3.4 Retail Education & Risk Transparency
* **Educational Narrative Integration:** Built-in modules covering market cycle histories, risk mitigation, self-custody principles, 2FA hardening, and avoiding high-leverage traps.
* **Market Sentiment & On-Chain Context:**
  * Crypto Fear & Greed Index tracker.
  * Bitcoin Halving countdown & historical cycle phase indicator.
  * Network gas fees / mempool fee estimator.

---

## 4. Technical Architecture & Tech Stack

```
+---------------------------------------------------------------------------------------+
|                                    APP ARCHITECTURE                                   |
+---------------------------------------------------------------------------------------+
|  UI Layer (Jetpack Compose + Material 3 + Adaptive Navigation Suite + MVI / MVVM)     |
+---------------------------------------------------------------------------------------+
|  Domain Layer (UseCases: GetLivePricesUseCase, CalculateDcaProfitUseCase, etc.)       |
+---------------------------------------------------------------------------------------+
|  Data Layer (Repositories: MarketRepo, PortfolioRepo, AuthRepo, PreferencesRepo)     |
+---------------------------------------------------------------------------------------+
|  Local Data (Room DB + SQLCipher, Encrypted DataStore)  |  Remote (Ktor / Retrofit,   |
|                                                         |  OkHttp WebSocket)          |
+---------------------------------------------------------------------------------------+
```

### 4.1 Recommended Android Stack
* **Language:** Kotlin 2.x (100% Kotlin Coroutines & Kotlin Flows).
* **UI Toolkit:** Jetpack Compose with Material Design 3 (`androidx.compose.material3.adaptive`).
* **Architecture:** Clean Architecture + Unidirectional Data Flow (MVI/MVVM).
* **Dependency Injection:** Hilt / Dagger.
* **Networking & WebSockets:** Ktor Client or Retrofit + OkHttp with WebSocket listener.
* **Persistence:** Room Database with SQLCipher encryption + Jetpack DataStore (Preferences / Proto).
* **Background Tasks:** WorkManager for periodic price alert checks, widget refreshes, and portfolio sync.
* **Android Widgets:** Jetpack Glance for responsive Home Screen and Lock Screen widgets (Compact ticker widget, 4x2 portfolio summary).

---

## 5. UI/UX Design System Specifications

### 5.1 Color Palette & Themes
* **Dark Mode (Default):** Deep Slate Background (`#0B0E14`), Surface (`#151A23`), Surface Variant (`#1E2532`), Border/Outline (`#2A3447`).
* **Light Mode:** Clean Off-White Background (`#F8F9FA`), Surface (`#FFFFFF`), Surface Variant (`#EDF2F7`), Outline (`#E2E8F0`).
* **Semantic Accents:**
  * **Bullish / Profit:** Vibrant Emerald (`#00C087` / `#10B981`).
  * **Bearish / Loss:** Crimson Red (`#F6465D` / `#EF4444`).
  * **Brand Primary:** Deep Cyan / Electric Indigo (`#3B82F6` / `#6366F1`).
  * **Gold / Benchmark:** Amber Gold (`#F59E0B`).

### 5.2 Typography
* **Primary System Font:** Roboto / Google Sans.
* **Numerical / Financial Display:** Monospaced tabular numerals (`FontFeatureSettings = "tnum"`) to eliminate jitter during real-time price updates.

---

## 6. Android Ecosystem Features

1. **Glance App Widgets:**
   * **Small (2x1):** Favorite crypto live price + 24h delta percentage.
   * **Medium (4x2):** Portfolio balance sparkline + top 3 gainers/losers.
2. **App Shortcuts & Quick Tiles:**
   * Quick Tile to toggle privacy screen or view instant BTC/ETH price in Quick Settings.
   * Dynamic shortcuts for direct navigation to "Add Buy Transaction" or "Portfolio Scan".
3. **Adaptive Keyboard & Mouse Handling (Tablets/DeX):**
   * Full hardware keyboard shortcuts (`Ctrl + F` search, `Ctrl + R` refresh, `1-5` tab switching).
   * Hover effects on interactive chart elements and list items.

---

## 7. Implementation Roadmap & Milestones

| Milestone | Deliverables | Target Timeline |
|:---|:---|:---|
| **Phase 1: Foundations** | Project setup, Hilt DI, Room DB with SQLCipher, Ktor Network/WebSocket client, Theme & M3 Adaptive Scaffold. | Weeks 1–3 |
| **Phase 2: Core Market Module** | Live price feeds, CoinGecko/Kraken/Binance public API integration, Jetpack Compose Canvas chart engine. | Weeks 4–6 |
| **Phase 3: Adaptive Layouts** | Implementation of Compact, Medium, Expanded WindowSizeClass layouts, FoldingFeature postures, and multi-pane scaffolds. | Weeks 7–9 |
| **Phase 4: Portfolio & Security** | Manual & API portfolio tracking, DCA calculator, BiometricPrompt, Keystore encryption, Glance widgets. | Weeks 10–12 |
| **Phase 5: Polish & QA** | Tablet testing, DeX testing, landscape optimization, battery/network performance profiling, and release build. | Weeks 13–14 |

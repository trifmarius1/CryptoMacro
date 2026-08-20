package com.cryptomacro.app.domain.model

/**
 * BEGINNER: CoreAssets.all is the built-in catalog (BTC, ETH, SPX, TOTAL, Fear & Greed, …).
 * featuredChips = the default Markets row: btc-usd, eth-usd, sol-usd, spx.
 * TimeframeMaps translates our Timeframe enum into Binance intervals and Yahoo ranges.
 */

object CoreAssets {
    val all: List<AssetDefinition> = listOf(
        // Layer 1 crypto
        AssetDefinition("btc-usd", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, binanceSymbol = "BTCUSDT", coingeckoId = "bitcoin", tag = CoinTag.LAYER1, description = "Primary crypto reserve asset"),
        AssetDefinition("eth-usd", "ETH/USD", "Ethereum", AssetCategory.CRYPTO, binanceSymbol = "ETHUSDT", coingeckoId = "ethereum", tag = CoinTag.LAYER1),
        AssetDefinition("sol-usd", "SOL/USD", "Solana", AssetCategory.CRYPTO, binanceSymbol = "SOLUSDT", coingeckoId = "solana", tag = CoinTag.LAYER1),
        AssetDefinition("ada-usd", "ADA/USD", "Cardano", AssetCategory.CRYPTO, binanceSymbol = "ADAUSDT", coingeckoId = "cardano", tag = CoinTag.LAYER1),
        AssetDefinition("avax-usd", "AVAX/USD", "Avalanche", AssetCategory.CRYPTO, binanceSymbol = "AVAXUSDT", coingeckoId = "avalanche-2", tag = CoinTag.LAYER1),
        AssetDefinition("dot-usd", "DOT/USD", "Polkadot", AssetCategory.CRYPTO, binanceSymbol = "DOTUSDT", coingeckoId = "polkadot", tag = CoinTag.LAYER1),
        AssetDefinition("near-usd", "NEAR/USD", "NEAR", AssetCategory.CRYPTO, binanceSymbol = "NEARUSDT", coingeckoId = "near", tag = CoinTag.LAYER1),
        // Layer 2
        AssetDefinition("arb-usd", "ARB/USD", "Arbitrum", AssetCategory.CRYPTO, binanceSymbol = "ARBUSDT", tag = CoinTag.LAYER2),
        AssetDefinition("op-usd", "OP/USD", "Optimism", AssetCategory.CRYPTO, binanceSymbol = "OPUSDT", tag = CoinTag.LAYER2),
        AssetDefinition("matic-usd", "MATIC/USD", "Polygon", AssetCategory.CRYPTO, binanceSymbol = "MATICUSDT", tag = CoinTag.LAYER2),
        // DeFi
        AssetDefinition("link-usd", "LINK/USD", "Chainlink", AssetCategory.CRYPTO, binanceSymbol = "LINKUSDT", tag = CoinTag.DEFI),
        AssetDefinition("uni-usd", "UNI/USD", "Uniswap", AssetCategory.CRYPTO, binanceSymbol = "UNIUSDT", tag = CoinTag.DEFI),
        AssetDefinition("aave-usd", "AAVE/USD", "Aave", AssetCategory.CRYPTO, binanceSymbol = "AAVEUSDT", tag = CoinTag.DEFI),
        // Memes
        AssetDefinition("doge-usd", "DOGE/USD", "Dogecoin", AssetCategory.CRYPTO, binanceSymbol = "DOGEUSDT", tag = CoinTag.MEME),
        AssetDefinition("shib-usd", "SHIB/USD", "Shiba Inu", AssetCategory.CRYPTO, binanceSymbol = "SHIBUSDT", tag = CoinTag.MEME),
        AssetDefinition("pepe-usd", "PEPE/USD", "Pepe", AssetCategory.CRYPTO, binanceSymbol = "PEPEUSDT", tag = CoinTag.MEME),
        // Stablecoins
        AssetDefinition("usdc-usd", "USDC/USD", "USD Coin", AssetCategory.CRYPTO, binanceSymbol = "USDCUSDT", coingeckoId = "usd-coin", tag = CoinTag.STABLE),
        AssetDefinition("usdt-usd", "USDT/USD", "Tether", AssetCategory.CRYPTO, coingeckoId = "tether", tag = CoinTag.STABLE, preferLine = true, description = "Dollar-pegged stablecoin"),
        // Benchmarks
        AssetDefinition("spx", "SPX", "S&P 500 Index", AssetCategory.EQUITY, yahooSymbol = "^GSPC", tag = CoinTag.BENCHMARK, description = "Equity risk-on benchmark"),
        AssetDefinition("gold", "XAU", "Gold", AssetCategory.EQUITY, yahooSymbol = "GC=F", tag = CoinTag.BENCHMARK, description = "Macro hard-asset benchmark"),
        // Aggregates
        AssetDefinition("btc-d", "BTC.D", "BTC Dominance", AssetCategory.AGGREGATE, isPercent = true, preferLine = true, unit = AssetUnit.PERCENT, description = "Bitcoin share of total crypto market cap"),
        AssetDefinition("total", "TOTAL", "Total Crypto Market Cap", AssetCategory.AGGREGATE, preferLine = true, description = "Aggregate market capitalization"),
        AssetDefinition("total2", "TOTAL2", "TOTAL2 (ex-BTC)", AssetCategory.AGGREGATE, preferLine = true, description = "Total market cap excluding Bitcoin"),
        AssetDefinition("total3", "TOTAL3", "TOTAL3 (ex-BTC & ETH)", AssetCategory.AGGREGATE, preferLine = true, description = "Total market cap excluding Bitcoin and Ethereum"),
        // BTC pairs
        AssetDefinition("eth-btc", "ETH/BTC", "Ethereum / Bitcoin", AssetCategory.PAIR, binanceSymbol = "ETHBTC", unit = AssetUnit.BTC),
        AssetDefinition("sol-btc", "SOL/BTC", "Solana / Bitcoin", AssetCategory.PAIR, binanceSymbol = "SOLBTC", unit = AssetUnit.BTC),
        AssetDefinition("ada-btc", "ADA/BTC", "Cardano / Bitcoin", AssetCategory.PAIR, binanceSymbol = "ADABTC", unit = AssetUnit.BTC),
        // Sentiment
        AssetDefinition("fear-greed", "F&G", "Crypto Fear & Greed Index", AssetCategory.MACRO, preferLine = true, unit = AssetUnit.INDEX, description = "0 extreme fear → 100 extreme greed"),
    )

    fun byId(id: String) = all.find { it.id == id }

    val featuredChips = listOf("btc-usd", "eth-usd", "sol-usd", "spx")

    val sectionMeta = mapOf(
        AssetCategory.CRYPTO to ("Single Crypto Assets" to "USD pairs — live & historical"),
        AssetCategory.EQUITY to ("Traditional Benchmarks" to "S&P 500 and gold correlation"),
        AssetCategory.AGGREGATE to ("Market Cap Aggregates" to "TOTAL · TOTAL2 · TOTAL3 · BTC.D"),
        AssetCategory.PAIR to ("Bitcoin Trading Pairs" to "Relative strength vs BTC"),
        AssetCategory.MACRO to ("Industry Sentiment" to "Crypto Fear & Greed Index"),
    )
}

data class IntervalSpec(val interval: String, val limit: Int)
data class YahooSpec(val range: String, val interval: String)

object TimeframeMaps {
    fun binance(tf: Timeframe): IntervalSpec = when (tf) {
        Timeframe.H1 -> IntervalSpec("1m", 60)
        Timeframe.H4 -> IntervalSpec("1m", 240)
        Timeframe.D1 -> IntervalSpec("15m", 96)
        Timeframe.D7 -> IntervalSpec("1h", 168)
        Timeframe.D30 -> IntervalSpec("4h", 180)
        Timeframe.D90 -> IntervalSpec("1d", 90)
        Timeframe.Y1 -> IntervalSpec("1d", 365)
        Timeframe.ALL -> IntervalSpec("1w", 500)
    }

    fun yahoo(tf: Timeframe): YahooSpec = when (tf) {
        Timeframe.H1, Timeframe.H4 -> YahooSpec("1d", "1m")
        Timeframe.D1 -> YahooSpec("5d", "15m")
        Timeframe.D7 -> YahooSpec("1mo", "1h")
        Timeframe.D30 -> YahooSpec("3mo", "1d")
        Timeframe.D90 -> YahooSpec("6mo", "1d")
        Timeframe.Y1 -> YahooSpec("1y", "1d")
        Timeframe.ALL -> YahooSpec("max", "1wk")
    }

    fun lookbackSec(tf: Timeframe): Long? = when (tf) {
        Timeframe.H1 -> 3600L
        Timeframe.H4 -> 4 * 3600L
        Timeframe.D1 -> 86_400L
        Timeframe.D7 -> 7 * 86_400L
        Timeframe.D30 -> 30L * 86_400L
        Timeframe.D90 -> 90L * 86_400L
        Timeframe.Y1 -> 365L * 86_400L
        Timeframe.ALL -> null
    }

    fun fearGreedLimit(tf: Timeframe): Int = when (tf) {
        Timeframe.H1, Timeframe.H4 -> 2
        Timeframe.D1 -> 2
        Timeframe.D7 -> 7
        Timeframe.D30 -> 30
        Timeframe.D90 -> 90
        Timeframe.Y1 -> 365
        Timeframe.ALL -> 0
    }

    fun stepSec(tf: Timeframe): Long = when (tf) {
        Timeframe.H1, Timeframe.H4 -> 60L
        Timeframe.D1 -> 15 * 60L
        Timeframe.D7 -> 3600L
        Timeframe.D30 -> 4 * 3600L
        Timeframe.D90, Timeframe.Y1 -> 86_400L
        Timeframe.ALL -> 7 * 86_400L
    }
}

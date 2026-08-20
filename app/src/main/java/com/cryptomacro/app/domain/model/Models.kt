package com.cryptomacro.app.domain.model

/**
 * BEGINNER: Plain Kotlin data classes (no Android). AssetDefinition describes *what* a coin/stock is.
 * AssetQuote is a *price snapshot*. ListedMarketItem is rank + asset + quote for the market list.
 */

enum class AssetCategory {
    CRYPTO, EQUITY, AGGREGATE, PAIR, MACRO
}

enum class AssetUnit { USD, BTC, PERCENT, INDEX }

enum class CoinTag { LAYER1, LAYER2, DEFI, MEME, STABLE, BENCHMARK, OTHER }

enum class Timeframe(val id: String, val label: String) {
    H1("1H", "1H"),
    H4("4H", "4H"),
    D1("1D", "24H"),
    D7("7D", "7D"),
    D30("30D", "30D"),
    D90("90D", "90D"),
    Y1("1Y", "1Y"),
    ALL("ALL", "ALL");

    companion object {
        val chartSelector = entries
        fun fromId(id: String) = entries.find { it.id == id } ?: D1
    }
}

enum class ChartKind { CANDLESTICK, LINE, AREA }

enum class ConnectionState { LIVE, REST, CACHED, OFFLINE }

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double? = null,
)

data class ListedMarketItem(
    val rank: Int,
    val asset: AssetDefinition,
    val quote: AssetQuote,
)

data class AssetQuote(
    val symbol: String,
    val price: Double,
    val change24h: Double,
    val changePercent24h: Double,
    val high52w: Double? = null,
    val low52w: Double? = null,
    val marketCap: Double? = null,
    val volume24h: Double? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class AssetDefinition(
    val id: String,
    val symbol: String,
    val name: String,
    val category: AssetCategory,
    val binanceSymbol: String? = null,
    val coingeckoId: String? = null,
    val yahooSymbol: String? = null,
    val isPercent: Boolean = false,
    val preferLine: Boolean = false,
    val unit: AssetUnit = AssetUnit.USD,
    val description: String? = null,
    val tag: CoinTag = CoinTag.OTHER,
    val custom: Boolean = false,
)

data class FeedStatus(
    val state: ConnectionState,
    val source: String,
    val lastUpdated: Long? = null,
    val message: String? = null,
)

data class CandleResult(
    val candles: List<Candle>,
    val status: FeedStatus,
)

data class MarketOverview(
    val totalMarketCap: Double = 0.0,
    val totalVolume24h: Double = 0.0,
    val btcDominance: Double = 0.0,
    val ethDominance: Double = 0.0,
    val spxPrice: Double = 0.0,
    val spxChangePercent: Double = 0.0,
    val goldPrice: Double = 0.0,
    val goldChangePercent: Double = 0.0,
    val fearGreed: Int = 50,
    val fearGreedLabel: String = "Neutral",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class GlobalSnapshot(
    val totalMarketCap: Double,
    val totalVolume24h: Double,
    val btcDominance: Double,
    val ethDominance: Double,
    val btcMarketCap: Double,
    val ethMarketCap: Double,
    val marketCapChange24h: Double,
    val source: String,
)

enum class TransactionType { BUY, SELL, TRANSFER_IN, TRANSFER_OUT, STAKING_REWARD }

data class PortfolioTransaction(
    val id: String,
    val assetId: String,
    val type: TransactionType,
    val amount: Double,
    val priceUsd: Double,
    val feeUsd: Double = 0.0,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

data class PortfolioHolding(
    val id: String,
    val assetId: String,
    val amount: Double,
    val avgBuyPriceUsd: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

enum class FiatCurrency { USD, EUR }

data class HoldingValuation(
    val holding: PortfolioHolding,
    val asset: AssetDefinition,
    val priceUsd: Double,
    val changePercent24h: Double,
    val valueUsd: Double,
    val pnl24hUsd: Double,
    val costBasisUsd: Double?,
    val unrealizedPnlUsd: Double?,
    val allocationPct: Double,
)

data class PortfolioSummary(
    val totalValueUsd: Double,
    val totalPnl24hUsd: Double,
    val totalPnl24hPct: Double,
    val totalCostBasisUsd: Double,
    val totalUnrealizedPnlUsd: Double,
    val rows: List<HoldingValuation>,
    val currency: FiatCurrency,
    val usdToEur: Double,
    val fxSource: String,
    val wsLive: Boolean,
)

data class DcaComparison(
    val investedUsd: Double,
    val actualValueUsd: Double,
    val actualReturnPct: Double,
    val weeklyBenchmarkValueUsd: Double,
    val weeklyBenchmarkReturnPct: Double,
    val monthlyBenchmarkValueUsd: Double,
    val monthlyBenchmarkReturnPct: Double,
)

data class WatchAddress(
    val id: String,
    val chain: String,
    val address: String,
    val label: String,
    val lastBalance: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@kotlinx.serialization.Serializable
data class ExchangeKey(
    val exchange: String,
    val apiKey: String,
    val apiSecret: String,
    val passphrase: String? = null,
    val lastSyncAt: Long? = null,
    val lastStatus: String? = null,
)

data class ShemitahEvent(
    val year: Int,
    val title: String,
    val description: String,
    val severity: String,
)

data class ShemitahBand(
    val endYear: Int,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val label: String,
    val events: List<ShemitahEvent>,
)

data class ShemitahStats(
    val sabbaticalAvgReturn: Double,
    val expansionAvgReturn: Double,
    val sabbaticalWinRate: Double,
    val expansionWinRate: Double,
    val sabbaticalAvgDrawdown: Double,
    val expansionAvgDrawdown: Double,
    val nextCycleWindow: String,
    val currentPhase: String,
    val yearsToNext: Double,
)

data class HalvingInfo(
    val currentHeight: Long,
    val nextHalvingHeight: Long,
    val blocksRemaining: Long,
    val etaEpochMs: Long,
    val currentRewardBtc: Double,
    val nextRewardBtc: Double,
)

data class FeeEstimates(
    val bitcoinFastest: Int,
    val bitcoinHalfHour: Int,
    val bitcoinHour: Int,
    val bitcoinEconomy: Int,
    val ethereumGwei: Double?,
    val source: String,
)

data class EducationModule(
    val id: String,
    val title: String,
    val subtitle: String,
    val body: List<String>,
    val takeaway: String,
)

data class BinanceUsdtCoin(
    val baseAsset: String,
    val binanceSymbol: String,
    val name: String,
)

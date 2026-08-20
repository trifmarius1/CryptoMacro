package com.cryptomacro.app.data.local

/**
 * BEGINNER: An @Entity is one SQLite table. Each property is a column.
 * @PrimaryKey is the unique id (like a row number you choose).
 *
 * candle_cache  — saved OHLC bars so charts work offline for a while
 * quote_cache   — last price JSON per asset
 * custom_assets — coins/stocks the user pinned or added to the portfolio (survive restart)
 * holdings      — how many units you own and average buy price
 * transactions  — the diary of buys/sells
 * watch_addresses — leftover table from a removed feature; kept so we do not wipe holdings
 */
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "candle_cache", primaryKeys = ["assetId", "timeframe"])
data class CandleCacheEntity(
    val assetId: String,
    val timeframe: String,
    val json: String,
    val savedAt: Long,
    val source: String,
)

@Entity(tableName = "quote_cache")
data class QuoteCacheEntity(
    @PrimaryKey val assetId: String,
    val json: String,
    val savedAt: Long,
)

@Entity(tableName = "custom_assets")
data class CustomAssetEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val binanceSymbol: String,
    val addedAt: Long,
)

@Entity(tableName = "holdings")
data class HoldingEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val amount: Double,
    val avgBuyPriceUsd: Double?,
    val addedAt: Long,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val type: String,
    val amount: Double,
    val priceUsd: Double,
    val feeUsd: Double,
    val note: String?,
    val timestamp: Long,
)

@Entity(tableName = "watch_addresses")
data class WatchAddressEntity(
    @PrimaryKey val id: String,
    val chain: String,
    val address: String,
    val label: String,
    val lastBalance: Double?,
    val addedAt: Long,
)

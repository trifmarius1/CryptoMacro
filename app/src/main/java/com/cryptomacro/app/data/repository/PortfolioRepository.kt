package com.cryptomacro.app.data.repository

/**
 * BEGINNER: All portfolio math lives here, not in the Composables.
 *
 * holdings / transactions are Flows from Room — the UI updates when you save a row.
 * summary() combines holdings + live quotes: value = amount * price, then sums a total.
 * addTransaction writes the ledger AND updates the holding (buy adds, sell subtracts).
 * dcaComparison pretends you invested the same cash on a weekly/monthly schedule.
 */
import com.cryptomacro.app.data.local.AppDatabase
import com.cryptomacro.app.data.local.HoldingEntity
import com.cryptomacro.app.data.local.TransactionEntity
import com.cryptomacro.app.data.local.WatchAddressEntity
import com.cryptomacro.app.domain.model.DcaComparison
import com.cryptomacro.app.domain.model.FiatCurrency
import com.cryptomacro.app.domain.model.HoldingValuation
import com.cryptomacro.app.domain.model.PortfolioHolding
import com.cryptomacro.app.domain.model.PortfolioSummary
import com.cryptomacro.app.domain.model.PortfolioTransaction
import com.cryptomacro.app.domain.model.Timeframe
import com.cryptomacro.app.domain.model.TransactionType
import com.cryptomacro.app.domain.model.WatchAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class PortfolioRepository @Inject constructor(
    db: AppDatabase,
    private val market: MarketRepository,
    private val assets: AssetRegistry,
) {
    private val holdingsDao = db.holdings()
    private val txDao = db.transactions()
    private val watchDao = db.watchAddresses()

    val holdings: Flow<List<PortfolioHolding>> = holdingsDao.observe().map { rows ->
        rows.map { PortfolioHolding(it.id, it.assetId, it.amount, it.avgBuyPriceUsd, it.addedAt) }
    }

    val transactions: Flow<List<PortfolioTransaction>> = txDao.observe().map { rows ->
        rows.map {
            PortfolioTransaction(
                id = it.id,
                assetId = it.assetId,
                type = runCatching { TransactionType.valueOf(it.type) }.getOrDefault(TransactionType.BUY),
                amount = it.amount,
                priceUsd = it.priceUsd,
                feeUsd = it.feeUsd,
                note = it.note,
                timestamp = it.timestamp,
            )
        }
    }

    val watchAddresses: Flow<List<WatchAddress>> = watchDao.observe().map { rows ->
        rows.map { WatchAddress(it.id, it.chain, it.address, it.label, it.lastBalance, it.addedAt) }
    }

    fun summary(currency: FiatCurrency, usdToEur: Double, fxSource: String): Flow<PortfolioSummary> =
        combine(holdings, market.quotes, market.ticks, market.wsLive) { holds, quotes, ticks, live ->
            val registry = assets.snapshot()
            val raw = holds.mapNotNull { h ->
                val asset = market.resolve(h.assetId)
                    ?: registry.find { it.id == h.assetId }
                    ?: com.cryptomacro.app.domain.model.AssetDefinition(
                        id = h.assetId,
                        symbol = h.assetId.substringAfterLast("-").uppercase().ifBlank { h.assetId },
                        name = h.assetId,
                        category = com.cryptomacro.app.domain.model.AssetCategory.CRYPTO,
                    )
                val price = ticks[h.assetId] ?: quotes[h.assetId]?.price ?: 0.0
                val change = quotes[h.assetId]?.changePercent24h ?: 0.0
                val value = h.amount * price
                val prev = if (change != 0.0) value / (1 + change / 100) else value
                val cost = h.avgBuyPriceUsd?.let { it * h.amount }
                HoldingValuation(
                    holding = h,
                    asset = asset,
                    priceUsd = price,
                    changePercent24h = change,
                    valueUsd = value,
                    pnl24hUsd = value - prev,
                    costBasisUsd = cost,
                    unrealizedPnlUsd = cost?.let { value - it },
                    allocationPct = 0.0,
                )
            }
            val total = raw.sumOf { it.valueUsd }
            val pnl = raw.sumOf { it.pnl24hUsd }
            val prevTotal = total - pnl
            val costTotal = raw.mapNotNull { it.costBasisUsd }.sum()
            val uPnl = raw.mapNotNull { it.unrealizedPnlUsd }.sum()
            PortfolioSummary(
                totalValueUsd = total,
                totalPnl24hUsd = pnl,
                totalPnl24hPct = if (prevTotal > 0) pnl / prevTotal * 100 else 0.0,
                totalCostBasisUsd = costTotal,
                totalUnrealizedPnlUsd = uPnl,
                rows = raw.map { it.copy(allocationPct = if (total > 0) it.valueUsd / total * 100 else 0.0) },
                currency = currency,
                usdToEur = usdToEur,
                fxSource = fxSource,
                wsLive = live,
            )
        }

    suspend fun addOrMergeHolding(assetId: String, amount: Double, avgBuy: Double?) {
        if (amount <= 0) return
        val existing = holdingsDao.all().find { it.assetId == assetId }
        if (existing != null) {
            val newAmt = existing.amount + amount
            val avg = when {
                avgBuy != null && avgBuy > 0 && existing.avgBuyPriceUsd != null && existing.avgBuyPriceUsd > 0 ->
                    (existing.avgBuyPriceUsd * existing.amount + avgBuy * amount) / newAmt
                avgBuy != null && avgBuy > 0 -> avgBuy
                else -> existing.avgBuyPriceUsd
            }
            holdingsDao.upsert(existing.copy(amount = newAmt, avgBuyPriceUsd = avg))
        } else {
            holdingsDao.upsert(
                HoldingEntity(newId("h"), assetId, amount, avgBuy?.takeIf { it > 0 }, System.currentTimeMillis()),
            )
        }
    }

    suspend fun updateHolding(id: String, amount: Double, avgBuy: Double?) {
        val row = holdingsDao.all().find { it.id == id } ?: return
        if (amount <= 0) holdingsDao.delete(id)
        else holdingsDao.upsert(row.copy(amount = amount, avgBuyPriceUsd = avgBuy?.takeIf { it > 0 }))
    }

    suspend fun removeHolding(id: String) = holdingsDao.delete(id)
    suspend fun clearHoldings() = holdingsDao.clear()

    suspend fun addTransaction(tx: PortfolioTransaction) {
        txDao.upsert(
            TransactionEntity(tx.id, tx.assetId, tx.type.name, tx.amount, tx.priceUsd, tx.feeUsd, tx.note, tx.timestamp),
        )
        val signed = when (tx.type) {
            TransactionType.BUY, TransactionType.TRANSFER_IN, TransactionType.STAKING_REWARD -> tx.amount
            TransactionType.SELL, TransactionType.TRANSFER_OUT -> -tx.amount
        }
        val avg = if (tx.type == TransactionType.BUY) tx.priceUsd else null
        if (signed > 0) addOrMergeHolding(tx.assetId, signed, avg)
        else {
            val existing = holdingsDao.all().find { it.assetId == tx.assetId } ?: return
            updateHolding(existing.id, existing.amount + signed, existing.avgBuyPriceUsd)
        }
    }

    suspend fun deleteTransaction(id: String) = txDao.delete(id)

    suspend fun addWatch(chain: String, address: String, label: String) {
        watchDao.upsert(WatchAddressEntity(newId("w"), chain, address.trim(), label, null, System.currentTimeMillis()))
    }

    suspend fun removeWatch(id: String) = watchDao.delete(id)

    suspend fun updateWatchBalance(id: String, chain: String, address: String, label: String) {
        val bal = runCatching {
            if (chain.equals("BTC", true)) market.btcBalance(address) else market.ethBalance(address)
        }.getOrNull()
        watchDao.upsert(WatchAddressEntity(id, chain, address, label, bal, System.currentTimeMillis()))
    }

    suspend fun dcaComparison(assetId: String): DcaComparison {
        val txs = txDao.all()
            .filter { it.assetId == assetId && it.type == TransactionType.BUY.name }
            .sortedBy { it.timestamp }
        val invested = txs.sumOf { it.amount * it.priceUsd }
        val qty = txs.sumOf { it.amount }
        val price = market.livePrice(assetId) ?: 0.0
        val actualValue = qty * price
        val actualRet = if (invested > 0) (actualValue - invested) / invested * 100 else 0.0

        suspend fun bench(intervalMs: Long): Pair<Double, Double> {
            if (txs.isEmpty() || invested <= 0) return 0.0 to 0.0
            val start = txs.first().timestamp
            val now = System.currentTimeMillis()
            val periods = max(1, ((now - start) / intervalMs).toInt() + 1)
            val per = invested / periods
            val candles = market.candles(assetId, Timeframe.ALL).candles.ifEmpty {
                market.candles(assetId, Timeframe.Y1).candles
            }
            if (candles.isEmpty()) return 0.0 to 0.0
            var units = 0.0
            var t = start
            while (t <= now) {
                val px = candles.lastOrNull { it.time * 1000 <= t }?.close ?: candles.first().close
                if (px > 0) units += per / px
                t += intervalMs
            }
            val value = units * price
            val ret = if (invested > 0) (value - invested) / invested * 100 else 0.0
            return value to ret
        }

        val weekly = bench(7L * 24 * 3600 * 1000)
        val monthly = bench(30L * 24 * 3600 * 1000)
        return DcaComparison(invested, actualValue, actualRet, weekly.first, weekly.second, monthly.first, monthly.second)
    }

    fun exportJson(holdings: List<PortfolioHolding>, txs: List<PortfolioTransaction>): String {
        val h = holdings.joinToString(",") {
            """{"id":"${it.id}","assetId":"${it.assetId}","amount":${it.amount},"avgBuyPriceUsd":${it.avgBuyPriceUsd},"addedAt":${it.addedAt}}"""
        }
        val t = txs.joinToString(",") {
            """{"id":"${it.id}","assetId":"${it.assetId}","type":"${it.type}","amount":${it.amount},"priceUsd":${it.priceUsd},"feeUsd":${it.feeUsd},"timestamp":${it.timestamp}}"""
        }
        return """{"app":"CryptoMacro","exportedAt":"${java.time.Instant.now()}","holdings":[$h],"transactions":[$t]}"""
    }

    companion object {
        fun newId(prefix: String) = "${prefix}_${UUID.randomUUID().toString().take(8)}"
    }
}

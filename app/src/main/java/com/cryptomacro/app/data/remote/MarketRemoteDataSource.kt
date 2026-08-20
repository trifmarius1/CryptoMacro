package com.cryptomacro.app.data.remote

/**
 * BEGINNER: This class talks to the internet for *market* data. It does not know about Compose.
 *
 * Each suspend fun is one job: candles, quote, top 200 coins, top 50 stocks, Fear & Greed, etc.
 * Failover is try A, if it throws try B. fetchTopCryptos() hits CoinGecko twice (page 1 and 2)
 * because the free API is happiest at 100 rows per page.
 *
 * Yahoo v7 quote returns 401; we use /v7/finance/spark with a Chrome User-Agent, 20 symbols max.
 */
import android.content.Context
import com.cryptomacro.app.domain.model.AssetDefinition
import com.cryptomacro.app.domain.model.AssetQuote
import com.cryptomacro.app.domain.model.BinanceUsdtCoin
import com.cryptomacro.app.domain.model.Candle
import com.cryptomacro.app.domain.model.CandleResult
import com.cryptomacro.app.domain.model.ConnectionState
import com.cryptomacro.app.domain.model.FeeEstimates
import com.cryptomacro.app.domain.model.FeedStatus
import com.cryptomacro.app.domain.model.GlobalSnapshot
import com.cryptomacro.app.domain.model.HalvingInfo
import com.cryptomacro.app.domain.model.ListedMarketItem
import com.cryptomacro.app.domain.model.MarketOverview
import com.cryptomacro.app.domain.model.Timeframe
import com.cryptomacro.app.domain.model.TopStocks
import com.cryptomacro.app.domain.model.TimeframeMaps
import com.cryptomacro.app.domain.util.Formatters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

@Singleton
class MarketRemoteDataSource @Inject constructor(
    private val http: HttpJson,
    @ApplicationContext private val context: Context,
) {
    private val candleMem = mutableMapOf<String, Pair<Long, CandleResult>>()
    private val candleMutex = Mutex()
    private val aggCache = mutableMapOf<Timeframe, Pair<Long, AggregateBase>>()
    private val aggMutex = Mutex()

    suspend fun fetchCandles(asset: AssetDefinition, timeframe: Timeframe): CandleResult {
        val key = "${asset.id}::${timeframe.id}"
        candleMutex.withLock {
            candleMem[key]?.let { (at, result) ->
                if (System.currentTimeMillis() - at < 90_000 &&
                    result.status.state in setOf(ConnectionState.LIVE, ConnectionState.REST) &&
                    result.candles.isNotEmpty()
                ) return result
            }
        }
        val live = runCatching { fetchLiveCandles(asset, timeframe) }.getOrNull()
        if (live != null && live.candles.isNotEmpty()) {
            val result = CandleResult(live.candles, FeedStatus(ConnectionState.REST, live.source, System.currentTimeMillis()))
            candleMutex.withLock { candleMem[key] = System.currentTimeMillis() to result }
            return result
        }
        val synthetic = generateSynthetic(asset, timeframe)
        return CandleResult(
            synthetic,
            FeedStatus(ConnectionState.OFFLINE, "Synthetic seed", System.currentTimeMillis(), "Live feed offline. Showing illustrative series."),
        )
    }

    private data class LiveSeries(val candles: List<Candle>, val source: String)

    private suspend fun fetchLiveCandles(asset: AssetDefinition, timeframe: Timeframe): LiveSeries {
        if (asset.id == "fear-greed") return LiveSeries(fetchFearGreed(timeframe), "alternative.me F&G")
        if (asset.category == com.cryptomacro.app.domain.model.AssetCategory.EQUITY || asset.yahooSymbol != null) {
            return fetchEquity(asset, timeframe)
        }
        if (asset.category == com.cryptomacro.app.domain.model.AssetCategory.AGGREGATE) {
            return fetchAggregate(asset, timeframe)
        }
        return try {
            LiveSeries(fetchBinanceCandles(asset, timeframe), "Binance REST")
        } catch (_: Throwable) {
            try {
                LiveSeries(fetchCryptoCompare(asset, timeframe), "CryptoCompare")
            } catch (_: Throwable) {
                if (asset.coingeckoId != null) LiveSeries(fetchCoinGeckoOhlc(asset, timeframe), "CoinGecko")
                else throw IllegalStateException("No series")
            }
        }
    }

    private suspend fun fetchBinanceKlines(symbol: String, interval: String, limit: Int): List<Candle> {
        val safeSymbol = symbol.uppercase().takeIf { BINANCE_SYMBOL.matches(it) } ?: error("Bad symbol")
        val safeInterval = interval.takeIf { BINANCE_INTERVAL.matches(it) } ?: error("Bad interval")
        val url = "https://api.binance.com/api/v3/klines?symbol=$safeSymbol&interval=$safeInterval&limit=${min(limit, 1000)}"
        val root = http.json.parseToJsonElement(http.get(url)).jsonArray
        return parseBinance(root)
    }

    private fun parseBinance(raw: JsonArray): List<Candle> =
        raw.mapNotNull { row ->
            val a = row.jsonArray
            val time = a[0].jsonPrimitive.long / 1000
            val o = a[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
            val h = a[2].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
            val l = a[3].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
            val c = a[4].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
            val v = a[5].jsonPrimitive.content.toDoubleOrNull()
            Candle(time, o, h, l, c, v)
        }.sortedBy { it.time }.distinctBy { it.time }

    private suspend fun fetchBinanceCandles(asset: AssetDefinition, timeframe: Timeframe): List<Candle> {
        val symbol = asset.binanceSymbol ?: error("No binance symbol")
        val spec = TimeframeMaps.binance(timeframe)
        val candles = fetchBinanceKlines(symbol, spec.interval, spec.limit)
        if (candles.isEmpty()) error("Binance empty")
        return candles
    }

    private suspend fun fetchYahoo(symbol: String, timeframe: Timeframe): List<Candle> {
        val spec = TimeframeMaps.yahoo(timeframe)
        val enc = java.net.URLEncoder.encode(symbol, "UTF-8")
        val hosts = listOf("https://query1.finance.yahoo.com", "https://query2.finance.yahoo.com")
        var last: Throwable? = null
        for (host in hosts) {
            try {
                val url = "$host/v8/finance/chart/$enc?range=${spec.range}&interval=${spec.interval}&includePrePost=false"
                val root = http.json.parseToJsonElement(http.get(url)).jsonObject
                val result = root["chart"]?.jsonObject?.get("result")?.jsonArray?.firstOrNull()?.jsonObject
                    ?: continue
                val timestamps = result["timestamp"]?.jsonArray ?: continue
                val quote = result["indicators"]?.jsonObject?.get("quote")?.jsonArray?.firstOrNull()?.jsonObject
                    ?: continue
                val opens = quote["open"]?.jsonArray
                val highs = quote["high"]?.jsonArray
                val lows = quote["low"]?.jsonArray
                val closes = quote["close"]?.jsonArray
                val vols = quote["volume"]?.jsonArray
                val candles = timestamps.mapIndexedNotNull { i, tEl ->
                    val close = closes?.getOrNull(i)?.jsonPrimitive?.doubleOrNull ?: return@mapIndexedNotNull null
                    val open = opens?.getOrNull(i)?.jsonPrimitive?.doubleOrNull ?: close
                    val high = highs?.getOrNull(i)?.jsonPrimitive?.doubleOrNull ?: max(open, close)
                    val low = lows?.getOrNull(i)?.jsonPrimitive?.doubleOrNull ?: min(open, close)
                    val vol = vols?.getOrNull(i)?.jsonPrimitive?.doubleOrNull
                    Candle(tEl.jsonPrimitive.long, open, high, low, close, vol)
                }.sortedBy { it.time }.distinctBy { it.time }
                val lookback = TimeframeMaps.lookbackSec(timeframe)
                val trimmed = if (lookback != null && candles.isNotEmpty()) {
                    val cutoff = candles.last().time - lookback
                    candles.filter { it.time >= cutoff }
                } else candles
                if (trimmed.isNotEmpty()) return trimmed
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("Yahoo empty")
    }

    private suspend fun fetchSpxStatic(timeframe: Timeframe): List<Candle> {
        val raw = context.assets.open("spx.json").bufferedReader().use { it.readText() }
        val obj = http.json.parseToJsonElement(raw).jsonObject
        fun read(key: String) = obj[key]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            Candle(
                time = o["time"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                open = o["open"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                high = o["high"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                low = o["low"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                close = o["close"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                volume = o["volume"]?.jsonPrimitive?.doubleOrNull,
            )
        }.orEmpty()
        val series = when (timeframe) {
            Timeframe.H1, Timeframe.H4, Timeframe.D1, Timeframe.D7 -> read("hourly").ifEmpty { read("daily") }
            Timeframe.ALL -> read("weekly").ifEmpty { read("daily") }
            else -> read("daily")
        }
        val lookback = TimeframeMaps.lookbackSec(timeframe)
        val trimmed = if (lookback != null && series.isNotEmpty()) {
            val cutoff = series.last().time - lookback
            series.filter { it.time >= cutoff }
        } else series
        if (trimmed.isEmpty()) error("Static SPX empty")
        return trimmed
    }

    private suspend fun fetchEquity(asset: AssetDefinition, timeframe: Timeframe): LiveSeries {
        val symbols = listOfNotNull(asset.yahooSymbol, if (asset.id == "spx") "SPY" else null).distinct()
        for (sym in symbols) {
            runCatching { fetchYahoo(sym, timeframe) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                return LiveSeries(it, "Yahoo Finance")
            }
        }
        if (asset.id == "spx") {
            return LiveSeries(fetchSpxStatic(timeframe), "SPX snapshot")
        }
        error("Equity series failed")
    }

    private suspend fun fetchCryptoCompare(asset: AssetDefinition, timeframe: Timeframe): List<Candle> {
        val sym = asset.binanceSymbol ?: error("No pair")
        val (fsym, tsym) = when {
            sym.endsWith("USDT") -> sym.dropLast(4) to "USDT"
            sym.endsWith("BTC") -> sym.dropLast(3) to "BTC"
            else -> "BTC" to "USD"
        }
        val (path, limit, aggregate) = when (timeframe) {
            Timeframe.H1 -> Triple("histominute", 60, 1)
            Timeframe.H4 -> Triple("histominute", 240, 1)
            Timeframe.D1 -> Triple("histominute", 96, 15)
            Timeframe.D7 -> Triple("histohour", 168, 1)
            Timeframe.D30 -> Triple("histoday", 30, 1)
            Timeframe.D90 -> Triple("histoday", 90, 1)
            Timeframe.Y1 -> Triple("histoday", 365, 1)
            Timeframe.ALL -> Triple("histoday", 2000, 7)
        }
        val url = "https://min-api.cryptocompare.com/data/v2/$path?fsym=$fsym&tsym=$tsym&limit=$limit&aggregate=$aggregate"
        val rows = http.parse(url).jsonObject["Data"]?.jsonObject?.get("Data")?.jsonArray ?: error("CryptoCompare empty")
        return rows.mapNotNull { el ->
            val o = el.jsonObject
            Candle(
                time = o["time"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                open = o["open"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                high = o["high"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                low = o["low"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                close = o["close"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                volume = o["volumefrom"]?.jsonPrimitive?.doubleOrNull,
            )
        }.sortedBy { it.time }
    }

    private suspend fun fetchFearGreed(timeframe: Timeframe): List<Candle> {
        val limit = TimeframeMaps.fearGreedLimit(timeframe)
        val url = "https://api.alternative.me/fng/?limit=$limit&format=json"
        val rows = http.parse(url, 120_000).jsonObject["data"]?.jsonArray ?: error("F&G empty")
        return rows.mapNotNull { el ->
            val o = el.jsonObject
            val value = o["value"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val time = o["timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            Candle(time, value, value, value, value)
        }.sortedBy { it.time }.distinctBy { it.time }
    }

    suspend fun fetchGlobal(): GlobalSnapshot {
        val lore = runCatching { globalFromCoinLore() }.getOrNull()
        if (lore != null) return lore
        return globalFromCoinGecko()
    }

    private suspend fun globalFromCoinLore(): GlobalSnapshot {
        val globalArr = http.parse("https://api.coinlore.net/api/global/").jsonArray
        val tickers = http.parse("https://api.coinlore.net/api/ticker/?id=90,80").jsonArray
        val g = globalArr.first().jsonObject
        val total = g["total_mcap"]?.jsonPrimitive?.doubleOrNull ?: error("empty")
        val btcDom = g["btc_d"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 50.0
        val ethDom = g["eth_d"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 12.0
        fun cap(sym: String) = tickers.map { it.jsonObject }
            .find { it["symbol"]?.jsonPrimitive?.contentOrNull == sym }
            ?.get("market_cap_usd")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        return GlobalSnapshot(
            totalMarketCap = total,
            totalVolume24h = g["total_volume"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            btcDominance = btcDom,
            ethDominance = ethDom,
            btcMarketCap = cap("BTC") ?: total * (btcDom / 100),
            ethMarketCap = cap("ETH") ?: total * (ethDom / 100),
            marketCapChange24h = g["mcap_change"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0,
            source = "CoinLore",
        )
    }

    private suspend fun globalFromCoinGecko(): GlobalSnapshot {
        val d = http.parse("https://api.coingecko.com/api/v3/global").jsonObject["data"]?.jsonObject
            ?: error("CG empty")
        val total = d["total_market_cap"]?.jsonObject?.get("usd")?.jsonPrimitive?.double ?: error("no cap")
        val btcDom = d["market_cap_percentage"]?.jsonObject?.get("btc")?.jsonPrimitive?.doubleOrNull ?: 50.0
        val ethDom = d["market_cap_percentage"]?.jsonObject?.get("eth")?.jsonPrimitive?.doubleOrNull ?: 15.0
        return GlobalSnapshot(
            totalMarketCap = total,
            totalVolume24h = d["total_volume"]?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            btcDominance = btcDom,
            ethDominance = ethDom,
            btcMarketCap = total * (btcDom / 100),
            ethMarketCap = total * (ethDom / 100),
            marketCapChange24h = d["market_cap_change_percentage_24h_usd"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            source = "CoinGecko",
        )
    }

    private data class AggregateBase(
        val snap: GlobalSnapshot,
        val btcCaps: List<Pair<Long, Double>>,
        val ethCaps: List<Pair<Long, Double>>,
        val source: String,
    )

    private suspend fun aggregateBase(timeframe: Timeframe): AggregateBase = aggMutex.withLock {
        aggCache[timeframe]?.let { (at, data) ->
            if (System.currentTimeMillis() - at < 90_000) return data
        }
        val spec = TimeframeMaps.binance(timeframe)
        val snap = fetchGlobal()
        val btc = fetchBinanceKlines("BTCUSDT", spec.interval, spec.limit)
        val eth = fetchBinanceKlines("ETHUSDT", spec.interval, spec.limit)
        if (btc.isEmpty()) error("Binance BTC empty")
        val btcPrice = btc.last().close
        val ethPrice = eth.lastOrNull()?.close ?: 0.0
        val btcSupply = if (btcPrice > 0) snap.btcMarketCap / btcPrice else 0.0
        val ethSupply = if (ethPrice > 0) snap.ethMarketCap / ethPrice else 0.0
        val data = AggregateBase(
            snap,
            btc.map { it.time to it.close * btcSupply },
            eth.map { it.time to if (ethSupply > 0) it.close * ethSupply else 0.0 },
            "Binance + ${snap.source}",
        )
        aggCache[timeframe] = System.currentTimeMillis() to data
        data
    }

    private suspend fun fetchAggregate(asset: AssetDefinition, timeframe: Timeframe): LiveSeries {
        val base = aggregateBase(timeframe)
        val ethAligned = align(base.btcCaps, base.ethCaps, base.snap.ethMarketCap.coerceAtLeast(base.snap.btcMarketCap * 0.2))
        val btcNow = base.btcCaps.last().second
        val ethNow = ethAligned.lastOrNull() ?: base.snap.ethMarketCap
        val othersNow = max(0.0, base.snap.totalMarketCap - base.snap.btcMarketCap - base.snap.ethMarketCap)
        val candles = base.btcCaps.mapIndexedNotNull { i, (time, btcCapRaw) ->
            val isLast = i == base.btcCaps.lastIndex
            val btcCap = if (isLast) base.snap.btcMarketCap else btcCapRaw
            val ethCap = if (isLast) base.snap.ethMarketCap else ethAligned.getOrElse(i) { 0.0 }
            val btcRatio = if (btcNow > 0) btcCapRaw / btcNow else 1.0
            val ethRatio = if (ethNow > 0) ethCap / ethNow else btcRatio
            val othersRatio = max(0.05, ethRatio).pow(0.85) * max(0.05, btcRatio).pow(0.15)
            val others = if (isLast) othersNow else othersNow * othersRatio
            val total = btcCap + ethCap + others
            val value = when (asset.id) {
                "btc-d" -> min(85.0, max(25.0, if (total > 0) (btcCap / total) * 100 else base.snap.btcDominance))
                "total" -> total
                "total2" -> max(0.0, total - btcCap)
                "total3" -> max(0.0, total - btcCap - ethCap)
                else -> return@mapIndexedNotNull null
            }
            Candle(time, value, value, value, value)
        }
        if (candles.isEmpty()) error("Empty aggregate")
        return LiveSeries(candles, base.source)
    }

    private fun align(btc: List<Pair<Long, Double>>, eth: List<Pair<Long, Double>>, fallback: Double): List<Double> {
        if (eth.isEmpty()) return btc.map { fallback }
        val ethSorted = eth.sortedBy { it.first }
        var j = 0
        return btc.map { (t, _) ->
            while (j < ethSorted.lastIndex && ethSorted[j + 1].first <= t) j++
            var pick = ethSorted[j]
            if (j + 1 < ethSorted.size && abs(ethSorted[j + 1].first - t) < abs(pick.first - t)) {
                pick = ethSorted[j + 1]
            }
            pick.second
        }
    }

    suspend fun fetchQuote(asset: AssetDefinition): AssetQuote {
        try {
            if (asset.category == com.cryptomacro.app.domain.model.AssetCategory.EQUITY) {
                val day = fetchEquity(asset, Timeframe.D1).candles
                val year = runCatching { fetchEquity(asset, Timeframe.Y1).candles }.getOrDefault(emptyList())
                val last = day.last()
                val prev = day.getOrElse(day.lastIndex - 1) { last }
                val change = last.close - prev.close
                return AssetQuote(
                    symbol = asset.symbol,
                    price = last.close,
                    change24h = change,
                    changePercent24h = if (prev.close != 0.0) change / prev.close * 100 else 0.0,
                    high52w = year.maxOfOrNull { it.high },
                    low52w = year.minOfOrNull { it.low },
                )
            }
            if (asset.binanceSymbol != null) {
                val sym = asset.binanceSymbol.uppercase().takeIf { BINANCE_SYMBOL.matches(it) } ?: error("Bad symbol")
                val t = http.parse("https://api.binance.com/api/v3/ticker/24hr?symbol=$sym").jsonObject
                return AssetQuote(
                    symbol = asset.symbol,
                    price = t["lastPrice"]!!.jsonPrimitive.content.toDouble(),
                    change24h = t["priceChange"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    changePercent24h = t["priceChangePercent"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    volume24h = t["quoteVolume"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                )
            }
            if (asset.id == "fear-greed") {
                val c = fetchFearGreed(Timeframe.D7)
                val last = c.last()
                val prev = c.getOrElse(c.lastIndex - 1) { last }
                return AssetQuote(asset.symbol, last.close, last.close - prev.close, last.close - prev.close)
            }
            if (asset.id == "usdt-usd") {
                return AssetQuote(asset.symbol, 1.0, 0.0, 0.0)
            }
            if (asset.category == com.cryptomacro.app.domain.model.AssetCategory.AGGREGATE) {
                val d = fetchGlobal()
                val price = when (asset.id) {
                    "btc-d" -> d.btcDominance
                    "total" -> d.totalMarketCap
                    "total2" -> max(0.0, d.totalMarketCap - d.btcMarketCap)
                    else -> max(0.0, d.totalMarketCap - d.btcMarketCap - d.ethMarketCap)
                }
                return AssetQuote(asset.symbol, price, 0.0, d.marketCapChange24h, marketCap = d.totalMarketCap, volume24h = d.totalVolume24h)
            }
        } catch (_: Throwable) { /* fall through */ }
        val candles = fetchCandles(asset, Timeframe.D1).candles
        val last = candles.last()
        val prev = candles.getOrElse(candles.lastIndex - 1) { last }
        val change = last.close - prev.close
        return AssetQuote(asset.symbol, last.close, change, if (prev.close != 0.0) change / prev.close * 100 else 0.0)
    }

    suspend fun fetchOverview(): MarketOverview = coroutineScope {
        val global = async { runCatching { fetchGlobal() }.getOrNull() }
        val spx = async { runCatching { fetchQuote(com.cryptomacro.app.domain.model.CoreAssets.byId("spx")!!) }.getOrNull() }
        val gold = async { runCatching { fetchQuote(com.cryptomacro.app.domain.model.CoreAssets.byId("gold")!!) }.getOrNull() }
        val fng = async {
            runCatching {
                val row = http.parse("https://api.alternative.me/fng/?limit=1").jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
                val v = row?.get("value")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 50
                v to (row?.get("value_classification")?.jsonPrimitive?.contentOrNull ?: Formatters.fearGreedLabel(v))
            }.getOrNull()
        }
        val g = global.await()
        val s = spx.await()
        val x = gold.await()
        val f = fng.await()
        MarketOverview(
            totalMarketCap = g?.totalMarketCap ?: 0.0,
            totalVolume24h = g?.totalVolume24h ?: 0.0,
            btcDominance = g?.btcDominance ?: 0.0,
            ethDominance = g?.ethDominance ?: 0.0,
            spxPrice = s?.price ?: 0.0,
            spxChangePercent = s?.changePercent24h ?: 0.0,
            goldPrice = x?.price ?: 0.0,
            goldChangePercent = x?.changePercent24h ?: 0.0,
            fearGreed = f?.first ?: 50,
            fearGreedLabel = f?.second ?: "Neutral",
        )
    }

    suspend fun fetchUsdToEur(): Pair<Double, String> {
        runCatching {
            val rate = http.parse("https://api.frankfurter.app/latest?from=USD&to=EUR")
                .jsonObject["rates"]?.jsonObject?.get("EUR")?.jsonPrimitive?.double
            if (rate != null && rate > 0) return rate to "ECB (Frankfurter)"
        }
        runCatching {
            val t = http.parse("https://api.coingecko.com/api/v3/simple/price?ids=tether&vs_currencies=usd,eur").jsonObject["tether"]?.jsonObject
            val usd = t?.get("usd")?.jsonPrimitive?.doubleOrNull ?: 1.0
            val eur = t?.get("eur")?.jsonPrimitive?.doubleOrNull
            if (eur != null && eur > 0) return (eur / usd) to "CoinGecko"
        }
        return 0.92 to "Fallback estimate"
    }

    suspend fun fetchHalving(): HalvingInfo {
        val height = http.parse("https://mempool.space/api/blocks/tip/height").jsonPrimitive.long
        val halvings = listOf(210_000L, 420_000L, 630_000L, 840_000L, 1_050_000L)
        val next = halvings.first { it > height }
        val remaining = next - height
        val eta = System.currentTimeMillis() + remaining * 10 * 60 * 1000
        val epoch = halvings.indexOf(next)
        val currentReward = 50.0 / 2.0.pow(epoch.toDouble())
        return HalvingInfo(height, next, remaining, eta, currentReward, currentReward / 2)
    }

    suspend fun fetchFees(): FeeEstimates {
        val btc = http.parse("https://mempool.space/api/v1/fees/recommended").jsonObject
        return FeeEstimates(
            bitcoinFastest = btc["fastestFee"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            bitcoinHalfHour = btc["halfHourFee"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            bitcoinHour = btc["hourFee"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            bitcoinEconomy = btc["economyFee"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            ethereumGwei = null,
            source = "mempool.space",
        )
    }

    suspend fun fetchBtcAddressBalance(address: String): Double {
        val addr = address.trim().takeIf { BTC_ADDRESS.matches(it) } ?: error("Invalid BTC address")
        val o = http.parse("https://mempool.space/api/address/$addr").jsonObject
        val chain = o["chain_stats"]?.jsonObject
        val funded = chain?.get("funded_txo_sum")?.jsonPrimitive?.longOrNull ?: 0L
        val spent = chain?.get("spent_txo_sum")?.jsonPrimitive?.longOrNull ?: 0L
        return (funded - spent) / 100_000_000.0
    }

    suspend fun fetchEthAddressBalance(address: String): Double {
        val addr = address.trim().takeIf { ETH_ADDRESS.matches(it) } ?: error("Invalid ETH address")
        val payload = """{"jsonrpc":"2.0","method":"eth_getBalance","params":["$addr","latest"],"id":1}"""
        val body = http.postJson("https://eth.llamarpc.com", payload)
        val hex = http.json.parseToJsonElement(body).jsonObject["result"]?.jsonPrimitive?.contentOrNull ?: return 0.0
        val wei = hex.removePrefix("0x").toBigIntegerOrNull(16) ?: return 0.0
        return wei.toDouble() / 1e18
    }

    private suspend fun fetchCoinGeckoOhlc(asset: AssetDefinition, timeframe: Timeframe): List<Candle> {
        val id = asset.coingeckoId?.takeIf { COINGECKO_ID.matches(it) } ?: error("No CoinGecko id")
        val days = when (timeframe) {
            Timeframe.H1, Timeframe.H4, Timeframe.D1 -> "1"
            Timeframe.D7 -> "7"
            Timeframe.D30 -> "30"
            Timeframe.D90 -> "90"
            Timeframe.Y1 -> "365"
            Timeframe.ALL -> "max"
        }
        val raw = http.parse("https://api.coingecko.com/api/v3/coins/$id/ohlc?vs_currency=usd&days=$days").jsonArray
        val candles = raw.mapNotNull { el ->
            val a = el.jsonArray
            val t = (a[0].jsonPrimitive.longOrNull ?: return@mapNotNull null) / 1000
            val o = a[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val h = a[2].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val l = a[3].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val c = a[4].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            Candle(t, o, h, l, c)
        }
        if (candles.isEmpty()) error("CoinGecko OHLC empty")
        return candles
    }

    suspend fun fetchTopCryptos(): List<ListedMarketItem> {
        val catalog = runCatching { fetchBinanceCatalog() }.getOrDefault(emptyList())
        val byBase = catalog.associateBy { it.baseAsset.uppercase() }
        val rows = buildList {
            for (page in 1..2) {
                val arr = runCatching {
                    http.parse(
                        "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=$page&sparkline=false&price_change_percentage=24h",
                        ttlMs = 45_000,
                    ).jsonArray
                }.getOrNull() ?: break
                addAll(arr)
                if (arr.size < 80) break
            }
        }
        return rows.mapIndexedNotNull { i, el ->
            val o = el.jsonObject
            val cgId = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val symbol = o["symbol"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return@mapIndexedNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: symbol
            val price = o["current_price"]?.jsonPrimitive?.doubleOrNull ?: return@mapIndexedNotNull null
            val chg = o["price_change_percentage_24h"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val cap = o["market_cap"]?.jsonPrimitive?.doubleOrNull
            val vol = o["total_volume"]?.jsonPrimitive?.doubleOrNull
            val core = com.cryptomacro.app.domain.model.CoreAssets.all.find { it.coingeckoId == cgId }
            val binance = byBase[symbol]?.binanceSymbol ?: core?.binanceSymbol
            val asset = if (core != null) {
                core.copy(binanceSymbol = binance ?: core.binanceSymbol)
            } else {
                com.cryptomacro.app.domain.model.AssetDefinition(
                    id = "cg-$cgId",
                    symbol = symbol,
                    name = name,
                    category = com.cryptomacro.app.domain.model.AssetCategory.CRYPTO,
                    binanceSymbol = binance,
                    coingeckoId = cgId,
                    unit = com.cryptomacro.app.domain.model.AssetUnit.USD,
                    tag = com.cryptomacro.app.domain.model.CoinTag.OTHER,
                )
            }
            ListedMarketItem(
                rank = i + 1,
                asset = asset,
                quote = AssetQuote(
                    symbol = asset.symbol,
                    price = price,
                    change24h = price * chg / 100.0,
                    changePercent24h = chg,
                    marketCap = cap,
                    volume24h = vol,
                ),
            )
        }
    }

    suspend fun fetchTopStocks(): List<ListedMarketItem> {
        val quotes = mutableMapOf<String, AssetQuote>()
        coroutineScope {
            TopStocks.tickers.chunked(20).map { chunk ->
                async { fetchSparkQuotes(chunk.map { it.first }) }
            }.forEach { part ->
                quotes.putAll(part.await())
            }
        }
        return TopStocks.tickers.mapIndexedNotNull { i, (sym, name) ->
            val q = quotes[sym] ?: return@mapIndexedNotNull null
            ListedMarketItem(rank = i + 1, asset = TopStocks.toAsset(sym, name), quote = q)
        }
    }

    private suspend fun fetchSparkQuotes(symbols: List<String>): Map<String, AssetQuote> {
        val joined = symbols.joinToString(",")
        val urls = listOf(
            "https://query1.finance.yahoo.com/v7/finance/spark?symbols=$joined&range=1d&interval=5m",
            "https://query2.finance.yahoo.com/v7/finance/spark?symbols=$joined&range=1d&interval=5m",
        )
        val root = urls.firstNotNullOfOrNull { url ->
            runCatching { http.parse(url, ttlMs = 3_000) }.getOrNull()
        } ?: return emptyMap()
        val rows = root.jsonObject["spark"]?.jsonObject?.get("result")?.jsonArray ?: return emptyMap()
        val out = mutableMapOf<String, AssetQuote>()
        rows.forEach { el ->
            val o = el.jsonObject
            val sym = o["symbol"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val meta = o["response"]?.jsonArray?.firstOrNull()?.jsonObject?.get("meta")?.jsonObject ?: return@forEach
            val price = meta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
            val prev = meta["previousClose"]?.jsonPrimitive?.doubleOrNull
                ?: meta["chartPreviousClose"]?.jsonPrimitive?.doubleOrNull
                ?: price
            val chg = price - prev
            val chgPct = if (prev != 0.0) chg / prev * 100.0 else 0.0
            out[sym] = AssetQuote(
                symbol = sym,
                price = price,
                change24h = chg,
                changePercent24h = chgPct,
                high52w = meta["fiftyTwoWeekHigh"]?.jsonPrimitive?.doubleOrNull,
                low52w = meta["fiftyTwoWeekLow"]?.jsonPrimitive?.doubleOrNull,
                volume24h = meta["regularMarketVolume"]?.jsonPrimitive?.doubleOrNull,
            )
        }
        return out
    }

    suspend fun fetchBinance24hr(): Map<String, AssetQuote> {
        val rows = http.parse("https://api.binance.com/api/v3/ticker/24hr", ttlMs = 8_000).jsonArray
        val out = HashMap<String, AssetQuote>(rows.size)
        rows.forEach { el ->
            val o = el.jsonObject
            val sym = o["symbol"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val price = o["lastPrice"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@forEach
            val chgPct = o["priceChangePercent"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
            val chg = o["priceChange"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
            val vol = o["quoteVolume"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            out[sym] = AssetQuote(sym, price, chg, chgPct, volume24h = vol)
        }
        return out
    }

    suspend fun fetchBinanceCatalog(): List<BinanceUsdtCoin> {
        val symbols = http.parse("https://api.binance.com/api/v3/exchangeInfo", 30 * 60_000)
            .jsonObject["symbols"]?.jsonArray ?: return emptyList()
        return symbols.mapNotNull { el ->
            val o = el.jsonObject
            if (o["quoteAsset"]?.jsonPrimitive?.contentOrNull != "USDT") return@mapNotNull null
            if (o["status"]?.jsonPrimitive?.contentOrNull != "TRADING") return@mapNotNull null
            val base = o["baseAsset"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (Regex("UP$|DOWN$|BULL$|BEAR$", RegexOption.IGNORE_CASE).containsMatchIn(base)) return@mapNotNull null
            BinanceUsdtCoin(base, o["symbol"]!!.jsonPrimitive.content, base)
        }.sortedBy { it.baseAsset }
    }

    private fun generateSynthetic(asset: AssetDefinition, timeframe: Timeframe): List<Candle> {
        val bases = mapOf(
            "btc-usd" to 95_000.0, "eth-usd" to 3_400.0, "sol-usd" to 180.0, "ada-usd" to 0.72,
            "spx" to 5_600.0, "gold" to 2_400.0, "btc-d" to 54.0, "total" to 3.2e12,
            "total2" to 1.45e12, "total3" to 9.5e11, "fear-greed" to 45.0,
            "eth-btc" to 0.036, "sol-btc" to 0.0019, "ada-btc" to 0.0000075, "usdt-usd" to 1.0,
        )
        val base = bases[asset.id] ?: 100.0
        val step = TimeframeMaps.stepSec(timeframe)
        val count = min(TimeframeMaps.binance(timeframe).limit, 400)
        val now = System.currentTimeMillis() / 1000
        val seed = asset.id.hashCode().toLong()
        var price = base
        return (count downTo 0).map { i ->
            val t = now - i * step
            val noise = sin((t + seed).toDouble() / (step * 7)) * 0.012
            val drift = cos((t + seed * 3).toDouble() / (step * 40)) * 0.008
            val open = price
            var close = price * (1 + noise + drift * 0.3)
            if (asset.id == "fear-greed") close = min(100.0, max(0.0, 50 + sin((t + seed).toDouble() / (step * 20)) * 25))
            val high = max(open, close) * (1 + abs(noise) * 0.5)
            val low = min(open, close) * (1 - abs(noise) * 0.5)
            price = close
            Candle(t, if (asset.id == "fear-greed") close else open, if (asset.id == "fear-greed") close else high, if (asset.id == "fear-greed") close else low, close, base * (0.5 + abs(noise) * 20))
        }
    }

    companion object {
        private val BINANCE_SYMBOL = Regex("^[A-Z0-9]{4,20}$")
        private val BINANCE_INTERVAL = Regex("^[0-9]+[mhdwM]$|^1M$")
        private val COINGECKO_ID = Regex("^[a-z0-9-]{1,80}$")
        private val BTC_ADDRESS = Regex("^(bc1[a-z0-9]{25,87}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})$")
        private val ETH_ADDRESS = Regex("^0x[a-fA-F0-9]{40}$")
    }
}

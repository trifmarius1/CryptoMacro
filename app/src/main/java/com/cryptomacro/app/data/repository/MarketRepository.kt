package com.cryptomacro.app.data.repository

/**
 * BEGINNER: The "brain" of live prices. UI never calls Binance itself — it reads these StateFlows:
 *  quotes      map assetId → last price / 24h change
 *  ticks       WebSocket last price
 *  wsLive      true when the socket is connected (green Live pill)
 *  topCryptos  CoinGecko ranking (hourly)
 *  topStocks   Yahoo spark quotes (every 10s)
 *
 * extraAssets remembers coins that are not in CoreAssets (cg-solana, stock-aapl, …).
 * resolve(id) looks there first, then AssetRegistry.
 */
import com.cryptomacro.app.data.local.AppDatabase
import com.cryptomacro.app.data.local.CandleCacheEntity
import com.cryptomacro.app.data.local.QuoteCacheEntity
import com.cryptomacro.app.data.remote.BinanceWebSocket
import com.cryptomacro.app.data.remote.LiveTick
import com.cryptomacro.app.data.remote.MarketRemoteDataSource
import com.cryptomacro.app.domain.model.AssetDefinition
import com.cryptomacro.app.domain.model.AssetQuote
import com.cryptomacro.app.domain.model.Candle
import com.cryptomacro.app.domain.model.CandleResult
import com.cryptomacro.app.domain.model.ConnectionState
import com.cryptomacro.app.domain.model.FeeEstimates
import com.cryptomacro.app.domain.model.FeedStatus
import com.cryptomacro.app.domain.model.HalvingInfo
import com.cryptomacro.app.domain.model.ListedMarketItem
import com.cryptomacro.app.domain.model.MarketOverview
import com.cryptomacro.app.domain.model.Timeframe
import com.cryptomacro.app.domain.model.TopStocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val remote: MarketRemoteDataSource,
    private val ws: BinanceWebSocket,
    private val assets: AssetRegistry,
    db: AppDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val candleDao = db.candles()
    private val quoteDao = db.quotes()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wsJob: Job? = null

    private val _ticks = MutableStateFlow<Map<String, Double>>(emptyMap())
    val ticks: StateFlow<Map<String, Double>> = _ticks
    private val _wsLive = MutableStateFlow(false)
    val wsLive: StateFlow<Boolean> = _wsLive
    private val _quotes = MutableStateFlow<Map<String, AssetQuote>>(emptyMap())
    val quotes: StateFlow<Map<String, AssetQuote>> = _quotes
    private val _overview = MutableStateFlow(MarketOverview())
    val overview: StateFlow<MarketOverview> = _overview
    private val _topCryptos = MutableStateFlow<List<ListedMarketItem>>(emptyList())
    val topCryptos: StateFlow<List<ListedMarketItem>> = _topCryptos
    private val _topStocks = MutableStateFlow<List<ListedMarketItem>>(emptyList())
    val topStocks: StateFlow<List<ListedMarketItem>> = _topStocks
    private val extraAssets = java.util.concurrent.ConcurrentHashMap<String, AssetDefinition>()

    init {
        _topStocks.value = TopStocks.tickers.mapIndexed { i, (sym, name) ->
            ListedMarketItem(i + 1, TopStocks.toAsset(sym, name), AssetQuote(sym, 0.0, 0.0, 0.0))
        }
        _topStocks.value.forEach { extraAssets[it.asset.id] = it.asset }

        scope.launch {
            assets.assets.collectLatest { list ->
                resubscribe(knownAssets(list))
                refreshQuotes(list)
            }
        }
        scope.launch {
            while (true) {
                runCatching { _overview.value = remote.fetchOverview() }
                delay(60_000)
            }
        }
        scope.launch {
            while (true) {
                runCatching { refreshTopStocksLive() }
                delay(10_000)
            }
        }
        scope.launch {
            while (true) {
                runCatching { refreshTopCryptos() }
                delay(60L * 60_000)
            }
        }
        scope.launch {
            while (true) {
                delay(15_000)
                runCatching { refreshCryptoLivePrices() }
            }
        }
        scope.launch {
            while (true) {
                delay(45_000)
                runCatching { refreshQuotes(assets.current()) }
            }
        }
    }

    private fun knownAssets(base: List<AssetDefinition> = assets.snapshot()): List<AssetDefinition> {
        val extras = extraAssets.values.toList()
        val ids = base.map { it.id }.toSet()
        return base + extras.filter { it.id !in ids }
    }

    fun resolve(id: String): AssetDefinition? =
        extraAssets[id] ?: assets.byId(id)

    fun ensureAsset(asset: AssetDefinition) {
        extraAssets[asset.id] = asset
    }

    suspend fun refreshTopLists() {
        refreshTopStocksLive()
        refreshTopCryptos()
        resubscribe(knownAssets())
    }

    private suspend fun refreshTopCryptos() {
        val cryptos = remote.fetchTopCryptos()
        if (cryptos.isEmpty()) return
        cryptos.forEach {
            extraAssets[it.asset.id] = it.asset
            _quotes.update { map -> map + (it.asset.id to it.quote) }
        }
        _topCryptos.value = cryptos
        resubscribe(knownAssets())
        refreshCryptoLivePrices()
    }

    private suspend fun refreshCryptoLivePrices() {
        val current = _topCryptos.value
        if (current.isEmpty()) return
        val tickers = remote.fetchBinance24hr()
        if (tickers.isEmpty()) return
        val updated = current.map { item ->
            val binance = item.asset.binanceSymbol ?: return@map item
            val t = tickers[binance] ?: return@map item
            val q = t.copy(symbol = item.asset.symbol, marketCap = item.quote.marketCap)
            extraAssets[item.asset.id] = item.asset
            _quotes.update { map -> map + (item.asset.id to q) }
            item.copy(quote = q)
        }
        _topCryptos.value = updated
    }

    private suspend fun refreshTopStocksLive() {
        applyStockQuotes(remote.fetchTopStocks())
    }

    private fun applyStockQuotes(stocks: List<ListedMarketItem>) {
        if (stocks.isEmpty()) return
        val incoming = stocks.associateBy { it.asset.id }
        val previous = _topStocks.value.associateBy { it.asset.id }
        val merged = TopStocks.tickers.mapIndexed { i, (sym, name) ->
            val asset = TopStocks.toAsset(sym, name)
            val fresh = incoming[asset.id]
            val old = previous[asset.id]
            val quote = when {
                fresh != null && fresh.quote.price > 0 -> fresh.quote
                old != null && old.quote.price > 0 -> old.quote
                else -> fresh?.quote ?: old?.quote ?: AssetQuote(sym, 0.0, 0.0, 0.0)
            }
            extraAssets[asset.id] = asset
            if (quote.price > 0) _quotes.update { map -> map + (asset.id to quote) }
            ListedMarketItem(i + 1, asset, quote)
        }
        _topStocks.value = merged
    }

    private fun resubscribe(list: List<AssetDefinition>) {
        wsJob?.cancel()
        val symbols = list.mapNotNull { it.binanceSymbol?.uppercase() }.distinct()
        wsJob = scope.launch {
            ws.ticks(symbols).collect { (live, tick) ->
                _wsLive.value = live
                if (tick != null) applyTick(tick, list)
            }
        }
    }

    private fun applyTick(tick: LiveTick, list: List<AssetDefinition>) {
        val asset = list.find { it.binanceSymbol?.uppercase() == tick.binanceSymbol } ?: return
        _ticks.update { it + (asset.id to tick.price) }
        _quotes.update { map ->
            val prev = map[asset.id]
            val changePct = prev?.changePercent24h ?: 0.0
            val openApprox = if (changePct != 0.0) tick.price / (1 + changePct / 100) else tick.price
            map + (asset.id to (prev?.copy(
                price = tick.price,
                change24h = tick.price - openApprox,
                updatedAt = System.currentTimeMillis(),
            ) ?: AssetQuote(asset.symbol, tick.price, 0.0, 0.0)))
        }
    }

    suspend fun refreshQuotes(list: List<AssetDefinition>? = null) {
        val targets = list ?: assets.current()
        targets.forEach { asset ->
            runCatching {
                val q = remote.fetchQuote(asset)
                _quotes.update { it + (asset.id to q) }
                quoteDao.upsert(
                    QuoteCacheEntity(
                        asset.id,
                        JsonObject(
                            mapOf(
                                "symbol" to JsonPrimitive(q.symbol),
                                "price" to JsonPrimitive(q.price),
                                "change24h" to JsonPrimitive(q.change24h),
                                "changePercent24h" to JsonPrimitive(q.changePercent24h),
                                "updatedAt" to JsonPrimitive(q.updatedAt),
                            ),
                        ).toString(),
                        System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    suspend fun candles(assetId: String, timeframe: Timeframe): CandleResult {
        val asset = resolve(assetId) ?: error("Unknown $assetId")
        val live = runCatching { remote.fetchCandles(asset, timeframe) }.getOrNull()
        if (live != null && live.candles.isNotEmpty() && live.status.state != ConnectionState.OFFLINE) {
            val payload = JsonArray(
                live.candles.map { c ->
                    JsonArray(
                        listOf(
                            JsonPrimitive(c.time),
                            JsonPrimitive(c.open),
                            JsonPrimitive(c.high),
                            JsonPrimitive(c.low),
                            JsonPrimitive(c.close),
                            JsonPrimitive(c.volume ?: 0.0),
                        ),
                    )
                },
            )
            candleDao.upsert(
                CandleCacheEntity(assetId, timeframe.id, payload.toString(), System.currentTimeMillis(), live.status.source),
            )
            return live
        }
        val cached = candleDao.get(assetId, timeframe.id)
        if (cached != null) {
            val parsed = runCatching {
                json.parseToJsonElement(cached.json).jsonArray.map { row ->
                    val a = row.jsonArray
                    Candle(
                        time = a[0].jsonPrimitive.long,
                        open = a[1].jsonPrimitive.double,
                        high = a[2].jsonPrimitive.double,
                        low = a[3].jsonPrimitive.double,
                        close = a[4].jsonPrimitive.double,
                        volume = a.getOrNull(5)?.jsonPrimitive?.doubleOrNull,
                    )
                }
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) {
                return CandleResult(
                    parsed,
                    FeedStatus(ConnectionState.CACHED, "Cache (${cached.source})", cached.savedAt, "Showing cached snapshot"),
                )
            }
        }
        return live ?: CandleResult(emptyList(), FeedStatus(ConnectionState.OFFLINE, "none", message = "No data"))
    }

    suspend fun usdToEur() = remote.fetchUsdToEur()
    suspend fun catalog() = remote.fetchBinanceCatalog()
    suspend fun halving(): HalvingInfo = remote.fetchHalving()
    suspend fun fees(): FeeEstimates = remote.fetchFees()
    suspend fun btcBalance(addr: String) = remote.fetchBtcAddressBalance(addr)
    suspend fun ethBalance(addr: String) = remote.fetchEthAddressBalance(addr)

    fun livePrice(assetId: String): Double? = _ticks.value[assetId] ?: _quotes.value[assetId]?.price
}

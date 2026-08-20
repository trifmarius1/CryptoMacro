package com.cryptomacro.app.ui.markets

/**
 * BEGINNER: A ViewModel survives rotation. The Composables in MarketsScreens.kt only *display*
 * these flows; they do not fetch HTTP themselves.
 *
 * selectedId  — which chart is open (btc-usd, stock-aapl, cg-dogecoin, …)
 * featuredIds — BTC ETH SOL SPX + pinned extras from DataStore
 * listed      — the scrollable market list for the current filter + search
 * pinChart()  — save the coin, add a chip, select it, close the Add sheet
 */
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptomacro.app.data.local.PreferencesRepository
import com.cryptomacro.app.data.repository.AssetRegistry
import com.cryptomacro.app.data.repository.MarketRepository
import com.cryptomacro.app.domain.model.AssetDefinition
import com.cryptomacro.app.domain.model.BinanceUsdtCoin
import com.cryptomacro.app.domain.model.CandleResult
import com.cryptomacro.app.domain.model.ChartKind
import com.cryptomacro.app.domain.model.CoinTag
import com.cryptomacro.app.domain.model.CoreAssets
import com.cryptomacro.app.domain.model.ListedMarketItem
import com.cryptomacro.app.domain.model.Timeframe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MarketFilter { TOP_CRYPTO, TOP_STOCKS, DEFI, LAYER, MEME, WATCH }

enum class AddChartTab { CRYPTO, STOCKS }

@HiltViewModel
class MarketsViewModel @Inject constructor(
    val market: MarketRepository,
    private val registry: AssetRegistry,
    val prefs: PreferencesRepository,
) : ViewModel() {
    val selectedId = MutableStateFlow(CoreAssets.featuredChips.first())
    val timeframe = MutableStateFlow(Timeframe.D1)
    val chartKind = MutableStateFlow(ChartKind.CANDLESTICK)
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(MarketFilter.TOP_CRYPTO)
    val candles = MutableStateFlow<CandleResult?>(null)
    val catalog = MutableStateFlow<List<BinanceUsdtCoin>>(emptyList())
    val showAdd = MutableStateFlow(false)
    val addTab = MutableStateFlow(AddChartTab.CRYPTO)

    val assets = registry.assets.stateIn(viewModelScope, SharingStarted.Eagerly, CoreAssets.all)
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.cryptomacro.app.data.local.AppSettings())

    val featuredIds = settings.map { s ->
        (CoreAssets.featuredChips + s.pinnedChartIds).distinct()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CoreAssets.featuredChips)

    val addCandidates = combine(addTab, market.topCryptos, market.topStocks, market.quotes) { tab, cryptos, stocks, quotes ->
        val base = if (tab == AddChartTab.CRYPTO) cryptos else stocks
        base.map { item ->
            val live = quotes[item.asset.id]
            if (live != null && live.price > 0) item.copy(quote = live) else item
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val listed = combine(filter, query, market.topCryptos, market.topStocks, assets) { f, q, cryptos, stocks, core ->
        Triple(f, q, Triple(cryptos, stocks, core))
    }.combine(featuredIds) { pack, pins ->
        pack to pins
    }.combine(market.quotes) { (pack, pins), quotes ->
        val (f, q, rest) = pack
        val (cryptos, stocks, core) = rest
        val base = when (f) {
            MarketFilter.TOP_CRYPTO -> cryptos
            MarketFilter.TOP_STOCKS -> stocks
            MarketFilter.DEFI -> core.filter { it.tag == CoinTag.DEFI }.toListed(quotes)
            MarketFilter.LAYER -> core.filter { it.tag == CoinTag.LAYER1 || it.tag == CoinTag.LAYER2 }.toListed(quotes)
            MarketFilter.MEME -> core.filter { it.tag == CoinTag.MEME }.toListed(quotes)
            MarketFilter.WATCH -> {
                val pinSet = pins.toSet()
                val merged = linkedMapOf<String, com.cryptomacro.app.domain.model.AssetDefinition>()
                core.filter { it.id in pinSet || it.custom }.forEach { merged[it.id] = it }
                (cryptos + stocks).forEach { item ->
                    if (item.asset.id in pinSet) merged.putIfAbsent(item.asset.id, item.asset)
                }
                merged.values.toList().toListed(quotes)
            }
        }
        val withLive = base.map { item ->
            val live = quotes[item.asset.id]
            if (live != null) item.copy(quote = live) else item
        }
        if (q.isBlank()) withLive
        else withLive.filter {
            it.asset.symbol.contains(q, true) || it.asset.name.contains(q, true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { loadCandles() }
        viewModelScope.launch {
            combine(selectedId, timeframe) { _, _ -> }.collect { loadCandles() }
        }
        viewModelScope.launch { runCatching { market.refreshTopLists() } }
    }

    fun select(id: String) { selectedId.value = id }
    fun setTimeframe(tf: Timeframe) { timeframe.value = tf }
    fun setKind(kind: ChartKind) { chartKind.value = kind }
    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: MarketFilter) { filter.value = f }

    fun selectedAsset(): AssetDefinition =
        market.resolve(selectedId.value) ?: registry.byId(selectedId.value) ?: CoreAssets.byId("btc-usd")!!

    fun chipLabel(id: String): String =
        (market.resolve(id) ?: registry.byId(id))?.symbol?.substringBefore("/") ?: id

    private suspend fun loadCandles() {
        candles.value = runCatching { market.candles(selectedId.value, timeframe.value) }.getOrNull()
    }

    fun refresh() = viewModelScope.launch {
        runCatching { market.refreshTopLists() }
        market.refreshQuotes()
        loadCandles()
    }

    fun openAddChart() {
        showAdd.value = true
        viewModelScope.launch {
            if (addCandidates.value.size < 50) {
                runCatching { market.refreshTopLists() }
            }
        }
    }

    fun setAddTab(tab: AddChartTab) { addTab.value = tab }

    fun pinChart(item: ListedMarketItem) = viewModelScope.launch {
        market.ensureAsset(item.asset)
        registry.ensureTracked(item.asset)
        val next = (settings.value.pinnedChartIds + item.asset.id)
            .filter { it !in CoreAssets.featuredChips }
            .distinct()
            .take(24)
        prefs.setPinnedCharts(next)
        selectedId.value = item.asset.id
        showAdd.value = false
    }

    fun unpinChart(id: String) = viewModelScope.launch {
        if (id in CoreAssets.featuredChips) return@launch
        prefs.setPinnedCharts(settings.value.pinnedChartIds.filter { it != id })
        if (selectedId.value == id) selectedId.value = CoreAssets.featuredChips.first()
    }

    fun loadCatalog() = viewModelScope.launch {
        catalog.value = runCatching { market.catalog() }.getOrDefault(emptyList())
        showAdd.value = true
    }

    fun addCoin(coin: BinanceUsdtCoin) = viewModelScope.launch {
        registry.addCustom(coin)
        showAdd.value = false
    }

    fun removeCustom(id: String) = viewModelScope.launch { registry.removeCustom(id) }
}

private fun List<AssetDefinition>.toListed(quotes: Map<String, com.cryptomacro.app.domain.model.AssetQuote>) =
    mapIndexed { i, a ->
        ListedMarketItem(
            rank = i + 1,
            asset = a,
            quote = quotes[a.id] ?: com.cryptomacro.app.domain.model.AssetQuote(a.symbol, 0.0, 0.0, 0.0),
        )
    }

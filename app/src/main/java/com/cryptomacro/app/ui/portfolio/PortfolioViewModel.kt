@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.cryptomacro.app.ui.portfolio

/**
 * BEGINNER: Portfolio screen state.
 * pickerCoins is the Add-transaction list (top 200 live, or CoreAssets crypto if ranking has not arrived).
 * addTransaction() looks up the AssetDefinition, persists it, writes the Room row, then refreshes that quote
 * so Portfolio total is not $0 until the next poll.
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptomacro.app.data.local.PreferencesRepository
import com.cryptomacro.app.data.repository.AssetRegistry
import com.cryptomacro.app.data.repository.MarketRepository
import com.cryptomacro.app.data.repository.PortfolioRepository
import com.cryptomacro.app.domain.model.AssetCategory
import com.cryptomacro.app.domain.model.AssetQuote
import com.cryptomacro.app.domain.model.CoreAssets
import com.cryptomacro.app.domain.model.DcaComparison
import com.cryptomacro.app.domain.model.FiatCurrency
import com.cryptomacro.app.domain.model.ListedMarketItem
import com.cryptomacro.app.domain.model.PortfolioSummary
import com.cryptomacro.app.domain.model.PortfolioTransaction
import com.cryptomacro.app.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val portfolio: PortfolioRepository,
    val market: MarketRepository,
    val registry: AssetRegistry,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    val showTxSheet = MutableStateFlow(false)
    val dca = MutableStateFlow<DcaComparison?>(null)
    val dcaAsset = MutableStateFlow("btc-usd")

    private val fx = MutableStateFlow(0.92 to "…")

    init {
        viewModelScope.launch { fx.value = market.usdToEur() }
    }

    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.cryptomacro.app.data.local.AppSettings())

    val summary = combine(settings, fx) { s, rate -> s.fiat to rate }
        .flatMapLatest { (fiat, rate) -> portfolio.summary(fiat, rate.first, rate.second) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PortfolioSummary(0.0, 0.0, 0.0, 0.0, 0.0, emptyList(), FiatCurrency.USD, 0.92, "…", false),
        )

    val transactions = portfolio.transactions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pickerCoins = combine(market.topCryptos, market.quotes) { top, quotes ->
        if (top.isNotEmpty()) {
            top.map { item ->
                val live = quotes[item.asset.id]
                if (live != null && live.price > 0) item.copy(quote = live) else item
            }
        } else {
            CoreAssets.all.filter { it.category == AssetCategory.CRYPTO }.mapIndexed { i, a ->
                ListedMarketItem(
                    rank = i + 1,
                    asset = a,
                    quote = quotes[a.id] ?: AssetQuote(a.symbol, 0.0, 0.0, 0.0),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setFiat(c: FiatCurrency) = viewModelScope.launch { prefs.setFiat(c) }

    fun openTxSheet() {
        showTxSheet.value = true
        viewModelScope.launch {
            if (market.topCryptos.value.size < 150) {
                runCatching { market.refreshTopLists() }
            }
        }
    }

    fun addHolding(assetId: String, amount: Double, avg: Double?) =
        viewModelScope.launch { portfolio.addOrMergeHolding(assetId, amount, avg) }

    fun updateHolding(id: String, amount: Double, avg: Double?) =
        viewModelScope.launch { portfolio.updateHolding(id, amount, avg) }

    fun removeHolding(id: String) = viewModelScope.launch { portfolio.removeHolding(id) }

    fun assetLabel(id: String): String = market.resolve(id)?.symbol ?: id

    fun addTransaction(
        assetId: String,
        type: TransactionType,
        amount: Double,
        price: Double,
        fee: Double = 0.0,
        note: String? = null,
    ) = viewModelScope.launch {
        if (amount <= 0.0 || !amount.isFinite()) return@launch
        if (price < 0.0 || !price.isFinite()) return@launch
        val asset = market.resolve(assetId) ?: registry.byId(assetId) ?: return@launch
        market.ensureAsset(asset)
        registry.ensureTracked(asset)
        val px = if (price > 0) price else (market.livePrice(asset.id) ?: 0.0)
        portfolio.addTransaction(
            PortfolioTransaction(
                id = PortfolioRepository.newId("tx"),
                assetId = asset.id,
                type = type,
                amount = amount,
                priceUsd = px,
                feeUsd = fee,
                note = note,
            ),
        )
        runCatching { market.refreshQuotes(listOf(asset)) }
        showTxSheet.value = false
        loadDca(asset.id)
    }

    fun loadDca(assetId: String = dcaAsset.value) = viewModelScope.launch {
        dcaAsset.value = assetId
        dca.value = runCatching { portfolio.dcaComparison(assetId) }.getOrNull()
    }
}

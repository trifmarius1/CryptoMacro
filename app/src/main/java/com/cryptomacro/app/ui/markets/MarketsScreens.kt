@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.cryptomacro.app.ui.markets

/**
 * BEGINNER: Composables = functions that *describe* UI. When a Flow emits, they redraw.
 *
 * MarketsListPane  — title, Live pill, chips (BTC… + Add), optional compact chart, filters, list
 * FeaturedChartChips — horizontal chips; extras have an X to unpin
 * AddChartSheet    — bottom sheet listing top 200 crypto or top 50 stocks
 * AssetChartCard   — name, price, timeframes, chart type, PriceChart canvas
 * AssetDetailPane  — chips + chart + metrics (used on tablets and after tapping a row on a phone)
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cryptomacro.app.domain.model.AssetUnit
import com.cryptomacro.app.domain.model.ChartKind
import com.cryptomacro.app.domain.model.CoreAssets
import com.cryptomacro.app.domain.model.ShemitahData
import com.cryptomacro.app.domain.model.Timeframe
import com.cryptomacro.app.domain.util.Formatters
import com.cryptomacro.app.ui.chart.PriceChart

import com.cryptomacro.app.ui.components.AppCard
import com.cryptomacro.app.ui.components.AssetAvatar
import com.cryptomacro.app.ui.components.DeltaText
import com.cryptomacro.app.ui.components.FilterChipBar
import com.cryptomacro.app.ui.components.LivePill
import com.cryptomacro.app.ui.components.MoneyText
import com.cryptomacro.app.ui.components.SectionLabel
import com.cryptomacro.app.ui.components.WrapRow
import com.cryptomacro.app.ui.portfolio.PortfolioViewModel

@Composable
fun MarketsListPane(
    vm: MarketsViewModel,
    portfolio: PortfolioViewModel,
    onOpenDetail: (String) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 240.dp,
) {
    val quotes by vm.market.quotes.collectAsStateWithLifecycle()
    val overview by vm.market.overview.collectAsStateWithLifecycle()
    val live by vm.market.wsLive.collectAsStateWithLifecycle()
    val selected by vm.selectedId.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val listed by vm.listed.collectAsStateWithLifecycle()
    val featuredIds by vm.featuredIds.collectAsStateWithLifecycle()
    val showAdd by vm.showAdd.collectAsStateWithLifecycle()

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Markets", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Live crypto, equity & macro",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LivePill(live)
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        }
        item { FeaturedChartChips(vm, featuredIds, selected) }
        if (compact) {
            item { AssetChartCard(vm, chartHeight = chartHeight) }
        }
        item {
            Text(
                "Cap ${Formatters.compactUsd(overview.totalMarketCap)}\nBTC.D ${"%.1f".format(overview.btcDominance)}%  ·  SPX ${Formatters.compactUsd(overview.spxPrice)}\nF&G ${overview.fearGreed} ${overview.fearGreedLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search assets") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        item {
            val filters = listOf(
                MarketFilter.TOP_CRYPTO to "Top 200 crypto",
                MarketFilter.TOP_STOCKS to "Top 50 stocks",
                MarketFilter.DEFI to "DeFi",
                MarketFilter.LAYER to "L1/L2",
                MarketFilter.MEME to "Memes",
                MarketFilter.WATCH to "Watch",
            )
            FilterChipBar(filters.map { it.second to (filter == it.first) }) { i -> vm.setFilter(filters[i].first) }
        }
        if (listed.isEmpty() && (filter == MarketFilter.TOP_CRYPTO || filter == MarketFilter.TOP_STOCKS)) {
            item {
                Text(
                    if (filter == MarketFilter.TOP_CRYPTO) "Loading top 200 cryptocurrencies…" else "Loading top 50 stocks…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(listed.distinctBy { it.asset.id }, key = { it.asset.id }) { item ->
            val asset = item.asset
            val q = quotes[asset.id] ?: item.quote
            AppCard(onClick = {
                vm.select(asset.id)
                onOpenDetail(asset.id)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${item.rank}",
                        modifier = Modifier.width(28.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AssetAvatar(asset.symbol)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(asset.symbol, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            asset.name,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(if (q.price > 0) shortPrice(q.price, asset.unit, asset.isPercent) else "—")
                        DeltaText(q.changePercent24h)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }

    if (showAdd) AddChartSheet(vm)
}

private fun shortPrice(value: Double, unit: AssetUnit, isPercent: Boolean): String {
    if (unit == AssetUnit.INDEX) return value.toInt().toString()
    return Formatters.price(value, unit, isPercent)
}

@Composable
private fun FeaturedChartChips(vm: MarketsViewModel, featuredIds: List<String>, selected: String) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        featuredIds.forEach { id ->
            val extra = id !in CoreAssets.featuredChips
            if (extra) {
                InputChip(
                    selected = selected == id,
                    onClick = { vm.select(id) },
                    label = { Text(vm.chipLabel(id), maxLines = 1) },
                    trailingIcon = {
                        IconButton(
                            onClick = { vm.unpinChart(id) },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove chart", modifier = Modifier.size(14.dp))
                        }
                    },
                )
            } else {
                FilterChip(
                    selected = selected == id,
                    onClick = { vm.select(id) },
                    label = { Text(vm.chipLabel(id), maxLines = 1) },
                )
            }
        }
        FilterChip(
            selected = false,
            onClick = { vm.openAddChart() },
            label = { Text("Add") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = "Add chart", modifier = Modifier.size(16.dp)) },
        )
        Spacer(Modifier.width(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChartSheet(vm: MarketsViewModel) {
    val tab by vm.addTab.collectAsStateWithLifecycle()
    val coins by vm.addCandidates.collectAsStateWithLifecycle()
    val pinned by vm.featuredIds.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val filtered = remember(coins, query) {
        if (query.isBlank()) coins
        else coins.filter {
            it.asset.symbol.contains(query, true) || it.asset.name.contains(query, true)
        }
    }
    ModalBottomSheet(onDismissRequest = { vm.showAdd.value = false }) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Add a chart", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pin any top-200 crypto or top-50 stock next to BTC, ETH, SOL and SPX.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == AddChartTab.CRYPTO,
                    onClick = { vm.setAddTab(AddChartTab.CRYPTO) },
                    label = { Text("Crypto") },
                )
                FilterChip(
                    selected = tab == AddChartTab.STOCKS,
                    onClick = { vm.setAddTab(AddChartTab.STOCKS) },
                    label = { Text("Stocks") },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (tab == AddChartTab.CRYPTO) "Search crypto" else "Search stocks") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
            )
            if (coins.isEmpty()) {
                Text(
                    if (tab == AddChartTab.CRYPTO) "Loading top 200 cryptocurrencies…" else "Loading top 50 stocks…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.asset.id }) { item ->
                    val already = item.asset.id in pinned
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (already) MaterialTheme.colorScheme.primary.copy(0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(0.45f),
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.4f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !already) { vm.pinChart(item) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${item.rank}",
                            modifier = Modifier.width(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        AssetAvatar(item.asset.symbol)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.asset.symbol, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                item.asset.name,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (already) {
                            Text("Pinned", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        } else {
                            MoneyText(if (item.quote.price > 0) Formatters.compactUsd(item.quote.price) else "—")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssetChartCard(
    vm: MarketsViewModel,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 240.dp,
) {
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val asset = remember(selectedId) { vm.selectedAsset() }
    val quotes by vm.market.quotes.collectAsStateWithLifecycle()
    val candles by vm.candles.collectAsStateWithLifecycle()
    val tf by vm.timeframe.collectAsStateWithLifecycle()
    val kind by vm.chartKind.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val q = quotes[asset.id]
    var cross by remember { mutableStateOf<String?>(null) }
    AppCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(asset.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(asset.symbol, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(if (q != null) shortPrice(q.price, asset.unit, asset.isPercent) else "—")
                DeltaText(q?.changePercent24h ?: 0.0)
            }
        }
        WrapRow {
            Timeframe.chartSelector.forEach {
                FilterChip(selected = tf == it, onClick = { vm.setTimeframe(it) }, label = { Text(it.label) })
            }
        }
        WrapRow {
            ChartKind.entries.forEach {
                FilterChip(
                    selected = kind == it,
                    onClick = { vm.setKind(it) },
                    label = { Text(it.name.lowercase().replaceFirstChar { c -> c.titlecase() }) },
                )
            }
        }
        candles?.status?.message?.let {
            Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
        }
        cross?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2)
        }
        PriceChart(
            candles = candles?.candles.orEmpty(),
            modifier = Modifier.fillMaxWidth().height(chartHeight),
            kind = if (asset.preferLine && kind == ChartKind.CANDLESTICK) ChartKind.AREA else kind,
            showSma = settings.showSma,
            showEma = settings.showEma,
            showVolume = settings.showVolume,
            shemitahBands = if (settings.shemitahOverlay) ShemitahData.bands() else emptyList(),
            onCrosshair = { ch ->
                cross = ch?.let { c ->
                    "O ${fmt(c.candle.open)}  H ${fmt(c.candle.high)}\nL ${fmt(c.candle.low)}  C ${fmt(c.candle.close)}"
                }
            },
        )
        Text(
            "Pinch to zoom · long-press for OHLC",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun fmt(v: Double): String = when {
    kotlin.math.abs(v) >= 1000 -> "%,.0f".format(v)
    kotlin.math.abs(v) >= 1 -> "%.2f".format(v)
    else -> "%.4f".format(v)
}

@Composable
fun AssetDetailPane(
    vm: MarketsViewModel,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 240.dp,
) {
    val featuredIds by vm.featuredIds.collectAsStateWithLifecycle()
    val selected by vm.selectedId.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeaturedChartChips(vm, featuredIds, selected)
        AssetChartCard(vm, chartHeight = chartHeight)
        MetricsAndContext(vm)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun MetricsAndContext(vm: MarketsViewModel) {
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val asset = remember(selectedId) { vm.selectedAsset() }
    val quotes by vm.market.quotes.collectAsStateWithLifecycle()
    val q = quotes[asset.id]
    AppCard(Modifier.fillMaxWidth()) {
        SectionLabel("Asset metrics")
        Metric("24h volume", q?.volume24h?.let { Formatters.compactUsd(it) } ?: "—")
        Metric("Market cap", q?.marketCap?.let { Formatters.compactUsd(it) } ?: "—")
        Metric("52w high", q?.high52w?.let { shortPrice(it, asset.unit, asset.isPercent) } ?: "—")
        Metric("52w low", q?.low52w?.let { shortPrice(it, asset.unit, asset.isPercent) } ?: "—")
        asset.description?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Educational only — not financial advice.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        MoneyText(value, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun ExpandedWorkbench(
    vm: MarketsViewModel,
    portfolio: PortfolioViewModel,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 240.dp,
) {
    Row(modifier.fillMaxSize()) {
        MarketsListPane(
            vm, portfolio, onOpenDetail = { vm.select(it) }, compact = false,
            modifier = Modifier.weight(0.28f).fillMaxHeight(),
        )
        AssetDetailPane(vm, Modifier.weight(0.44f).fillMaxHeight(), chartHeight)
        ContextPane(vm, portfolio, Modifier.weight(0.28f).fillMaxHeight())
    }
}

@Composable
fun ContextPane(vm: MarketsViewModel, portfolio: PortfolioViewModel, modifier: Modifier = Modifier) {
    val summary by portfolio.summary.collectAsStateWithLifecycle()
    val asset = vm.selectedAsset()
    val row = summary.rows.find { it.asset.id == asset.id }
    val dca by portfolio.dca.collectAsStateWithLifecycle()
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            SectionLabel("Holdings · ${asset.symbol}")
            if (row == null) {
                Text("No position. Add a transaction from Portfolio.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MoneyText(Formatters.fiat(row.valueUsd, summary.currency, summary.usdToEur), large = true)
                Text("${row.holding.amount} units · avg ${row.holding.avgBuyPriceUsd?.let { Formatters.compactUsd(it) } ?: "—"}")
                DeltaText(row.changePercent24h)
            }
        }
        AppCard {
            SectionLabel("DCA vs actual")
            TextButton(onClick = { portfolio.loadDca(asset.id) }) { Text("Calculate") }
            dca?.let {
                Metric("Invested", Formatters.compactUsd(it.investedUsd))
                Metric("Actual value", Formatters.compactUsd(it.actualValueUsd))
                Metric("Actual return", Formatters.percent(it.actualReturnPct))
                Metric("Weekly DCA", Formatters.percent(it.weeklyBenchmarkReturnPct))
                Metric("Monthly DCA", Formatters.percent(it.monthlyBenchmarkReturnPct))
            }
        }
        AppCard {
            SectionLabel("Risk disclosure")
            Text(
                "Crypto is volatile. Self-custody keys are never stored here. Treat charts as context, not signals.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

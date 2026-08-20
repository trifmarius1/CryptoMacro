@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.cryptomacro.app.ui.portfolio

/**
 * BEGINNER: Portfolio UI.
 * PnlBanner "Portfolio total" is sum of holdings × live price (USD or EUR).
 * TransactionSheet: search top 200, tap a coin, type amount, price auto-fills, Save.
 * parseDecimal accepts "1,5" as well as "1.5".
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.cryptomacro.app.ui.components.readableWidth
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cryptomacro.app.domain.model.FiatCurrency
import com.cryptomacro.app.domain.model.TransactionType
import com.cryptomacro.app.domain.util.Formatters
import com.cryptomacro.app.ui.components.AppCard
import com.cryptomacro.app.ui.components.AssetAvatar
import com.cryptomacro.app.ui.components.DeltaText
import com.cryptomacro.app.ui.components.MoneyText
import com.cryptomacro.app.ui.components.PnlBanner
import com.cryptomacro.app.ui.components.SectionLabel
import com.cryptomacro.app.ui.components.WrapRow
import com.cryptomacro.app.ui.theme.AllocPalette

@Composable
fun PortfolioScreen(vm: PortfolioViewModel, modifier: Modifier = Modifier) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val dca by vm.dca.collectAsStateWithLifecycle()
    val showTx by vm.showTxSheet.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        readableWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Portfolio", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Local-only · encrypted on this device",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                WrapRow {
                    FilterChip(selected = summary.currency == FiatCurrency.USD, onClick = { vm.setFiat(FiatCurrency.USD) }, label = { Text("USD") })
                    FilterChip(selected = summary.currency == FiatCurrency.EUR, onClick = { vm.setFiat(FiatCurrency.EUR) }, label = { Text("EUR") })
                }
            }
        }
        item {
            PnlBanner(
                "Portfolio total",
                Formatters.fiat(summary.totalValueUsd, summary.currency, summary.usdToEur),
                summary.totalPnl24hPct,
                "${summary.rows.size} assets · Unrealized ${Formatters.fiat(summary.totalUnrealizedPnlUsd, summary.currency, summary.usdToEur, signed = true)}\nFX ${summary.fxSource}",
            )
        }
        item {
            AppCard {
                SectionLabel("Allocation")
                AllocationDonut(summary.rows.map { it.allocationPct to it.asset.symbol })
                if (summary.rows.isEmpty()) {
                    Text("No holdings yet. Add a transaction to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                summary.rows.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.asset.symbol,
                            color = AllocPalette[i % AllocPalette.size],
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${"%.1f".format(r.allocationPct)}%", modifier = Modifier.padding(horizontal = 8.dp))
                        MoneyText(Formatters.fiat(r.valueUsd, summary.currency, summary.usdToEur))
                    }
                }
            }
        }
        item {
            Button(onClick = { vm.openTxSheet() }, modifier = Modifier.fillMaxWidth()) {
                Text("Add transaction")
            }
        }
        items(summary.rows, key = { it.holding.id }) { row ->
            AppCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AssetAvatar(row.asset.symbol)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.asset.symbol, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${row.holding.amount} · avg ${row.holding.avgBuyPriceUsd?.let { Formatters.compactUsd(it) } ?: "—"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(Formatters.fiat(row.valueUsd, summary.currency, summary.usdToEur))
                        DeltaText(row.changePercent24h)
                    }
                }
                TextButton(onClick = { vm.removeHolding(row.holding.id) }, modifier = Modifier.align(Alignment.End)) {
                    Text("Remove")
                }
            }
        }
        item {
            AppCard {
                SectionLabel("DCA calculator")
                Text(
                    "Compares your actual buys vs a weekly/monthly schedule of the same cash.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { vm.loadDca() }) { Text("Run vs BTC") }
                dca?.let {
                    Text("Invested ${Formatters.compactUsd(it.investedUsd)}")
                    Text("Actual ${Formatters.percent(it.actualReturnPct)}")
                    Text("Weekly bench ${Formatters.percent(it.weeklyBenchmarkReturnPct)}")
                    Text("Monthly bench ${Formatters.percent(it.monthlyBenchmarkReturnPct)}")
                }
            }
        }
        item { SectionLabel("Recent transactions") }
        items(txs.take(20), key = { it.id }) { tx ->
            AppCard {
                Text("${tx.type.name.replace('_', ' ')}  ${tx.amount}", fontWeight = FontWeight.SemiBold)
                Text(vm.assetLabel(tx.assetId), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("@ ${Formatters.compactUsd(tx.priceUsd)} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(tx.timestamp))}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }

    if (showTx) TransactionSheet(vm)
    }
}

@Composable
private fun AllocationDonut(slices: List<Pair<Double, String>>) {
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val dim = size.minDimension
        val origin = Offset((size.width - dim) / 2, (size.height - dim) / 2)
        if (slices.isEmpty()) {
            drawArc(AllocPalette.last().copy(0.2f), 0f, 360f, false, style = Stroke(18f, cap = StrokeCap.Butt), size = Size(dim, dim), topLeft = origin)
            return@Canvas
        }
        var start = -90f
        slices.forEachIndexed { i, (pct, _) ->
            val sweep = (pct / 100.0 * 360).toFloat()
            drawArc(AllocPalette[i % AllocPalette.size], start, sweep, false, style = Stroke(18f, cap = StrokeCap.Butt), size = Size(dim, dim), topLeft = origin)
            start += sweep
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheet(vm: PortfolioViewModel) {
    val coins by vm.pickerCoins.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var assetId by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.BUY) }
    var amount by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var priceTouched by remember { mutableStateOf(false) }

    val filtered = remember(coins, query) {
        if (query.isBlank()) coins
        else coins.filter {
            it.asset.symbol.contains(query, true) ||
                it.asset.name.contains(query, true)
        }
    }
    val selected = coins.find { it.asset.id == assetId }
    val selectedId = selected?.asset?.id.orEmpty()

    LaunchedEffect(selectedId, selected?.quote?.price) {
        val live = selected?.quote?.price ?: 0.0
        if (!priceTouched && live > 0) price = draftPrice(live)
    }

    ModalBottomSheet(onDismissRequest = { vm.showTxSheet.value = false }) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Add transaction", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick any coin from the top 200, then enter how many you bought.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search top 200 crypto") },
                singleLine = true,
            )
            if (coins.isEmpty()) {
                Text("Loading top 200 cryptocurrencies…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (selected == null) {
                Text("Tap a coin to add it to your portfolio.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "Selected ${selected.asset.name} (${selected.asset.symbol})",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.asset.id }) { item ->
                    val on = item.asset.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (on) MaterialTheme.colorScheme.primary.copy(0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(0.45f),
                            )
                            .border(
                                1.dp,
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(0.4f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                assetId = item.asset.id
                                priceTouched = false
                            }
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
                        MoneyText(
                            if (item.quote.price > 0) Formatters.compactUsd(item.quote.price) else "—",
                        )
                    }
                }
            }
            WrapRow {
                TransactionType.entries.forEach {
                    FilterChip(
                        selected = type == it,
                        onClick = { type = it },
                        label = { Text(txLabel(it)) },
                    )
                }
            }
            OutlinedTextField(
                amount,
                { amount = it },
                label = { Text("Amount of ${selected?.asset?.symbol ?: "coin"}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                price,
                { price = it; priceTouched = true },
                label = { Text("Price USD") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    val qty = Formatters.parseDecimal(amount) ?: 0.0
                    val px = Formatters.parseDecimal(price) ?: selected?.quote?.price ?: 0.0
                    if (qty > 0 && px > 0) {
                        Text("This lot: ${Formatters.compactUsd(qty * px)}")
                    }
                },
            )
            Button(
                onClick = {
                    val a = Formatters.parseDecimal(amount) ?: return@Button
                    val p = Formatters.parseDecimal(price) ?: selected?.quote?.price ?: 0.0
                    val id = selectedId.ifBlank { return@Button }
                    vm.addTransaction(id, type, a, p)
                },
                enabled = selectedId.isNotBlank() && (Formatters.parseDecimal(amount) ?: 0.0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save to portfolio") }
        }
    }
}

private fun draftPrice(value: Double): String = when {
    value >= 1000 -> "%.2f".format(value)
    value >= 1 -> "%.4f".format(value)
    value >= 0.01 -> "%.6f".format(value)
    else -> "%.8f".format(value)
}

private fun txLabel(type: TransactionType) = when (type) {
    TransactionType.BUY -> "Buy"
    TransactionType.SELL -> "Sell"
    TransactionType.TRANSFER_IN -> "Transfer in"
    TransactionType.TRANSFER_OUT -> "Transfer out"
    TransactionType.STAKING_REWARD -> "Staking"
}

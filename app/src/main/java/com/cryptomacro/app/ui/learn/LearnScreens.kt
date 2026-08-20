package com.cryptomacro.app.ui.learn

/**
 * BEGINNER: Learn has three tabs (Sentiment, Cycles, Lessons). SegmentedTabs is a custom
 * 3-button row, not Navigation. Sentiment cards load halving + fees from the ViewModel.
 * wide=true (tablet) shows lesson list and article side by side.
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cryptomacro.app.domain.model.EducationCatalog
import com.cryptomacro.app.domain.model.EducationModule
import com.cryptomacro.app.domain.model.FeeEstimates
import com.cryptomacro.app.domain.model.HalvingInfo
import com.cryptomacro.app.domain.model.ShemitahData
import com.cryptomacro.app.domain.model.ShemitahEvent
import com.cryptomacro.app.ui.components.AppCard
import com.cryptomacro.app.ui.theme.Bear
import com.cryptomacro.app.ui.theme.Bull
import com.cryptomacro.app.ui.theme.Gold
import java.text.DateFormat
import java.util.Date

@Composable
fun LearnScreen(vm: LearnViewModel, modifier: Modifier = Modifier, wide: Boolean = false) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()
    val halving by vm.halving.collectAsStateWithLifecycle()
    val fees by vm.fees.collectAsStateWithLifecycle()
    val moduleId by vm.selectedModule.collectAsStateWithLifecycle()
    val module = EducationCatalog.modules.find { it.id == moduleId } ?: EducationCatalog.modules.first()

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Learn", style = MaterialTheme.typography.headlineMedium)
            Text(
                when (tab) {
                    LearnTab.SENTIMENT -> "Live market mood and on-chain pressure"
                    LearnTab.CYCLES -> "7-year overlay — context, not a trade signal"
                    LearnTab.LESSONS -> "Short lessons on risk, custody, and leverage"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            SegmentedTabs(tab) { vm.setTab(it) }
        }

        if (wide && tab == LearnTab.LESSONS) {
            Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(0.38f).fillMaxSize()) {
                    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { LessonPicker(vm.modules, moduleId) { vm.open(it) } }
                        item { Spacer(Modifier.height(28.dp)) }
                    }
                }
                Box(Modifier.weight(0.62f).fillMaxSize()) {
                    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { LessonArticle(module) }
                        item { Spacer(Modifier.height(28.dp)) }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    LearnTab.SENTIMENT -> {
                        item { FearGreedCard(overview.fearGreed, overview.fearGreedLabel) }
                        item { HalvingCard(halving) }
                        item { FeesCard(fees) }
                    }
                    LearnTab.CYCLES -> {
                        item { PhaseCard(vm) }
                        item { CycleStatsGrid(vm) }
                        item { GuidanceCard() }
                        item { TimelineCard(vm.events) }
                        item { DisclaimerBox() }
                    }
                    LearnTab.LESSONS -> {
                        item { LessonPicker(vm.modules, moduleId) { vm.open(it) } }
                        item { LessonArticle(module) }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun SegmentedTabs(selected: LearnTab, onSelect: (LearnTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LearnTab.entries.forEach { tab ->
            val on = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FearGreedCard(value: Int, label: String) {
    AppCard {
        Text("FEAR & GREED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FearGauge(value)
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$value", fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, lineHeight = 44.sp)
                Text(label, color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("0 extreme fear  →  100 extreme greed", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HalvingCard(info: HalvingInfo?) {
    AppCard {
        Text("BITCOIN HALVING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Supply cut countdown", style = MaterialTheme.typography.titleMedium)
        if (info == null) {
            Text("Loading chain height…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            KeyValue("Current height", "%,d".format(info.currentHeight))
            KeyValue("Next halving block", "%,d".format(info.nextHalvingHeight))
            KeyValue("Blocks remaining", "%,d".format(info.blocksRemaining))
            KeyValue("Estimated date", DateFormat.getDateInstance().format(Date(info.etaEpochMs)))
            KeyValue("Block reward", "${trimNum(info.currentRewardBtc)} → ${trimNum(info.nextRewardBtc)} BTC")
        }
    }
}

@Composable
private fun FeesCard(fees: FeeEstimates?) {
    AppCard {
        Text("NETWORK FEES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Bitcoin mempool", style = MaterialTheme.typography.titleMedium)
        if (fees == null) {
            Text("Loading recommended fees…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            KeyValue("Fastest", "${fees.bitcoinFastest} sat/vB")
            KeyValue("30 minutes", "${fees.bitcoinHalfHour} sat/vB")
            KeyValue("1 hour", "${fees.bitcoinHour} sat/vB")
            KeyValue("Economy", "${fees.bitcoinEconomy} sat/vB")
            Text("Source: ${fees.source}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PhaseCard(vm: LearnViewModel) {
    val phase = when (vm.shemitah.currentPhase) {
        "shemitah" -> "In Shemitah year"
        "approaching" -> "Approaching Shemitah"
        else -> "Expansion years"
    }
    AppCard {
        Text("CURRENT PHASE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(phase, style = MaterialTheme.typography.headlineMedium)
        KeyValue("Next window", vm.shemitah.nextCycleWindow)
        KeyValue("Years remaining", "${vm.shemitah.yearsToNext}")
        Text(
            "Educational overlay only. Past cycle alignments do not predict returns.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CycleStatsGrid(vm: LearnViewModel) {
    val s = vm.shemitah
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("HISTORICAL AVERAGES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Sabbatical return", "${s.sabbaticalAvgReturn}%", false, Modifier.weight(1f))
            StatTile("Expansion return", "+${s.expansionAvgReturn}%", true, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Sabbatical win rate", "${s.sabbaticalWinRate.toInt()}%", false, Modifier.weight(1f))
            StatTile("Expansion win rate", "${s.expansionWinRate.toInt()}%", true, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, bull: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.55f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        Text(value, color = if (bull) Bull else Bear, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GuidanceCard() {
    AppCard {
        Text("HOW TO USE THIS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Capital preservation", style = MaterialTheme.typography.titleMedium, color = Bull)
        ShemitahData.investWindows.forEach { Bullet(it) }
        Spacer(Modifier.height(4.dp))
        Text("Risk-off windows", style = MaterialTheme.typography.titleMedium, color = Bear)
        ShemitahData.riskOffWindows.forEach { Bullet(it) }
    }
}

@Composable
private fun TimelineCard(events: List<ShemitahEvent>) {
    AppCard {
        Text("HISTORICAL MARKERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Stress clustered near some cycle ends", style = MaterialTheme.typography.titleMedium)
        events.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.4f))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    e.year.toString(),
                    modifier = Modifier.width(52.dp),
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(e.title, fontWeight = FontWeight.SemiBold)
                    Text(e.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DisclaimerBox() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(ShemitahData.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LessonPicker(modules: List<EducationModule>, selectedId: String, onSelect: (String) -> Unit) {
    AppCard {
        Text("CHOOSE A LESSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        modules.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.35f))
            val on = m.id == selectedId
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Gold.copy(0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(m.id) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Text(m.title, fontWeight = FontWeight.SemiBold, color = if (on) Gold else MaterialTheme.colorScheme.onSurface)
                Text(m.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LessonArticle(module: EducationModule) {
    AppCard {
        Text("LESSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(module.title, style = MaterialTheme.typography.titleLarge)
        Text(module.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        module.body.forEachIndexed { i, paragraph ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    "${i + 1}.",
                    modifier = Modifier.width(24.dp).padding(top = 2.dp),
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
                Text(paragraph, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Gold.copy(0.12f))
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("TAKEAWAY", style = MaterialTheme.typography.labelSmall, color = Gold)
                Text(module.takeaway, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text("•", modifier = Modifier.padding(end = 8.dp, top = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FearGauge(value: Int) {
    Canvas(Modifier.size(92.dp)) {
        drawArc(Bear.copy(0.22f), 140f, 260f, false, style = Stroke(14f, cap = StrokeCap.Round))
        val sweep = 260f * (value.coerceIn(0, 100) / 100f)
        val color = when {
            value >= 75 -> Gold
            value >= 55 -> Gold.copy(0.85f)
            value >= 45 -> Bull
            else -> Bear
        }
        drawArc(color, 140f, sweep, false, style = Stroke(14f, cap = StrokeCap.Round))
    }
}

private fun trimNum(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)

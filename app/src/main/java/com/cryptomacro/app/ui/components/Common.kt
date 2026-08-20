@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.cryptomacro.app.ui.components

/**
 * BEGINNER: Reusable building blocks.
 * AppCard     — rounded bordered column (never a Box that stacks children on top of each other — that caused overlapping text).
 * DeltaText   — green +% / red -%
 * MoneyText   — monospace price
 * FilterChipBar / WrapRow — chips that wrap to the next line on narrow phones
 * readableWidth() — max 720dp on tablets so lines do not stretch 14 inches
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cryptomacro.app.ui.theme.Bear
import com.cryptomacro.app.ui.theme.Bull
import com.cryptomacro.app.ui.theme.Gold
import com.cryptomacro.app.ui.theme.Teal

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.55f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun DeltaText(percent: Double, modifier: Modifier = Modifier) {
    val color = if (percent >= 0) Bull else Bear
    val sign = if (percent >= 0) "+" else ""
    Text(
        text = "$sign${"%.2f".format(percent)}%",
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun MoneyText(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = if (large) 24.sp else 15.sp,
        lineHeight = if (large) 28.sp else 20.sp,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (large) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
    )
}

@Composable
fun AssetAvatar(symbol: String, modifier: Modifier = Modifier) {
    val letter = symbol.replace(Regex("[^A-Za-z]"), "").take(3).ifBlank { "?" }
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Teal.copy(0.18f))
            .border(1.dp, Teal.copy(0.35f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Teal, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun LivePill(live: Boolean) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (live) Bull.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (live) Bull.copy(0.3f) else MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (live) Bull else MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (live) "Live" else "REST",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (live) Bull else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun QuickAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterChipBar(items: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items.forEachIndexed { i, (label, selected) ->
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) Teal.copy(0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, if (selected) Teal.copy(0.5f) else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onPick(i) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Teal else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun PnlBanner(title: String, value: String, delta: Double, subtitle: String) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(title)
        MoneyText(value, large = true, modifier = Modifier.fillMaxWidth())
        DeltaText(delta)
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun GoldAccent(text: String) {
    Text(
        text,
        color = Gold,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun readableWidth(): Modifier {
    val width = LocalConfiguration.current.screenWidthDp
    return if (width >= 840) Modifier.widthIn(max = 720.dp).fillMaxWidth() else Modifier.fillMaxWidth()
}

@Composable
fun WrapRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

package com.cryptomacro.app.widget

/** BEGINNER: Small ticker of the user's favoriteAssetId (default BTC). Public market data, OK to show with privacy on. */
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.cryptomacro.app.di.WidgetEntryPoint
import com.cryptomacro.app.domain.util.Formatters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

class PriceTickerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PriceTickerWidget()
}

class PriceTickerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val fav = ep.prefs().settings.first().favoriteAssetId
        val quote = ep.market().quotes.value[fav]
        val symbol = quote?.symbol ?: "BTC/USD"
        val price = quote?.let { Formatters.compactUsd(it.price) } ?: "—"
        val delta = quote?.let { Formatters.percent(it.changePercent24h) } ?: ""
        provideContent {
            GlanceTheme { TickerContent(symbol, price, delta) }
        }
    }
}

@Composable
private fun TickerContent(symbol: String, price: String, delta: String) {
    Column(
        modifier = GlanceModifier
            .background(ColorProvider(Color(0xFF151A23), Color(0xFF151A23)))
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(symbol, style = TextStyle(color = ColorProvider(Color(0xFF8B9BB4), Color(0xFF8B9BB4)), fontSize = 11.sp))
        Text(price, style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold))
        Text(delta, style = TextStyle(color = ColorProvider(Color(0xFF00C087), Color(0xFF00C087)), fontSize = 12.sp))
    }
}

package com.cryptomacro.app.widget

/**
 * BEGINNER: Glance widgets are Compose-like but run on the home screen (limited API).
 * If privacy or biometric is on we render "Hidden" — widgets cannot use FLAG_SECURE.
 */
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

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PortfolioWidget()
}

class PortfolioWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val settings = ep.prefs().settings.first()
        val locked = settings.privacyShield || settings.biometricLock
        val value: String
        val pnl: String
        val movers: String
        if (locked) {
            value = "Hidden"
            pnl = "Unlock in app"
            movers = "Privacy shield is on"
        } else {
            val fx = runCatching { ep.market().usdToEur() }.getOrDefault(0.92 to "fx")
            val summary = ep.portfolio().summary(settings.fiat, fx.first, fx.second).first()
            value = Formatters.fiat(summary.totalValueUsd, summary.currency, summary.usdToEur)
            pnl = Formatters.percent(summary.totalPnl24hPct)
            movers = summary.rows.sortedByDescending { kotlin.math.abs(it.changePercent24h) }.take(3)
                .joinToString("  ") { "${it.asset.symbol} ${Formatters.percent(it.changePercent24h)}" }
                .ifBlank { "Add holdings in the app" }
        }
        provideContent {
            GlanceTheme { PortfolioContent(value, pnl, movers, locked) }
        }
    }
}

@Composable
private fun PortfolioContent(value: String, pnl: String, movers: String, locked: Boolean) {
    Column(
        modifier = GlanceModifier.background(ColorProvider(Color(0xFF151A23), Color(0xFF151A23))).padding(16.dp),
    ) {
        Text("Portfolio", style = TextStyle(color = ColorProvider(Color(0xFF8B9BB4), Color(0xFF8B9BB4)), fontSize = 11.sp))
        Text(value, style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 22.sp, fontWeight = FontWeight.Bold))
        Text(if (locked) pnl else "24h $pnl", style = TextStyle(color = ColorProvider(Color(0xFF00C087), Color(0xFF00C087)), fontSize = 13.sp))
        Text(movers, style = TextStyle(color = ColorProvider(Color(0xFFE8EEF8), Color(0xFFE8EEF8)), fontSize = 12.sp))
    }
}

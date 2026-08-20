package com.cryptomacro.app.domain.util

/**
 * BEGINNER: All money/percent text goes through here so the UI stays consistent.
 * compactUsd: $1.2K / $3.4M / $1.1T. parseDecimal: "1,5" and "1.5" both become 1.5.
 */
import com.cryptomacro.app.domain.model.AssetUnit
import com.cryptomacro.app.domain.model.FiatCurrency
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object Formatters {
    private val usd = NumberFormat.getCurrencyInstance(Locale.US)
    private val pct = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun price(value: Double, unit: AssetUnit = AssetUnit.USD, isPercent: Boolean = false): String {
        if (isPercent || unit == AssetUnit.PERCENT) return "${pct.format(value)}%"
        if (unit == AssetUnit.INDEX) {
            val n = kotlin.math.round(value).toInt()
            val label = when {
                n <= 24 -> "Extreme Fear"
                n <= 44 -> "Fear"
                n <= 55 -> "Neutral"
                n <= 74 -> "Greed"
                else -> "Extreme Greed"
            }
            return "$n · $label"
        }
        if (unit == AssetUnit.BTC) {
            return when {
                value < 0.0001 -> "%.8f".format(value)
                value < 0.01 -> "%.6f".format(value)
                else -> "%.5f".format(value)
            }
        }
        return compactUsd(value)
    }

    fun compactUsd(value: Double): String {
        val sign = if (value < 0) "-" else ""
        val v = abs(value)
        return when {
            v >= 1e12 -> "$sign$${pct.format(v / 1e12)}T"
            v >= 1e9 -> "$sign$${pct.format(v / 1e9)}B"
            v >= 1e6 -> "$sign$${pct.format(v / 1e6)}M"
            v >= 1000 -> "$sign${usd.format(v)}"
            v >= 1 -> "$sign$${pct.format(v)}"
            else -> "$sign$${"%.4f".format(v)}"
        }
    }

    fun parseDecimal(raw: String): Double? {
        val normalized = raw.trim().replace(" ", "").replace(',', '.')
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun percent(value: Double, signed: Boolean = true): String {
        val body = "${pct.format(abs(value))}%"
        if (!signed) return body
        return when {
            value > 0 -> "+$body"
            value < 0 -> "-$body"
            else -> body
        }
    }

    fun fiat(amountUsd: Double, currency: FiatCurrency, usdToEur: Double, signed: Boolean = false): String {
        val v = if (currency == FiatCurrency.EUR) amountUsd * usdToEur else amountUsd
        val absV = abs(v)
        val prefix = when {
            signed && v > 0 -> "+"
            v < 0 -> "−"
            else -> ""
        }
        val sym = if (currency == FiatCurrency.EUR) "€" else "$"
        val body = when {
            absV >= 1e9 -> "${pct.format(absV / 1e9)}B"
            absV >= 1e6 -> "${pct.format(absV / 1e6)}M"
            else -> NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(absV)
        }
        return "$prefix$sym$body"
    }

    fun fearGreedLabel(value: Int): String = when {
        value <= 24 -> "Extreme Fear"
        value <= 44 -> "Fear"
        value <= 55 -> "Neutral"
        value <= 74 -> "Greed"
        else -> "Extreme Greed"
    }
}

fun sma(closes: List<Pair<Long, Double>>, period: Int): List<Pair<Long, Double>> {
    if (closes.size < period) return emptyList()
    val out = ArrayList<Pair<Long, Double>>(closes.size - period + 1)
    var sum = 0.0
    for (i in closes.indices) {
        sum += closes[i].second
        if (i >= period) sum -= closes[i - period].second
        if (i >= period - 1) out += closes[i].first to (sum / period)
    }
    return out
}

fun ema(closes: List<Pair<Long, Double>>, period: Int): List<Pair<Long, Double>> {
    if (closes.size < period) return emptyList()
    val k = 2.0 / (period + 1)
    val out = ArrayList<Pair<Long, Double>>()
    var prev = closes.take(period).map { it.second }.average()
    out += closes[period - 1].first to prev
    for (i in period until closes.size) {
        prev = closes[i].second * k + prev * (1 - k)
        out += closes[i].first to prev
    }
    return out
}

fun rsi(closes: List<Double>, period: Int = 14): List<Double> {
    if (closes.size <= period) return emptyList()
    val out = MutableList(period) { Double.NaN }
    var gain = 0.0
    var loss = 0.0
    for (i in 1..period) {
        val d = closes[i] - closes[i - 1]
        if (d >= 0) gain += d else loss -= d
    }
    gain /= period
    loss /= period
    fun value(g: Double, l: Double) = if (l == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + g / l)
    out.add(value(gain, loss))
    for (i in period + 1 until closes.size) {
        val d = closes[i] - closes[i - 1]
        val g = if (d > 0) d else 0.0
        val l = if (d < 0) -d else 0.0
        gain = (gain * (period - 1) + g) / period
        loss = (loss * (period - 1) + l) / period
        out.add(value(gain, loss))
    }
    return out
}

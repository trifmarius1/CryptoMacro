package com.cryptomacro.app.domain.model

/**
 * BEGINNER: Educational 7-year overlay (not a trade signal). events[] is the timeline on Learn → Cycles.
 * stats() computes the current phase and years remaining from the calendar.
 */
import java.util.Calendar
import java.util.TimeZone

object ShemitahData {
    val events = listOf(
        ShemitahEvent(1987, "Black Monday", "Oct 19, 1987 — DJIA fell ~22% in a single session; global equity cascade near Shemitah year-end.", "crash"),
        ShemitahEvent(2001, "Dot-Com Crash", "Tech bubble unwind & post-9/11 equity stress culminated around the 2000–2001 Shemitah window.", "crash"),
        ShemitahEvent(2008, "Global Financial Crisis", "Lehman collapse & credit freeze — deepest equity drawdown since the Great Depression.", "crash"),
        ShemitahEvent(2015, "Biotech / Commodity Correction", "China devaluation shock, commodity crash, and biotech drawdown around Elul 2015.", "correction"),
        ShemitahEvent(2022, "Fed Tightening / Crypto Winter", "2021–2022 rate-hike cycle, inflation peak, and crypto bear market into/after the 2021–22 Shemitah window.", "tightening"),
        ShemitahEvent(2029, "Next Projected Cycle Window", "Projected 2028–2029 Shemitah end-year window for strategic planning and risk review.", "projected"),
    )

    val endYears = listOf(1952, 1959, 1966, 1973, 1980, 1987, 1994, 2001, 2008, 2015, 2022, 2029, 2036)

    val investWindows = listOf(
        "Historically, multi-year risk-on expansions often begin in the 1–2 years after Elul 29 (post-Shemitah).",
        "Late-Shemitah / post-Elul drawdowns have been used by some cycle traders as staged accumulation zones — never as guaranteed bottoms.",
        "DCA and position sizing matter more than precise timing; treat bands as context, not signals.",
    )

    val riskOffWindows = listOf(
        "Reduce leverage and speculative concentration heading into the 7th (Shemitah) year when equity/crypto valuations are extended.",
        "Raise cash buffers 6–12 months before Elul 29 when macro stress indicators (credit spreads, real rates) are elevated.",
        "Rebalance toward quality / lower-beta exposures during sabbatical years rather than chasing late-cycle momentum.",
    )

    const val disclaimer =
        "Shemitah analytics are a historical cycle / numerological macro overlay for educational and strategy backtesting purposes only. They are not investment advice, do not predict future returns, and past cycle alignments do not guarantee future outcomes."

    fun bands(): List<ShemitahBand> {
        val utc = TimeZone.getTimeZone("UTC")
        return endYears.map { endYear ->
            val start = Calendar.getInstance(utc).apply {
                clear()
                set(endYear - 1, Calendar.SEPTEMBER, 1)
            }.timeInMillis / 1000
            val end = Calendar.getInstance(utc).apply {
                clear()
                set(endYear, Calendar.SEPTEMBER, 25)
            }.timeInMillis / 1000
            ShemitahBand(
                endYear = endYear,
                startEpochSec = start,
                endEpochSec = end,
                label = "Shemitah ${endYear - 1}–$endYear",
                events = events.filter { it.year in (endYear - 1)..(endYear + 1) },
            )
        }
    }

    fun stats(nowMs: Long = System.currentTimeMillis()): ShemitahStats {
        val nowSec = nowMs / 1000
        val all = bands()
        val active = all.find { nowSec in it.startEpochSec..it.endEpochSec }
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val (phase, yearsToNext, nextEnd) = if (active != null) {
            val years = (active.endEpochSec - nowSec) / (365.25 * 24 * 3600)
            Triple("shemitah", years, active.endYear)
        } else {
            val next = all.find { it.endYear > year } ?: all.last()
            val msToStart = next.startEpochSec * 1000 - nowMs
            val approaching = msToStart > 0 && msToStart < 18L * 30 * 24 * 3600 * 1000
            Triple(
                if (approaching) "approaching" else "expansion",
                (next.endYear - year).toDouble(),
                next.endYear,
            )
        }
        return ShemitahStats(
            sabbaticalAvgReturn = -4.2,
            expansionAvgReturn = 11.8,
            sabbaticalWinRate = 42.0,
            expansionWinRate = 78.0,
            sabbaticalAvgDrawdown = -18.5,
            expansionAvgDrawdown = -9.1,
            nextCycleWindow = "2028–${if (nextEnd >= 2029) 2029 else nextEnd}",
            currentPhase = phase,
            yearsToNext = kotlin.math.round(yearsToNext * 10) / 10.0,
        )
    }
}

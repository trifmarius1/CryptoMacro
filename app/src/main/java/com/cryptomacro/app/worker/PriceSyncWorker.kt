package com.cryptomacro.app.worker

/**
 * BEGINNER: WorkManager runs this every 15 minutes even if the UI is closed (best-effort).
 * doWork() refreshes quotes then asks both Glance widgets to redraw.
 */
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cryptomacro.app.data.repository.MarketRepository
import com.cryptomacro.app.widget.PortfolioWidget
import com.cryptomacro.app.widget.PriceTickerWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class PriceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val market: MarketRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            market.refreshQuotes()
            PriceTickerWidget().updateAll(applicationContext)
            PortfolioWidget().updateAll(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<PriceSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "price-sync",
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}

package com.cryptomacro.app.tile

/** BEGINNER: Quick Settings tile (shade). onStartListening loads BTC/ETH; onStopListening cancels the coroutine so we do not leak work. */
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cryptomacro.app.di.WidgetEntryPoint
import com.cryptomacro.app.domain.util.Formatters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PriceTileService : TileService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob = scope.launch {
            val ep = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
            runCatching { ep.market().refreshQuotes() }
            val btc = ep.market().quotes.value["btc-usd"]
            val eth = ep.market().quotes.value["eth-usd"]
            qsTile?.apply {
                state = Tile.STATE_ACTIVE
                label = "BTC ${btc?.let { Formatters.compactUsd(it.price) } ?: "—"}"
                subtitle = "ETH ${eth?.let { Formatters.compactUsd(it.price) } ?: "—"}"
                updateTile()
            }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        super.onStopListening()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}

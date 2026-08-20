package com.cryptomacro.app.tile

/** BEGINNER: Tapping the tile flips privacyShield in DataStore, which MainActivity maps to FLAG_SECURE. */
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cryptomacro.app.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrivacyTileService : TileService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob = scope.launch { render() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val ep = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
            val current = ep.prefs().settings.first().privacyShield
            ep.prefs().setPrivacyShield(!current)
            render()
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

    private suspend fun render() {
        val ep = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
        val on = ep.prefs().settings.first().privacyShield
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (on) "Privacy on" else "Privacy off"
            subtitle = "Screen capture shield"
            updateTile()
        }
    }
}

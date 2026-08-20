package com.cryptomacro.app

/**
 * BEGINNER: This file is the "Application" class — Android creates ONE of these when the
 * app process starts, before any screen (Activity) is shown.
 *
 * Think of it as the building, and MainActivity as a room inside the building.
 *
 * @HiltAndroidApp tells the Hilt library: "scan this app and wire up all @Inject constructors."
 * Without this annotation, Hilt would not start and the app would crash on launch.
 */
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cryptomacro.app.worker.PriceSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CryptoMacroApp : Application(), Configuration.Provider {
    /**
     * Hilt fills this in at runtime. WorkManager (background jobs) needs a special factory
     * so our PriceSyncWorker can receive MarketRepository through @AssistedInject.
     */
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager asks the Application for its configuration. We point it at Hilt's factory
     * instead of the default one (the default cannot inject our repositories).
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /**
     * onCreate() runs once per process. super.onCreate() is required so Android finishes setup.
     * Then we schedule the 15-minute widget price refresh (it is unique, so we won't duplicate it).
     */
    override fun onCreate() {
        super.onCreate()
        PriceSyncWorker.enqueue(this)
    }
}

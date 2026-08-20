package com.cryptomacro.app.di

/**
 * BEGINNER: Home-screen widgets and Quick Settings tiles are created by Android, not by Hilt.
 * They cannot use constructor @Inject. An @EntryPoint is a "door" into the Hilt graph:
 * from Application we ask for MarketRepository / prefs / portfolio and then call them.
 */
import com.cryptomacro.app.data.local.PreferencesRepository
import com.cryptomacro.app.data.repository.MarketRepository
import com.cryptomacro.app.data.repository.PortfolioRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun market(): MarketRepository
    fun prefs(): PreferencesRepository
    fun portfolio(): PortfolioRepository
}

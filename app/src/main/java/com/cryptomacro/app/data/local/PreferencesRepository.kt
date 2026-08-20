package com.cryptomacro.app.data.local

/**
 * BEGINNER: DataStore is the modern replacement for SharedPreferences for *settings*.
 * `context.dataStore.data` is a Flow: every time a value changes, collectors (the UI) redraw.
 *
 * AppSettings is a snapshot of all toggles. Each setX() function writes one key.
 * pinnedChartIds is a comma-separated list of extra Markets chips the user added.
 */
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cryptomacro.app.domain.model.FiatCurrency
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("cryptomacro_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val biometricLock: Boolean = false,
    val privacyShield: Boolean = true,
    val fiat: FiatCurrency = FiatCurrency.USD,
    val favoriteAssetId: String = "btc-usd",
    val showSma: Boolean = true,
    val showEma: Boolean = false,
    val showVolume: Boolean = true,
    val shemitahOverlay: Boolean = false,
    val pinnedChartIds: List<String> = emptyList(),
)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val biometric = booleanPreferencesKey("biometric")
        val privacy = booleanPreferencesKey("privacy")
        val fiat = stringPreferencesKey("fiat")
        val favorite = stringPreferencesKey("favorite")
        val sma = booleanPreferencesKey("sma")
        val ema = booleanPreferencesKey("ema")
        val volume = booleanPreferencesKey("volume")
        val shemitah = booleanPreferencesKey("shemitah")
        val pinned = stringPreferencesKey("pinned_charts")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            biometricLock = p[Keys.biometric] ?: false,
            privacyShield = p[Keys.privacy] ?: true,
            fiat = if (p[Keys.fiat] == "EUR") FiatCurrency.EUR else FiatCurrency.USD,
            favoriteAssetId = p[Keys.favorite] ?: "btc-usd",
            showSma = p[Keys.sma] ?: true,
            showEma = p[Keys.ema] ?: false,
            showVolume = p[Keys.volume] ?: true,
            shemitahOverlay = p[Keys.shemitah] ?: false,
            pinnedChartIds = (p[Keys.pinned] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setBiometric(enabled: Boolean) = context.dataStore.edit { it[Keys.biometric] = enabled }
    suspend fun setPrivacyShield(enabled: Boolean) = context.dataStore.edit { it[Keys.privacy] = enabled }
    suspend fun setFiat(currency: FiatCurrency) = context.dataStore.edit { it[Keys.fiat] = currency.name }
    suspend fun setFavorite(id: String) = context.dataStore.edit { it[Keys.favorite] = id }
    suspend fun setSma(v: Boolean) = context.dataStore.edit { it[Keys.sma] = v }
    suspend fun setEma(v: Boolean) = context.dataStore.edit { it[Keys.ema] = v }
    suspend fun setVolume(v: Boolean) = context.dataStore.edit { it[Keys.volume] = v }
    suspend fun setShemitah(v: Boolean) = context.dataStore.edit { it[Keys.shemitah] = v }
    suspend fun setPinnedCharts(ids: List<String>) = context.dataStore.edit {
        it[Keys.pinned] = ids.distinct().joinToString(",")
    }
}

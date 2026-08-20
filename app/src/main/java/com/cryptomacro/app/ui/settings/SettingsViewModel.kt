package com.cryptomacro.app.ui.settings

/** BEGINNER: Thin wrappers around PreferencesRepository + SecureStore. saveKey() encrypts locally; testBinance() is debug-only HMAC check. */
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptomacro.app.data.local.PreferencesRepository
import com.cryptomacro.app.data.local.SecureStore
import com.cryptomacro.app.data.local.ThemeMode
import com.cryptomacro.app.data.repository.MarketRepository
import com.cryptomacro.app.domain.model.ExchangeKey
import com.cryptomacro.app.domain.model.FiatCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val secure: SecureStore,
    val market: MarketRepository,
    private val http: OkHttpClient,
) : ViewModel() {
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.cryptomacro.app.data.local.AppSettings())
    val exchangeStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val keyDrafts = MutableStateFlow(mapOf("binance" to "", "coinbase" to "", "kraken" to ""))
    val secretDrafts = MutableStateFlow(mapOf("binance" to "", "coinbase" to "", "kraken" to ""))
    val unlocked = MutableStateFlow(false)

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setTheme(mode) }
    fun setBiometric(v: Boolean) = viewModelScope.launch { prefs.setBiometric(v) }
    fun setPrivacy(v: Boolean) = viewModelScope.launch { prefs.setPrivacyShield(v) }
    fun setFiat(c: FiatCurrency) = viewModelScope.launch { prefs.setFiat(c) }
    fun setFavorite(id: String) = viewModelScope.launch { prefs.setFavorite(id) }
    fun setSma(v: Boolean) = viewModelScope.launch { prefs.setSma(v) }
    fun setEma(v: Boolean) = viewModelScope.launch { prefs.setEma(v) }
    fun setVolume(v: Boolean) = viewModelScope.launch { prefs.setVolume(v) }
    fun setShemitah(v: Boolean) = viewModelScope.launch { prefs.setShemitah(v) }
    fun markUnlocked() { unlocked.value = true }

    fun saveKey(exchange: String) = viewModelScope.launch {
        val key = keyDrafts.value[exchange].orEmpty()
        val secret = secretDrafts.value[exchange].orEmpty()
        if (key.isBlank() || secret.isBlank()) return@launch
        secure.saveExchangeKey(ExchangeKey(exchange, key, secret, lastStatus = "Saved locally"))
        exchangeStatus.value = exchangeStatus.value + (exchange to "Saved (encrypted on device)")
        if (exchange == "binance") testBinance(key, secret)
    }

    fun deleteKey(exchange: String) {
        secure.deleteExchangeKey(exchange)
        exchangeStatus.value = exchangeStatus.value + (exchange to "Removed")
    }

    private fun testBinance(apiKey: String, secret: String) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val ts = System.currentTimeMillis()
                val query = "timestamp=$ts"
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
                val sig = mac.doFinal(query.toByteArray()).joinToString("") { "%02x".format(it) }
                val req = Request.Builder()
                    .url("https://api.binance.com/api/v3/account?$query&signature=$sig")
                    .header("X-MBX-APIKEY", apiKey)
                    .build()
                http.newCall(req).execute().use { res ->
                    if (res.isSuccessful) "Read-only account OK"
                    else "HTTP ${res.code} — use a withdraw-disabled key"
                }
            }.getOrElse { "Could not reach Binance: ${it.message}" }
        }
        secure.getExchangeKey("binance")?.let {
            secure.saveExchangeKey(it.copy(lastSyncAt = System.currentTimeMillis(), lastStatus = result))
        }
        exchangeStatus.value = exchangeStatus.value + ("binance" to result)
    }
}

package com.cryptomacro.app.data.local

/**
 * BEGINNER: This is a tiny encrypted notebook for exchange API keys (debug builds only).
 *
 * Android Keystore creates a hardware-backed AES key. EncryptedSharedPreferences uses it
 * so even if someone copies the prefs file off the phone, the values are ciphertext.
 * If Keystore fails we store null and refuse to save keys — we never fall back to plaintext.
 */
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.cryptomacro.app.domain.model.ExchangeKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Encrypted AES-256-GCM store only. Never fall back to plaintext prefs. */
    private val prefs: SharedPreferences? = runCatching {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "cryptomacro_secure",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    fun saveExchangeKey(key: ExchangeKey) {
        prefs?.edit()?.putString("ex_${key.exchange}", json.encodeToString(key))?.apply()
    }

    fun getExchangeKey(exchange: String): ExchangeKey? {
        val raw = prefs?.getString("ex_$exchange", null) ?: return null
        return runCatching { json.decodeFromString<ExchangeKey>(raw) }.getOrNull()
    }

    fun allExchangeKeys(): List<ExchangeKey> =
        listOf("binance", "coinbase", "kraken").mapNotNull { getExchangeKey(it) }

    fun deleteExchangeKey(exchange: String) {
        prefs?.edit()?.remove("ex_$exchange")?.apply()
    }
}

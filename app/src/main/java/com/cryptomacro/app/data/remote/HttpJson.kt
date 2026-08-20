package com.cryptomacro.app.data.remote

/**
 * BEGINNER: A thin HTTPS JSON downloader.
 *
 * get(url)     — one GET, must be https, Yahoo gets a browser User-Agent (their API rejects bots).
 * getCached    — remember the body for ttlMs so we do not hammer CoinGecko.
 * inflight     — if two callers ask for the same URL at once, they share one network call.
 * parse        — getCached + kotlinx.serialization JSON tree.
 * postJson     — HTTPS POST (used only for optional ETH RPC).
 */
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpJson @Inject constructor(
    private val client: OkHttpClient,
) {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val mem = ConcurrentHashMap<String, Pair<Long, String>>()
    private val inflight = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<String>>()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        assertHttps(url)
        val ua = if (url.contains("yahoo.com", ignoreCase = true) || url.contains("finance.yahoo", ignoreCase = true)) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        } else {
            "CryptoMacro/1.0 (Android)"
        }
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", ua)
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            res.body?.string() ?: error("Empty body")
        }
    }

    suspend fun postJson(url: String, jsonBody: String): String = withContext(Dispatchers.IO) {
        assertHttps(url)
        val media = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "CryptoMacro/1.0 (Android)")
            .post(jsonBody.toRequestBody(media))
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            res.body?.string() ?: error("Empty body")
        }
    }

    suspend fun getCached(url: String, ttlMs: Long = 90_000): String {
        mem[url]?.let { (at, body) -> if (System.currentTimeMillis() - at < ttlMs) return body }
        inflight[url]?.let { return it.await() }
        val deferred = kotlinx.coroutines.CompletableDeferred<String>()
        inflight[url] = deferred
        return try {
            val body = get(url)
            mem[url] = System.currentTimeMillis() to body
            deferred.complete(body)
            body
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            inflight.remove(url)
        }
    }

    suspend fun parse(url: String, ttlMs: Long = 90_000): JsonElement =
        json.parseToJsonElement(getCached(url, ttlMs))

    companion object {
        fun assertHttps(url: String) {
            check(url.startsWith("https://", ignoreCase = true)) { "Blocked non-HTTPS URL" }
        }
    }
}

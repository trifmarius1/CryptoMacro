package com.cryptomacro.app.data.remote

/**
 * BEGINNER: A WebSocket is a long-lived connection. The server *pushes* prices instead of us polling.
 *
 * ticks(symbols) returns a Flow: first value is (connected?, tick).
 * IMPORTANT: OkHttp only accepts https:// URLs. We use https://stream.binance.com:9443/...
 * and OkHttp upgrades it to WSS. Writing wss:// here would crash Request.Builder.
 *
 * Symbols are uppercased and must match ^[A-Z0-9]{4,20}$ so we cannot inject extra path segments.
 * On failure we wait 1s, 2s, 4s... up to 30s and reconnect until the Flow is cancelled.
 */
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

data class LiveTick(val binanceSymbol: String, val price: Double, val timeSec: Long)

@Singleton
class BinanceWebSocket @Inject constructor(
    private val client: OkHttpClient,
    private val http: HttpJson,
) {
    fun ticks(symbols: List<String>): Flow<Pair<Boolean, LiveTick?>> = callbackFlow {
        val sanitized = symbols
            .map { it.uppercase() }
            .filter { BINANCE_SYMBOL.matches(it) }
            .distinct()
            .take(200)
        if (sanitized.isEmpty()) {
            trySend(false to null)
            awaitClose { }
            return@callbackFlow
        }
        var socket: WebSocket? = null
        var closed = false
        var retry = 0
        val scope = this

        fun connect() {
            if (closed) return
            val streams = sanitized.joinToString("/") { "${it.lowercase()}@miniTicker" }
            // OkHttp WebSocket requires https:// (upgrades to wss). wss:// is rejected by HttpUrl.
            val req = Request.Builder()
                .url("https://stream.binance.com:9443/stream?streams=$streams")
                .header("User-Agent", "CryptoMacro/1.0 (Android)")
                .build()
            socket = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    retry = 0
                    trySend(true to null)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching {
                        val data = http.json.parseToJsonElement(text).jsonObject["data"]?.jsonObject ?: return
                        val s = data["s"]?.jsonPrimitive?.content?.uppercase() ?: return
                        if (!BINANCE_SYMBOL.matches(s)) return
                        val c = data["c"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return
                        if (c <= 0 || !c.isFinite()) return
                        val t = data["E"]?.jsonPrimitive?.content?.toLongOrNull()?.div(1000)
                            ?: System.currentTimeMillis() / 1000
                        trySend(true to LiveTick(s, c, t))
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    trySend(false to null)
                    if (!closed) {
                        val wait = minOf(30_000L, 1000L * (1 shl retry.coerceAtMost(5)))
                        retry++
                        scope.launch { delay(wait); connect() }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    trySend(false to null)
                    if (!closed) {
                        val wait = minOf(30_000L, 1000L * (1 shl retry.coerceAtMost(5)))
                        retry++
                        scope.launch { delay(wait); connect() }
                    }
                }
            })
        }

        connect()
        awaitClose {
            closed = true
            socket?.close(1000, "bye")
        }
    }

    companion object {
        private val BINANCE_SYMBOL = Regex("^[A-Z0-9]{4,20}$")
    }
}

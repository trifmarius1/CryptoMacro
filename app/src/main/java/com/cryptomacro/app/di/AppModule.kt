package com.cryptomacro.app.di

/**
 * BEGINNER: "DI" means Dependency Injection. Instead of every class doing `OkHttpClient()`,
 * Hilt calls these @Provides functions once and hands the same object to whoever needs it.
 *
 * @Module + @InstallIn(SingletonComponent) = "these providers live for the whole app process."
 * @Singleton on a function = "create this object only once and reuse it."
 */
import android.content.Context
import androidx.room.Room
import com.cryptomacro.app.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Builds the HTTP client used for REST and WebSocket.
     * Timeouts stop the UI from waiting forever if the network is dead.
     * followRedirects(false) blocks http→https tricks that could leak a request.
     * The interceptor is a gate: if the URL is not https, the app throws instead of sending it.
     */
    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)   // give up connecting after 8s
        .readTimeout(12, TimeUnit.SECONDS)     // give up waiting for a body after 12s
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)    // keep WebSockets alive
        .followRedirects(false)                // never follow http redirects
        .followSslRedirects(false)
        .addInterceptor { chain ->
            val scheme = chain.request().url.scheme.lowercase()
            check(scheme == "https") { "Blocked non-HTTPS request" }
            chain.proceed(chain.request())     // actually send the request
        }
        .build()

    /**
     * Room is SQLite with Kotlin types. The file is cryptomacro.db in the app's private folder.
     * fallbackToDestructiveMigration() means: if we bump the schema version without a migration,
     * wipe the DB rather than crash. We avoid bumping version so holdings are not wiped.
     */
    @Provides
    @Singleton
    fun db(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "cryptomacro.db")
            .fallbackToDestructiveMigration()
            .build()
}

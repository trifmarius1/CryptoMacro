package com.cryptomacro.app.data.local

/**
 * BEGINNER: Room turns this abstract class into a real SQLite database at compile time.
 *
 * entities = the tables. Each Entity class becomes a table.
 * version = 1. If we raise this without a Migration, fallbackToDestructiveMigration wipes data.
 * exportSchema = false means we do not write a JSON schema file into the repo.
 *
 * Each abstract fun xxx(): XxxDao is how the rest of the app reads/writes that table.
 */
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CandleCacheEntity::class,
        QuoteCacheEntity::class,
        CustomAssetEntity::class,
        HoldingEntity::class,
        TransactionEntity::class,
        WatchAddressEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun candles(): CandleDao
    abstract fun quotes(): QuoteDao
    abstract fun customAssets(): CustomAssetDao
    abstract fun holdings(): HoldingDao
    abstract fun transactions(): TransactionDao
    abstract fun watchAddresses(): WatchAddressDao
}

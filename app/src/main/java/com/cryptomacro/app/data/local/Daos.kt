package com.cryptomacro.app.data.local

/**
 * BEGINNER: A DAO (Data Access Object) is the API for one table.
 * @Query runs SQL. @Insert(onConflict = REPLACE) means "insert, or overwrite if the id exists."
 * Functions marked suspend run off the main thread when called from a coroutine.
 * Functions that return Flow<List<...>> automatically emit again when the table changes.
 */
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CandleDao {
    @Query("SELECT * FROM candle_cache WHERE assetId = :assetId AND timeframe = :tf")
    suspend fun get(assetId: String, tf: String): CandleCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CandleCacheEntity)
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quote_cache")
    fun observeAll(): Flow<List<QuoteCacheEntity>>

    @Query("SELECT * FROM quote_cache WHERE assetId = :id")
    suspend fun get(id: String): QuoteCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: QuoteCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<QuoteCacheEntity>)
}

@Dao
interface CustomAssetDao {
    @Query("SELECT * FROM custom_assets ORDER BY addedAt ASC")
    fun observe(): Flow<List<CustomAssetEntity>>

    @Query("SELECT * FROM custom_assets")
    suspend fun all(): List<CustomAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CustomAssetEntity)

    @Query("DELETE FROM custom_assets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings ORDER BY addedAt ASC")
    fun observe(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings")
    suspend fun all(): List<HoldingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: HoldingEntity)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM holdings")
    suspend fun clear()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observe(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    suspend fun all(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface WatchAddressDao {
    @Query("SELECT * FROM watch_addresses ORDER BY addedAt ASC")
    fun observe(): Flow<List<WatchAddressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: WatchAddressEntity)

    @Query("DELETE FROM watch_addresses WHERE id = :id")
    suspend fun delete(id: String)
}

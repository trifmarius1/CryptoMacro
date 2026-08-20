package com.cryptomacro.app.data.repository

/**
 * BEGINNER: The phonebook of assets. CoreAssets is the built-in list (BTC, ETH, SPX, …).
 * ensureTracked() copies a CoinGecko/Yahoo coin into the custom_assets table so pins and
 * portfolio rows still have a name after a restart (in-memory extraAssets would be empty).
 */
import com.cryptomacro.app.data.local.AppDatabase
import com.cryptomacro.app.data.local.CustomAssetEntity
import com.cryptomacro.app.domain.model.AssetCategory
import com.cryptomacro.app.domain.model.AssetDefinition
import com.cryptomacro.app.domain.model.AssetUnit
import com.cryptomacro.app.domain.model.BinanceUsdtCoin
import com.cryptomacro.app.domain.model.CoinTag
import com.cryptomacro.app.domain.model.CoreAssets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRegistry @Inject constructor(
    db: AppDatabase,
) {
    private val dao = db.customAssets()
    private val customCache = MutableStateFlow<List<AssetDefinition>>(emptyList())

    val assets: Flow<List<AssetDefinition>> = dao.observe().map { rows ->
        val custom = rows.map { it.toAsset() }
        customCache.value = custom
        merge(custom)
    }

    suspend fun current(): List<AssetDefinition> {
        val custom = dao.all().map { it.toAsset() }
        customCache.value = custom
        return merge(custom)
    }

    fun snapshot(): List<AssetDefinition> = merge(customCache.value)

    fun byId(id: String): AssetDefinition? = snapshot().find { it.id == id } ?: CoreAssets.byId(id)

    suspend fun addCustom(coin: BinanceUsdtCoin) {
        val id = "crypto-${coin.baseAsset.lowercase()}-usd"
        if (CoreAssets.byId(id) != null) return
        dao.upsert(
            CustomAssetEntity(
                id = id,
                symbol = "${coin.baseAsset}/USD",
                name = coin.name,
                binanceSymbol = coin.binanceSymbol,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Persist a market coin so portfolio holdings survive restarts. */
    suspend fun ensureTracked(asset: AssetDefinition) {
        if (CoreAssets.byId(asset.id) != null) return
        dao.upsert(
            CustomAssetEntity(
                id = asset.id,
                symbol = asset.symbol,
                name = asset.name,
                binanceSymbol = asset.binanceSymbol.orEmpty(),
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeCustom(id: String) = dao.delete(id)

    private fun merge(custom: List<AssetDefinition>): List<AssetDefinition> {
        val coreIds = CoreAssets.all.map { it.id }.toSet()
        return CoreAssets.all + custom.filter { it.id !in coreIds }
    }

    private fun CustomAssetEntity.toAsset(): AssetDefinition {
        val stock = id.startsWith("stock-")
        return AssetDefinition(
            id = id,
            symbol = symbol,
            name = name,
            category = if (stock) AssetCategory.EQUITY else AssetCategory.CRYPTO,
            binanceSymbol = binanceSymbol.takeIf { it.isNotBlank() && !stock },
            coingeckoId = id.removePrefix("cg-").takeIf { id.startsWith("cg-") },
            yahooSymbol = if (stock) symbol else null,
            unit = AssetUnit.USD,
            tag = if (stock) CoinTag.BENCHMARK else CoinTag.OTHER,
            custom = true,
            description = "User-added · ${if (stock) symbol else binanceSymbol.ifBlank { name }}",
        )
    }
}

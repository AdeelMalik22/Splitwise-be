package com.splitwise.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OverviewSnapshot(
    val userId: Int,
    val profile: Profile? = null,
    val groups: List<Group> = emptyList(),
    val balances: Map<Int, Settlements> = emptyMap(),
    val members: Map<Int, List<Member>> = emptyMap(),
    val invites: List<Invite> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val expenses: List<Expense> = emptyList(),
)

@Entity(tableName = "overview_cache")
data class OverviewCacheEntity(
    @PrimaryKey val userId: Int,
    val payload: String,
    val updatedAt: Long,
)

@Dao
interface OverviewCacheDao {
    @Query("SELECT * FROM overview_cache WHERE userId = :userId")
    suspend fun get(userId: Int): OverviewCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: OverviewCacheEntity)

    @Query("DELETE FROM overview_cache WHERE userId = :userId")
    suspend fun delete(userId: Int)

    @Query("DELETE FROM overview_cache")
    suspend fun clear()
}

@Database(entities = [OverviewCacheEntity::class, CachedDataEntity::class, PendingWriteEntity::class], version = 3, exportSchema = false)
abstract class SplitEaseDatabase : RoomDatabase() {
    abstract fun overviewCache(): OverviewCacheDao
    abstract fun cachedData(): CachedDataDao
    abstract fun pendingWrites(): PendingWriteDao
}

class OverviewCache(private val dao: OverviewCacheDao) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun get(userId: Int): OverviewSnapshot? = dao.get(userId)?.let {
        runCatching { json.decodeFromString<OverviewSnapshot>(it.payload) }.getOrNull()
    }

    suspend fun put(snapshot: OverviewSnapshot) {
        dao.put(OverviewCacheEntity(snapshot.userId, json.encodeToString(snapshot), System.currentTimeMillis()))
    }

    suspend fun delete(userId: Int) = dao.delete(userId)
    suspend fun clear() = dao.clear()
}

/** User-scoped records used by repository reads. Values stay JSON so API models remain independent of Room. */
@Entity(tableName = "cached_data", primaryKeys = ["userId", "kind", "key"])
data class CachedDataEntity(
    val userId: Int,
    val kind: String,
    val key: String,
    val payload: String,
    val updatedAt: Long,
)

@Dao
interface CachedDataDao {
    @Query("SELECT * FROM cached_data WHERE userId = :userId AND kind = :kind AND key = :key")
    suspend fun get(userId: Int, kind: String, key: String): CachedDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: CachedDataEntity)

    @Query("DELETE FROM cached_data WHERE userId = :userId")
    suspend fun deleteUser(userId: Int)

    @Query("DELETE FROM cached_data WHERE userId = :userId AND kind = :kind AND key = :key")
    suspend fun delete(userId: Int, kind: String, key: String)

    @Query("DELETE FROM cached_data")
    suspend fun clear()
}

class DataCache(@PublishedApi internal val dao: CachedDataDao) {
    @PublishedApi
    internal val cacheJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend inline fun <reified T> get(userId: Int, kind: String, key: String = ""): T? =
        dao.get(userId, kind, key)?.let { runCatching { cacheJson.decodeFromString<T>(it.payload) }.getOrNull() }

    suspend inline fun <reified T> put(userId: Int, kind: String, value: T, key: String = "") {
        dao.put(CachedDataEntity(userId, kind, key, cacheJson.encodeToString(value), System.currentTimeMillis()))
    }

    suspend fun deleteUser(userId: Int) = dao.deleteUser(userId)
    suspend fun delete(userId: Int, kind: String, key: String = "") = dao.delete(userId, kind, key)
    suspend fun clear() = dao.clear()
}

@Entity(tableName = "pending_writes")
data class PendingWriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int,
    val kind: String,
    val payload: String,
    val createdAt: Long,
)

@Dao
interface PendingWriteDao {
    @Query("SELECT * FROM pending_writes WHERE userId = :userId ORDER BY id")
    suspend fun list(userId: Int): List<PendingWriteEntity>
    @Insert suspend fun add(value: PendingWriteEntity): Long
    @Query("DELETE FROM pending_writes WHERE id = :id") suspend fun remove(id: Long)
    @Query("DELETE FROM pending_writes WHERE userId = :userId") suspend fun deleteUser(userId: Int)
}

class OfflineWriteQueue(@PublishedApi internal val dao: PendingWriteDao) {
    @PublishedApi internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    suspend inline fun <reified T> add(userId: Int, kind: String, value: T) {
        dao.add(PendingWriteEntity(userId = userId, kind = kind, payload = json.encodeToString(value), createdAt = System.currentTimeMillis()))
    }
    suspend fun list(userId: Int) = dao.list(userId)
    suspend fun remove(id: Long) = dao.remove(id)
    suspend fun deleteUser(userId: Int) = dao.deleteUser(userId)
}

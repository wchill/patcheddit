package app.morphe.extension.boostforreddit.http.cache

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "cache_entries", primaryKeys = ["cache_name", "cache_key"])
data class CacheEntry(
    @ColumnInfo(name = "cache_name")
    val cacheName: String,

    @ColumnInfo(name = "cache_key")
    val key: String,

    @ColumnInfo(name = "cache_value")
    val value: String,

    @ColumnInfo(name = "last_accessed")
    val lastAccessed: Long
)


/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.http.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {
    @Query("SELECT cache_value FROM cache_entries WHERE cache_name = :cacheName AND cache_key = :key LIMIT 1")
    fun get(cacheName: String, key: String): String?

    @Query("UPDATE cache_entries SET last_accessed = :lastAccessed WHERE cache_name = :cacheName AND cache_key = :key")
    fun updateLastAccessed(cacheName: String, key: String, lastAccessed: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entry: CacheEntry)

    @Query("SELECT COUNT(*) FROM cache_entries WHERE cache_name = :cacheName")
    fun getCount(cacheName: String): Int

    @Query(
        "DELETE FROM cache_entries WHERE cache_name = :cacheName AND cache_key IN " +
                "(SELECT cache_key FROM cache_entries WHERE cache_name = :cacheName ORDER BY last_accessed ASC LIMIT :count)"
    )
    fun evictOldest(cacheName: String, count: Int)
}


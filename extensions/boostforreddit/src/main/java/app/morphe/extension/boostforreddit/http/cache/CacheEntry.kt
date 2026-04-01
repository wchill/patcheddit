/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

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


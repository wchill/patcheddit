/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.http.cache

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.morphe.extension.shared.Utils

@Database(entities = [CacheEntry::class], version = 1, exportSchema = false)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile
        private var INSTANCE: CacheDatabase? = null

        @JvmStatic
        fun getInstance(): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    Utils.getContext(),
                    CacheDatabase::class.java,
                    "undelete_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}


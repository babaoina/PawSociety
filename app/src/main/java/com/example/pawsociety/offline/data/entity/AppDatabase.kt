package com.example.pawsociety.offline.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.example.pawsociety.offline.data.dao.PostDao
import com.example.pawsociety.offline.data.entity.PostEntity

@Database(
    entities = [PostEntity::class],
    version = 2,  // Keep this at 2
    exportSchema = false
)
@TypeConverters(Converters::class)  // ← ADD THIS LINE
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pawsociety_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
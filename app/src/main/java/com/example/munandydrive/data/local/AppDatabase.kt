package com.example.munandydrive.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.munandydrive.data.local.dao.CustomerDao
import com.example.munandydrive.data.local.dao.PendingStopUpdateDao
import com.example.munandydrive.data.local.dao.RouteDao
import com.example.munandydrive.data.local.dao.UserDao
import com.example.munandydrive.data.local.entity.CustomerEntity
import com.example.munandydrive.data.local.entity.PendingStopUpdateEntity
import com.example.munandydrive.data.local.entity.RouteEntity
import com.example.munandydrive.data.local.entity.RouteStopEntity
import com.example.munandydrive.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        CustomerEntity::class,
        RouteEntity::class,
        RouteStopEntity::class,
        PendingStopUpdateEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun routeDao(): RouteDao
    abstract fun pendingStopUpdateDao(): PendingStopUpdateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "munandy_database"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}

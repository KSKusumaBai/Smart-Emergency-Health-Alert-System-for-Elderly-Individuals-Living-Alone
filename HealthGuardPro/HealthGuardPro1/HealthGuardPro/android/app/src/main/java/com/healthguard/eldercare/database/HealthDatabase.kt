package com.healthguard.eldercare.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.healthguard.eldercare.database.converters.Converters
import com.healthguard.eldercare.database.dao.EmergencyContactDao
import com.healthguard.eldercare.database.dao.HealthDataDao
import com.healthguard.eldercare.database.dao.UserDao
import com.healthguard.eldercare.database.entities.EmergencyContactEntity
import com.healthguard.eldercare.database.entities.HealthDataEntity
import com.healthguard.eldercare.database.entities.UserEntity

@Database(
    entities = [
        UserEntity::class,
        HealthDataEntity::class,
        EmergencyContactEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HealthDatabase : RoomDatabase() {
    
    abstract fun userDao(): UserDao
    abstract fun healthDataDao(): HealthDataDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    
    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null
        
        fun getDatabase(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "health_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
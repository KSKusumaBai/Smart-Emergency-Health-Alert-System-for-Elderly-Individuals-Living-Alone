package com.healthguard.eldercare.database.dao

import androidx.room.*
import com.healthguard.eldercare.database.entities.HealthDataEntity

@Dao
interface HealthDataDao {
    
    @Query("SELECT * FROM health_data WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestData(limit: Int = 100): List<HealthDataEntity>
    
    @Query("SELECT * FROM health_data WHERE userId = :userId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getDataInRange(startTime: Long, endTime: Long, limit: Int = 100): List<HealthDataEntity>
    
    @Query("SELECT * FROM health_data WHERE isAbnormal = 1 ORDER BY timestamp DESC")
    suspend fun getAbnormalData(): List<HealthDataEntity>
    
    @Query("SELECT * FROM health_data WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedData(): List<HealthDataEntity>
    
    @Query("SELECT * FROM health_data WHERE id = :id")
    suspend fun getById(id: String): HealthDataEntity?
    
    @Query("SELECT * FROM health_data ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): HealthDataEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(healthData: HealthDataEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(healthDataList: List<HealthDataEntity>)
    
    @Update
    suspend fun update(healthData: HealthDataEntity)
    
    @Delete
    suspend fun delete(healthData: HealthDataEntity)
    
    @Query("DELETE FROM health_data")
    suspend fun deleteAll()
    
    @Query("DELETE FROM health_data WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM health_data WHERE userId = :userId")
    suspend fun getCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM health_data WHERE isAbnormal = 1 AND userId = :userId")
    suspend fun getAbnormalCount(userId: String): Int
}
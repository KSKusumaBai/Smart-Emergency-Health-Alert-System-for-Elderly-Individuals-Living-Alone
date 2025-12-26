package com.healthguard.eldercare.database.dao

import androidx.room.*
import com.healthguard.eldercare.database.entities.EmergencyContactEntity

@Dao
interface EmergencyContactDao {
    
    @Query("SELECT * FROM emergency_contacts WHERE userId = :userId AND isActive = 1 ORDER BY priority ASC")
    suspend fun getActiveContacts(userId: String): List<EmergencyContactEntity>
    
    @Query("SELECT * FROM emergency_contacts WHERE userId = :userId ORDER BY priority ASC")
    suspend fun getAllContacts(userId: String): List<EmergencyContactEntity>
    
    @Query("SELECT * FROM emergency_contacts WHERE id = :id")
    suspend fun getContact(id: String): EmergencyContactEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: EmergencyContactEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<EmergencyContactEntity>)
    
    @Update
    suspend fun update(contact: EmergencyContactEntity)
    
    @Delete
    suspend fun delete(contact: EmergencyContactEntity)
    
    @Query("DELETE FROM emergency_contacts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
    
    @Query("SELECT COUNT(*) FROM emergency_contacts WHERE userId = :userId AND isActive = 1")
    suspend fun getActiveCount(userId: String): Int
}
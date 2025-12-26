package com.healthguard.eldercare.database.dao

import androidx.room.*
import com.healthguard.eldercare.database.entities.UserEntity

@Dao
interface UserDao {
    
    @Query("SELECT * FROM users WHERE uid = :uid")
    suspend fun getUser(uid: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): UserEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)
    
    @Update
    suspend fun update(user: UserEntity)
    
    @Delete
    suspend fun delete(user: UserEntity)
    
    @Query("DELETE FROM users WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)
    
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getCount(): Int
}
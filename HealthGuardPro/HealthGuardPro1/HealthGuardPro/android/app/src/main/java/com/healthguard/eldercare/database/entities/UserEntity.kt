package com.healthguard.eldercare.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.healthguard.eldercare.models.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val email: String,
    val dateOfBirth: String,
    val gender: String,
    val createdAt: Long,
    val lastLoginAt: Long,
    val profileImageUrl: String
) {
    companion object {
        fun fromUser(user: User): UserEntity {
            return UserEntity(
                uid = user.uid,
                name = user.name,
                email = user.email,
                dateOfBirth = user.dateOfBirth,
                gender = user.gender,
                createdAt = user.createdAt,
                lastLoginAt = user.lastLoginAt,
                profileImageUrl = user.profileImageUrl
            )
        }
    }
    
    fun toUser(): User {
        return User(
            uid = uid,
            name = name,
            email = email,
            dateOfBirth = dateOfBirth,
            gender = gender,
            createdAt = createdAt,
            lastLoginAt = lastLoginAt,
            profileImageUrl = profileImageUrl
        )
    }
}
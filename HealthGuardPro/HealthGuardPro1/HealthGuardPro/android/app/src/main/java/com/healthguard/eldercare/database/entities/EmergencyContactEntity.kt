package com.healthguard.eldercare.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.healthguard.eldercare.models.EmergencyContact

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val priority: Int,
    val isActive: Boolean,
    val lastContactedAt: Long,
    val responseReceived: Boolean
) {
    companion object {
        fun fromEmergencyContact(contact: EmergencyContact, userId: String): EmergencyContactEntity {
            return EmergencyContactEntity(
                id = contact.id.ifEmpty { java.util.UUID.randomUUID().toString() },
                userId = userId,
                name = contact.name,
                phoneNumber = contact.phoneNumber,
                relationship = contact.relationship,
                priority = contact.priority,
                isActive = contact.isActive,
                lastContactedAt = contact.lastContactedAt,
                responseReceived = contact.responseReceived
            )
        }
    }
    
    fun toEmergencyContact(): EmergencyContact {
        return EmergencyContact(
            id = id,
            name = name,
            phoneNumber = phoneNumber,
            relationship = relationship,
            priority = priority,
            isActive = isActive,
            lastContactedAt = lastContactedAt,
            responseReceived = responseReceived
        )
    }
}
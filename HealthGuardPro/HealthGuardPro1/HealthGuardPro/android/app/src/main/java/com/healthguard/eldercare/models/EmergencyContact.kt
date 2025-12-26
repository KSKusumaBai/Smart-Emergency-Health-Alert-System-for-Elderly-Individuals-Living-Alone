package com.healthguard.eldercare.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = "", // caretaker, family, neighbor, doctor, etc.
    val priority: Int = 0, // 1 = highest priority
    val isActive: Boolean = true,
    val lastContactedAt: Long = 0L,
    val responseReceived: Boolean = false
) : Parcelable

@Parcelize
data class EmergencyAlert(
    val id: String = "",
    val userId: String = "",
    val type: String = "", // manual_sos, fall_detected, health_emergency, etc.
    val healthData: HealthData? = null,
    val location: Location? = null,
    val accelerometerData: AccelerometerData? = null,
    val triggeredAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long = 0L,
    val status: String = "active", // active, resolved, cancelled
    val contactsNotified: List<String> = emptyList(),
    val responseReceived: Boolean = false,
    val responseTime: Long = 0L
) : Parcelable

@Parcelize
data class AccelerometerData(
    val x: Float = 0.0f,
    val y: Float = 0.0f,
    val z: Float = 0.0f,
    val magnitude: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable
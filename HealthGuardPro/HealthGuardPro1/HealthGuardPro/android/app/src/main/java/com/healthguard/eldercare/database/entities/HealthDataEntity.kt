package com.healthguard.eldercare.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.healthguard.eldercare.models.HealthAnalysisResult
import com.healthguard.eldercare.models.HealthData
import com.healthguard.eldercare.models.Location

@Entity(tableName = "health_data")
data class HealthDataEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val heartRate: Int,
    val bloodPressureSystolic: Int,
    val bloodPressureDiastolic: Int,
    val temperature: Float,
    val oxygenSaturation: Int,
    val activityLevel: String,
    val isAbnormal: Boolean,
    val analysisStatus: String?,
    val analysisRiskLevel: String?,
    val analysisMessage: String?,
    val analysisConfidenceScore: Float?,
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    val locationAddress: String?,
    val timestamp: Long,
    val deviceId: String,
    val deviceType: String,
    val firebaseId: String?,
    val isSynced: Boolean = false
) {
    companion object {
        fun fromHealthData(healthData: HealthData, firebaseId: String?): HealthDataEntity {
            return HealthDataEntity(
                id = healthData.id.ifEmpty { java.util.UUID.randomUUID().toString() },
                userId = healthData.userId,
                heartRate = healthData.heartRate,
                bloodPressureSystolic = healthData.bloodPressureSystolic,
                bloodPressureDiastolic = healthData.bloodPressureDiastolic,
                temperature = healthData.temperature,
                oxygenSaturation = healthData.oxygenSaturation,
                activityLevel = healthData.activityLevel,
                isAbnormal = healthData.isAbnormal,
                analysisStatus = healthData.analysisResult?.status,
                analysisRiskLevel = healthData.analysisResult?.riskLevel,
                analysisMessage = healthData.analysisResult?.message,
                analysisConfidenceScore = healthData.analysisResult?.confidenceScore,
                locationLatitude = healthData.location?.latitude,
                locationLongitude = healthData.location?.longitude,
                locationAddress = healthData.location?.address,
                timestamp = healthData.timestamp,
                deviceId = healthData.deviceId,
                deviceType = healthData.deviceType,
                firebaseId = firebaseId,
                isSynced = firebaseId != null
            )
        }
    }
    
    fun toHealthData(): HealthData {
        return HealthData(
            id = id,
            userId = userId,
            heartRate = heartRate,
            bloodPressureSystolic = bloodPressureSystolic,
            bloodPressureDiastolic = bloodPressureDiastolic,
            temperature = temperature,
            oxygenSaturation = oxygenSaturation,
            activityLevel = activityLevel,
            isAbnormal = isAbnormal,
            analysisResult = if (analysisStatus != null) {
                HealthAnalysisResult(
                    status = analysisStatus,
                    riskLevel = analysisRiskLevel ?: "",
                    message = analysisMessage ?: "",
                    confidenceScore = analysisConfidenceScore ?: 0.0f
                )
            } else null,
            location = if (locationLatitude != null && locationLongitude != null) {
                Location(
                    latitude = locationLatitude,
                    longitude = locationLongitude,
                    address = locationAddress ?: ""
                )
            } else null,
            timestamp = timestamp,
            deviceId = deviceId,
            deviceType = deviceType
        )
    }
}
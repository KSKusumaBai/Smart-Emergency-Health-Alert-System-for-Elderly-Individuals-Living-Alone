package com.healthguard.eldercare.repositories

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DatabaseReference
import com.healthguard.eldercare.database.HealthDatabase
import com.healthguard.eldercare.database.entities.HealthDataEntity
import com.healthguard.eldercare.firebase.FirebaseManager
import com.healthguard.eldercare.models.HealthData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HealthDataRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "HealthDataRepository"
    }
    
    private val firebaseManager = FirebaseManager()
    private val localDatabase = HealthDatabase.getDatabase(context)
    private val healthDataDao = localDatabase.healthDataDao()
    
    private val _latestHealthData = MutableLiveData<HealthData>()
    val latestHealthData: LiveData<HealthData> = _latestHealthData
    
    private val _healthDataHistory = MutableLiveData<List<HealthData>>()
    val healthDataHistory: LiveData<List<HealthData>> = _healthDataHistory
    
    private var realtimeListener: DatabaseReference? = null
    
    init {
        setupRealtimeListener()
    }
    
    private fun setupRealtimeListener() {
        val userId = firebaseManager.getCurrentUser()?.uid
        if (userId != null) {
            realtimeListener = firebaseManager.addHealthDataListener(userId) { healthData ->
                _latestHealthData.postValue(healthData)
                
                // Also store locally for offline access
                storeLocalHealthData(healthData)
            }
        }
    }
    
    suspend fun storeHealthData(healthData: HealthData): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Store in Firebase
                val documentId = firebaseManager.storeHealthData(healthData)
                
                // Store locally as backup
                val entity = HealthDataEntity.fromHealthData(healthData, documentId)
                healthDataDao.insert(entity)
                
                // Update live data
                _latestHealthData.postValue(healthData)
                
                Log.d(TAG, "Health data stored successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store health data", e)
                
                // Store locally if Firebase fails
                try {
                    val entity = HealthDataEntity.fromHealthData(healthData, null)
                    healthDataDao.insert(entity)
                    _latestHealthData.postValue(healthData)
                    true
                } catch (localException: Exception) {
                    Log.e(TAG, "Failed to store health data locally", localException)
                    false
                }
            }
        }
    }
    
    suspend fun getHealthDataHistory(
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 100
    ): List<HealthData> {
        return withContext(Dispatchers.IO) {
            try {
                // Try Firebase first
                val firebaseData = firebaseManager.getHealthDataHistory(
                    null, startTime, endTime, limit
                )
                
                if (firebaseData.isNotEmpty()) {
                    _healthDataHistory.postValue(firebaseData)
                    firebaseData
                } else {
                    // Fallback to local data
                    getLocalHealthDataHistory(startTime, endTime, limit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get health data from Firebase, using local data", e)
                getLocalHealthDataHistory(startTime, endTime, limit)
            }
        }
    }
    
    suspend fun getAbnormalHealthData(): List<HealthData> {
        return withContext(Dispatchers.IO) {
            try {
                firebaseManager.getAbnormalHealthData()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get abnormal health data", e)
                
                // Fallback to local abnormal data
                healthDataDao.getAbnormalData().map { it.toHealthData() }
            }
        }
    }
    
    suspend fun syncLocalDataToFirebase() {
        withContext(Dispatchers.IO) {
            try {
                val unsynced = healthDataDao.getUnsyncedData()
                
                unsynced.forEach { entity ->
                    try {
                        val healthData = entity.toHealthData()
                        val documentId = firebaseManager.storeHealthData(healthData)
                        
                        if (documentId != null) {
                            // Mark as synced
                            healthDataDao.update(entity.copy(firebaseId = documentId, isSynced = true))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync individual health data record", e)
                    }
                }
                
                Log.d(TAG, "Local data sync completed. Synced ${unsynced.size} records")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync local data to Firebase", e)
            }
        }
    }
    
    private suspend fun getLocalHealthDataHistory(
        startTime: Long?,
        endTime: Long?,
        limit: Int
    ): List<HealthData> {
        return try {
            val entities = if (startTime != null && endTime != null) {
                healthDataDao.getDataInRange(startTime, endTime, limit)
            } else {
                healthDataDao.getLatestData(limit)
            }
            
            val healthDataList = entities.map { it.toHealthData() }
            _healthDataHistory.postValue(healthDataList)
            healthDataList
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local health data", e)
            emptyList()
        }
    }
    
    private suspend fun storeLocalHealthData(healthData: HealthData) {
        withContext(Dispatchers.IO) {
            try {
                val entity = HealthDataEntity.fromHealthData(healthData, null)
                healthDataDao.insert(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store health data locally", e)
            }
        }
    }
    
    suspend fun clearLocalData() {
        withContext(Dispatchers.IO) {
            try {
                healthDataDao.deleteAll()
                Log.d(TAG, "Local health data cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear local data", e)
            }
        }
    }
    
    fun cleanup() {
        realtimeListener?.removeEventListener(null)
    }
}
package com.healthguard.eldercare.repositories

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DatabaseReference
import com.healthguard.eldercare.database.HealthDatabase
import com.healthguard.eldercare.database.entities.EmergencyContactEntity
import com.healthguard.eldercare.firebase.FirebaseManager
import com.healthguard.eldercare.models.EmergencyAlert
import com.healthguard.eldercare.models.EmergencyContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmergencyRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "EmergencyRepository"
    }
    
    private val firebaseManager = FirebaseManager()
    private val localDatabase = HealthDatabase.getDatabase(context)
    private val emergencyContactDao = localDatabase.emergencyContactDao()
    
    private val _emergencyContacts = MutableLiveData<List<EmergencyContact>>()
    val emergencyContacts: LiveData<List<EmergencyContact>> = _emergencyContacts
    
    private val _activeEmergencyAlerts = MutableLiveData<List<EmergencyAlert>>()
    val activeEmergencyAlerts: LiveData<List<EmergencyAlert>> = _activeEmergencyAlerts
    
    private var alertListener: DatabaseReference? = null
    
    init {
        setupEmergencyAlertListener()
    }
    
    private fun setupEmergencyAlertListener() {
        val userId = firebaseManager.getCurrentUser()?.uid
        if (userId != null) {
            alertListener = firebaseManager.addEmergencyAlertListener(userId) { alert ->
                // Update the active alerts list
                updateActiveAlerts()
            }
        }
    }
    
    suspend fun saveEmergencyContacts(contacts: List<EmergencyContact>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = firebaseManager.getCurrentUser()?.uid ?: return@withContext false
                
                // Save to Firebase
                firebaseManager.saveEmergencyContacts(contacts)
                
                // Save locally
                emergencyContactDao.deleteAllForUser(userId)
                val entities = contacts.map { EmergencyContactEntity.fromEmergencyContact(it, userId) }
                emergencyContactDao.insertAll(entities)
                
                // Update live data
                _emergencyContacts.postValue(contacts)
                
                Log.d(TAG, "Emergency contacts saved successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save emergency contacts", e)
                false
            }
        }
    }
    
    suspend fun getEmergencyContacts(): List<EmergencyContact> {
        return withContext(Dispatchers.IO) {
            try {
                // Try Firebase first
                val firebaseContacts = firebaseManager.getEmergencyContacts()
                
                if (firebaseContacts.isNotEmpty()) {
                    _emergencyContacts.postValue(firebaseContacts)
                    firebaseContacts
                } else {
                    // Fallback to local data
                    getLocalEmergencyContacts()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get emergency contacts from Firebase, using local data", e)
                getLocalEmergencyContacts()
            }
        }
    }
    
    private suspend fun getLocalEmergencyContacts(): List<EmergencyContact> {
        return try {
            val userId = firebaseManager.getCurrentUser()?.uid ?: return emptyList()
            val entities = emergencyContactDao.getActiveContacts(userId)
            val contacts = entities.map { it.toEmergencyContact() }
            _emergencyContacts.postValue(contacts)
            contacts
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local emergency contacts", e)
            emptyList()
        }
    }
    
    suspend fun triggerEmergencyAlert(alert: EmergencyAlert): String? {
        return withContext(Dispatchers.IO) {
            try {
                val alertId = firebaseManager.storeEmergencyAlert(alert)
                
                if (alertId != null) {
                    // Update active alerts
                    updateActiveAlerts()
                    
                    Log.d(TAG, "Emergency alert triggered successfully: $alertId")
                    alertId
                } else {
                    Log.e(TAG, "Failed to trigger emergency alert")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger emergency alert", e)
                null
            }
        }
    }
    
    suspend fun updateEmergencyAlert(alert: EmergencyAlert): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                firebaseManager.updateEmergencyAlert(alert)
                updateActiveAlerts()
                
                Log.d(TAG, "Emergency alert updated successfully: ${alert.id}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update emergency alert", e)
                false
            }
        }
    }
    
    suspend fun getActiveEmergencyAlerts(): List<EmergencyAlert> {
        return withContext(Dispatchers.IO) {
            try {
                val alerts = firebaseManager.getActiveEmergencyAlerts()
                _activeEmergencyAlerts.postValue(alerts)
                alerts
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get active emergency alerts", e)
                emptyList()
            }
        }
    }
    
    private suspend fun updateActiveAlerts() {
        withContext(Dispatchers.IO) {
            try {
                val alerts = firebaseManager.getActiveEmergencyAlerts()
                _activeEmergencyAlerts.postValue(alerts)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update active alerts", e)
            }
        }
    }
    
    suspend fun markContactAsContacted(contactId: String, responseReceived: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = firebaseManager.getCurrentUser()?.uid ?: return@withContext false
                val contact = emergencyContactDao.getContact(contactId)
                
                if (contact != null) {
                    val updatedContact = contact.copy(
                        lastContactedAt = System.currentTimeMillis(),
                        responseReceived = responseReceived
                    )
                    emergencyContactDao.update(updatedContact)
                    
                    Log.d(TAG, "Contact marked as contacted: $contactId")
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark contact as contacted", e)
                false
            }
        }
    }
    
    suspend fun addEmergencyContact(contact: EmergencyContact): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = firebaseManager.getCurrentUser()?.uid ?: return@withContext false
                
                // Get current contacts
                val currentContacts = getEmergencyContacts().toMutableList()
                currentContacts.add(contact)
                
                // Save updated list
                saveEmergencyContacts(currentContacts)
                
                Log.d(TAG, "Emergency contact added successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add emergency contact", e)
                false
            }
        }
    }
    
    suspend fun removeEmergencyContact(contactId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = firebaseManager.getCurrentUser()?.uid ?: return@withContext false
                
                // Get current contacts
                val currentContacts = getEmergencyContacts().toMutableList()
                currentContacts.removeAll { it.id == contactId }
                
                // Save updated list
                saveEmergencyContacts(currentContacts)
                
                Log.d(TAG, "Emergency contact removed successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove emergency contact", e)
                false
            }
        }
    }
    
    fun cleanup() {
        alertListener?.removeEventListener(null)
    }
}
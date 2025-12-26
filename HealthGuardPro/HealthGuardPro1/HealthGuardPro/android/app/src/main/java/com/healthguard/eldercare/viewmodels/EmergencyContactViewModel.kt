package com.healthguard.eldercare.viewmodels

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.healthguard.eldercare.models.EmergencyAlert
import com.healthguard.eldercare.models.EmergencyContact
import com.healthguard.eldercare.repositories.EmergencyRepository
import com.healthguard.eldercare.services.EmergencyAlertService
import kotlinx.coroutines.launch
import java.util.*

class EmergencyContactViewModel(application: Application) : AndroidViewModel(application) {
    
    private val emergencyRepository = EmergencyRepository(application)
    
    private val _emergencyContacts = MutableLiveData<List<EmergencyContact>>()
    val emergencyContacts: LiveData<List<EmergencyContact>> = _emergencyContacts
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _operationResult = MutableLiveData<Boolean?>()
    val operationResult: LiveData<Boolean?> = _operationResult
    
    private val _emergencyAlertTriggered = MutableLiveData<String?>()
    val emergencyAlertTriggered: LiveData<String?> = _emergencyAlertTriggered
    
    fun loadEmergencyContacts() {
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val contacts = emergencyRepository.getEmergencyContacts()
                _emergencyContacts.value = contacts
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load emergency contacts: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun addEmergencyContact(contact: EmergencyContact) {
        viewModelScope.launch {
            try {
                val success = emergencyRepository.addEmergencyContact(contact)
                _operationResult.value = success
                
                if (success) {
                    loadEmergencyContacts() // Refresh the list
                } else {
                    _errorMessage.value = "Failed to add emergency contact"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error adding contact: ${e.message}"
                _operationResult.value = false
            }
        }
    }
    
    fun updateEmergencyContact(contact: EmergencyContact) {
        viewModelScope.launch {
            try {
                val currentContacts = _emergencyContacts.value?.toMutableList() ?: mutableListOf()
                val index = currentContacts.indexOfFirst { it.id == contact.id }
                
                if (index >= 0) {
                    currentContacts[index] = contact
                    val success = emergencyRepository.saveEmergencyContacts(currentContacts)
                    _operationResult.value = success
                    
                    if (success) {
                        _emergencyContacts.value = currentContacts
                    } else {
                        _errorMessage.value = "Failed to update emergency contact"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error updating contact: ${e.message}"
                _operationResult.value = false
            }
        }
    }
    
    fun removeEmergencyContact(contactId: String) {
        viewModelScope.launch {
            try {
                val success = emergencyRepository.removeEmergencyContact(contactId)
                _operationResult.value = success
                
                if (success) {
                    loadEmergencyContacts() // Refresh the list
                } else {
                    _errorMessage.value = "Failed to remove emergency contact"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error removing contact: ${e.message}"
                _operationResult.value = false
            }
        }
    }
    
    fun saveAllContacts() {
        val contacts = _emergencyContacts.value ?: return
        
        viewModelScope.launch {
            try {
                val success = emergencyRepository.saveEmergencyContacts(contacts)
                _operationResult.value = success
                
                if (!success) {
                    _errorMessage.value = "Failed to save emergency contacts"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error saving contacts: ${e.message}"
                _operationResult.value = false
            }
        }
    }
    
    fun triggerManualSOS() {
        viewModelScope.launch {
            try {
                val emergencyAlert = EmergencyAlert(
                    id = "sos_${System.currentTimeMillis()}",
                    type = "manual_sos",
                    triggeredAt = System.currentTimeMillis(),
                    status = "active"
                )
                
                val alertId = emergencyRepository.triggerEmergencyAlert(emergencyAlert)
                
                if (alertId != null) {
                    _emergencyAlertTriggered.value = alertId
                    
                    // Start the emergency alert service
                    val intent = Intent(getApplication(), EmergencyAlertService::class.java).apply {
                        action = EmergencyAlertService.ACTION_TRIGGER_SOS
                        putExtra("alert_id", alertId)
                    }
                    getApplication<Application>().startService(intent)
                } else {
                    _errorMessage.value = "Failed to trigger emergency alert"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error triggering SOS: ${e.message}"
            }
        }
    }
    
    fun cancelEmergencyAlert() {
        val alertId = _emergencyAlertTriggered.value
        if (alertId != null) {
            viewModelScope.launch {
                try {
                    val intent = Intent(getApplication(), EmergencyAlertService::class.java).apply {
                        action = EmergencyAlertService.ACTION_CANCEL_EMERGENCY
                        putExtra("alert_id", alertId)
                    }
                    getApplication<Application>().startService(intent)
                    
                    _emergencyAlertTriggered.value = null
                } catch (e: Exception) {
                    _errorMessage.value = "Error cancelling emergency alert: ${e.message}"
                }
            }
        }
    }
    
    fun respondToEmergency(responseType: String) {
        val alertId = _emergencyAlertTriggered.value
        if (alertId != null) {
            viewModelScope.launch {
                try {
                    val intent = Intent(getApplication(), EmergencyAlertService::class.java).apply {
                        action = EmergencyAlertService.ACTION_USER_RESPONSE
                        putExtra("alert_id", alertId)
                        putExtra("response_type", responseType)
                    }
                    getApplication<Application>().startService(intent)
                    
                    _emergencyAlertTriggered.value = null
                } catch (e: Exception) {
                    _errorMessage.value = "Error responding to emergency: ${e.message}"
                }
            }
        }
    }
    
    fun reorderContacts(fromPosition: Int, toPosition: Int) {
        val contacts = _emergencyContacts.value?.toMutableList() ?: return
        
        if (fromPosition < contacts.size && toPosition < contacts.size) {
            val contact = contacts.removeAt(fromPosition)
            contacts.add(toPosition, contact)
            
            // Update priorities
            contacts.forEachIndexed { index, contact ->
                contacts[index] = contact.copy(priority = index + 1)
            }
            
            _emergencyContacts.value = contacts
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun clearOperationResult() {
        _operationResult.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        emergencyRepository.cleanup()
    }
}
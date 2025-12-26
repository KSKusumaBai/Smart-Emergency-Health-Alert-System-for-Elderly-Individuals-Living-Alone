package com.healthguard.eldercare.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.healthguard.eldercare.models.*
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseManager {
    
    companion object {
        private const val TAG = "FirebaseManager"
        
        // Collections
        const val USERS_COLLECTION = "users"
        const val HEALTH_DATA_COLLECTION = "health_data"
        const val ABNORMAL_DATA_COLLECTION = "abnormal_health_data"
        const val EMERGENCY_ALERTS_COLLECTION = "emergency_alerts"
        const val EMERGENCY_CONTACTS_COLLECTION = "emergency_contacts"
        
        // Real-time Database paths
        const val REALTIME_HEALTH_DATA = "health_data"
        const val REALTIME_EMERGENCY_ALERTS = "emergency_alerts"
        const val REALTIME_USER_STATUS = "user_status"
    }
    
    // Firebase instances
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val realtimeDb: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    
    // Authentication
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            updateUserLoginStatus(result.user?.uid, true)
            AuthResult(isSuccess = true, user = getUserProfile(result.user?.uid))
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            AuthResult(isSuccess = false, errorMessage = e.message)
        }
    }
    
    suspend fun createUserWithEmailAndPassword(email: String, password: String, userData: User): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            
            if (user != null) {
                val userProfile = userData.copy(uid = user.uid, email = email)
                saveUserProfile(userProfile)
                updateUserLoginStatus(user.uid, true)
                AuthResult(isSuccess = true, user = userProfile)
            } else {
                AuthResult(isSuccess = false, errorMessage = "Failed to create user")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign up failed", e)
            AuthResult(isSuccess = false, errorMessage = e.message)
        }
    }
    
    suspend fun signOut() {
        try {
            val userId = getCurrentUser()?.uid
            updateUserLoginStatus(userId, false)
            auth.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
        }
    }
    
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    
    // User Profile Management
    suspend fun saveUserProfile(user: User) {
        try {
            firestore.collection(USERS_COLLECTION)
                .document(user.uid)
                .set(user)
                .await()
            Log.d(TAG, "User profile saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user profile", e)
            throw e
        }
    }
    
    suspend fun getUserProfile(userId: String?): User? {
        if (userId == null) return null
        
        return try {
            val document = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()
            document.toObject<User>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user profile", e)
            null
        }
    }
    
    suspend fun updateUserProfile(user: User) {
        try {
            firestore.collection(USERS_COLLECTION)
                .document(user.uid)
                .set(user)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update user profile", e)
            throw e
        }
    }
    
    // Health Data Management
    suspend fun storeHealthData(healthData: HealthData): String? {
        return try {
            val userId = getCurrentUser()?.uid ?: return null
            val dataWithUser = healthData.copy(userId = userId)
            
            // Store in Firestore for querying and analytics
            val docRef = firestore.collection(HEALTH_DATA_COLLECTION)
                .add(dataWithUser)
                .await()
            
            // Store in Realtime Database for real-time updates
            realtimeDb.child(REALTIME_HEALTH_DATA)
                .child(userId)
                .child(docRef.id)
                .setValue(dataWithUser)
                .await()
            
            // If abnormal, store separately for doctor access
            if (dataWithUser.isAbnormal) {
                firestore.collection(ABNORMAL_DATA_COLLECTION)
                    .add(dataWithUser)
                    .await()
            }
            
            Log.d(TAG, "Health data stored successfully: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store health data", e)
            null
        }
    }
    
    suspend fun getHealthDataHistory(
        userId: String? = null, 
        startTime: Long? = null, 
        endTime: Long? = null,
        limit: Int = 100
    ): List<HealthData> {
        return try {
            val targetUserId = userId ?: getCurrentUser()?.uid ?: return emptyList()
            
            var query: Query = firestore.collection(HEALTH_DATA_COLLECTION)
                .whereEqualTo("userId", targetUserId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            
            if (startTime != null) {
                query = query.whereGreaterThanOrEqualTo("timestamp", startTime)
            }
            
            if (endTime != null) {
                query = query.whereLessThanOrEqualTo("timestamp", endTime)
            }
            
            val documents = query.get().await()
            documents.mapNotNull { it.toObject<HealthData>() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get health data history", e)
            emptyList()
        }
    }
    
    suspend fun getAbnormalHealthData(userId: String? = null): List<HealthData> {
        return try {
            val targetUserId = userId ?: getCurrentUser()?.uid ?: return emptyList()
            
            val documents = firestore.collection(ABNORMAL_DATA_COLLECTION)
                .whereEqualTo("userId", targetUserId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            documents.mapNotNull { it.toObject<HealthData>() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get abnormal health data", e)
            emptyList()
        }
    }
    
    // Emergency Contact Management
    suspend fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        try {
            val userId = getCurrentUser()?.uid ?: return
            
            // Clear existing contacts
            val existingContacts = firestore.collection(EMERGENCY_CONTACTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            existingContacts.forEach { doc ->
                doc.reference.delete()
            }
            
            // Add new contacts
            contacts.forEach { contact ->
                val contactWithUser = contact.copy(id = UUID.randomUUID().toString())
                firestore.collection(EMERGENCY_CONTACTS_COLLECTION)
                    .document(contactWithUser.id)
                    .set(mapOf(
                        "id" to contactWithUser.id,
                        "userId" to userId,
                        "name" to contactWithUser.name,
                        "phoneNumber" to contactWithUser.phoneNumber,
                        "relationship" to contactWithUser.relationship,
                        "priority" to contactWithUser.priority,
                        "isActive" to contactWithUser.isActive
                    ))
                    .await()
            }
            
            Log.d(TAG, "Emergency contacts saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save emergency contacts", e)
            throw e
        }
    }
    
    suspend fun getEmergencyContacts(userId: String? = null): List<EmergencyContact> {
        return try {
            val targetUserId = userId ?: getCurrentUser()?.uid ?: return emptyList()
            
            val documents = firestore.collection(EMERGENCY_CONTACTS_COLLECTION)
                .whereEqualTo("userId", targetUserId)
                .whereEqualTo("isActive", true)
                .orderBy("priority")
                .get()
                .await()
            
            documents.mapNotNull { doc ->
                val data = doc.data
                EmergencyContact(
                    id = data["id"] as? String ?: "",
                    name = data["name"] as? String ?: "",
                    phoneNumber = data["phoneNumber"] as? String ?: "",
                    relationship = data["relationship"] as? String ?: "",
                    priority = (data["priority"] as? Long)?.toInt() ?: 0,
                    isActive = data["isActive"] as? Boolean ?: true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get emergency contacts", e)
            emptyList()
        }
    }
    
    // Emergency Alert Management
    suspend fun storeEmergencyAlert(alert: EmergencyAlert): String? {
        return try {
            val userId = getCurrentUser()?.uid ?: return null
            val alertWithUser = alert.copy(userId = userId, id = UUID.randomUUID().toString())
            
            // Store in Firestore
            firestore.collection(EMERGENCY_ALERTS_COLLECTION)
                .document(alertWithUser.id)
                .set(alertWithUser)
                .await()
            
            // Store in Realtime Database for immediate notifications
            realtimeDb.child(REALTIME_EMERGENCY_ALERTS)
                .child(userId)
                .child(alertWithUser.id)
                .setValue(alertWithUser)
                .await()
            
            Log.d(TAG, "Emergency alert stored successfully: ${alertWithUser.id}")
            alertWithUser.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store emergency alert", e)
            null
        }
    }
    
    suspend fun updateEmergencyAlert(alert: EmergencyAlert) {
        try {
            firestore.collection(EMERGENCY_ALERTS_COLLECTION)
                .document(alert.id)
                .set(alert)
                .await()
            
            realtimeDb.child(REALTIME_EMERGENCY_ALERTS)
                .child(alert.userId)
                .child(alert.id)
                .setValue(alert)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update emergency alert", e)
            throw e
        }
    }
    
    suspend fun getActiveEmergencyAlerts(userId: String? = null): List<EmergencyAlert> {
        return try {
            val targetUserId = userId ?: getCurrentUser()?.uid ?: return emptyList()
            
            val documents = firestore.collection(EMERGENCY_ALERTS_COLLECTION)
                .whereEqualTo("userId", targetUserId)
                .whereEqualTo("status", "active")
                .orderBy("triggeredAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            documents.mapNotNull { it.toObject<EmergencyAlert>() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active emergency alerts", e)
            emptyList()
        }
    }
    
    // Real-time listeners
    fun addHealthDataListener(userId: String, callback: (HealthData) -> Unit): DatabaseReference {
        val ref = realtimeDb.child(REALTIME_HEALTH_DATA).child(userId)
        ref.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.getValue(HealthData::class.java)?.let { callback(it) }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.getValue(HealthData::class.java)?.let { callback(it) }
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Health data listener cancelled", error.toException())
            }
        })
        return ref
    }
    
    fun addEmergencyAlertListener(userId: String, callback: (EmergencyAlert) -> Unit): DatabaseReference {
        val ref = realtimeDb.child(REALTIME_EMERGENCY_ALERTS).child(userId)
        ref.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.getValue(EmergencyAlert::class.java)?.let { callback(it) }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.getValue(EmergencyAlert::class.java)?.let { callback(it) }
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Emergency alert listener cancelled", error.toException())
            }
        })
        return ref
    }
    
    // File Storage
    suspend fun uploadProfileImage(userId: String, imageData: ByteArray): String? {
        return try {
            val storageRef = storage.reference
            val profileImageRef = storageRef.child("profile_images/$userId.jpg")
            
            profileImageRef.putBytes(imageData).await()
            val downloadUrl = profileImageRef.downloadUrl.await()
            
            Log.d(TAG, "Profile image uploaded successfully")
            downloadUrl.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload profile image", e)
            null
        }
    }
    
    private suspend fun updateUserLoginStatus(userId: String?, isOnline: Boolean) {
        if (userId == null) return
        
        try {
            realtimeDb.child(REALTIME_USER_STATUS)
                .child(userId)
                .setValue(mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to System.currentTimeMillis()
                ))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update user status", e)
        }
    }
    
    // Doctor Portal Access
    suspend fun getAllAbnormalData(): List<PatientHealthData> {
        return try {
            val documents = firestore.collection(ABNORMAL_DATA_COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1000)
                .get()
                .await()
            
            val healthDataList = documents.mapNotNull { it.toObject<HealthData>() }
            
            // Group by user and get user info
            val groupedData = healthDataList.groupBy { it.userId }
            
            groupedData.map { (userId, healthData) ->
                val user = getUserProfile(userId)
                PatientHealthData(
                    user = user,
                    healthData = healthData
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all abnormal data", e)
            emptyList()
        }
    }
    
    data class PatientHealthData(
        val user: User?,
        val healthData: List<HealthData>
    )
}
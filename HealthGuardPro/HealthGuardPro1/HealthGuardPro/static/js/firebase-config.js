// Firebase configuration and integration
class FirebaseManager {
    constructor() {
        this.app = null;
        this.db = null;
        this.auth = null;
        this.isInitialized = false;
        this.userId = null;
        this.initializeFirebase();
    }

    initializeFirebase() {
        // Firebase configuration - get from environment or use defaults
        const firebaseConfig = {
            apiKey: process.env.FIREBASE_API_KEY || "your-firebase-api-key",
            authDomain: process.env.FIREBASE_AUTH_DOMAIN || "healthguard-app.firebaseapp.com",
            databaseURL: process.env.FIREBASE_DATABASE_URL || "https://healthguard-app-default-rtdb.firebaseio.com",
            projectId: process.env.FIREBASE_PROJECT_ID || "healthguard-app",
            storageBucket: process.env.FIREBASE_STORAGE_BUCKET || "healthguard-app.appspot.com",
            messagingSenderId: process.env.FIREBASE_MESSAGING_SENDER_ID || "123456789000",
            appId: process.env.FIREBASE_APP_ID || "1:123456789000:web:abcdef123456"
        };

        try {
            // Initialize Firebase using CDN loaded libraries
            if (typeof firebase !== 'undefined') {
                this.app = firebase.initializeApp(firebaseConfig);
                this.db = firebase.firestore();
                this.auth = firebase.auth();
                this.realtimeDB = firebase.database();
                this.isInitialized = true;
                
                console.log('Firebase initialized successfully');
                
                // Set up authentication state listener
                this.setupAuthListener();
                
                // Initialize real-time listeners
                this.setupRealtimeListeners();
                
            } else {
                console.error('Firebase SDK not loaded. Make sure to include Firebase CDN scripts.');
                this.fallbackToLocalStorage();
            }
        } catch (error) {
            console.error('Firebase initialization failed:', error);
            this.fallbackToLocalStorage();
        }
    }

    fallbackToLocalStorage() {
        console.log('Using localStorage as fallback for data persistence');
        this.isInitialized = false;
    }

    setupAuthListener() {
        this.auth.onAuthStateChanged((user) => {
            if (user) {
                this.userId = user.uid;
                console.log('User authenticated:', user.email);
                this.syncLocalDataToFirebase();
            } else {
                this.userId = null;
                console.log('User signed out');
            }
        });
    }

    setupRealtimeListeners() {
        if (!this.isInitialized) return;

        // Listen for real-time health data updates
        this.realtimeDB.ref('health-data').on('value', (snapshot) => {
            const data = snapshot.val();
            if (data && window.healthMonitor) {
                // Handle real-time updates from other devices
                this.handleRealtimeHealthData(data);
            }
        });
    }

    handleRealtimeHealthData(data) {
        // Process incoming real-time health data
        console.log('Received real-time health data:', data);
        
        // Update UI if data is for current user
        if (data.userId === this.userId && window.healthMonitor) {
            // Update charts and displays with new data
            window.healthMonitor.updateDisplay(data.healthData);
        }
    }

    // Authentication methods
    async signInWithEmailAndPassword(email, password) {
        if (!this.isInitialized) {
            return { success: false, error: 'Firebase not initialized' };
        }

        try {
            const userCredential = await this.auth.signInWithEmailAndPassword(email, password);
            return { success: true, user: userCredential.user };
        } catch (error) {
            return { success: false, error: error.message };
        }
    }

    async createUserWithEmailAndPassword(email, password, userData) {
        if (!this.isInitialized) {
            return { success: false, error: 'Firebase not initialized' };
        }

        try {
            const userCredential = await this.auth.createUserWithEmailAndPassword(email, password);
            const user = userCredential.user;
            
            // Store additional user data
            await this.db.collection('users').doc(user.uid).set({
                ...userData,
                email: email,
                createdAt: firebase.firestore.FieldValue.serverTimestamp(),
                lastLoginAt: firebase.firestore.FieldValue.serverTimestamp()
            });
            
            return { success: true, user: user };
        } catch (error) {
            return { success: false, error: error.message };
        }
    }

    async signOut() {
        if (!this.isInitialized) return;
        
        try {
            await this.auth.signOut();
            return { success: true };
        } catch (error) {
            return { success: false, error: error.message };
        }
    }

    // Health data storage methods
    async storeHealthData(healthData) {
        if (!this.isInitialized || !this.userId) {
            return this.storeHealthDataLocally(healthData);
        }

        try {
            const dataWithMetadata = {
                ...healthData,
                userId: this.userId,
                timestamp: firebase.firestore.FieldValue.serverTimestamp(),
                deviceTimestamp: new Date().toISOString()
            };

            // Store in Firestore for querying and analytics
            const docRef = await this.db.collection('health-data').add(dataWithMetadata);
            
            // Also store in Realtime Database for real-time updates
            await this.realtimeDB.ref(`health-data/${this.userId}/${docRef.id}`).set(dataWithMetadata);
            
            // If this is abnormal data, store separately for doctor access
            if (healthData.is_abnormal) {
                await this.db.collection('abnormal-health-data').add(dataWithMetadata);
            }
            
            console.log('Health data stored successfully:', docRef.id);
            return { success: true, id: docRef.id };
            
        } catch (error) {
            console.error('Failed to store health data in Firebase:', error);
            // Fallback to local storage
            return this.storeHealthDataLocally(healthData);
        }
    }

    storeHealthDataLocally(healthData) {
        try {
            const localData = JSON.parse(localStorage.getItem('healthData') || '[]');
            localData.push({
                ...healthData,
                id: Date.now().toString(),
                stored_locally: true,
                timestamp: new Date().toISOString()
            });
            
            // Keep only last 1000 records locally
            if (localData.length > 1000) {
                localData.splice(0, localData.length - 1000);
            }
            
            localStorage.setItem('healthData', JSON.stringify(localData));
            return { success: true, stored_locally: true };
        } catch (error) {
            console.error('Failed to store health data locally:', error);
            return { success: false, error: error.message };
        }
    }

    // Retrieve health data
    async getHealthData(startDate, endDate, limit = 100) {
        if (!this.isInitialized || !this.userId) {
            return this.getHealthDataLocally(startDate, endDate, limit);
        }

        try {
            let query = this.db.collection('health-data')
                .where('userId', '==', this.userId)
                .orderBy('timestamp', 'desc')
                .limit(limit);

            if (startDate) {
                query = query.where('timestamp', '>=', new Date(startDate));
            }
            if (endDate) {
                query = query.where('timestamp', '<=', new Date(endDate));
            }

            const snapshot = await query.get();
            const data = [];
            
            snapshot.forEach((doc) => {
                data.push({ id: doc.id, ...doc.data() });
            });

            return { success: true, data: data };
            
        } catch (error) {
            console.error('Failed to retrieve health data from Firebase:', error);
            return this.getHealthDataLocally(startDate, endDate, limit);
        }
    }

    getHealthDataLocally(startDate, endDate, limit = 100) {
        try {
            let localData = JSON.parse(localStorage.getItem('healthData') || '[]');
            
            // Filter by date range if provided
            if (startDate || endDate) {
                localData = localData.filter(record => {
                    const recordDate = new Date(record.timestamp);
                    if (startDate && recordDate < new Date(startDate)) return false;
                    if (endDate && recordDate > new Date(endDate)) return false;
                    return true;
                });
            }
            
            // Sort by timestamp and limit
            localData.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
            localData = localData.slice(0, limit);
            
            return { success: true, data: localData, source: 'local' };
        } catch (error) {
            console.error('Failed to retrieve local health data:', error);
            return { success: false, error: error.message };
        }
    }

    // Emergency contacts management
    async storeEmergencyContacts(contacts) {
        if (!this.isInitialized || !this.userId) {
            localStorage.setItem('emergencyContacts', JSON.stringify(contacts));
            return { success: true, stored_locally: true };
        }

        try {
            await this.db.collection('users').doc(this.userId).update({
                emergencyContacts: contacts,
                contactsUpdatedAt: firebase.firestore.FieldValue.serverTimestamp()
            });
            
            return { success: true };
        } catch (error) {
            console.error('Failed to store emergency contacts:', error);
            localStorage.setItem('emergencyContacts', JSON.stringify(contacts));
            return { success: true, stored_locally: true };
        }
    }

    async getEmergencyContacts() {
        if (!this.isInitialized || !this.userId) {
            const contacts = JSON.parse(localStorage.getItem('emergencyContacts') || '[]');
            return { success: true, data: contacts, source: 'local' };
        }

        try {
            const doc = await this.db.collection('users').doc(this.userId).get();
            
            if (doc.exists && doc.data().emergencyContacts) {
                return { success: true, data: doc.data().emergencyContacts };
            } else {
                return { success: true, data: [] };
            }
        } catch (error) {
            console.error('Failed to retrieve emergency contacts:', error);
            const contacts = JSON.parse(localStorage.getItem('emergencyContacts') || '[]');
            return { success: true, data: contacts, source: 'local' };
        }
    }

    // Emergency event logging
    async logEmergencyEvent(emergencyData) {
        const eventData = {
            ...emergencyData,
            userId: this.userId,
            timestamp: new Date().toISOString(),
            resolved: false
        };

        if (!this.isInitialized || !this.userId) {
            const emergencyLog = JSON.parse(localStorage.getItem('emergencyLog') || '[]');
            emergencyLog.push(eventData);
            localStorage.setItem('emergencyLog', JSON.stringify(emergencyLog));
            return { success: true, stored_locally: true };
        }

        try {
            const docRef = await this.db.collection('emergency-events').add({
                ...eventData,
                createdAt: firebase.firestore.FieldValue.serverTimestamp()
            });
            
            return { success: true, id: docRef.id };
        } catch (error) {
            console.error('Failed to log emergency event:', error);
            // Fallback to local storage
            const emergencyLog = JSON.parse(localStorage.getItem('emergencyLog') || '[]');
            emergencyLog.push(eventData);
            localStorage.setItem('emergencyLog', JSON.stringify(emergencyLog));
            return { success: true, stored_locally: true };
        }
    }

    // Doctor access methods
    async getAbnormalHealthDataForDoctors() {
        if (!this.isInitialized) {
            return { success: false, error: 'Firebase not initialized' };
        }

        try {
            const snapshot = await this.db.collection('abnormal-health-data')
                .orderBy('timestamp', 'desc')
                .limit(100)
                .get();

            const data = [];
            const userMap = new Map();

            for (const doc of snapshot.docs) {
                const record = { id: doc.id, ...doc.data() };
                
                // Get user information if not already cached
                if (!userMap.has(record.userId)) {
                    try {
                        const userDoc = await this.db.collection('users').doc(record.userId).get();
                        if (userDoc.exists) {
                            userMap.set(record.userId, {
                                email: userDoc.data().email,
                                name: userDoc.data().name
                            });
                        }
                    } catch (error) {
                        console.error('Failed to get user data:', error);
                        userMap.set(record.userId, { email: 'Unknown', name: 'Unknown' });
                    }
                }
                
                record.userInfo = userMap.get(record.userId);
                data.push(record);
            }

            return { success: true, data: data };
        } catch (error) {
            console.error('Failed to retrieve abnormal health data:', error);
            return { success: false, error: error.message };
        }
    }

    // Sync local data to Firebase when user logs in
    async syncLocalDataToFirebase() {
        if (!this.isInitialized || !this.userId) return;

        try {
            // Sync health data
            const localHealthData = JSON.parse(localStorage.getItem('healthData') || '[]');
            const unsyncedData = localHealthData.filter(record => record.stored_locally);

            for (const record of unsyncedData) {
                const { stored_locally, id, ...cleanRecord } = record;
                await this.storeHealthData(cleanRecord);
            }

            // Clear local unsynced data after successful sync
            if (unsyncedData.length > 0) {
                const syncedData = localHealthData.filter(record => !record.stored_locally);
                localStorage.setItem('healthData', JSON.stringify(syncedData));
                console.log(`Synced ${unsyncedData.length} health records to Firebase`);
            }

            // Sync emergency contacts
            const localContacts = JSON.parse(localStorage.getItem('emergencyContacts') || '[]');
            if (localContacts.length > 0) {
                await this.storeEmergencyContacts(localContacts);
                console.log('Synced emergency contacts to Firebase');
            }

        } catch (error) {
            console.error('Failed to sync local data to Firebase:', error);
        }
    }

    // Real-time health monitoring
    startRealtimeMonitoring(callback) {
        if (!this.isInitialized || !this.userId) return null;

        const healthDataRef = this.realtimeDB.ref(`health-data/${this.userId}`);
        
        const listener = healthDataRef.limitToLast(1).on('child_added', (snapshot) => {
            const data = snapshot.val();
            if (data && callback) {
                callback(data);
            }
        });

        return () => healthDataRef.off('child_added', listener);
    }

    // Analytics and insights
    async getHealthInsights(days = 30) {
        const endDate = new Date();
        const startDate = new Date();
        startDate.setDate(startDate.getDate() - days);

        const healthData = await this.getHealthData(startDate.toISOString(), endDate.toISOString(), 1000);
        
        if (!healthData.success || healthData.data.length === 0) {
            return { success: false, error: 'Insufficient data for insights' };
        }

        // Calculate insights
        const insights = this.calculateHealthInsights(healthData.data);
        return { success: true, insights: insights };
    }

    calculateHealthInsights(data) {
        const insights = {
            averageHeartRate: 0,
            averageBloodPressure: { systolic: 0, diastolic: 0 },
            averageTemperature: 0,
            abnormalReadingsCount: 0,
            mostCommonActivity: 'rest',
            trends: {
                heartRate: 'stable',
                bloodPressure: 'stable',
                temperature: 'stable'
            }
        };

        if (data.length === 0) return insights;

        // Calculate averages
        const totalHR = data.reduce((sum, record) => sum + (record.heartRate || 0), 0);
        insights.averageHeartRate = Math.round(totalHR / data.length);

        const totalSystolic = data.reduce((sum, record) => sum + (record.bloodPressure?.systolic || 0), 0);
        const totalDiastolic = data.reduce((sum, record) => sum + (record.bloodPressure?.diastolic || 0), 0);
        insights.averageBloodPressure.systolic = Math.round(totalSystolic / data.length);
        insights.averageBloodPressure.diastolic = Math.round(totalDiastolic / data.length);

        const totalTemp = data.reduce((sum, record) => sum + (record.temperature || 0), 0);
        insights.averageTemperature = Math.round((totalTemp / data.length) * 10) / 10;

        // Count abnormal readings
        insights.abnormalReadingsCount = data.filter(record => record.is_abnormal).length;

        // Find most common activity
        const activityCounts = {};
        data.forEach(record => {
            const activity = record.activityState || 'rest';
            activityCounts[activity] = (activityCounts[activity] || 0) + 1;
        });
        insights.mostCommonActivity = Object.keys(activityCounts).reduce((a, b) => 
            activityCounts[a] > activityCounts[b] ? a : b
        );

        return insights;
    }
}

// Global Firebase manager instance
window.firebaseManager = new FirebaseManager();

// Integration with existing auth system
document.addEventListener('DOMContentLoaded', function() {
    // Override existing auth methods to use Firebase when available
    if (window.authManager && window.firebaseManager.isInitialized) {
        const originalLogin = window.authManager.login.bind(window.authManager);
        const originalRegister = window.authManager.register.bind(window.authManager);
        const originalLogout = window.authManager.logout.bind(window.authManager);

        window.authManager.login = async function(email, password) {
            const firebaseResult = await window.firebaseManager.signInWithEmailAndPassword(email, password);
            if (firebaseResult.success) {
                sessionStorage.setItem('userLoggedIn', 'true');
                sessionStorage.setItem('userId', firebaseResult.user.uid);
                window.location.href = '/dashboard';
                return { success: true };
            } else {
                // Fallback to original method
                return originalLogin(email, password);
            }
        };

        window.authManager.register = async function(name, email, password) {
            const firebaseResult = await window.firebaseManager.createUserWithEmailAndPassword(email, password, { name });
            if (firebaseResult.success) {
                sessionStorage.setItem('userLoggedIn', 'true');
                sessionStorage.setItem('userId', firebaseResult.user.uid);
                window.location.href = '/dashboard';
                return { success: true };
            } else {
                // Fallback to original method
                return originalRegister(name, email, password);
            }
        };

        window.authManager.logout = async function() {
            await window.firebaseManager.signOut();
            originalLogout();
        };
    }

    // Override health data storage to use Firebase
    if (window.healthMonitor && window.firebaseManager.isInitialized) {
        const originalHandleAbnormalReading = window.healthMonitor.handleAbnormalReading.bind(window.healthMonitor);
        
        window.healthMonitor.handleAbnormalReading = async function(data, analysis) {
            // Store in Firebase
            const location = await this.getCurrentLocation();
            
            const healthRecord = {
                ...data,
                location: location,
                is_abnormal: true,
                analysis_result: analysis.message
            };

            await window.firebaseManager.storeHealthData(healthRecord);
            
            // Continue with original logic
            originalHandleAbnormalReading(data, analysis);
        };
    }

    console.log('Firebase integration initialized');
});

// Add Firebase CDN scripts dynamically
function loadFirebaseSDK() {
    const scripts = [
        'https://www.gstatic.com/firebasejs/9.22.0/firebase-app-compat.js',
        'https://www.gstatic.com/firebasejs/9.22.0/firebase-firestore-compat.js',
        'https://www.gstatic.com/firebasejs/9.22.0/firebase-auth-compat.js',
        'https://www.gstatic.com/firebasejs/9.22.0/firebase-database-compat.js'
    ];

    let loadedCount = 0;
    scripts.forEach(src => {
        const script = document.createElement('script');
        script.src = src;
        script.onload = () => {
            loadedCount++;
            if (loadedCount === scripts.length) {
                // All scripts loaded, initialize Firebase
                if (window.firebaseManager && !window.firebaseManager.isInitialized) {
                    window.firebaseManager.initializeFirebase();
                }
            }
        };
        document.head.appendChild(script);
    });
}

// Load Firebase SDK if not already loaded
if (typeof firebase === 'undefined') {
    loadFirebaseSDK();
}

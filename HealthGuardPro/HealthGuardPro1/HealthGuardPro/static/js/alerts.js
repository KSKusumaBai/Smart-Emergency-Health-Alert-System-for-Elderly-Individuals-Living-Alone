// Emergency alert and notification system
class AlertManager {
    constructor() {
        this.emergencyContacts = [];
        this.emergencyActive = false;
        this.emergencyTimeout = null;
        this.currentContactIndex = 0;
        this.userResponseReceived = false;
        this.loadEmergencyContacts();
    }

    async loadEmergencyContacts() {
        try {
            const response = await fetch('/api/emergency-contacts');
            if (response.ok) {
                this.emergencyContacts = await response.json();
            }
        } catch (error) {
            console.error('Failed to load emergency contacts:', error);
        }
    }

    async triggerEmergency(emergencyData) {
        if (this.emergencyActive) {
            console.log('Emergency already active');
            return;
        }

        this.emergencyActive = true;
        this.currentContactIndex = 0;
        this.userResponseReceived = false;

        console.log('Emergency triggered:', emergencyData);

        // Show emergency modal
        this.showEmergencyModal();

        // Get current location
        const location = window.locationManager ? 
            await window.locationManager.getCurrentLocation() : null;

        // Store emergency event
        await this.storeEmergencyEvent(emergencyData, location);

        // Start emergency protocol
        this.startEmergencyProtocol(emergencyData, location);
    }

    showEmergencyModal() {
        const modal = document.getElementById('emergencyModal');
        if (modal) {
            const bootstrapModal = new bootstrap.Modal(modal, {
                backdrop: 'static',
                keyboard: false
            });
            bootstrapModal.show();
            
            // Start countdown
            this.startUserResponseCountdown();
        }
    }

    startUserResponseCountdown() {
        let timeLeft = 60; // 60 seconds for user to respond
        const progressBar = document.querySelector('#emergencyModal .progress-bar');
        const currentContactElement = document.getElementById('currentContact');
        
        const updateCountdown = () => {
            if (this.userResponseReceived) {
                return;
            }

            const percentage = ((60 - timeLeft) / 60) * 100;
            if (progressBar) {
                progressBar.style.width = percentage + '%';
            }

            if (currentContactElement) {
                if (this.emergencyContacts.length > 0 && this.currentContactIndex < this.emergencyContacts.length) {
                    const contact = this.emergencyContacts[this.currentContactIndex];
                    currentContactElement.textContent = `${contact.name} (${contact.phone})`;
                } else {
                    currentContactElement.textContent = 'Emergency Services';
                }
            }

            timeLeft--;

            if (timeLeft >= 0 && !this.userResponseReceived) {
                setTimeout(updateCountdown, 1000);
            } else if (!this.userResponseReceived) {
                // User didn't respond, continue with emergency protocol
                this.continueEmergencyProtocol();
            }
        };

        updateCountdown();
    }

    async startEmergencyProtocol(emergencyData, location) {
        // Wait 60 seconds for user response
        this.emergencyTimeout = setTimeout(() => {
            if (!this.userResponseReceived) {
                this.continueEmergencyProtocol();
            }
        }, 60000);
    }

    async continueEmergencyProtocol() {
        if (this.userResponseReceived) return;

        // Contact emergency contacts in order
        for (let i = 0; i < this.emergencyContacts.length; i++) {
            this.currentContactIndex = i;
            const contact = this.emergencyContacts[i];
            
            console.log(`Contacting: ${contact.name} at ${contact.phone}`);
            
            // Simulate contacting (in real app, would send SMS/call)
            const contacted = await this.contactPerson(contact);
            
            if (contacted) {
                // Wait for response (simulate 1 minute per contact)
                await this.waitForResponse(60000);
                
                if (this.userResponseReceived) {
                    break;
                }
            }
        }

        // If no response from any contact, alert authorities
        if (!this.userResponseReceived) {
            await this.alertAuthorities();
        }
    }

    async contactPerson(contact) {
        try {
            // In a real implementation, this would:
            // 1. Send SMS with location and health data
            // 2. Make automated voice call
            // 3. Send push notification if they have the app
            
            const message = this.generateEmergencyMessage(contact);
            console.log(`Emergency message to ${contact.name}:`, message);
            
            // Simulate SMS/call API
            const response = await this.sendEmergencyNotification(contact, message);
            
            return response.success;
        } catch (error) {
            console.error(`Failed to contact ${contact.name}:`, error);
            return false;
        }
    }

    generateEmergencyMessage(contact) {
        const locationInfo = window.locationManager ? 
            window.locationManager.generateEmergencyLocationInfo() : 
            { available: false };

        let message = `🚨 HEALTH EMERGENCY ALERT 🚨\n\n`;
        message += `Your contact needs immediate assistance.\n\n`;
        
        if (locationInfo.available) {
            message += `Location: ${locationInfo.message}\n`;
            message += `Map: ${locationInfo.mapsUrl}\n\n`;
        }
        
        message += `Time: ${new Date().toLocaleString()}\n\n`;
        message += `If this is a false alarm, please contact them immediately.\n`;
        message += `Otherwise, please check on them or call emergency services.`;

        return message;
    }

    async sendEmergencyNotification(contact, message) {
        // Simulate sending notification
        // In production, integrate with:
        // - Twilio for SMS
        // - Voice API for calls
        // - Push notification service
        
        return new Promise((resolve) => {
            setTimeout(() => {
                resolve({ success: true, messageId: Date.now().toString() });
            }, 1000);
        });
    }

    waitForResponse(timeout) {
        return new Promise((resolve) => {
            const checkResponse = () => {
                if (this.userResponseReceived) {
                    resolve(true);
                } else {
                    setTimeout(checkResponse, 5000); // Check every 5 seconds
                }
            };
            
            setTimeout(() => resolve(false), timeout);
            checkResponse();
        });
    }

    async alertAuthorities() {
        console.log('No response from emergency contacts. Alerting authorities...');
        
        // In a real implementation, this would:
        // 1. Call emergency services API
        // 2. Send location and health data
        // 3. Provide contact information
        
        const emergencyCall = await this.makeEmergencyCall();
        
        if (emergencyCall.success) {
            console.log('Emergency services contacted successfully');
        } else {
            console.error('Failed to contact emergency services');
        }
    }

    async makeEmergencyCall() {
        // Simulate emergency services call
        return new Promise((resolve) => {
            setTimeout(() => {
                resolve({ 
                    success: true, 
                    callId: 'EMG-' + Date.now(),
                    message: 'Emergency services have been notified'
                });
            }, 2000);
        });
    }

    cancelEmergency() {
        this.userResponseReceived = true;
        this.emergencyActive = false;
        
        if (this.emergencyTimeout) {
            clearTimeout(this.emergencyTimeout);
        }
        
        // Hide modal
        const modal = bootstrap.Modal.getInstance(document.getElementById('emergencyModal'));
        if (modal) {
            modal.hide();
        }
        
        console.log('Emergency cancelled by user');
        
        // Send cancellation message to contacted people
        this.sendCancellationNotifications();
    }

    async sendCancellationNotifications() {
        // Send "false alarm" message to contacts who were notified
        for (let i = 0; i <= this.currentContactIndex && i < this.emergencyContacts.length; i++) {
            const contact = this.emergencyContacts[i];
            const message = `False alarm - Emergency alert cancelled. Your contact is safe. Thank you for your concern.`;
            
            try {
                await this.sendEmergencyNotification(contact, message);
                console.log(`Cancellation sent to ${contact.name}`);
            } catch (error) {
                console.error(`Failed to send cancellation to ${contact.name}:`, error);
            }
        }
    }

    async storeEmergencyEvent(emergencyData, location) {
        try {
            const healthData = emergencyData.healthData || {};
            const response = await fetch('/api/trigger-emergency', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    type: emergencyData.type || 'manual_trigger',
                    location: location,
                    health_data: {
                        heart_rate: healthData.heartRate,
                        blood_pressure_systolic: healthData.bloodPressure?.systolic,
                        blood_pressure_diastolic: healthData.bloodPressure?.diastolic,
                        oxygen_saturation: healthData.oxygenSaturation || healthData.oxygen_saturation,
                        temperature: healthData.temperature,
                        activity_state: healthData.activityState || healthData.activity_state
                    },
                    timestamp: new Date().toISOString()
                })
            });
            
            if (response.ok) {
                const result = await response.json();
                console.log('Emergency alert sent! Contacts notified:', result.contacts_notified);
                if (result.warning) {
                    console.warn(result.warning);
                }
                return result;
            } else {
                const error = await response.json();
                throw new Error(error.error || 'Failed to trigger emergency');
            }
        } catch (error) {
            console.error('Failed to trigger emergency:', error);
            alert('Emergency alert failed: ' + error.message);
        }
    }

    // Test emergency system
    async testEmergencySystem() {
        if (this.emergencyContacts.length === 0) {
            alert('No emergency contacts configured. Please add contacts in Settings.');
            return;
        }

        const confirm = window.confirm(
            'This will send a TEST emergency alert to your emergency contacts. Continue?'
        );
        
        if (confirm) {
            // Send test notifications
            for (const contact of this.emergencyContacts.slice(0, 1)) { // Only first contact for test
                const testMessage = `🧪 TEST ALERT 🧪\n\nThis is a test of the HealthGuard emergency system.\n\nIf you received this, the system is working correctly.\n\nNo action is required.`;
                
                try {
                    await this.sendEmergencyNotification(contact, testMessage);
                    console.log(`Test alert sent to ${contact.name}`);
                } catch (error) {
                    console.error(`Test failed for ${contact.name}:`, error);
                }
            }
            
            alert('Test emergency alert sent to your primary contact.');
        }
    }

    // Manual SOS trigger
    manualSOS() {
        const healthData = window.healthMonitor ? window.healthMonitor.currentData : null;
        
        this.triggerEmergency({
            type: 'manual_sos',
            healthData: healthData,
            trigger: 'user_initiated'
        });
    }

    // Fall detection trigger
    fallDetected(accelerometerData) {
        const healthData = window.healthMonitor ? window.healthMonitor.currentData : null;
        
        this.triggerEmergency({
            type: 'fall_detected',
            healthData: healthData,
            accelerometerData: accelerometerData,
            trigger: 'automatic'
        });
    }

    // Health abnormality trigger
    healthAbnormalityDetected(analysisResult) {
        const healthData = window.healthMonitor ? window.healthMonitor.currentData : null;
        
        this.triggerEmergency({
            type: 'health_abnormality',
            healthData: healthData,
            analysisResult: analysisResult,
            trigger: 'automatic'
        });
    }
}

// Global alert manager instance
window.alertManager = new AlertManager();

// Global functions for emergency handling
function cancelEmergency() {
    window.alertManager.cancelEmergency();
}

function testEmergencySystem() {
    window.alertManager.testEmergencySystem();
}

function manualSOS() {
    window.alertManager.manualSOS();
}

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    console.log('Alert Manager initialized');
    
    // Load emergency contacts
    window.alertManager.loadEmergencyContacts();
});

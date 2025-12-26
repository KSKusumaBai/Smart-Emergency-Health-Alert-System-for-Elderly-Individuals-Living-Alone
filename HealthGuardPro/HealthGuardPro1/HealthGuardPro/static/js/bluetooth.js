// Bluetooth device management
class BluetoothManager {
    constructor() {
        this.device = null;
        this.server = null;
        this.characteristic = null;
        this.connected = false;
        this.healthDataCallback = null;
        
        // Check if Web Bluetooth is supported
        this.isSupported = 'bluetooth' in navigator;
    }

    isConnected() {
        return this.connected;
    }

    async connect() {
        if (!this.isSupported) {
            alert('Web Bluetooth is not supported in this browser. Please use Chrome or Edge.');
            return false;
        }

        try {
            // Request device with health-related services
            this.device = await navigator.bluetooth.requestDevice({
                filters: [
                    { services: ['heart_rate'] },
                    { services: ['health_thermometer'] },
                    { namePrefix: 'Health' },
                    { namePrefix: 'Fitbit' },
                    { namePrefix: 'Garmin' },
                    { namePrefix: 'Apple Watch' }
                ],
                optionalServices: [
                    'heart_rate',
                    'health_thermometer',
                    'battery_service',
                    'device_information'
                ]
            });

            console.log('Device selected:', this.device.name);
            
            // Connect to device
            this.server = await this.device.gatt.connect();
            console.log('Connected to GATT server');
            
            this.connected = true;
            this.updateConnectionStatus();
            
            // Start monitoring health data
            await this.startHealthDataMonitoring();
            
            return true;
            
        } catch (error) {
            console.error('Bluetooth connection failed:', error);
            alert('Failed to connect to device: ' + error.message);
            return false;
        }
    }

    async startHealthDataMonitoring() {
        try {
            // Monitor heart rate if available
            await this.monitorHeartRate();
            
            // Monitor temperature if available
            await this.monitorTemperature();
            
            // Start periodic data collection
            this.startDataCollection();
            
        } catch (error) {
            console.error('Failed to start health monitoring:', error);
        }
    }

    async monitorHeartRate() {
        try {
            const service = await this.server.getPrimaryService('heart_rate');
            const characteristic = await service.getCharacteristic('heart_rate_measurement');
            
            characteristic.addEventListener('characteristicvaluechanged', (event) => {
                const heartRate = this.parseHeartRate(event.target.value);
                this.updateHealthData({ heartRate });
            });
            
            await characteristic.startNotifications();
            console.log('Heart rate monitoring started');
            
        } catch (error) {
            console.log('Heart rate service not available:', error.message);
        }
    }

    async monitorTemperature() {
        try {
            const service = await this.server.getPrimaryService('health_thermometer');
            const characteristic = await service.getCharacteristic('temperature_measurement');
            
            characteristic.addEventListener('characteristicvaluechanged', (event) => {
                const temperature = this.parseTemperature(event.target.value);
                this.updateHealthData({ temperature });
            });
            
            await characteristic.startNotifications();
            console.log('Temperature monitoring started');
            
        } catch (error) {
            console.log('Temperature service not available:', error.message);
        }
    }

    parseHeartRate(value) {
        const flags = value.getUint8(0);
        let heartRate;
        
        if (flags & 0x01) {
            heartRate = value.getUint16(1, true);
        } else {
            heartRate = value.getUint8(1);
        }
        
        return heartRate;
    }

    parseTemperature(value) {
        // Temperature in Celsius * 100
        const tempCelsius = value.getUint32(1, true) / 100;
        // Convert to Fahrenheit
        const tempFahrenheit = (tempCelsius * 9/5) + 32;
        return Math.round(tempFahrenheit * 10) / 10;
    }

    startDataCollection() {
        // Collect data every 5 seconds
        this.dataInterval = setInterval(() => {
            this.collectHealthData();
        }, 5000);
    }

    async collectHealthData() {
        try {
            // Simulate reading additional health parameters
            // In a real implementation, these would come from the device
            const mockData = this.generateMockHealthData();
            this.updateHealthData(mockData);
            
        } catch (error) {
            console.error('Failed to collect health data:', error);
        }
    }

    generateMockHealthData() {
        // Generate realistic health data for demonstration
        // In production, this would come from actual device readings
        return {
            heartRate: 70 + Math.round((Math.random() - 0.5) * 30),
            bloodPressure: {
                systolic: 120 + Math.round((Math.random() - 0.5) * 20),
                diastolic: 80 + Math.round((Math.random() - 0.5) * 15)
            },
            temperature: 98.6 + ((Math.random() - 0.5) * 2),
            activityState: this.detectActivity()
        };
    }

    detectActivity() {
        // Simple activity detection based on heart rate patterns
        // In a real implementation, this would use accelerometer data
        const activities = ['rest', 'light', 'moderate', 'sleep'];
        const weights = [0.4, 0.3, 0.2, 0.1]; // Probability weights
        
        let random = Math.random();
        for (let i = 0; i < activities.length; i++) {
            random -= weights[i];
            if (random <= 0) {
                return activities[i];
            }
        }
        return 'rest';
    }

    updateHealthData(data) {
        if (this.healthDataCallback) {
            this.healthDataCallback(data);
        }
        
        // Update main health monitor if available
        if (window.healthMonitor) {
            Object.assign(window.healthMonitor.currentData, data);
            window.healthMonitor.updateDisplay(window.healthMonitor.currentData);
        }
    }

    updateConnectionStatus() {
        const statusElement = document.getElementById('deviceStatus');
        if (!statusElement) return;
        
        if (this.connected) {
            statusElement.innerHTML = `
                <i class="fas fa-bluetooth fa-3x text-success mb-3 device-connected"></i>
                <p class="text-success mb-2">Connected to ${this.device.name}</p>
                <button class="btn btn-outline-danger btn-sm" onclick="disconnectDevice()">
                    <i class="fas fa-unlink me-2"></i>Disconnect
                </button>
            `;
        } else {
            statusElement.innerHTML = `
                <i class="fas fa-bluetooth fa-3x text-muted mb-3"></i>
                <p class="text-muted">No device connected</p>
                <button class="btn btn-primary" onclick="connectToDevice()">
                    <i class="fas fa-bluetooth me-2"></i>Connect Watch
                </button>
            `;
        }
    }

    async disconnect() {
        try {
            if (this.dataInterval) {
                clearInterval(this.dataInterval);
            }
            
            if (this.server && this.server.connected) {
                await this.server.disconnect();
            }
            
            this.connected = false;
            this.device = null;
            this.server = null;
            this.characteristic = null;
            
            this.updateConnectionStatus();
            console.log('Disconnected from device');
            
        } catch (error) {
            console.error('Disconnect error:', error);
        }
    }

    setHealthDataCallback(callback) {
        this.healthDataCallback = callback;
    }
}

// Global bluetooth manager instance
window.bluetoothManager = new BluetoothManager();

// Global disconnect function
function disconnectDevice() {
    window.bluetoothManager.disconnect();
    
    // Start simulation mode after disconnect
    if (window.healthMonitor) {
        setTimeout(() => {
            window.healthMonitor.startSimulation();
        }, 1000);
    }
}

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    // Update initial connection status
    window.bluetoothManager.updateConnectionStatus();
});

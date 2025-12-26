// Location tracking and management
class LocationManager {
    constructor() {
        this.currentLocation = null;
        this.watchId = null;
        this.isTracking = false;
        this.locationHistory = [];
        this.checkPermissions();
    }

    async checkPermissions() {
        if (!navigator.geolocation) {
            console.warn('Geolocation is not supported by this browser');
            return false;
        }

        try {
            const permission = await navigator.permissions.query({ name: 'geolocation' });
            console.log('Geolocation permission:', permission.state);
            return permission.state === 'granted';
        } catch (error) {
            console.warn('Could not check geolocation permission:', error);
            return false;
        }
    }

    async getCurrentLocation() {
        return new Promise((resolve, reject) => {
            if (!navigator.geolocation) {
                reject(new Error('Geolocation not supported'));
                return;
            }

            const options = {
                enableHighAccuracy: true,
                timeout: 10000,
                maximumAge: 60000 // 1 minute
            };

            navigator.geolocation.getCurrentPosition(
                (position) => {
                    const location = {
                        latitude: position.coords.latitude,
                        longitude: position.coords.longitude,
                        accuracy: position.coords.accuracy,
                        timestamp: new Date().toISOString()
                    };
                    
                    this.currentLocation = location;
                    resolve(location);
                },
                (error) => {
                    console.error('Geolocation error:', error);
                    resolve(null); // Don't reject, just return null
                },
                options
            );
        });
    }

    startTracking() {
        if (!navigator.geolocation || this.isTracking) {
            return false;
        }

        const options = {
            enableHighAccuracy: false, // Less battery intensive
            timeout: 30000,
            maximumAge: 300000 // 5 minutes
        };

        this.watchId = navigator.geolocation.watchPosition(
            (position) => {
                const location = {
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                    accuracy: position.coords.accuracy,
                    timestamp: new Date().toISOString()
                };
                
                this.currentLocation = location;
                this.addToHistory(location);
                
                console.log('Location updated:', location);
            },
            (error) => {
                console.error('Location tracking error:', error);
            },
            options
        );

        this.isTracking = true;
        console.log('Location tracking started');
        return true;
    }

    stopTracking() {
        if (this.watchId !== null) {
            navigator.geolocation.clearWatch(this.watchId);
            this.watchId = null;
            this.isTracking = false;
            console.log('Location tracking stopped');
        }
    }

    addToHistory(location) {
        this.locationHistory.push(location);
        
        // Keep only last 50 locations
        if (this.locationHistory.length > 50) {
            this.locationHistory.shift();
        }
    }

    getLocationHistory() {
        return this.locationHistory;
    }

    // Get human-readable address from coordinates
    async getAddressFromCoordinates(latitude, longitude) {
        try {
            // Using a free geocoding service (in production, use a proper API key)
            const response = await fetch(
                `https://api.opencagedata.com/geocode/v1/json?q=${latitude}+${longitude}&key=${
                    process.env.GEOCODING_API_KEY || 'demo_key'
                }&limit=1`
            );
            
            if (response.ok) {
                const data = await response.json();
                if (data.results && data.results.length > 0) {
                    return data.results[0].formatted;
                }
            }
        } catch (error) {
            console.error('Reverse geocoding failed:', error);
        }
        
        // Fallback to coordinates
        return `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`;
    }

    // Calculate distance between two points (in meters)
    calculateDistance(lat1, lon1, lat2, lon2) {
        const R = 6371e3; // Earth's radius in meters
        const φ1 = lat1 * Math.PI / 180;
        const φ2 = lat2 * Math.PI / 180;
        const Δφ = (lat2 - lat1) * Math.PI / 180;
        const Δλ = (lon2 - lon1) * Math.PI / 180;

        const a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
                  Math.cos(φ1) * Math.cos(φ2) *
                  Math.sin(Δλ/2) * Math.sin(Δλ/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    }

    // Detect if user has moved significantly
    hasMovedSignificantly(threshold = 100) { // 100 meters default
        if (this.locationHistory.length < 2) return false;
        
        const current = this.currentLocation;
        const previous = this.locationHistory[this.locationHistory.length - 2];
        
        if (!current || !previous) return false;
        
        const distance = this.calculateDistance(
            previous.latitude, previous.longitude,
            current.latitude, current.longitude
        );
        
        return distance > threshold;
    }

    // Generate location context for emergency
    generateEmergencyLocationInfo() {
        if (!this.currentLocation) {
            return {
                available: false,
                message: 'Location not available'
            };
        }

        return {
            available: true,
            coordinates: {
                latitude: this.currentLocation.latitude,
                longitude: this.currentLocation.longitude
            },
            accuracy: this.currentLocation.accuracy,
            timestamp: this.currentLocation.timestamp,
            mapsUrl: `https://maps.google.com/?q=${this.currentLocation.latitude},${this.currentLocation.longitude}`,
            message: `Location: ${this.currentLocation.latitude.toFixed(6)}, ${this.currentLocation.longitude.toFixed(6)} (±${Math.round(this.currentLocation.accuracy)}m)`
        };
    }

    // Check if user is in a safe zone (e.g., home, office)
    isInSafeZone() {
        // This would check against predefined safe zones
        // For demo purposes, we'll return a random value
        const safeZones = JSON.parse(localStorage.getItem('safeZones') || '[]');
        
        if (!this.currentLocation || safeZones.length === 0) {
            return { inSafeZone: false, zoneName: null };
        }

        for (const zone of safeZones) {
            const distance = this.calculateDistance(
                this.currentLocation.latitude,
                this.currentLocation.longitude,
                zone.latitude,
                zone.longitude
            );
            
            if (distance <= zone.radius) {
                return { inSafeZone: true, zoneName: zone.name };
            }
        }
        
        return { inSafeZone: false, zoneName: null };
    }

    // Add a safe zone
    addSafeZone(name, latitude, longitude, radius = 200) {
        const safeZones = JSON.parse(localStorage.getItem('safeZones') || '[]');
        
        safeZones.push({
            name,
            latitude,
            longitude,
            radius,
            createdAt: new Date().toISOString()
        });
        
        localStorage.setItem('safeZones', JSON.stringify(safeZones));
    }

    // Get safe zones
    getSafeZones() {
        return JSON.parse(localStorage.getItem('safeZones') || '[]');
    }

    // Emergency location sharing
    async shareLocationForEmergency() {
        const location = await this.getCurrentLocation();
        const locationInfo = this.generateEmergencyLocationInfo();
        
        if (locationInfo.available) {
            // In a real implementation, this would send the location to emergency services
            console.log('Emergency location shared:', locationInfo);
            
            return {
                success: true,
                location: locationInfo,
                shareUrl: locationInfo.mapsUrl
            };
        }
        
        return {
            success: false,
            error: 'Location not available'
        };
    }
}

// Global location manager instance
window.locationManager = new LocationManager();

// Initialize location tracking when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    // Check privacy settings
    const privacySettings = JSON.parse(localStorage.getItem('privacySettings') || '{"locationTracking": true}');
    
    if (privacySettings.locationTracking) {
        window.locationManager.startTracking();
    }
    
    console.log('Location Manager initialized');
});

// Add current location as home (utility function)
async function addCurrentLocationAsHome() {
    const location = await window.locationManager.getCurrentLocation();
    if (location) {
        window.locationManager.addSafeZone('Home', location.latitude, location.longitude);
        alert('Current location saved as Home');
    } else {
        alert('Could not get current location');
    }
}

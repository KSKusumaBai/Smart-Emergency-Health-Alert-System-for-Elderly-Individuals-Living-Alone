# HealthGuard - Smart Health Monitoring System

## Overview

HealthGuard is a comprehensive health monitoring platform designed for seniors that integrates real Bluetooth health sensors, Firebase cloud storage, and Twilio-powered emergency response systems. The application provides real-time health tracking with continuous monitoring of vital signs like heart rate, blood pressure, oxygen saturation, and body temperature from actual BLE devices, coupled with intelligent anomaly detection and automated emergency SMS alerts.

The system features a patient dashboard for health data visualization, Firebase-based authentication and data storage, a dedicated doctor portal for medical professionals to review abnormal readings, and a robust Twilio-powered emergency contact system that automatically sends SMS alerts with location data when health anomalies are detected.

## Recent Updates (October 8, 2025)

### Major Enhancements Completed
- **Firebase Integration**: Replaced in-memory storage with Firebase Authentication and Realtime Database
- **Bluetooth BLE Support**: Created bluetooth_service.py module using bleak library for real-time sensor data from BLE health devices
- **Twilio SMS Alerts**: Integrated Twilio API (via Replit connector) for sending emergency SMS with health data and GPS location
- **User Profile Management**: Added comprehensive profile page for editing user details and emergency contacts
- **Automatic Emergency Triggers**: Critical vitals (HR <40 or >140, BP >180/110, SpO2 <90, Temp <95°F or >103°F) automatically trigger SMS alerts
- **Secure Authentication**: Implemented Firebase Auth REST API for proper password verification during login

## User Preferences

Preferred communication style: Simple, everyday language.

## System Architecture

### Frontend Architecture
- **Framework**: Vanilla JavaScript with Bootstrap 5 for responsive UI components
- **Visualization**: Chart.js for real-time health data graphs and trend analysis
- **PWA Features**: Designed for mobile-first experience with offline capabilities
- **Modular Design**: Separate JavaScript modules for different functionalities (auth, bluetooth, alerts, ML analysis)

### Backend Architecture
- **Framework**: Flask (Python) with session-based authentication
- **Database**: Firebase Realtime Database for user profiles, health records, and emergency logs
- **Authentication**: Firebase Authentication with email/password via REST API
- **API Design**: RESTful endpoints for user management, health data storage, and emergency triggers
- **SMS Service**: Twilio integration for emergency alerts via Replit connector

### Real-time Data Processing
- **Device Integration**: 
  - Web Bluetooth API for browser-based smartwatch connectivity (frontend)
  - Bleak library for server-side BLE device communication (backend)
  - Support for standard GATT health services (heart_rate, health_thermometer, blood_pressure, pulse_oximeter)
- **ML Analysis**: TensorFlow.js integration for client-side health pattern analysis and anomaly detection
- **Activity-based Thresholds**: Dynamic health parameter validation based on user activity state (rest, active, sleep, etc.)

### Emergency Response System
- **Multi-tier Alerting**: SMS alerts sent to emergency contacts in priority order via Twilio
- **Location Services**: Geolocation API integration for emergency location sharing via Google Maps links
- **Automated Responses**: 
  - Automatic SMS triggers when critical vitals detected
  - Manual SOS button for user-initiated emergencies
  - Includes current health data and GPS coordinates in alert messages
- **Twilio Integration**: Real SMS delivery using Twilio REST API through Replit connector

### External Dependencies

#### Backend Services
- **Firebase Admin SDK**: User authentication and Realtime Database operations
- **Twilio Python SDK**: SMS alert delivery
- **Bleak**: Bluetooth Low Energy device communication
- **Flask**: Web framework and API server
- **Python-dotenv**: Environment variable management

#### Frontend Libraries
- **Bootstrap 5**: Responsive design framework
- **Chart.js**: Data visualization and trend charts
- **Font Awesome**: Icon library
- **TensorFlow.js**: Machine learning for health analysis
- **Web Bluetooth API**: Browser-based BLE device connectivity

#### Cloud Services
- **Firebase Authentication**: Email/password user authentication
- **Firebase Realtime Database**: Real-time data synchronization and storage
- **Twilio SMS**: Emergency alert delivery via SMS
- **Geolocation API**: GPS location tracking for emergencies

## Key Features

### 1. Real-time Bluetooth Health Monitoring
- Connect to BLE health sensors (smartwatches, fitness trackers, medical devices)
- Monitor heart rate, blood pressure, oxygen saturation, and temperature
- Standard GATT service support for compatibility with multiple device types
- Automatic data streaming and storage in Firebase

### 2. Firebase Cloud Integration
- Secure user authentication with email/password
- Real-time database for health records under authenticated users
- User profile management with medical history
- Emergency contact storage and management

### 3. SOS Emergency System
- **Manual Trigger**: SOS button on dashboard
- **Automatic Trigger**: Critical vitals detection
- **SMS Alerts**: Twilio-powered messages with:
  - Current vital signs
  - GPS location with Google Maps link
  - Timestamp and emergency type
- **Multi-contact Support**: Alerts sent to all emergency contacts

### 4. Dashboard & Visualization
- Real-time vital signs display
- Color-coded health status indicators
- Trend charts for heart rate and blood pressure
- Recent alerts and abnormal readings log
- Activity state monitoring

### 5. Settings & Profile
- User profile management (name, age, phone, medical conditions)
- Emergency contact configuration (name, phone, relationship)
- Health threshold customization
- Privacy and data sharing settings

## Environment Variables Required

```
# Firebase Configuration
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_PRIVATE_KEY=your-private-key
FIREBASE_CLIENT_EMAIL=your-client-email
FIREBASE_DATABASE_URL=https://your-project.firebaseio.com
FIREBASE_WEB_API_KEY=your-web-api-key

# Twilio Configuration (via Replit connector)
TWILIO_ACCOUNT_SID=your-account-sid
TWILIO_AUTH_TOKEN=your-auth-token
TWILIO_PHONE_NUMBER=your-twilio-number

# Session Security
SESSION_SECRET=random-secret-key
```

## File Structure

```
├── app.py                          # Main Flask application with all API endpoints
├── bluetooth_service.py            # BLE device communication module using bleak
├── templates/
│   ├── index.html                 # Login/signup page
│   ├── dashboard.html             # Main dashboard with vitals and charts
│   ├── settings.html              # User profile and emergency contacts
│   └── doctor-portal.html         # Doctor access to abnormal data
├── static/
│   ├── css/
│   │   └── styles.css            # Custom styles
│   ├── js/
│   │   ├── auth.js               # Authentication handling
│   │   ├── bluetooth.js          # Web Bluetooth API integration
│   │   ├── main.js               # Dashboard logic and charts
│   │   ├── alerts.js             # Emergency alert system
│   │   ├── location.js           # GPS location tracking
│   │   ├── ml-analysis.js        # Health data analysis
│   │   └── firebase-config.js    # Firebase client configuration
│   └── images/
│       └── logo.svg              # Application logo
└── replit.md                      # This file
```

## How to Use

### For Patients

1. **Sign Up/Login**: Create account or login with email/password
2. **Connect Device**: 
   - Click "Connect Watch" on dashboard
   - Select your BLE health device from the list
   - Grant Bluetooth permissions
3. **Monitor Health**: View real-time vitals on dashboard
4. **Set Up Emergency Contacts**: 
   - Go to Settings
   - Add emergency contacts with phone numbers
   - Save contacts
5. **Emergency Use**: 
   - Manual: Click SOS button
   - Automatic: System sends alerts if vitals are critical

### For Doctors

1. Access doctor portal with authorization key
2. View abnormal health readings from all patients
3. Review patient health history and trends

### Bluetooth Device Setup

The system supports standard BLE health devices with these GATT services:
- Heart Rate Service (0x180D)
- Health Thermometer Service (0x1809)
- Blood Pressure Service (0x1810)
- Pulse Oximeter Service (0x1822)

Compatible devices include Fitbit, Garmin, Polar, and other BLE health sensors.

## Critical Vitals Thresholds

Automatic emergency alerts triggered when:
- Heart Rate: < 40 or > 140 bpm
- Blood Pressure: Systolic < 80 or > 180 mmHg, Diastolic < 50 or > 110 mmHg
- Oxygen Saturation: < 90%
- Temperature: < 95°F or > 103°F

## Security Features

- Firebase Authentication with password verification via REST API
- Session-based user management
- Secure environment variable storage for API keys
- HTTPS-only communication for production deployment
- Emergency contact data encryption in Firebase

## Known Limitations

1. **Web Bluetooth API**: Only supported in Chrome, Edge, and Opera browsers
2. **BLE Device Compatibility**: Requires devices with standard GATT health services
3. **Development Server**: Flask development server used (replace with Gunicorn for production)
4. **Password Reset**: Not yet implemented (future enhancement)

## Future Enhancements

- Historical data export and PDF reports
- Medication reminders and tracking
- Multi-device support with device switching
- Geofencing alerts for safe zones
- Video call integration for emergencies
- Apple Health and Google Fit integration

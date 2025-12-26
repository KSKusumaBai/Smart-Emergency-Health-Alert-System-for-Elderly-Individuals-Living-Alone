from flask import Flask, render_template, request, jsonify, session, redirect, url_for
import os
import json
from datetime import datetime, timedelta
import secrets
import firebase_admin
from firebase_admin import credentials, auth, db
from twilio.rest import Client
from dotenv import load_dotenv
import asyncio
from threading import Thread
import sys

# Load environment variables
load_dotenv()

app = Flask(__name__)
app.secret_key = os.getenv('SESSION_SECRET', secrets.token_hex(16))

# Validate required environment variables at startup
required_env_vars = [
    'FIREBASE_PROJECT_ID',
    'FIREBASE_PRIVATE_KEY',
    'FIREBASE_CLIENT_EMAIL',
    'FIREBASE_DATABASE_URL',
    'FIREBASE_WEB_API_KEY'
]

missing_vars = [var for var in required_env_vars if not os.getenv(var)]
if missing_vars:
    raise ValueError(f"Missing required environment variables: {', '.join(missing_vars)}")

# Initialize Firebase Admin SDK
firebase_cred = credentials.Certificate({
    "type": "service_account",
    "project_id": os.getenv('FIREBASE_PROJECT_ID'),
    "private_key": os.getenv('FIREBASE_PRIVATE_KEY').replace('\\n', '\n'),
    "client_email": os.getenv('FIREBASE_CLIENT_EMAIL'),
    "token_uri": "https://oauth2.googleapis.com/token",
    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
    "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs"
})

firebase_admin.initialize_app(firebase_cred, {
    'databaseURL': os.getenv('FIREBASE_DATABASE_URL')
})

# Initialize Twilio Client (using Replit connector)
try:
    twilio_account_sid = os.getenv('TWILIO_ACCOUNT_SID')
    twilio_auth_token = os.getenv('TWILIO_AUTH_TOKEN')
    twilio_phone_number = os.getenv('TWILIO_PHONE_NUMBER')
    twilio_client = Client(twilio_account_sid, twilio_auth_token) if twilio_account_sid else None
except Exception as e:
    print(f"Twilio initialization warning: {e}")
    twilio_client = None

@app.route('/')
def index():
    if 'user_id' in session:
        return redirect(url_for('dashboard'))
    return render_template('index.html')

@app.route('/dashboard')
def dashboard():
    if 'user_id' not in session:
        return redirect(url_for('index'))
    return render_template('dashboard.html')

@app.route('/doctor-portal')
def doctor_portal():
    return render_template('doctor-portal.html')

@app.route('/settings')
def settings():
    if 'user_id' not in session:
        return redirect(url_for('index'))
    return render_template('settings.html')

@app.route('/alerts')
def alerts():
    if 'user_id' not in session:
        return redirect(url_for('index'))
    return render_template('alerts.html')

@app.route('/api/register', methods=['POST'])
def register():
    data = request.json or {}
    email = data.get('email')
    password = data.get('password')
    name = data.get('name')
    
    if not email or not password or not name:
        return jsonify({'error': 'All fields are required'}), 400
    
    try:
        # Create user in Firebase Auth
        user = auth.create_user(
            email=email,
            password=password,
            display_name=name
        )
        
        # Store user profile in Realtime Database
        user_ref = db.reference(f'users/{user.uid}')
        user_ref.set({
            'name': name,
            'email': email,
            'created_at': datetime.now().isoformat(),
            'emergency_contacts': []
        })
        
        session['user_id'] = user.uid
        session['user_email'] = email
        session['user_name'] = name
        
        return jsonify({'success': True, 'user_id': user.uid})
    
    except auth.EmailAlreadyExistsError:
        return jsonify({'error': 'Email already registered'}), 400
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/login', methods=['POST'])
def login():
    data = request.json or {}
    email = data.get('email')
    password = data.get('password')
    
    if not email or not password:
        return jsonify({'error': 'Email and password are required'}), 400
    
    # Get Firebase Web API Key (already validated at startup)
    firebase_api_key = os.getenv('FIREBASE_WEB_API_KEY')
    
    try:
        import requests as req
        
        # Firebase Auth REST API endpoint for password verification
        auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={firebase_api_key}"
        
        auth_response = req.post(auth_url, json={
            'email': email,
            'password': password,
            'returnSecureToken': True
        }, timeout=10)
        
        if auth_response.status_code != 200:
            error_data = auth_response.json()
            error_message = error_data.get('error', {}).get('message', 'Invalid credentials')
            return jsonify({'error': error_message}), 401
        
        auth_data = auth_response.json()
        user_id = auth_data.get('localId')
        
        if not user_id:
            return jsonify({'error': 'Authentication failed'}), 401
        
        # Get user profile from database
        user_ref = db.reference(f'users/{user_id}')
        user_data = user_ref.get()
        
        # Create session only after successful Firebase verification
        session['user_id'] = user_id
        session['user_email'] = email
        session['user_name'] = user_data.get('name', '') if user_data else ''
        
        return jsonify({
            'success': True, 
            'user_id': user_id,
            'id_token': auth_data.get('idToken')
        })
    
    except req.exceptions.RequestException as e:
        print(f"Firebase Auth API error: {e}")
        return jsonify({'error': 'Authentication service unavailable'}), 503
    except Exception as e:
        print(f"Login error: {e}")
        return jsonify({'error': 'Authentication failed'}), 401

@app.route('/api/logout', methods=['POST'])
def logout():
    session.clear()
    return jsonify({'success': True})

@app.route('/api/health-data', methods=['POST'])
def store_health_data():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
    
    data = request.json or {}
    user_id = session['user_id']
    
    try:
        # Create health record
        health_record = {
            'timestamp': datetime.now().isoformat(),
            'heart_rate': data.get('heart_rate'),
            'blood_pressure_systolic': data.get('blood_pressure_systolic'),
            'blood_pressure_diastolic': data.get('blood_pressure_diastolic'),
            'oxygen_saturation': data.get('oxygen_saturation'),
            'temperature': data.get('temperature'),
            'activity_state': data.get('activity_state', 'rest'),
            'location': data.get('location'),
            'is_abnormal': data.get('is_abnormal', False),
            'analysis_result': data.get('analysis_result')
        }
        
        # Check for critical vitals and trigger automatic SOS
        is_critical = check_critical_vitals(health_record)
        if is_critical:
            health_record['is_abnormal'] = True
            health_record['critical_alert'] = True
            # Trigger automatic emergency alert in background
            Thread(target=auto_trigger_emergency, args=(user_id, health_record)).start()
        
        # Store in Firebase Realtime Database
        health_ref = db.reference(f'health_data/{user_id}')
        new_record = health_ref.push(health_record)
        
        return jsonify({
            'success': True, 
            'record_id': new_record.key,
            'is_critical': is_critical
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/health-data', methods=['GET'])
def get_health_data():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
    
    user_id = session['user_id']
    
    try:
        # Get health data from Firebase
        health_ref = db.reference(f'health_data/{user_id}')
        data = health_ref.get()
        
        if not data:
            return jsonify([])
        
        # Convert to list format with record IDs
        health_records = []
        for record_id, record in data.items():
            record['id'] = record_id
            health_records.append(record)
        
        # Sort by timestamp (newest first)
        health_records.sort(key=lambda x: x.get('timestamp', ''), reverse=True)
        
        # Filter by date range if provided
        start_date = request.args.get('start_date')
        end_date = request.args.get('end_date')
        limit = request.args.get('limit', type=int)
        
        if start_date and end_date:
            filtered_data = []
            for record in health_records:
                record_date = datetime.fromisoformat(record['timestamp']).date()
                if datetime.fromisoformat(start_date).date() <= record_date <= datetime.fromisoformat(end_date).date():
                    filtered_data.append(record)
            health_records = filtered_data
        
        if limit:
            health_records = health_records[:limit]
        
        return jsonify(health_records)
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/user-profile', methods=['GET'])
def get_user_profile():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
    
    user_id = session['user_id']
    
    try:
        user_ref = db.reference(f'users/{user_id}')
        user_data = user_ref.get()
        
        if not user_data:
            return jsonify({'error': 'User not found'}), 404
        
        return jsonify(user_data)
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/user-profile', methods=['PUT'])
def update_user_profile():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
    
    user_id = session['user_id']
    data = request.json or {}
    
    try:
        user_ref = db.reference(f'users/{user_id}')
        
        # Update allowed fields
        update_data = {}
        if 'name' in data:
            update_data['name'] = data['name']
            session['user_name'] = data['name']
        if 'phone' in data:
            update_data['phone'] = data['phone']
        if 'age' in data:
            update_data['age'] = data['age']
        if 'medical_conditions' in data:
            update_data['medical_conditions'] = data['medical_conditions']
        
        update_data['updated_at'] = datetime.now().isoformat()
        user_ref.update(update_data)
        
        return jsonify({'success': True})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/emergency-contacts', methods=['POST'])
def save_emergency_contacts():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
    
    data = request.json or {}
    user_id = session['user_id']
    contacts = data.get('contacts', [])
    
    try:
        user_ref = db.reference(f'users/{user_id}')
        user_ref.update({
            'emergency_contacts': contacts,
            'updated_at': datetime.now().isoformat()
        })
        
        return jsonify({'success': True})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/emergency-contacts', methods=['GET'])
def get_emergency_contacts():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
    
    user_id = session['user_id']
    
    try:
        user_ref = db.reference(f'users/{user_id}')
        user_data = user_ref.get()
        
        contacts = user_data.get('emergency_contacts', []) if user_data else []
        return jsonify(contacts)
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/trigger-emergency', methods=['POST'])
def trigger_emergency():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
    
    data = request.json or {}
    user_id = session['user_id']
    
    try:
        # Get user data and emergency contacts
        user_ref = db.reference(f'users/{user_id}')
        user_data = user_ref.get()
        
        emergency_contacts = user_data.get('emergency_contacts', [])
        
        if not emergency_contacts:
            return jsonify({'error': 'No emergency contacts configured'}), 400
        
        # Get location and health data
        location = data.get('location', {})
        health_data = data.get('health_data', {})
        emergency_type = data.get('type', 'manual_trigger')
        
        # Create emergency log
        emergency_ref = db.reference(f'emergencies/{user_id}')
        emergency_record = {
            'type': emergency_type,
            'timestamp': datetime.now().isoformat(),
            'location': location,
            'health_data': health_data,
            'status': 'triggered',
            'contacts_notified': []
        }
        
        new_emergency = emergency_ref.push(emergency_record)
        emergency_id = new_emergency.key
        
        # Send SMS alerts to emergency contacts
        if twilio_client and twilio_phone_number:
            sent_contacts = send_emergency_sms(
                user_data, 
                emergency_contacts, 
                location, 
                health_data,
                emergency_type
            )
            
            # Update emergency record with notification status
            new_emergency.update({'contacts_notified': sent_contacts})
            
            return jsonify({
                'success': True, 
                'emergency_id': emergency_id,
                'contacts_notified': len(sent_contacts)
            })
        else:
            return jsonify({
                'success': True,
                'emergency_id': emergency_id,
                'warning': 'Twilio not configured - SMS not sent'
            })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/bluetooth/scan', methods=['POST'])
def bluetooth_scan():
    """Endpoint to initiate Bluetooth device scanning"""
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
    
    # This endpoint returns available devices
    # The actual BLE scanning happens on the backend using bleak
    return jsonify({
        'success': True,
        'message': 'Use the Bluetooth service module for device scanning'
    })

@app.route('/api/doctor/abnormal-data', methods=['GET'])
def get_abnormal_data():
    # Doctor access to abnormal health data
    doctor_key = request.headers.get('Authorization')
    if not doctor_key or doctor_key != f"Bearer {os.getenv('DOCTOR_ACCESS_KEY', 'doctor123')}":
        return jsonify({'error': 'Unauthorized'}), 401
    
    try:
        all_abnormal_data = []
        users_ref = db.reference('users')
        users = users_ref.get()
        
        if users:
            for user_id, user_data in users.items():
                health_ref = db.reference(f'health_data/{user_id}')
                health_records = health_ref.get()
                
                if health_records:
                    abnormal_records = [
                        {**record, 'id': record_id} 
                        for record_id, record in health_records.items() 
                        if record.get('is_abnormal', False)
                    ]
                    
                    if abnormal_records:
                        all_abnormal_data.append({
                            'user_id': user_id,
                            'user_email': user_data.get('email'),
                            'user_name': user_data.get('name'),
                            'records': abnormal_records
                        })
        
        return jsonify(all_abnormal_data)
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

def check_critical_vitals(health_data):
    """Check if vitals are in critical range"""
    heart_rate = health_data.get('heart_rate')
    bp_systolic = health_data.get('blood_pressure_systolic')
    bp_diastolic = health_data.get('blood_pressure_diastolic')
    oxygen = health_data.get('oxygen_saturation')
    temp = health_data.get('temperature')
    
    # Critical thresholds
    if heart_rate and (heart_rate < 40 or heart_rate > 140):
        return True
    if bp_systolic and (bp_systolic < 80 or bp_systolic > 180):
        return True
    if bp_diastolic and (bp_diastolic < 50 or bp_diastolic > 110):
        return True
    if oxygen and oxygen < 90:
        return True
    if temp and (temp < 95 or temp > 103):
        return True
    
    return False

def auto_trigger_emergency(user_id, health_data):
    """Automatically trigger emergency alert for critical vitals"""
    try:
        user_ref = db.reference(f'users/{user_id}')
        user_data = user_ref.get()
        
        if not user_data:
            return
        
        emergency_contacts = user_data.get('emergency_contacts', [])
        
        if emergency_contacts and twilio_client:
            # Create emergency record
            emergency_ref = db.reference(f'emergencies/{user_id}')
            emergency_record = {
                'type': 'automatic_critical_vitals',
                'timestamp': datetime.now().isoformat(),
                'health_data': health_data,
                'status': 'auto_triggered'
            }
            
            new_emergency = emergency_ref.push(emergency_record)
            
            # Send SMS alerts
            sent_contacts = send_emergency_sms(
                user_data,
                emergency_contacts,
                {},
                health_data,
                'automatic_critical_vitals'
            )
            
            new_emergency.update({'contacts_notified': sent_contacts})
    
    except Exception as e:
        print(f"Auto emergency trigger error: {e}")

def send_emergency_sms(user_data, contacts, location, health_data, emergency_type):
    """Send SMS alerts to emergency contacts using Twilio"""
    sent_contacts = []
    
    if not twilio_client or not twilio_phone_number:
        return sent_contacts
    
    user_name = user_data.get('name', 'Unknown')
    
    # Build message
    if emergency_type == 'automatic_critical_vitals':
        alert_msg = f"🚨 CRITICAL HEALTH ALERT for {user_name}!\n\n"
        alert_msg += "Critical vital signs detected:\n"
    else:
        alert_msg = f"🚨 EMERGENCY ALERT from {user_name}!\n\n"
        alert_msg += "SOS button activated.\n"
    
    # Add health data
    if health_data:
        if health_data.get('heart_rate'):
            alert_msg += f"Heart Rate: {health_data['heart_rate']} bpm\n"
        if health_data.get('blood_pressure_systolic'):
            alert_msg += f"Blood Pressure: {health_data['blood_pressure_systolic']}/{health_data.get('blood_pressure_diastolic', '--')} mmHg\n"
        if health_data.get('oxygen_saturation'):
            alert_msg += f"Oxygen: {health_data['oxygen_saturation']}%\n"
        if health_data.get('temperature'):
            alert_msg += f"Temperature: {health_data['temperature']}°F\n"
    
    # Add location
    if location and location.get('latitude'):
        lat = location['latitude']
        lng = location['longitude']
        alert_msg += f"\nLocation: https://maps.google.com/?q={lat},{lng}\n"
    
    alert_msg += "\nPlease check on them immediately!"
    
    # Send to each contact
    for contact in contacts:
        try:
            phone = contact.get('phone', '').strip()
            if phone:
                # Ensure phone number has country code
                if not phone.startswith('+'):
                    phone = '+1' + phone.replace('-', '').replace(' ', '')
                
                message = twilio_client.messages.create(
                    body=alert_msg,
                    from_=twilio_phone_number,
                    to=phone
                )
                
                sent_contacts.append({
                    'name': contact.get('name'),
                    'phone': phone,
                    'message_sid': message.sid,
                    'status': 'sent'
                })
        except Exception as e:
            print(f"Failed to send SMS to {contact.get('name')}: {e}")
            sent_contacts.append({
                'name': contact.get('name'),
                'phone': phone,
                'status': 'failed',
                'error': str(e)
            })
    
    return sent_contacts

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)

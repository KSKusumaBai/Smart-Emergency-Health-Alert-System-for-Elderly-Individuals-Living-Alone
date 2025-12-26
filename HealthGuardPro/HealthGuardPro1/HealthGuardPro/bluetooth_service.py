"""
Bluetooth Low Energy (BLE) Service for Health Monitoring
Uses bleak library to connect to Bluetooth health sensors
"""

import asyncio
from bleak import BleakScanner, BleakClient
from typing import Optional, Dict, Callable
import struct
from datetime import datetime

# Standard Bluetooth GATT UUIDs for health services
HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
HEART_RATE_MEASUREMENT_UUID = "00002a37-0000-1000-8000-00805f9b34fb"
BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"
BATTERY_LEVEL_UUID = "00002a19-0000-1000-8000-00805f9b34fb"
HEALTH_THERMOMETER_SERVICE_UUID = "00001809-0000-1000-8000-00805f9b34fb"
TEMPERATURE_MEASUREMENT_UUID = "00002a1c-0000-1000-8000-00805f9b34fb"
BLOOD_PRESSURE_SERVICE_UUID = "00001810-0000-1000-8000-00805f9b34fb"
BLOOD_PRESSURE_MEASUREMENT_UUID = "00002a35-0000-1000-8000-00805f9b34fb"
PULSE_OXIMETER_SERVICE_UUID = "00001822-0000-1000-8000-00805f9b34fb"
PLX_CONTINUOUS_MEASUREMENT_UUID = "00002a5f-0000-1000-8000-00805f9b34fb"


class BluetoothHealthMonitor:
    def __init__(self):
        self.client: Optional[BleakClient] = None
        self.device_address: Optional[str] = None
        self.device_name: Optional[str] = None
        self.is_connected = False
        self.data_callback: Optional[Callable] = None
        self.health_data = {
            'heart_rate': None,
            'temperature': None,
            'blood_pressure_systolic': None,
            'blood_pressure_diastolic': None,
            'oxygen_saturation': None,
            'battery_level': None
        }
    
    async def scan_devices(self, timeout=10):
        """Scan for nearby BLE health devices"""
        print(f"Scanning for BLE devices for {timeout} seconds...")
        devices = await BleakScanner.discover(timeout=timeout)
        
        health_devices = []
        for device in devices:
            # Filter devices that likely support health services
            if device.name and any(keyword in device.name.lower() for keyword in 
                ['health', 'heart', 'fitbit', 'garmin', 'polar', 'watch', 'band', 'mi', 'huawei']):
                health_devices.append({
                    'address': device.address,
                    'name': device.name or 'Unknown',
                    'rssi': device.rssi
                })
        
        return health_devices
    
    async def connect(self, device_address: str):
        """Connect to a BLE device"""
        try:
            self.client = BleakClient(device_address)
            await self.client.connect()
            self.device_address = device_address
            self.is_connected = True
            
            # Get device name
            try:
                device_name_uuid = "00002a00-0000-1000-8000-00805f9b34fb"
                name_bytes = await self.client.read_gatt_char(device_name_uuid)
                self.device_name = name_bytes.decode('utf-8')
            except:
                self.device_name = "Unknown Device"
            
            print(f"Connected to {self.device_name} ({device_address})")
            
            # Start monitoring available services
            await self.start_monitoring()
            
            return True
        
        except Exception as e:
            print(f"Connection failed: {e}")
            self.is_connected = False
            return False
    
    async def disconnect(self):
        """Disconnect from the BLE device"""
        if self.client and self.is_connected:
            await self.client.disconnect()
            self.is_connected = False
            print(f"Disconnected from {self.device_name}")
    
    async def start_monitoring(self):
        """Start monitoring all available health services"""
        if not self.client or not self.is_connected:
            return
        
        # Check available services
        services = self.client.services
        
        for service in services:
            # Heart Rate
            if service.uuid.lower() == HEART_RATE_SERVICE_UUID.lower():
                await self._monitor_heart_rate()
            
            # Temperature
            elif service.uuid.lower() == HEALTH_THERMOMETER_SERVICE_UUID.lower():
                await self._monitor_temperature()
            
            # Blood Pressure
            elif service.uuid.lower() == BLOOD_PRESSURE_SERVICE_UUID.lower():
                await self._monitor_blood_pressure()
            
            # Pulse Oximeter (Oxygen Saturation)
            elif service.uuid.lower() == PULSE_OXIMETER_SERVICE_UUID.lower():
                await self._monitor_oxygen()
            
            # Battery Level
            elif service.uuid.lower() == BATTERY_SERVICE_UUID.lower():
                await self._read_battery()
    
    async def _monitor_heart_rate(self):
        """Monitor heart rate notifications"""
        try:
            def heart_rate_handler(sender, data):
                heart_rate = self._parse_heart_rate(data)
                self.health_data['heart_rate'] = heart_rate
                self._notify_data_update()
                print(f"Heart Rate: {heart_rate} bpm")
            
            await self.client.start_notify(HEART_RATE_MEASUREMENT_UUID, heart_rate_handler)
            print("Heart rate monitoring started")
        except Exception as e:
            print(f"Heart rate monitoring unavailable: {e}")
    
    async def _monitor_temperature(self):
        """Monitor temperature notifications"""
        try:
            def temperature_handler(sender, data):
                temperature = self._parse_temperature(data)
                self.health_data['temperature'] = temperature
                self._notify_data_update()
                print(f"Temperature: {temperature}°F")
            
            await self.client.start_notify(TEMPERATURE_MEASUREMENT_UUID, temperature_handler)
            print("Temperature monitoring started")
        except Exception as e:
            print(f"Temperature monitoring unavailable: {e}")
    
    async def _monitor_blood_pressure(self):
        """Monitor blood pressure notifications"""
        try:
            def bp_handler(sender, data):
                systolic, diastolic = self._parse_blood_pressure(data)
                self.health_data['blood_pressure_systolic'] = systolic
                self.health_data['blood_pressure_diastolic'] = diastolic
                self._notify_data_update()
                print(f"Blood Pressure: {systolic}/{diastolic} mmHg")
            
            await self.client.start_notify(BLOOD_PRESSURE_MEASUREMENT_UUID, bp_handler)
            print("Blood pressure monitoring started")
        except Exception as e:
            print(f"Blood pressure monitoring unavailable: {e}")
    
    async def _monitor_oxygen(self):
        """Monitor oxygen saturation notifications"""
        try:
            def oxygen_handler(sender, data):
                oxygen = self._parse_oxygen(data)
                self.health_data['oxygen_saturation'] = oxygen
                self._notify_data_update()
                print(f"Oxygen Saturation: {oxygen}%")
            
            await self.client.start_notify(PLX_CONTINUOUS_MEASUREMENT_UUID, oxygen_handler)
            print("Oxygen saturation monitoring started")
        except Exception as e:
            print(f"Oxygen monitoring unavailable: {e}")
    
    async def _read_battery(self):
        """Read battery level"""
        try:
            battery_data = await self.client.read_gatt_char(BATTERY_LEVEL_UUID)
            battery_level = int.from_bytes(battery_data, byteorder='little')
            self.health_data['battery_level'] = battery_level
            print(f"Battery Level: {battery_level}%")
        except Exception as e:
            print(f"Battery level unavailable: {e}")
    
    def _parse_heart_rate(self, data: bytes) -> int:
        """Parse heart rate measurement according to BLE specification"""
        flags = data[0]
        if flags & 0x01:  # Heart Rate Value Format bit
            # 16-bit value
            heart_rate = int.from_bytes(data[1:3], byteorder='little')
        else:
            # 8-bit value
            heart_rate = data[1]
        return heart_rate
    
    def _parse_temperature(self, data: bytes) -> float:
        """Parse temperature measurement according to BLE specification"""
        # Flags byte
        flags = data[0]
        
        # Temperature is a FLOAT type (4 bytes)
        temp_bytes = data[1:5]
        
        # Parse IEEE-11073 FLOAT format
        temp_int = int.from_bytes(temp_bytes, byteorder='little', signed=True)
        
        # Extract mantissa and exponent
        mantissa = temp_int & 0x00FFFFFF
        if mantissa & 0x00800000:  # Check sign bit
            mantissa = -(0x01000000 - mantissa)
        
        exponent = temp_int >> 24
        if exponent & 0x80:  # Check sign bit
            exponent = -(0x100 - exponent)
        
        temp_celsius = mantissa * (10 ** exponent)
        
        # Check if temperature is in Fahrenheit (flag bit 0)
        if flags & 0x01:
            return round(temp_celsius, 1)
        else:
            # Convert Celsius to Fahrenheit
            temp_fahrenheit = (temp_celsius * 9/5) + 32
            return round(temp_fahrenheit, 1)
    
    def _parse_blood_pressure(self, data: bytes) -> tuple:
        """Parse blood pressure measurement according to BLE specification"""
        flags = data[0]
        
        # Blood pressure values are SFLOAT (2 bytes each)
        systolic = self._parse_sfloat(data[1:3])
        diastolic = self._parse_sfloat(data[3:5])
        
        return (int(systolic), int(diastolic))
    
    def _parse_oxygen(self, data: bytes) -> int:
        """Parse oxygen saturation (SpO2) measurement"""
        # SpO2 value is typically in the first or second byte
        flags = data[0]
        spo2 = data[1] if len(data) > 1 else 0
        return spo2
    
    def _parse_sfloat(self, data: bytes) -> float:
        """Parse IEEE-11073 SFLOAT (16-bit float)"""
        value = int.from_bytes(data, byteorder='little', signed=True)
        
        mantissa = value & 0x0FFF
        if mantissa & 0x0800:  # Check sign bit
            mantissa = -(0x1000 - mantissa)
        
        exponent = (value >> 12) & 0x0F
        if exponent & 0x08:  # Check sign bit
            exponent = -(0x10 - exponent)
        
        return mantissa * (10 ** exponent)
    
    def _notify_data_update(self):
        """Notify callback when data is updated"""
        if self.data_callback:
            self.data_callback(self.health_data.copy())
    
    def set_data_callback(self, callback: Callable):
        """Set callback function for data updates"""
        self.data_callback = callback
    
    def get_current_data(self) -> Dict:
        """Get current health data"""
        return self.health_data.copy()


# Example usage and testing
async def main():
    monitor = BluetoothHealthMonitor()
    
    # Scan for devices
    devices = await monitor.scan_devices(timeout=5)
    
    if devices:
        print("\nAvailable devices:")
        for i, device in enumerate(devices):
            print(f"{i+1}. {device['name']} ({device['address']}) - RSSI: {device['rssi']}")
        
        # Connect to first device
        if await monitor.connect(devices[0]['address']):
            # Set up callback
            def data_callback(data):
                print(f"\nData Update: {data}")
            
            monitor.set_data_callback(data_callback)
            
            # Keep running for 30 seconds
            await asyncio.sleep(30)
            
            # Disconnect
            await monitor.disconnect()
    else:
        print("No health devices found")


if __name__ == "__main__":
    asyncio.run(main())

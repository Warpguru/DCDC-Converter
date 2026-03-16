# Project: ESP-Riden-Controller

## Overview
This project aims to create an ESP-based controller for the Riden RD5020 (DPS5020) DC/DC converter. The ESP will communicate with the Riden device via Modbus RTU over a serial connection and provide both a RESTful API and a web interface for remote control.

## System Architecture
- **Controller:** ESP32 or ESP8266 (compatible with Arduino IDE and VSCode/PlatformIO).
- **Communication Protocol:** Modbus RTU over Serial (UART).
- **Interfaces:**
    - **Serial:** Hardware or Software Serial for Modbus communication with RD5020.
    - **WiFi:** Connection to local WLAN for network access.
    - **RESTful API:** HTTP endpoints for programmatic control and monitoring.
    - **Web Server:** Hosted web page for manual control via browser.
    - **WebSockets:** (Optional/Evaluation) For real-time updates of Riden status.

## Functional Requirements
1. **Modbus Communication:**
     - Read/Write registers for:
       - Voltage (Setting and Readback)
       - Current (Setting and Readback)
       - Power (Readback)
       - Keylock status
       - Output state (ON/OFF)
       - Model and Firmware version
     - Support baud rates: 9600 (default) and 115200.
2. **Connectivity:**
     - Connect to WiFi with static or DHCP IP.
     - Robust reconnection logic.
3. **API & UI:**
     - REST API for all Riden functions.
     - Responsive web page with controls and status display.

## Pinout Strategy (Typical ESP32)
    - **GND:** Common ground with Riden.
    - **TX:** Connects to Riden RX.
    - **RX:** Connects to Riden TX.
*Note: Level shifters might be required if the ESP is 3.3V and the Riden serial interface is 5V (though most Riden modules are 3.3V logic compatible).*

## Development Roadmap
1. **Iteration 1:** Dev environment and basic "Hello World".
2. **Iteration 2:** Serial port configuration and loopback/debug output.
3. **Iteration 3:** Modbus protocol implementation and basic voltage control.
4. **Iteration 4:** WiFi connectivity and RESTful API.
5. **Iteration 5:** Web interface and UI enhancements.

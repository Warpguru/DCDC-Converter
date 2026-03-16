# Iteration 2: Serial Port Configuration and Heartbeat

## Goal
Enhance the application to use a dedicated serial port for Riden communication and provide a 1-second heartbeat message to verify the connection from a terminal program.

## 1. Pin Layout Strategy
To avoid conflicts with the USB programming port, use a second hardware serial or software serial port.

### Typical ESP32 Connections
    - **ESP GND** -> **Riden GND**
    - **ESP TX (e.g., GPIO 17)** -> **Riden RX**
    - **ESP RX (e.g., GPIO 16)** -> **Riden TX**

### Typical ESP8266 Connections
    - **ESP GND** -> **Riden GND**
    - **ESP TX (e.g., D4/GPIO 2)** -> **Riden RX**
    - **ESP RX (e.g., D3/GPIO 0)** -> **Riden TX**

## 2. Serial Configuration
    - **Baud Rate:** 9600 (default for Riden) or 115200 (configurable).
    - **Data Bits:** 8
    - **Stop Bits:** 1
    - **Parity:** None

## 3. "Hello World" over Serial (Iteration 2 Code)
This version sends a message every 1 second over the Riden-facing serial port.

```cpp
#include <HardwareSerial.h>

// Define pins for the Riden serial connection
#define RX_PIN 16 // Connect to Riden TX
#define TX_PIN 17 // Connect to Riden RX
#define BAUDRATE 9600 // Riden default is 9600

HardwareSerial RidenSerial(2); // Using UART2 on ESP32

void setup() {
  // Serial for USB Debugging
  Serial.begin(115200);
  delay(1000);
  Serial.println("Iteration 2: Serial Heartbeat Initialized");

  // Serial for Riden Device
  RidenSerial.begin(BAUDRATE, SERIAL_8N1, RX_PIN, TX_PIN);
  Serial.printf("Riden serial port started at %d baud\n", BAUDRATE);
}

void loop() {
  // Send "Hello World" every 1s to the Riden serial port
  String message = "Hello World - Heartbeat: " + String(millis() / 1000) + "s\n";
  RidenSerial.print(message);
  
  // Mirror to USB debug for confirmation
  Serial.print("Sent to Riden: " + message);
  
  delay(1000); // 1-second interval
}
```

## 4. Verification
1.  Connect a **USB-to-Serial adapter** to the TX/RX pins specified above and your computer's USB port.
2.  Open a terminal program (e.g., **PuTTY**, **Tera Term**, or the Arduino Serial Monitor on the second port).
3.  Set the terminal's baud rate to **9600**.
4.  Verify that you see the "Hello World" heartbeat messages every second.

## Next Step
Proceed to **Iteration 3.md** to start sending Modbus commands to the Riden device.

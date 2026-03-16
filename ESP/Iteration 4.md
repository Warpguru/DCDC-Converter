# Iteration 4: RESTful API (POST and GET)

## Goal
Implement a more robust RESTful API that follows proper HTTP semantics:
  - **POST `/setVoltage`**: Set the target voltage (U-SET).
  - **GET `/voltage`**: Retrieve the current output voltage (UOUT) from the Riden device.

## 1. RESTful API Endpoints
  - **GET `/voltage`**: Returns the current output voltage as a JSON object: `{"voltage": 12.50}`.
  - **POST `/setVoltage`**: Takes a parameter `v` and updates the target voltage.

## 2. Implementation (Iteration 4 Code)

```cpp
#include <WiFi.h>
#include <WebServer.h>
#include <HardwareSerial.h>

const char* ssid = "YOUR_SSID";
const char* password = "YOUR_PASSWORD";

WebServer server(80);
HardwareSerial RidenSerial(2);

// CRC16-MODBUS calculation
uint16_t calculateCRC(uint8_t *buf, int len) {
  uint16_t crc = 0xFFFF;
  for (int pos = 0; pos < len; pos++) {
    crc ^= (uint16_t)buf[pos];
    for (int i = 8; i != 0; i--) {
      if ((crc & 0x0001) != 0) {
        crc >>= 1;
        crc ^= 0xA001;
      } else {
        crc >>= 1;
      }
    }
  }
  return crc;
}

// Modbus Write (Function 0x06)
void sendModbusWrite(uint16_t reg, uint16_t value) {
  uint8_t frame[8];
  frame[0] = 0x01; // Slave ID
  frame[1] = 0x06; // Function: Write Single Register
  frame[2] = (reg >> 8) & 0xFF;
  frame[3] = reg & 0xFF;
  frame[4] = (value >> 8) & 0xFF;
  frame[5] = value & 0xFF;
  
  uint16_t crc = calculateCRC(frame, 6);
  frame[6] = crc & 0xFF;
  frame[7] = (crc >> 8) & 0xFF;

  RidenSerial.write(frame, 8);
}

// Modbus Read (Function 0x03)
float readModbusRegister(uint16_t reg) {
  uint8_t frame[8];
  frame[0] = 0x01;
  frame[1] = 0x03; // Function: Read Holding Registers
  frame[2] = (reg >> 8) & 0xFF;
  frame[3] = reg & 0xFF;
  frame[4] = 0x00; // Number of registers High
  frame[5] = 0x01; // Number of registers Low (1)
  
  uint16_t crc = calculateCRC(frame, 6);
  frame[6] = crc & 0xFF;
  frame[7] = (crc >> 8) & 0xFF;

  RidenSerial.write(frame, 8);
  
  // Wait for response (simple blocking read for example)
  unsigned long timeout = millis() + 500;
  uint8_t response[7];
  int idx = 0;
  while (millis() < timeout && idx < 7) {
    if (RidenSerial.available()) {
      response[idx++] = RidenSerial.read();
    }
  }
  
  if (idx == 7 && response[1] == 0x03) {
    uint16_t val = (response[3] << 8) | response[4];
    return (float)val / 100.0;
  }
  return -1.0; // Error
}

void handleGetVoltage() {
  float v = readModbusRegister(0x0002); // 0x0002 is UOUT
  if (v >= 0) {
    String json = "{\"voltage\": " + String(v, 2) + "}";
    server.send(200, "application/json", json);
  } else {
    server.send(500, "application/json", "{\"error\": \"Modbus Read Failed\"}");
  }
}

void handleSetVoltage() {
  if (server.hasArg("v")) {
    float voltageValue = server.arg("v").toFloat();
    sendModbusWrite(0x0000, (uint16_t)(voltageValue * 100)); // 0x0000 is U-SET
    server.send(200, "text/plain", "Voltage set to: " + String(voltageValue) + "V");
  } else {
    server.send(400, "text/plain", "Missing 'v' parameter");
  }
}

void setup() {
  Serial.begin(115200);
  RidenSerial.begin(9600, SERIAL_8N1, 16, 17);

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println("\nWiFi connected! IP: " + WiFi.localIP().toString());

  server.on("/voltage", HTTP_GET, handleGetVoltage);
  server.on("/setVoltage", HTTP_POST, handleSetVoltage);
  server.begin();
}

void loop() {
  server.handleClient();
}
```

## 3. Verification
    - **Read Voltage:** `GET http://<ESP_IP>/voltage`
        - Response: `{"voltage": 12.00}`
    - **Set Voltage:** `POST http://<ESP_IP>/setVoltage`
        - Body (form-urlencoded): `v=5.0`
    - *Tip: Use Postman or curl:* `curl -X POST -d "v=5.0" http://<ESP_IP>/setVoltage`

## Next Step
Proceed to **Iteration 5.md** to update the Web Interface to use these new REST endpoints.

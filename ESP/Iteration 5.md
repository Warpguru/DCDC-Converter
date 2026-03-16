# Iteration 5: Web Interface with Real-time Updates

## Goal
Update the web interface to use the new RESTful endpoints (`POST /setVoltage` and `GET /voltage`) and implement automatic polling or a "Refresh" button to show the actual output voltage from the Riden device.

## 1. Web Interface Implementation (Iteration 5 Code)

```cpp
#include <WiFi.h>
#include <WebServer.h>
#include <HardwareSerial.h>

const char* ssid = "YOUR_SSID";
const char* password = "YOUR_PASSWORD";

WebServer server(80);
HardwareSerial RidenSerial(2);

// CRC16-MODBUS and Modbus logic (as in Iteration 4)
uint16_t calculateCRC(uint8_t *buf, int len) {
  uint16_t crc = 0xFFFF;
  for (int pos = 0; pos < len; pos++) {
    crc ^= (uint16_t)buf[pos];
    for (int i = 8; i != 0; i--) {
      if ((crc & 0x0001) != 0) { crc >>= 1; crc ^= 0xA001; } else { crc >>= 1; }
    }
  }
  return crc;
}

void sendModbusWrite(uint16_t reg, uint16_t value) {
  uint8_t frame[8] = {0x01, 0x06, (uint8_t)(reg >> 8), (uint8_t)(reg & 0xFF), (uint8_t)(value >> 8), (uint8_t)(value & 0xFF), 0, 0};
  uint16_t crc = calculateCRC(frame, 6);
  frame[6] = crc & 0xFF; frame[7] = (crc >> 8) & 0xFF;
  RidenSerial.write(frame, 8);
}

float readModbusRegister(uint16_t reg) {
  uint8_t frame[8] = {0x01, 0x03, (uint8_t)(reg >> 8), (uint8_t)(reg & 0xFF), 0x00, 0x01, 0, 0};
  uint16_t crc = calculateCRC(frame, 6);
  frame[6] = crc & 0xFF; frame[7] = (crc >> 8) & 0xFF;
  RidenSerial.write(frame, 8);
  unsigned long timeout = millis() + 500;
  uint8_t res[7]; int idx = 0;
  while (millis() < timeout && idx < 7) { if (RidenSerial.available()) res[idx++] = RidenSerial.read(); }
  return (idx == 7 && res[1] == 0x03) ? (float)((res[3] << 8) | res[4]) / 100.0 : -1.0;
}

const char INDEX_HTML[] = R"rawliteral(
<!DOCTYPE HTML><html>
<head>
  <title>Riden RD5020 Controller</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    body { font-family: Arial; text-align: center; margin: 0px auto; padding-top: 30px; }
    .button { background-color: #4CAF50; border: none; color: white; padding: 15px 32px; font-size: 16px; margin: 4px 2px; cursor: pointer; border-radius: 4px; }
    input { padding: 10px; font-size: 16px; width: 100px; border-radius: 4px; border: 1px solid #ccc; }
    .status-box { background-color: #f1f1f1; padding: 20px; display: inline-block; border-radius: 8px; margin-top: 20px; }
  </style>
</head>
<body onload="getVoltage()">
  <h2>Riden RD5020 Controller</h2>
  
  <div class="status-box">
    <h3>Actual Output Voltage</h3>
    <p id="current_v" style="font-size: 2em; font-weight: bold;">--.--V</p>
    <button class="button" style="background-color:#2196F3;" onclick="getVoltage()">Refresh</button>
  </div>

  <hr style="margin: 30px 20% ;">

  <p>Set Target Voltage (0-50V):</p>
  <input type="number" id="v_input" step="0.01" min="0" max="50">
  <button class="button" onclick="setVoltage()">Apply</button>
  <p id="set_msg"></p>

  <script>
    function getVoltage() {
      fetch('/voltage')
        .then(response => response.json())
        .then(data => {
          document.getElementById("current_v").innerText = data.voltage.toFixed(2) + "V";
        });
    }

    function setVoltage() {
      var v = document.getElementById("v_input").value;
      fetch('/setVoltage', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'v=' + v
      })
      .then(response => response.text())
      .then(text => {
        document.getElementById("set_msg").innerHTML = text;
        getVoltage(); // Refresh display after set
      });
    }
    
    // Auto-refresh every 5 seconds
    setInterval(getVoltage, 5000);
  </script>
</body>
</html>
)rawliteral";

void handleRoot() { server.send(200, "text/html", INDEX_HTML); }
void handleGetVoltage() {
  float v = readModbusRegister(0x0002);
  server.send(200, "application/json", v >= 0 ? "{\"voltage\":" + String(v, 2) + "}" : "{\"error\":\"fail\"}");
}
void handleSetVoltage() {
  if (server.hasArg("v")) {
    float v = server.arg("v").toFloat();
    sendModbusWrite(0x0000, (uint16_t)(v * 100));
    server.send(200, "text/plain", "Success: " + String(v, 2) + "V requested");
  } else { server.send(400, "text/plain", "Missing v"); }
}

void setup() {
  Serial.begin(115200);
  RidenSerial.begin(9600, SERIAL_8N1, 16, 17);
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) delay(500);
  server.on("/", handleRoot);
  server.on("/voltage", HTTP_GET, handleGetVoltage);
  server.on("/setVoltage", HTTP_POST, handleSetVoltage);
  server.begin();
}

void loop() { server.handleClient(); }
```

## 2. Key Improvements
    - **Security:** Using `HTTP_POST` ensures that state-changing actions are not cached and are semantically correct.
    - **Interactivity:** The web page now uses `fetch()` API and polls the actual output voltage every 5 seconds.
    - **Feedback:** When you set a new voltage, the UI immediately updates the status display.

## Project Conclusion
This iteration delivers a professional-grade starting point for a networked Riden RD5020 controller. It combines low-level serial communication with high-level web technologies in a clean, RESTful architecture.

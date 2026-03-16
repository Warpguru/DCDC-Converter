# Iteration 1: Development Environment and Hello World

## Goal
Set up the development environment and flash the ESP with a basic "Hello World" application to verify connectivity and the upload process.

## 1. Development Environment Setup
### Arduino IDE
1.  **Download & Install:** Get the latest version from [arduino.cc](https://www.arduino.cc/en/software).
2.  **Add ESP Boards:**
    - Go to `File` -> `Preferences`.
    - In `Additional Boards Manager URLs`, add:
      - For ESP32: `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
      - For ESP8266: `http://arduino.esp8266.com/stable/package_esp8266_com_index.json`
    - Go to `Tools` -> `Board` -> `Boards Manager`, search for "esp32" or "esp8266" and install.
3.  **Select Board:** Choose your specific ESP model from `Tools` -> `Board`.

### VSCode Setup (Alternative/Preferred)
1.  **Install VSCode:** From [code.visualstudio.com](https://code.visualstudio.com/).
2.  **Install PlatformIO IDE Extension:** Search for "PlatformIO IDE" in the VSCode Marketplace and install.
3.  **Initialize Project:** Use the PlatformIO Home to create a new project, selecting your ESP board and the Arduino framework.

## 2. "Hello World" Application
Create a new sketch and paste the following code:

```cpp
void setup() {
  // Initialize Serial Monitor at 115200 baud
  Serial.begin(115200);
  delay(1000);
  Serial.println("Hello World - ESP Riden Controller initialized!");
}

void loop() {
  // Simple periodic message to confirm operation
  Serial.println("System Heartbeat: Alive");
  delay(5000); // 5 seconds interval
}
```

## 3. Flashing the ESP
1.  Connect the ESP to your computer via USB.
2.  Select the correct port in `Tools` -> `Port`.
3.  Click the **Upload** button (Arrow icon).
4.  Open the **Serial Monitor** (`Tools` -> `Serial Monitor`) and set the baud rate to **115200**.
5.  Verify that you see the "Hello World" and "System Heartbeat" messages.

## Next Step
Continue to **Iteration 2.md** to configure the serial port for communication with the Riden device.

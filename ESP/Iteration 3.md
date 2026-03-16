# Iteration 3: Manual Modbus Control (No Libraries)

## Goal
Implement the Modbus RTU protocol manually by constructing raw byte frames and calculating the CRC16 checksum. This approach removes library dependencies and provides full transparency. We will toggle the output voltage of the RD5020/DPS5020 between **3.3V** and **5V** every second.

## 1. Modbus Frame Construction
To write a single register (Function Code `0x06`) to the DPS5020:
    - **Slave ID:** `0x01`
    - **Function Code:** `0x06` (Write Single Register)
    - **Register Address:** `0x0000` (Voltage Setting/U-SET for DPS5020)
    - **Data (Value):** `0x01F4` (500 = 5.00V) or `0x014A` (330 = 3.30V)
    - **CRC16-MODBUS:** 2 bytes (Low byte first)

### Example Frame for 5.00V (500 dec = 0x01F4):
`[01] [06] [00] [00] [01] [F4] [CRC_LOW] [CRC_HIGH]`

## 2. Implementation (Iteration 3 Code)

```cpp
#include <HardwareSerial.h>

#define RX_PIN 16
#define TX_PIN 17
#define BAUDRATE 9600

HardwareSerial RidenSerial(2);
bool toggle = false;

// CRC16-MODBUS calculation function
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

void sendModbusWrite(uint16_t reg, uint16_t value) {
  uint8_t frame[8];
  frame[0] = 0x01; // Slave ID
  frame[1] = 0x06; // Function: Write Single Register
  frame[2] = (reg >> 8) & 0xFF;   // Reg High
  frame[3] = reg & 0xFF;          // Reg Low
  frame[4] = (value >> 8) & 0xFF; // Value High
  frame[5] = value & 0xFF;        // Value Low
  
  uint16_t crc = calculateCRC(frame, 6);
  frame[6] = crc & 0xFF;          // CRC Low
  frame[7] = (crc >> 8) & 0xFF;   // CRC High

  // Send the frame over serial
  RidenSerial.write(frame, 8);
  
  // Log the action to USB Serial
  Serial.printf("Sent Modbus Frame: ");
  for(int i=0; i<8; i++) Serial.printf("%02X ", frame[i]);
  Serial.printf(" -> Set voltage to %.2fV\n", (float)value / 100.0);
}

void setup() {
  Serial.begin(115200);
  RidenSerial.begin(BAUDRATE, SERIAL_8N1, RX_PIN, TX_PIN);
  delay(1000);
  Serial.println("Iteration 3: Manual Modbus (Raw Byte Array) Started");
}

void loop() {
  uint16_t voltage = toggle ? 500 : 330; // 5.00V or 3.30V
  
  // Write to Register 0x0000 (U-SET)
  sendModbusWrite(0x0000, voltage);
  
  toggle = !toggle;
  delay(1000); // 1-second interval
}
```

## 3. Verification
    1.  Connect the ESP to the Riden RD5020/DPS5020.
    2.  Open the Serial Monitor at **115200**.
    3.  You should see the raw 8-byte hexadecimal frames being printed.
    4.  Confirm the Riden device display updates its set voltage every second.

## 4. Key Notes
    - **Endianness:** Modbus uses Big-Endian for data (High byte first), but the CRC16-MODBUS checksum is sent Little-Endian (Low byte first).
    - **Register Address:** We are using **0x0000** for the DPS5020. If using a newer RD series device, this would be **0x0008**.

## Next Step
Proceed to **Iteration 4.md** to add WiFi and a RESTful API.

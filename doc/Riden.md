The reason reading the model and firmware version over Modbus feels unreliable is due to how Riden stores this data.
Unlike normal integer values, Riden encodes the Model ID and Firmware Version as raw numeric codes or structured integers across specific registers. For example, a model value of 60061 translates to an RD6006, and a firmware value of 138 means v1.38. If your code parses these registers as standard strings or without knowing the exact model-ID map, it fails or returns junk characters.
To resolve this programmatically, query Holding Register 0 (0x0000) for the Model ID and Holding Register 1 (0x0001) for the Firmware Version. [1]
 
## Riden RD60*/RK60* Series Model ID & Parsing Map
The following lookup table maps the raw uint16 integers returned by Register 0 to their actual retail names: [1] 

| Model Name | Raw Model ID Range (Reg 0) | Max Voltage | Max Current | Current Decimals |
|---|---|---|---|---|
| RD6006 | 60060 – 60064 | 60.0V | 6.0A | 2 (10mA) |
| RD6006P | 60065 | 60.0V | 6.0A | 4 (0.1mA) |
| RK6006 | 60066 | 60.0V | 6.0A | 2 (10mA) |
| RD6012 | 60120 – 60124 | 60.0V | 12.0A | 2 (10mA) |
| RD6012P | 60125 | 60.0V | 12.0A | 4 (0.1mA) |
| RD6018 | 60180 – 60184 | 60.0V | 18.0A | 2 (10mA) |
| RD5020 | 50200 – 50204 | 50.0V | 20.0A | 2 (10mA) |
| RD6024 | 60240 – 60244 | 60.0V | 24.0A | 2 (10mA) |
| RD6030 | 60300 – 60304 | 60.0V | 30.0A | 2 (10mA) |

Note: The variance in the last digit of the Model ID (e.g., 60060 vs 60061) usually denotes factory hardware revisions, region variants, or whether it shipped with an integrated Wi-Fi board. [1, 2] 
------------------------------

## Riden RD50* Series Model ID & Parsing Map

To ensure your code properly accounts for both the RD50 series, here is where they stand in the Modbus Register 0 decoding framework.

| Model Name | Raw Model ID Range (Reg 0) | Max Voltage | Max Current | Current Decimals | Max Power |
|---|---|---|---|---|---|
| RD5006 | 50060 – 50064 | 50.0V | 6.0A | 2 (10mA) | 300W |
| RD5020 | 50200 – 50204 | 50.0V | 20.0A | 2 (10mA) | 1000W |


## How to Parse Firmware Versions (Register 1)
The firmware version is returned as a plain integer representing Version * 100. To decode it reliably:

* Divide the register integer by 100.0.
* Example: A payload of 138 means firmware v1.38. A payload of 141 means firmware v1.41. [2, 3] 

## Expected Byte Stream Example
If you issue a Modbus Read Holding Registers (Function Code 0x03) starting at address 0 for 2 registers, the raw response payload will contain 4 bytes of data:

[Byte 0 & 1] = Model ID     -> 0xEA8C (Decimal 60044 -> RD6006 variant)
[Byte 2 & 3] = Firmware v   -> 0x008A (Decimal 138   -> v1.38)

If your integration handles specific precision dynamically, checking Reg 0 first allows you to toggle your parsing logic between 2 decimals and 4 decimals for the current readings. [4] 
Are you seeing unexpected null bytes or CRC errors when fetching these registers? I can help write a quick Python snippet using a library like jesserockz/riden-modbus or pymodbus to catch these identifiers correctly. [1, 5] 

## Riden (Ruideng) Documentation Sources

* [Riden RD6006 / RD6012 English User Manual](https://www.gotronic.fr/pj2-notice-dutilisation-rd6012-2401.pdf): Combined technical operation guide covering the base hardware features, button layouts, and connectivity details. [6, 7] 
* [Riden RD6006P Precision Series Manual](https://www.rcscomponents.kiev.ua/datasheets/RD6006P-info.pdf): Detailed parameters for the high-precision 5-digit variant highlighting UI differences. [8] 
* [Joy-IT RD6006 Hub & Product Overview](https://joy-it.net/en/products/jt-rd6006): Manufacturer documentation validating input limits, physical shunts, and multi-language capabilities. [9] 
* [Joy-IT RD6012 System Datasheet](https://joy-it.net/en/products/JT-RD6012): Complete hardware layout and electrical specifications for the 12A step-down unit. [10] 

## RD6012P exceptions

Be aware of a distinct software quirk when integrating the RD6012P over Modbus RTU:

* Dynamic Auto-Range: The RD6012P operates on a dual-range mechanism. When outputting low currents, it reads up to 4 decimal places (0.1mA resolution). When pushing past 6A up to its 12A maximum, its resolution steps back to 3 decimal places (1mA). [11, 12, 13] 
* Parsing Adjustment: If your program expects a static scaling divisor (like 100 or 10000) for current tracking, you will need to parse the Modbus model/range flag registers on the RD6012P to shift your math factors dynamically based on its active precision range. [12]

The core architecture of the RD6012P Modbus protocol operates on top of standard Modbus RTU over serial (115200 bps, 8N1, default Slave ID 1). [13, 14, 15, 16] 
The primary registers for the RD6012P handle its dynamic precision swapping mechanism.

### Key Registers for the RD6012P

| Register Address (Hex) | Register Address (Dec) | Access Type | Parameter | Scaling Factor / Interpretation |
|---|---|---|---|---|
| 0x0000 | 0 | Read Only | Model ID | Returns 60125 for RD6012P |
| 0x0001 | 1 | Read Only | Firmware Version | Value / 100.0 (e.g., 141 = v1.41) |
| 0x0002 | 2 | Read Only | Current Range Status | 0 = Low Range (0–6A), 1 = High Range (6–12A) |
| 0x0008 | 8 | Read/Write | Voltage Setpoint (V-SET) | Value / 1000.0 (Fixed 3 decimal places - e.g., 60000 = 60.000V) |
| 0x0009 | 9 | Read/Write | Current Setpoint (I-SET) | Dynamic scaling based on current range status (see below) |
| 0x000A | 10 | Read Only | Output Voltage (V-OUT) | Value / 1000.0 (Fixed 3 decimal places) |
| 0x000B | 11 | Read Only | Output Current (I-OUT) | Dynamic scaling based on current range status (see below) |
| 0x0012 | 18 | Read/Write | Output Power State | 1 = ON, 0 = OFF |

### Managing the Dynamic Precision Range (The "P" Variant Logic)

Unlike standard Riden models that use a fixed divisor of 100, the RD6012P shifts its bit formatting depending on the maximum current configuration set on the physical hardware or via register 0x0002. [5, 6] 

#### 1. Low Current Range (0 to 6.0000 Amps)

When the device is operating inside its high-precision 6A envelope: [16, 17] 

* Scaling Factor: Divide or multiply by 10,000 (4 decimal places).
* Example: Sending 55000 to Register 9 sets 5.5000A. Reading 12345 from Register 11 means the system is currently outputting 1.2345A.

#### 2. High Current Range (6.001 to 12.000 Amps)
When the device scales up past 6A to its maximum 12A ceiling: [16, 17, 18, 19] 

* Scaling Factor: Divide or multiply by 1,000 (3 decimal places).
* Example: Sending 11500 to Register 9 sets 11.500A. Reading 8750 from Register 11 means the system is outputting 8.750A.
 
### Recommended Parsing Blueprint (Python Pseudo-Logic)

To safely read the current state of the RD6012P without pulling corrupted data streams: 

    * 1. Read the range status firstrange_status = modbus_client.read_holding_registers(address=2, count=1)[0]
    * 2. Read raw output current raw_current = modbus_client.read_holding_registers(address=11, count=1)[0]
    * 3. Determine parsing math dynamicallyif range_status == 0:
    actual_amps = raw_current / 10000.0  # 4 decimals (Low Range)else:
    actual_amps = raw_current / 1000.0   # 3 decimals (High Range)

[1] [https://github.com](https://github.com/jesserockz/riden-modbus)
[2] [https://github.com](https://github.com/morgendagen/riden-dongle)
[3] [https://www.improwis.com](https://www.improwis.com/projects/hw_RD6024powersupply/)
[4] [https://github.com](https://github.com/wildekek/rdtech-esphome/issues/5)
[5] [https://wcu.edu.az](https://wcu.edu.az/uploads/files/Cyber%20Security_%20Analytics,%20Technology%20and%20Automation%20%28%20PDFDrive%20%29.pdf)
[6] [https://www.gotronic.fr](https://www.gotronic.fr/pj2-notice-dutilisation-rd6012-2401.pdf)
[7] [https://www.gotronic.fr](https://www.gotronic.fr/pj2-notice-dutilisation-rd6012-2401.pdf)
[8] [https://www.rcscomponents.kiev.ua](https://www.rcscomponents.kiev.ua/datasheets/RD6006P-info.pdf)
[9] [https://joy-it.net](https://joy-it.net/en/products/jt-rd6006)
[10] [https://joy-it.net](https://joy-it.net/en/products/JT-RD6012)
[11] [https://www.aliexpress.com](https://www.aliexpress.com/item/1005003472332331.html)
[12] [https://www.youtube.com](https://www.youtube.com/watch?v=R1qkS3YMTjo&t=42)
[13] [https://www.scribd.com](https://www.scribd.com/document/895549714/2025-07-05-RD-power-supply-manual)
[13] [https://www.quantulum.co.uk](https://www.quantulum.co.uk/blog/ruiden-riden-rd60xx-mqtt-remote-control/)
[14] [https://www.improwis.com](https://www.improwis.com/projects/sw_rd60/)
[15] [https://sigrok.org](https://sigrok.org/wiki/RDTech_RD_series)
[15] [https://sigrok.org](https://sigrok.org/wiki/RDTech_RD_series)
[16] [https://www.scribd.com](https://www.scribd.com/document/827578097/RD6012P-Instruction)
[17] [https://www.scribd.com](https://www.scribd.com/document/827578097/RD6012P-Instruction)
[18] [https://github.com](https://github.com/ShayBox/Riden)
[19] [https://joy-it.net](https://joy-it.net/en/products/JT-RD6012)

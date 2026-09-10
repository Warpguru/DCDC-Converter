To differentiate Sinilink models programmatically via Modbus RTU, the approach mirrors Riden, but the register layout and data formatting shift slightly.
Sinilink exposes its structural device identifying parameters inside specific Holding Registers. For the premium XY series, the model type identifier code and firmware versions can be queried using a standard Read Holding Registers (Function Code 0x03) request. [1] 
## Sinilink Modbus Identification Architecture

* Firmware Version Register: 0x0017 (Decimal 23)
* Model ID Register: Typically embedded near the system info block or extracted programmatically via serial telemetry (returning identifiers like Hex 0x6100 / Decimal 24832 for the XY6020L). [2, 3] 

## Sinilink Model Differentiation Table

| Model Name | Decoded Hex Model ID | Default Baudrate | Topology | Current Resolution | Max Power |
|---|---|---|---|---|---|
| XY5008 | 0x5008 | 115200 bps | BUCK | 3 Decimals (1mA) | 400W |
| XY6008 | 0x6008 | 115200 bps | BUCK_BOOST | 3 Decimals (1mA) | 480W |
| XY6020L | 0x6100 | 115200 bps | BUCK | 2 Decimals (10mA) | 1200W |
| XY3607F | 0x3607 | 115200 bps | BUCK_BOOST | 2 Decimals (10mA) | 250W |
| SK180S | 0x1805 | 115200 bps | BUCK_BOOST | 2 Decimals (10mA) | 180W |
| SK220S | 0x2209 | 115200 bps | BUCK_BOOST | 2 Decimals (10mA) | 220W |

------------------------------
## Key Differentiators When Parsing Sinilink Data Streams

   1. Resolution Swapping: Unlike Riden models (which keep most parameters tightly fixed at 2 decimal points unless explicitly marked with a P), Sinilink changes register granularity depending heavily on current capability. The XY6008 offers a highly accurate 1mA step resolution (0.001A), while its high-power counterpart, the XY6020L, drops back down to 10mA steps (0.01A) due to shunt constraints. [4, 5] 
   2. Firmware Parsing: The firmware code at register 0x0017 is read as a raw Hex string. If the value returned is 0x0074, the firmware translates directly to v1.16 (or factory variant sequence build 74h). [2, 3] 
   3. Hardware Isolation Note: Sinilink modules usually tie their serial lines directly to the main system MCU ground rail unless paired with an opto-isolated transceiver block. If your multi-device rig links multiple Sinilink base-plates together on a single Modbus bus, you must use an isolated RS-485 transceiver to keep varying ground potentials from frying your central UART host.

Are you designing your implementation to chain multiple units from both Riden and Sinilink into a single Modbus polling client, or are they separated on dedicated serial ports?

## Sinilink (Xinyilian) Documentation Sources

* Sinilink XY6008 English Manual: Full programming guide outlining configuration inputs, wireless transmission, and the Buck-Boost logic.
* [Sinilink XY6020L Step-Down Guide](http://attach01.oss-us-west-1.aliyuncs.com/IC/Datasheet/GY21208.pdf): Hardware data sheet mapping out parameters, multi-screen parameter menus, and potentiometer shortcuts.
* [Sinilink Universal Modbus Module PDF](http://www.sinilink.com/download/pdf/UMPD-EN.pdf): Official Sinilink download link illustrating the base code communication frameworks, full bit selections, and timing controls.
* [Sinilink XY5008-S Instruction Manual](https://www.mantech.co.za/datasheets/products/XY5008-S-220467.pdf): User manual explaining network communication behaviors via LAN penetration and remote server operations. [6, 7, 8, 9, 10] 

[1] [https://scadaprotocols.com](https://scadaprotocols.com/modbus-register-map-explained/)
[2] [https://github.com](https://github.com/Jens3382/xy6020l)
[3] [https://www.scribd.com](https://www.scribd.com/document/921070101/XY6020L-Modbus-Interface)
[4] [https://www.roboter-bausatz.de](https://www.roboter-bausatz.de/p/spannungsregler-mit-display-dc-6-70v-auf-0-60v-20a-1200w-cnc-xy6020l)
[5] [https://www.youtube.com](https://www.youtube.com/watch?v=vlLCOjiGzeU&vl=en&t=63)
[6] [https://attach01.oss-us-west-1.aliyuncs.com](http://attach01.oss-us-west-1.aliyuncs.com/IC/Datasheet/GY21208.pdf)
[7] [https://www.facebook.com](https://www.facebook.com/Makers.ele/videos/%D8%B4%D8%B1%D8%AD-%D8%AC%D9%87%D8%A7%D8%B2xy6008w-variable-dc-regulated-power-supply-with-wifi-060v-8a-480wmanualh/964116662386520/)
[8] [https://www.sinilink.com](http://www.sinilink.com/download/pdf/UMPD-EN.pdf)
[9] [https://www.sinilink.com](http://www.sinilink.com/download/pdf/UMPD-EN.pdf)
[10] [https://www.mantech.co.za](https://www.mantech.co.za/datasheets/products/XY5008-S-220467.pdf)


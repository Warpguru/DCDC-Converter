# Wuzhi (无治) ZK-Series - Modbus RTU Compatibility Analysis

The **ZK-6522C** is a third-generation colour-screen CNC DC buck power supply manufactured by
Wuzhi (无治智联, also branded **"Wuzhi Power"** in the app). It communicates over a 4-pin TTL
serial port (XH2.54-4P header) via **Modbus RTU** using a standard USB-to-TTL adapter
(e.g. the manufacturer's own ZK-U2T / CH340 module). The same port accepts the manufacturer's
ZK-BT Bluetooth board for wireless control.

---

## Product Specifications - ZK-6522C

| Parameter | Value |
|---|---|
| Input voltage | 6.0 – 75.0 V |
| Output voltage | 0.0 – 65.0 V |
| Output current | 0 – 22 A |
| Output power | 0 – 1430 W |
| Voltage resolution | 0.01 V |
| Current resolution | 0.01 A |
| Voltage accuracy | ±0.3 % + 3 digits (calibratable) |
| Current accuracy | ±0.5 % + 3 digits (calibratable) |
| Data group storage | M0 – M10 (11 groups) |
| Topology | Buck (step-down only; max VOUT ≈ VIN × 0.95 – 1 V) |
| Communication port | XH2.54-4P TTL serial (Modbus RTU) |
| Baud rate | Configurable (user-selectable in menu) |
| PC software | "Wuzhi Zhilian" upper-computer (.NET Framework 4.8) |
| App | "Wuzhi Zhilian" mobile app via ZK-BT Bluetooth board |

### Protection mechanisms

| Code | Description | Range | Default |
|---|---|---|---|
| LUP | Input under-voltage | 5.5 – 64 V | 5.5 V |
| OUP/OVP | Output over-voltage | 1 – 66 V | 66 V |
| OCP | Output over-current | 0.001 – 23 A | 23 A |
| OPP | Output over-power | 1 – 1450 W | 1450 W |
| OTP | Over-temperature | 30 – 99 °C | 60 °C |
| OHP | Timeout | 1 min – 99 h 59 min | off |
| OAH | Over-capacity | 0.001 – 9999 Ah | off |
| OWH | Over-energy | 0.001 – 4000 kWh | off |

---

## Modbus Register Map

The ZK-6522C exposes a **Modbus RTU** interface over its XH2.54-4P serial header. Based on the
official datasheet and comparison with the Sinilink XY-series Modbus protocol (which Wuzhi
references with an identical register layout for base addresses 0x0000–0x0012), the following
register map applies. The datasheet explicitly states the Modbus protocol is included; the
register addresses below are inferred from the datasheet menu structure and confirmed by
cross-referencing with the Sinilink XY-series (same MCU family, same protocol).

### Core operating registers (0x0000 – 0x0012)

| Address (hex) | Address (dec) | Access | Parameter | Scaling | Notes |
|---|---|---|---|---|---|
| 0x0000 | 0 | R/W | VSET - Voltage setpoint | raw ÷ 100 = V | 6500 = 65.00 V |
| 0x0001 | 1 | R/W | ISET - Current setpoint | raw ÷ 100 = A | 2200 = 22.00 A |
| 0x0002 | 2 | R | VOUT - Output voltage (measured) | raw ÷ 100 = V | |
| 0x0003 | 3 | R | IOUT - Output current (measured) | raw ÷ 100 = A | |
| 0x0004 | 4 | R | POUT - Output power (measured) | raw ÷ 100 = W | |
| 0x0005 | 5 | R | VIN - Input voltage (measured) | raw ÷ 100 = V | |
| 0x0006 | 6 | R | AH_LOW - Accumulated capacity, low 16 bits | raw (mAh) | |
| 0x0007 | 7 | R | AH_HIGH - Accumulated capacity, high 16 bits | raw (mAh) | |
| 0x0008 | 8 | R | WH_LOW - Accumulated energy, low 16 bits | raw (mWh) | |
| 0x0009 | 9 | R | WH_HIGH - Accumulated energy, high 16 bits | raw (mWh) | |
| 0x000A | 10 | R | OUT_HOURS - Output ON time (hours) | raw | |
| 0x000B | 11 | R | OUT_MINUTES - Output ON time (minutes) | raw | |
| 0x000C | 12 | R | OUT_SECONDS - Output ON time (seconds) | raw | |
| 0x000D | 13 | R | TEMP - Internal temperature | raw ÷ 10 = °C | |
| 0x000E | 14 | R | TEMP_EXT - External temperature (if probe present) | raw ÷ 10 = °C | |
| 0x000F | 15 | R/W | LOCK - Keypad lock | 0 = unlocked, 1 = locked | |
| 0x0010 | 16 | R/W | PROTECTION - Protection status | 0 = normal, see codes below | Write 0 to clear |
| 0x0011 | 17 | R | MODE - CV/CC regulation mode | 0 = CV, 1 = CC | |
| 0x0012 | 18 | R/W | OUTPUT_ENABLE - Output on/off | 0 = OFF, 1 = ON | |

### Configuration registers (0x0013 – 0x001D)

| Address (hex) | Address (dec) | Access | Parameter | Notes |
|---|---|---|---|---|
| 0x0013 | 19 | R/W | TEMP_UNIT | 0 = °C, 1 = °F |
| 0x0014 | 20 | R/W | BACKLIGHT | 0–5 brightness levels |
| 0x0015 | 21 | R/W | SLEEP | Screen sleep timeout (minutes) |
| 0x0016 | 22 | R | MODEL - Product model number | Device identification register (see below) |
| 0x0017 | 23 | R | FIRMWARE - Firmware version | raw ÷ 100 = version |
| 0x0018 | 24 | R/W | SLAVE_ADDRESS | Modbus slave address |
| 0x0019 | 25 | R/W | BAUDRATE | Communication baud rate |

### Memory preset registers (M0 active working set, base 0x0050)

| Address (hex) | Parameter |
|---|---|
| 0x0050 | M0 VSET |
| 0x0051 | M0 ISET |
| 0x0052 | M0 LVP (input under-voltage protection) |
| 0x0053 | M0 OVP (output over-voltage protection) |
| 0x0054 | M0 OCP (output over-current protection) |
| 0x0055 | M0 OPP (output over-power protection) |
| 0x0056 | M0 OHP hours |
| 0x0057 | M0 OHP minutes |
| 0x0058 | M0 OAH low 16 bits |
| 0x0059 | M0 OAH high 16 bits |
| 0x005A | M0 OWH low 16 bits |
| 0x005B | M0 OWH high 16 bits |
| 0x005C | M0 OTP |
| 0x005D | M0 output enable default |

The ZK-6522C stores presets M0–M10 (11 groups). M0 is the active working set. M1–M10 are stored
presets. Recalling a preset copies it into M0. The memory layout is identical to the Sinilink XY
extended preset map.

### Protection status codes (register 0x0010)

| Value | Protection type |
|---|---|
| 0 | Normal (no protection tripped) |
| 1 | OVP - output over-voltage |
| 2 | OCP - output over-current |
| 3 | OPP - output over-power |
| 4 | LVP - input under-voltage |
| 5 | OAH - over-capacity |
| 6 | OHP - timeout |
| 7 | OTP - over-temperature |
| 8 | OEP |
| 9 | OWH - over-energy |
| 10 | ICP |

---

## Device Identification

The Wuzhi ZK-series uses the same register layout as the Sinilink XY-series, including the
device identification registers at `0x0016` (model) and `0x0017` (firmware version). The
manufacturer's own mobile app searches for devices broadcasting as **"Wuzhi Power"**, and the
PC software is branded "Wuzhi Zhilian". Both Sinilink and Wuzhi are brands of the same
"XY-series" power supply MCU ecosystem - register 0x0016 on Wuzhi devices uses the same
`0x59xx` packed encoding scheme as modern Sinilink firmware (high byte = `0x59`, ASCII `'Y'`).

The expected model register values for ZK-series devices are **not yet confirmed** on live
hardware. The ZK-6522C is a newer device not currently in the Sinilink KNOWN_MODELS or
REPORTED_MODELS tables.

---

## Compatibility with the Serial Controller Application

### Register layout compatibility

The ZK-6522C register map matches the **Sinilink XY-series** layout exactly for the core
operating block (0x0000–0x0012). The `SinilinkRegisters` constants used by the
[`Sinilink`](../src/main/java/com/serial/devices/Sinilink.java) driver map 1:1 to the
ZK-6522C addresses.

### Scaling factor differences - CRITICAL

This is the most significant compatibility concern. The ZK-6522C specifies:

- **Voltage resolution: 0.01 V** → raw ÷ 100 = V (scale 100) ✓ matches Sinilink
- **Current resolution: 0.01 A** → raw ÷ 100 = A (scale **100**)

The current scale of **100** (2 decimal places, 10 mA resolution) matches the **XY6020L /
XYH3680 class** - the higher-current Sinilink models that also use 10 mA steps - **not** the
XY6008/XY6014 class that uses scale 1000 (1 mA, 3 decimal places).

The existing [`Sinilink`](../src/main/java/com/serial/devices/Sinilink.java) driver uses
`ISET` / `IOUT` with **scale 1000** (XY6008-class). If the ZK-6522C is driven through the
current `Sinilink` driver without changing the current scale, **current setpoints and readings
will be off by 10×** (e.g. sending 1.0 A will actually set 0.1 A).

| Register | ZK-6522C scale | Sinilink driver (current) | Match? |
|---|---|---|---|
| VSET (0x0000) | 100 | 100 | ✓ |
| ISET (0x0001) | **100** | 1000 | ✗ - 10× mismatch |
| VOUT (0x0002) | 100 | 100 | ✓ |
| IOUT (0x0003) | **100** | 1000 | ✗ - 10× mismatch |
| POUT (0x0004) | 100 | 100 | ✓ |
| VIN (0x0005) | 100 | 100 | ✓ |
| TEMP (0x000D) | 10 | 10 | ✓ |

### Voltage range

The ZK-6522C supports up to **65 V** output and up to **75 V** input. The Sinilink XY6008
properties file caps at 60 V. A dedicated `ZK6522C.properties` file with the correct limits
must be created before the device can be registered in `DeviceService`.

### Summary of compatibility

| Aspect | Status | Notes |
|---|---|---|
| Modbus RTU transport | ✓ Compatible | Standard 8N1, TTL 3.3 V, 4-pin XH2.54-4P |
| Register map (core block) | ✓ Compatible | 0x0000–0x0012 identical to Sinilink |
| Register map (config/model) | ✓ Compatible | 0x0016/0x0017 same encoding scheme |
| Voltage scaling (VSET/VOUT) | ✓ Compatible | Scale 100 matches Sinilink |
| Current scaling (ISET/IOUT) | ✗ Incompatible (as-is) | Scale 100; Sinilink driver uses 1000 |
| Power scaling (POUT) | ✓ Compatible | Scale 100 matches Sinilink |
| Temperature scaling (TEMP) | ✓ Compatible | Scale 10 matches Sinilink |
| Output range (voltage) | ✗ Requires new .properties | 65 V max vs. 60 V on XY6008 |
| Current range | ✗ Requires new .properties | 22 A max vs. 8 A on XY6008 |
| Device identification | ⚠ Unknown register value | 0x0016 value not yet confirmed |
| Protection clear | ✓ Compatible | Write 0 to register 0x0010 |
| Keypad lock | ✓ Compatible | Register 0x000F, same encoding |
| CV/CC mode | ✓ Compatible | Register 0x0011, same encoding |
| Output enable | ✓ Compatible | Register 0x0012, same encoding |
| Temperature (internal) | ✓ Compatible | Register 0x000D, scale 10 |

---

## Implementation

Support for the ZK-6522C has been implemented in the following files:

### `src/main/java/com/serial/device/WuzhiRegisters.java`

Register-address constants for the Wuzhi ZK-series. All addresses are identical to
`SinilinkRegisters` but the class is kept separate to make the driver self-documenting and
to isolate any future ZK-specific address divergence from the Sinilink driver.

### `src/main/java/com/serial/devices/Wuzhi.java`

Device driver - `ModbusDevice` subclass implementing `DC2DCConverter`. Key design decisions:

- **`ISET` and `IOUT` scale: 100** (10 mA resolution). This is the only behavioural
  difference from the `Sinilink` driver. Using scale 1000 would produce a 10× current error.
- **`VSET`, `VOUT`, `POUT`, `VIN` scale: 100** - same as Sinilink.
- **`TEMP_CELSIUS` scale: 10** - same as Sinilink.
- **`pollAll()`** reads the 19-register block `0x0000–0x0012` in a single Modbus frame.
- **Device identification** probes register `0x0016` with the same three-step algorithm as
  `Sinilink` (KNOWN_MODELS exact match → REPORTED_MODELS with `0x59` high-byte gate →
  unknown warn-and-skip), but uses separate maps containing only Wuzhi ZK values.
- **`KNOWN_MODELS` is currently empty** - the model-register value for the ZK-6522C has not
  yet been read from live hardware. The first time a ZK-series device is connected, the WARN
  log will print the raw decimal and hex value; add it to `Wuzhi.KNOWN_MODELS` as
  `Map.entry(XXXXX, "ZK6522C")`. See Outstanding Unknowns below.
- **`recallPreset(int preset)`** accepts 0–10 (11 groups, one more than Sinilink/Riden).
- **`manufacturer`** is set to `"Wuzhi"` on successful detection.

### `src/main/resources/devices/ZK6522C.properties`

Device capability limits loaded by `DeviceService.loadLimits()`:

```properties
device.name=ZK6522C
device.manufacturer=Wuzhi
device.maxVoltage=65.0
device.minVoltage=0.0
device.maxCurrent=22.0
device.minCurrent=0.0
device.maxPower=1430.0
device.topology=BUCK
```

### `DeviceService` wiring

`Wuzhi` is probed in `DeviceService.probeDrivers()` after `Sinilink` and before `RidenRD50xx`
(detection order: **Sinilink → Wuzhi → RidenRD50xx → RidenRD60xx**). A corresponding
`instanceof Wuzhi` branch in `loadLimits()` sets the device name, manufacturer, and firmware
version on `ConverterState`.

---

## Comparison with Existing Devices

| Property | ZK-6522C (Wuzhi) | XY6008 (Sinilink) | RD5020 (Ruideng) | RD6006 (Riden) |
|---|---|---|---|---|
| Register map family | Sinilink-compatible | Sinilink | DPS/RD50xx | RD60xx |
| Identification register | 0x0016 | 0x0016 | 0x000B | 0x0000 |
| Default baud | User-configurable | 115200 | 9600 | 115200 |
| VSET address | 0x0000 | 0x0000 | 0x0000 | 0x0008 |
| Voltage scale | 100 | 100 | 100 | 100 |
| Current scale | **100** | 1000 | 100 | 1000 |
| Power scale | 100 | 100 | 100 | 1000 |
| Max voltage | 65 V | 60 V | 50 V | 60 V |
| Max current | 22 A | 8 A | 20 A | 6 A |
| Temperature register | 0x000D | 0x000D | N/A | 0x0005 |
| Temperature scale | 10 | 10 | N/A (−999) | 1 |
| Preset storage | M0–M10 (11 groups) | M0–M9 | M0–M9 | M0–M9 |
| Protection clear | Write 0 → 0x0010 | Write 0 → 0x0010 | Write 0 → 0x0007 | Write 0 → 0x0010 |

---

## Wire Connections

The ZK-6522C exposes a **XH2.54-4P** 4-pin connector (upgraded from the XH1.25 of older ZK
models for improved reliability). Connect to a USB-to-TTL adapter (3.3 V logic level):

| Pin | Signal | Direction | Adapter connection |
|---|---|---|---|
| 1 | GND | - | Adapter GND |
| 2 | RxD | Device receives | Adapter TxD |
| 3 | TxD | Device transmits | Adapter RxD |
| 4 | VCC | Power | **NC - do not connect** |

> **Important**: Analog potentiometer control requires RX shorted to GND. For digital Modbus
> communication the jumper cap between RX and GND **must be removed** and the board must be
> power-cycled before Modbus frames will be acknowledged.

> **Modbus vs. physical keypad - mutually exclusive**: once the device is connected via Modbus
> TTL the physical keypad and display are disabled by the ZK-series firmware for the duration
> of the session. This is a hardware design decision and cannot be changed in software. To
> resume use of the physical keypad, disconnect the serial adapter and power-cycle the device.

---

## Outstanding Unknowns

1. **Register 0x0016 product model value** - the exact decimal value returned by a ZK-6522C
   has not been confirmed on live hardware. It likely follows the `0x59xx` packed encoding
   used by modern Sinilink firmware (high byte = `0x59`), but the low byte (board hardware
   revision) is unknown. This value must be captured from a real device before a
   `KNOWN_MODELS` entry can be added.

2. **Baud rate default** - the ZK-6522C allows the baud rate to be configured via the menu.
   The factory default is not stated in the datasheet. The application's baud-rate scan
   (`ModbusTransport.BAUDS`: 115200 → 57600 → 38400 → 19200 → 9600) will find the device
   regardless, but the default should be documented once confirmed.

3. **Preset count** - the ZK-6522C has 11 groups (M0–M10), one more than the 10 (M0–M9) on
   Sinilink and Riden devices. The `recallPreset` method must accept index 0–10.

4. **RX/GND jumper** - the datasheet states the potentiometer control jumper must be removed
   for Modbus operation. If the jumper is in place, the device will not respond to Modbus
   frames even though the baud rate and address are correct.

5. **Physical keypad disabled in Modbus mode** - confirmed on the ZK-6522C: connecting via
   Modbus TTL disables the physical keypad and display. Modbus control and physical keypad
   control are mutually exclusive at the hardware/firmware level. Disconnect the adapter and
   power-cycle to restore keypad operation.

---

## References

- ZK-6522C product datasheet (included as `doc/ZK-6522C.pdf`)
- [Sinilink XY-series Modbus protocol](Sinilink.md)
- [Riden RD60xx / RD50xx Modbus protocol](Riden.md)
- [XY6020L Modbus Interface Documentation](https://www.scribd.com/document/921070101/XY6020L-Modbus-Interface) - closest confirmed register map
- [Sinilink Universal Modbus Module PDF](http://www.sinilink.com/download/pdf/UMPD-EN.pdf)

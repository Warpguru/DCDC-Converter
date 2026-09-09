# Modbus RTU — Register Read and Write Reference

This document describes the three Modbus RTU function codes used by the devices in this project and provides worked frame examples for each. It is the reference for any implementation work in `ModbusTransport`.

All devices in this project (Sinilink XY6008, Riden RD50xx, Riden RD60xx) support **only these three function codes**:

| Code | Name                    | Direction |
|------|-------------------------|-----------|
| 0x03 | Read Holding Registers  | Master → Slave (request), Slave → Master (response) |
| 0x06 | Write Single Register   | Master → Slave (request), Slave → Master (echo)     |
| 0x10 | Write Multiple Registers| Master → Slave (request), Slave → Master (ack)      |

---

## Serial Parameters

| Parameter    | Value      |
|--------------|------------|
| Baud rate    | 115200 (Sinilink) / 9600 default (Riden) |
| Data bits    | 8          |
| Parity       | None       |
| Stop bits    | 1          |
| Flow control | None       |
| Slave address| 1 (default)|

Frame timing: a silent gap of **≥ 3.5 character times** must precede and follow every frame.

---

## CRC16 (Modbus)

All frames end with a 16-bit CRC calculated over every preceding byte. The algorithm uses polynomial `0xA001` (reflected). Transmit order: **CRC low byte first, then high byte**.

```java
int crc = 0xFFFF;
for (byte b : data) {
    crc ^= (b & 0xFF);
    for (int i = 0; i < 8; i++) {
        if ((crc & 1) != 0)
            crc = (crc >> 1) ^ 0xA001;
        else
            crc >>= 1;
    }
}
// frame[n]   = (byte) crc;        // CRC low
// frame[n+1] = (byte)(crc >> 8);  // CRC high
```

---

## 0x03 — Read Holding Registers

Reads one or more consecutive 16-bit holding registers.

### Request frame

```
[slave][0x03][start_hi][start_lo][count_hi][count_lo][crc_lo][crc_hi]
  1 B    1 B    1 B       1 B      1 B       1 B       1 B     1 B
```

| Field       | Size   | Description                              |
|-------------|--------|------------------------------------------|
| slave       | 1 byte | Slave address (e.g. `0x01`)              |
| 0x03        | 1 byte | Function code                            |
| start       | 2 bytes| Starting register address, big-endian    |
| count       | 2 bytes| Number of registers to read (1–32)       |
| CRC         | 2 bytes| CRC16, low byte first                    |

### Response frame

```
[slave][0x03][byte_count][data_hi][data_lo]...[crc_lo][crc_hi]
  1 B    1 B     1 B       2 B per register        1 B    1 B
```

Total response length = **3 + (count × 2) + 2** bytes.

| Field       | Size         | Description                                 |
|-------------|--------------|---------------------------------------------|
| slave       | 1 byte       | Echoed slave address                        |
| 0x03        | 1 byte       | Echoed function code                        |
| byte_count  | 1 byte       | Number of data bytes = count × 2            |
| data        | count × 2 B  | Register values, big-endian, in address order|
| CRC         | 2 bytes      | CRC16, low byte first                       |

---

### Example A — Read one register (VOUT on Sinilink, address 0x0002)

**Request** (8 bytes):

```
01  03  00 02  00 01  65 CB
│   │   └──┬─┘ └──┬─┘ └──┬─┘
│   │   reg=0x0002 cnt=1  CRC
│   fc=0x03
slave=0x01
```

**Response** (7 bytes) — device reports 5.00 V (raw = 500 = 0x01F4):

```
01  03  02  01 F4  B9 30
│   │   │   └──┬─┘ └──┬─┘
│   │   │   500 raw   CRC
│   │   byte_count=2
│   fc=0x03
slave=0x01
```

Decoded: `500 / 100 = 5.00 V`

---

### Example B — Read two consecutive registers (VOUT + IOUT on Sinilink, addresses 0x0002–0x0003)

**Request** (8 bytes):

```
01  03  00 02  00 02  65 CB → CRC changes for count=2: use actual CRC
```

Exact bytes: `01 03 00 02 00 02 65 CB`

> **Note:** The CRC in the examples above uses the actual Modbus CRC for those exact byte sequences. Always recalculate for your specific register/count combination.

**Response** (9 bytes) — 5.00 V, 1.500 A:

```
01  03  04  01 F4  05 DC  xx xx
│   │   │   └──┬─┘ └──┬─┘ └──┬─┘
│   │   │   VOUT=500  IOUT=1500  CRC
│   │   byte_count=4
```

Decoded:
- `VOUT = 500 / 100 = 5.00 V`
- `IOUT = 1500 / 1000 = 1.500 A`  *(Sinilink current scale is ×1000)*

---

## 0x06 — Write Single Register

Writes a 16-bit value to one register. The slave echoes the entire request frame back unchanged as confirmation.

### Request frame

```
[slave][0x06][reg_hi][reg_lo][val_hi][val_lo][crc_lo][crc_hi]
  1 B    1 B   1 B     1 B     1 B     1 B     1 B     1 B
```

| Field  | Size   | Description                           |
|--------|--------|---------------------------------------|
| slave  | 1 byte | Slave address                         |
| 0x06   | 1 byte | Function code                         |
| reg    | 2 bytes| Target register address, big-endian   |
| value  | 2 bytes| 16-bit value to write, big-endian     |
| CRC    | 2 bytes| CRC16, low byte first                 |

### Response frame

Identical to the request (8 bytes). If the write failed the slave returns an error frame instead (function code `0x86`, followed by an exception code).

---

### Example C — Set voltage to 12.00 V on Sinilink (VSET = 0x0000, raw = 1200 = 0x04B0)

**Request** (8 bytes):

```
01  06  00 00  04 B0  48 3B
│   │   └──┬─┘ └──┬─┘ └──┬─┘
│   │   reg=0x0000  1200   CRC
│   fc=0x06
slave=0x01
```

**Response** (8 bytes — echo):

```
01  06  00 00  04 B0  48 3B
```

Decoded: `1200 / 100 = 12.00 V`

---

### Example D — Enable output on RidenRD60xx (OUTPUT_ENABLE = 0x0012, value = 1)

**Request** (8 bytes):

```
01  06  00 12  00 01  E8 0F
│   │   └──┬─┘ └──┬─┘ └──┬─┘
│   │   reg=0x0012  1=ON   CRC
│   fc=0x06
slave=0x01
```

**Response** (8 bytes — echo):

```
01  06  00 12  00 01  E8 0F
```

---

## 0x10 — Write Multiple Registers

Writes 16-bit values to a **contiguous block** of registers in one frame. Useful when two or more adjacent registers must be updated together (e.g. VSET + ISET).

### Request frame

```
[slave][0x10][start_hi][start_lo][qty_hi][qty_lo][byte_count][data...][crc_lo][crc_hi]
  1 B    1 B    1 B      1 B      1 B     1 B       1 B       qty×2 B   1 B     1 B
```

Total request length = **7 + (qty × 2) + 2** bytes.

| Field       | Size      | Description                                        |
|-------------|-----------|---------------------------------------------------|
| slave       | 1 byte    | Slave address                                      |
| 0x10        | 1 byte    | Function code                                      |
| start       | 2 bytes   | Starting register address, big-endian              |
| qty         | 2 bytes   | Number of registers to write (1–32)                |
| byte_count  | 1 byte    | Number of data bytes = qty × 2                     |
| data        | qty × 2 B | Register values in address order, big-endian each  |
| CRC         | 2 bytes   | CRC16 over all preceding bytes, low byte first     |

### Response frame

```
[slave][0x10][start_hi][start_lo][qty_hi][qty_lo][crc_lo][crc_hi]
  1 B    1 B    1 B      1 B      1 B     1 B      1 B     1 B
```

Fixed 8-byte acknowledgement. Echoes the starting address and quantity written.

| Field  | Size   | Description                            |
|--------|--------|----------------------------------------|
| slave  | 1 byte | Echoed slave address                   |
| 0x10   | 1 byte | Echoed function code                   |
| start  | 2 bytes| Echoed starting address                |
| qty    | 2 bytes| Number of registers written            |
| CRC    | 2 bytes| CRC16, low byte first                  |

---

### Example E — Set VSET + ISET together on Sinilink (addresses 0x0000–0x0001): 5.00 V, 2.500 A

Sinilink scales: voltage ×100, current ×1000.

Raw values:
- VSET = 5.00 × 100 = 500 = `0x01F4`
- ISET = 2.500 × 1000 = 2500 = `0x09C4`

**Request** (13 bytes):

```
01  10  00 00  00 02  04  01 F4  09 C4  xx xx
│   │   └──┬─┘ └──┬─┘ │   └──┬─┘ └──┬─┘ └──┬─┘
│   │  start=0 qty=2  │   500    2500    CRC
│   fc=0x10           byte_count=4
slave=0x01
```

**Response** (8 bytes):

```
01  10  00 00  00 02  41 C8
│   │   └──┬─┘ └──┬─┘ └──┬─┘
│   │  start=0  qty=2     CRC
│   fc=0x10
slave=0x01
```

---

### Example F — Set VSET + ISET together on RidenRD60xx (addresses 0x0008–0x0009): 12.00 V, 1.000 A

RidenRD60xx scales: voltage ×100, current ×1000.

Raw values:
- VSET = 12.00 × 100 = 1200 = `0x04B0`
- ISET = 1.000 × 1000 = 1000 = `0x03E8`

**Request** (13 bytes):

```
01  10  00 08  00 02  04  04 B0  03 E8  xx xx
│   │   └──┬─┘ └──┬─┘ │   └──┬─┘ └──┬─┘ └──┬─┘
│   │  start=8 qty=2  │   1200    1000    CRC
│   fc=0x10           byte_count=4
slave=0x01
```

**Response** (8 bytes):

```
01  10  00 08  00 02  xx xx
```

---

## Register Address Reference

### Sinilink XY6008

| Address | Name      | Access | Scale | Description         |
|---------|-----------|--------|-------|---------------------|
| 0x0000  | VSET      | R/W    | ×100  | Voltage setpoint    |
| 0x0001  | ISET      | R/W    | ×1000 | Current setpoint    |
| 0x0002  | VOUT      | R      | ×100  | Measured voltage    |
| 0x0003  | IOUT      | R      | ×1000 | Measured current    |
| 0x0004  | POUT      | R      | ×100  | Measured power      |
| 0x0005  | VIN       | R      | ×100  | Input voltage       |
| 0x000F  | LOCK      | R/W    | —     | Keypad lock (0/1)   |
| 0x0010  | PROTECTION| R/W    | —     | Protection state    |
| 0x0012  | OUTPUT    | R/W    | —     | Output enable (0/1) |
| 0x0016  | MODEL     | R      | —     | Model identifier    |
| 0x0017  | FIRMWARE  | R      | —     | Firmware version    |

### Riden RD50xx

| Address | Name      | Access | Scale | Description         |
|---------|-----------|--------|-------|---------------------|
| 0x0000  | VSET      | R/W    | ×100  | Voltage setpoint    |
| 0x0001  | ISET      | R/W    | ×100  | Current setpoint    |
| 0x0002  | VOUT      | R      | ×100  | Measured voltage    |
| 0x0003  | IOUT      | R      | ×100  | Measured current    |
| 0x0004  | POUT      | R      | ×100  | Measured power      |
| 0x0005  | VIN       | R      | ×100  | Input voltage       |
| 0x0006  | LOCK      | R/W    | —     | Keypad lock (0/1)   |
| 0x0007  | PROTECTION| R      | —     | Protection state    |
| 0x0009  | OUTPUT    | R/W    | —     | Output enable (0/1) |
| 0x000B  | DEVICE_ID | R      | —     | Model identifier    |
| 0x0014  | FIRMWARE  | R      | ÷100  | Firmware version    |

### Riden RD60xx

| Address | Name      | Access | Scale | Description         |
|---------|-----------|--------|-------|---------------------|
| 0x0000  | DEVICE_ID | R      | —     | Model identifier    |
| 0x0003  | FIRMWARE  | R      | ÷100  | Firmware version    |
| 0x0008  | VSET      | R/W    | ×100  | Voltage setpoint    |
| 0x0009  | ISET      | R/W    | ×1000 | Current setpoint    |
| 0x000A  | VOUT      | R      | ×100  | Measured voltage    |
| 0x000B  | IOUT      | R      | ×1000 | Measured current    |
| 0x000D  | POUT      | R      | ×100  | Measured power (÷1000 in driver — see AGENTS.md) |
| 0x000E  | VIN       | R      | ×100  | Input voltage       |
| 0x000F  | LOCK      | R/W    | —     | Keypad lock (0/1)   |
| 0x0010  | PROTECTION| R      | —     | Protection state    |
| 0x0012  | OUTPUT    | R/W    | —     | Output enable (0/1) |

---

## Key Observations for this Codebase

- **VSET and ISET are always at consecutive addresses** (VSET, VSET+1) on all three devices. A single `0x10` frame can therefore set both in one serial round-trip instead of two.
- **`0x10` with `qty=1`** is functionally equivalent to `0x06` but produces a shorter acknowledgement (8 bytes vs 8 bytes — identical). Prefer `0x06` for single-register writes; it is simpler.
- The **`0x10` response is always 8 bytes** regardless of how many registers were written. Plan `readBytes(8)` after transmitting a multi-write frame.
- The **`0x03` response length** is variable: `3 + (count × 2) + 2` bytes. Calculate it from the requested count before reading.

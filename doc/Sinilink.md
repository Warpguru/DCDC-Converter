# Sinilink Model Identification via Modbus RTU

Sinilink XY-series devices expose their identity through two Holding Registers readable with
Function Code `0x03`:

| Register | Address | Description |
|---|---|---|
| Product model number | `0x0016` | Unique firmware-embedded product code (see below) |
| Firmware version | `0x0017` | Raw integer; divide by 100 for version number |

---

## Register 0x0016 — Product Model Number

The value returned by this register changed format between hardware generations. There are
**two distinct encoding schemes** in the field; both must be handled.

### Scheme 1 — Legacy (flat integer)

Early hardware batches return a plain decimal integer that directly encodes the model number.
No bit manipulation is required.

| Decimal value | Model |
|---|---|
| 5008 | XY5008 |
| 6008 | XY6008 |
| 6014 | XY6014 |
| 6020 | XY6020L |
| 3680 | XYH3680 |

Some early datasheets also document the model number encoded as a hex word read as decimal
(e.g. the XY6020L documentation lists `0x6100` = 24832). These are treated as confirmed
values:

| Decimal value | Hex | Model |
|---|---|---|
| 20488 | `0x5008` | XY5008 |
| 24584 | `0x6008` | XY6008 |
| 24832 | `0x6100` | XY6020L |
| 13831 | `0x3607` | XY3607F |
| 6149 | `0x1805` | SK180S |
| 8713 | `0x2209` | SK220S |

### Scheme 2 — Modern packed encoding (`0x59xx`)

Unified firmware on current production boards packs two pieces of information into the
16-bit register:

- **High byte = `0x59`** — the ASCII character `'Y'`, the 'Y' from the "XY" product-line
  prefix. This is the Sinilink family gate: any value whose high byte is `0x59` is a modern
  Sinilink device.
- **Low byte** — the control-board hardware revision (e.g. `0x12` = revision 1.8).

> The official factory name for this register is **Chan-pin Xing-hao** ("product model
> number"), described as the "unique product identification code built into the firmware".
> The factory documentation never explains the high/low byte split; the community derived it
> from raw register dumps across device batches.

Community-reported packed values (not confirmed by factory documentation; low byte encodes
board hardware revision):

| Decimal value | Hex | Board revision | Model (reported) |
|---|---|---|---|
| 22792 | `0x5908` | v0.8 | XY5008 |
| 22802 | `0x5912` | v1.8 | XY6008 |
| 22804 | `0x5914` | v2.0 | XY6008 / XY6020L |
| 22798 | `0x590E` | v1.4 | XY6014 |
| 22797 | `0x590D` | v1.3 | XY6020L |
| 22794 | `0x590A` | v1.0 | XYH3680 |

### Detection algorithm

1. **Exact match against confirmed values** (legacy flat integers + documented hex words).
   If found, use that model — no warning.
2. **Packed-encoding fallback** — only if no exact match and high byte = `0x59`: look up the
   full word in the community-reported table. If found, log a `WARN` and treat as that model
   (promote to confirmed table once verified on live hardware).
3. **Unknown packed value** — high byte = `0x59` but word not in either table: log a `WARN`
   and skip — do not guess.

---

## Register 0x0017 — Firmware Version

The raw integer value divides by 100 to give the firmware version.

| Raw value | Firmware version |
|---|---|
| 110 | v1.10 |
| 116 | v1.16 |

---

## Model Capability Reference

| Model | Topology | Max voltage | Max current | Current resolution | Max power | Default baud |
|---|---|---|---|---|---|---|
| XY5008 | Buck | 50 V | 8 A | 1 mA (3 decimal places) | 400 W | 115200 |
| XY6008 | Buck | 60 V | 8 A | 1 mA (3 decimal places) | 480 W | 115200 |
| XY6014 | Buck | 60 V | 14 A | 1 mA (3 decimal places) | 840 W | 115200 |
| XY6020L | Buck | 60 V | 20 A | 10 mA (2 decimal places) | 1200 W | 115200 |
| XYH3680 | Buck | 36 V | 80 A | 10 mA (2 decimal places) | 2880 W | 115200 |
| XY3607F | Buck | 36 V | 7 A | 10 mA (2 decimal places) | 250 W | 115200 |
| SK180S | Buck | 18 V | — | — | 180 W | 115200 |
| SK220S | Buck | 22 V | — | — | 220 W | 115200 |

> **Note on current resolution:** The XY6008 offers 1 mA steps; the XY6020L drops to 10 mA
> steps due to shunt resistor constraints at higher current. This affects the scaling factor
> used for the `ISET` and `IOUT` registers (×1000 vs. ×100).

---

## Key Implementation Notes

### Current scaling

Unlike Riden devices (which use a fixed 2-decimal current scale), Sinilink varies the `ISET`
/ `IOUT` scale by model:

| Scale | Models | Resolution |
|---|---|---|
| 1000 (raw ÷ 1000 = A) | XY5008, XY6008, XY6014 | 1 mA |
| 100 (raw ÷ 100 = A) | XY6020L, XYH3680, XY3607F | 10 mA |

### Electrical isolation

Sinilink modules tie their serial lines directly to the main MCU ground rail. When connecting
multiple units to a single Modbus bus, use an optically isolated 3.3 V TTL adapter to prevent
ground loops from damaging the host UART.

---

## References

- [XY6020L Modbus Interface Documentation](https://www.scribd.com/document/921070101/XY6020L-Modbus-Interface)
- [Sinilink Universal Modbus Module PDF](http://www.sinilink.com/download/pdf/UMPD-EN.pdf)
- [XY5008-S Instruction Manual](https://www.mantech.co.za/datasheets/products/XY5008-S-220467.pdf)
- [XY6020L hardware datasheet](http://attach01.oss-us-west-1.aliyuncs.com/IC/Datasheet/GY21208.pdf)
- [Community ESPHome / register dump data](https://github.com/framenic/sinilink-modbus)
- [XY6020L community integration](https://github.com/tinkering4fun/XY6020L-Modbus)

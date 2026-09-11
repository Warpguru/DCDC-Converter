# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Environment Setup (CRITICAL)

Before running **any** Maven or Java command, you MUST first invoke these two batch files in `cmd.exe`:

```bat
D:\Development\SetupEnvMaven.cmd
D:\Development\SetupEnvJava21.cmd
```

Only then will `mvn` and `java` (21) be available in the environment.

## Build & Run Commands

```bat
mvn clean source:jar install                   # Compile + fat JAR → target/SerialController.jar
java -jar target/SerialController.jar <port>   # Run; <port> e.g. COM3 or /dev/ttyUSB0
```

There are **no automated tests** - testing requires a physical serial device to be attached.

## Stack

Java 21 · Javalin 7.2.3 · Jackson 2.22.2 · jSerialComm 2.11.4 · SLF4J 2.0.17 → Log4j2 2.25.3 · Maven Shade (fat JAR)

## Architecture

```
SerialControllerApp (entry point - wiring only)
  ├── DeviceService               # owns: converter, ConverterState, polling thread (modbus-poller)
  │     ├── DC2DCConverter (ifc)  # com.serial.devices.ifc.DC2DCConverter
  │     │     ├── Sinilink
  │     │     ├── RidenRD50xx
  │     │     └── RidenRD60xx
  │     │           └── ModbusDevice (abstract, com.serial.device.base)
  │     │                 └── ModbusTransport  # raw RTU framing via jSerialComm
  │     └── ConverterState        # all fields volatile; limit setters are package-private
  ├── WebSocketService            # owns: client set, broadcast thread (ws-broadcaster)
  └── RestService                 # Javalin route handlers + OpenAPI annotations
```

- `DC2DCConverter` - common interface for all power-supply drivers; lives at `com.serial.devices.ifc.DC2DCConverter`.
- `DeviceRegister` - holds address + scale factor; all instances self-register into static `REGISTRY` (`ConcurrentHashMap`) on construction (used by `ModbusTransport.log()` for frame annotation).
- **CRITICAL:** `DC2DCConverter.getVoltage()` reads VOUT (measured output), NOT the setpoint VSET. Setpoints require driver-specific register access via `DeviceService.readSetpoints()`, which casts to the concrete driver type.
- `ConverterState` limit setters (`setMaxVoltage`, `setMinVoltage`, etc.) are **package-private** - only `DeviceService` may call them.
- `DeviceService.getObjectMapper()` returns the single shared Jackson `ObjectMapper`; do not construct additional instances.

## Threading Model

Two application-owned background threads only:
1. `modbus-poller` (in `DeviceService`) - reads all registers every 1 s; `synchronized` on `DeviceService`.
2. `ws-broadcaster` (in `WebSocketService`) - pushes full `ConverterState` JSON to all WS clients every 1 s.

All write methods in `DeviceService` (`setVoltage`, `setCurrent`, `setOutput`, `setKeypad`, `clearProtection`) are `synchronized` on `DeviceService`. This maps to a FreeRTOS mutex in the planned ESP32 C port. Do **not** introduce `ExecutorService`, `CompletableFuture`, or other Java-specific threading abstractions.

## Device Detection Pattern

Detection order in `DeviceService.detectDevice()`: Sinilink → RidenRD50xx → RidenRD60xx.
`Sinilink.verifyDevicePresent()` probes baud rates in descending order (`115200 → 57600 → 38400 → 19200 → 9600`) via `ModbusTransport.BAUDS`.

## Register Scaling

| Device          | Quantity    | Scale | Example              |
|-----------------|-------------|-------|----------------------|
| Sinilink XY6008 | Voltage     | 100   | 500 raw = 5.00 V     |
| Sinilink XY6008 | Current     | 1000  | 2500 raw = 2.500 A   |
| Sinilink XY6008 | Power       | 100   | 123 raw = 1.23 W     |
| Riden RD50xx    | Current     | 100   | 250 raw = 2.50 A     |
| Riden RD50xx    | Power       | 100   | 123 raw = 1.23 W     |
| Sinilink REG_MODE | CV/CC     | -     | 0 = CV, 1 = CC       |

## Reconnect / Online Hysteresis

- After USB unplug, `SerialPort` object is invalid - recovery requires a fresh `SerialPort.getCommPort()` call via `ModbusTransport.reconnect()`.
- `ConverterState.deviceOnline` uses **3 consecutive failures** → Offline + reconnect; **3 consecutive successes** → Online (symmetric threshold prevents false-Online flash during unstable reconnect).

## REST / WebSocket API (Javalin, port 8000)

Static UI served from `src/main/resources/public/` at `/`; OpenAPI JSON at `/openapi`; Swagger UI at `/openapi/ui`.

REST endpoints (all under `/api`):

| Method | Path                    | Description                                           |
|--------|-------------------------|-------------------------------------------------------|
| GET    | `/api/state`            | Full `ConverterState` snapshot                        |
| GET    | `/api/limits`           | Device limits only (`LimitsResponse` DTO)             |
| PUT    | `/api/voltage`          | `{"voltage": 5.0}` - set output voltage setpoint      |
| PUT    | `/api/current`          | `{"current": 1.0}` - set output current setpoint      |
| PUT    | `/api/output`           | `{"outputEnable": true}` - enable/disable output      |
| PUT    | `/api/keypad`           | `{"keypadLock": true}` - lock/unlock keypad           |
| POST   | `/api/protection/clear` | Clear tripped protection (no body)                    |
| POST   | `/api/exit`             | Shutdown (Basic Auth; credentials in `serial-controller.properties` next to JAR) |

WebSocket `/ws/data` - server pushes full `ConverterState` JSON every 1 s.
Client may send: `{"setCurrent": 1.0}`, `{"setVoltage": 5.0}`, `{"setOutput": true}`, `{"setKeypad": true}`.

Application runs for 600 s then shuts down; `/api/exit` wakes it early via `shutdownLock.notifyAll()`.

## Code Style

- **Logger**: `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);` (SLF4J → Log4j2)
- Logging config: `src/main/resources/log4j2.xml` (not `.properties`); logs to `./SerialController.log` + console.
- `@formatter:off` / `@formatter:on` around **all** `@OpenApi` annotation blocks - preserve this.
- `@Deprecated` on `demoVoltages()` - do not remove; do not call.
- `final` on **all** method parameters is the project convention.
- Javadoc is mandatory on all public/protected members; include `<p>`, `<pre>`, `<ul>` examples where relevant.
- DTO and inner classes (e.g. `VoltageRequest`, `LimitsResponse`) live as `public static` inner classes of their owning service.
- **String literals that appear in logic must be named constants** - e.g. `ERR_SERIAL_TIMEOUT`, `KEY_SET_CURRENT`. Never compare against or branch on a bare string literal.
- **Web Interface**: plain HTML, minimal CSS, JavaScript only when HTML cannot do it - no JS libraries.

## Duplicate / Legacy Code

- `src/main/java/com/serial/util/` - superseded by `com.serial.modbus.*`; only `.txt` stubs remain; do not import from here.
- `*.java.txt`, `*Old.java.txt`, `XY6008TestTool*.java`, `TestXY6008.java` at the `com.serial` root - scratch/legacy; do not modify.
- `HtmlService.java` - stub only; `index.html` is served directly as static file.
- `demoVoltages()` in `SerialControllerApp` - `@Deprecated`, empty body; do not call, do not delete.

## Cross-Platform Notes

- Target: JDK 21, Windows primary; must remain compatible with Linux and Raspberry Pi.
- A future C port for ESP32 is planned - keep Modbus framing logic self-contained and free of Java-specific idioms.

## Agent Behaviour Rules

- **Do not assume** when multiple solutions are possible - explain the alternatives and ask how to proceed.
- **Do not drop existing implementation** - prefer enhancing it; if large removal seems necessary, ask first.
- **Follow per-iteration instructions carefully** - additional context and constraints are in each `Iteration N.md` file; read the latest one before acting.
- **Verify library currency** - ensure all suggested third-party libraries are up-to-date and actively maintained.
- **Plan before implementing** - carefully plan every change before starting implementation.

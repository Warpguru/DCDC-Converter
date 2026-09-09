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

There are **no automated tests** — testing requires a physical serial device to be attached.

## Architecture

```
SerialControllerApp (main)
  └── ModbusTransport        # raw RTU framing over jSerialComm SerialPort
        └── ModbusDevice (abstract)
              ├── Sinilink   ──implements──> DC2DCConverter interface
              ├── RidenRD50xx
              └── RidenRD60xx
```

- `DC2DCConverter` — the common interface for all power-supply drivers; all new device drivers must implement it.
- `DeviceRegister` — holds address + scale factor; `encode()` / `decode()` convert engineering ↔ raw values.
- `ModbusDevice.writeVerified()` retries up to `MAX_RETRY=3` times and reads back to confirm; use it instead of plain `write()` when accuracy matters.

## Device Detection Pattern

`Sinilink.verifyDevicePresent()` probes baud rates in descending order (`115200 → 57600 → 38400 → 19200 → 9600`) via `ModbusTransport.BAUDS`. New device drivers should follow the same auto-baud pattern.

## Register Scaling (Sinilink / XY6008)

| Quantity    | Scale factor | Example         |
|-------------|-------------|-----------------|
| Voltage     | 100         | 500 raw = 5.00 V |
| Current     | 1000        | 2500 raw = 2.500 A |
| Power       | 100         | 123 raw = 1.23 W |
| Temperature | 10          | 350 raw = 35.0 °C |

Registers 0x0000/0x0001 mirror active memory M0 (0x0050/0x0051). Calling `REG_MEMORY_RECALL` copies a preset into M0.

## REST / WebSocket API (Javalin, port 8000)

- Static UI served from `src/main/resources/public/` at `/`
- WebSocket `/ws/data` — server pushes `{"voltage":…,"current":…}` every 1 s; client sends `{"setCurrent":<0.0–2.0>}`
- OpenAPI JSON endpoint at `/openapi`
- Swagger UI at `/openapi/ui`
- Server runs for 600 s then stops; adjust `sleepSeconds(600)` in `SerialControllerApp` to change lifetime.

## Code Style

- **Logger**: `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);` (SLF4J)
- Log to file (`./SerialController.log`) + console simultaneously via `log4j2.properties`.
- `@formatter:off` / `@formatter:on` used around multi-line `@OpenApi` annotations to suppress IDE reformatting — preserve this.
- `@Deprecated` on `demoVoltages()` and `ModbusTransport.log()` — do not remove these markers.
- `final` on all method parameters is the project convention (`final String portName`, etc.).
- Javadoc is mandatory on all public/protected members; include `<p>`, `<pre>`, `<ul>` examples where relevant.
- **Web Interface**
      * Do use plain HTML whenever possible
      * Do use CSS as sparse as possible
      * Do use JavaScript only if plain HTML is not sufficient
      * Do not use any JavaScript library!

## Duplicate / Legacy Code

`src/main/java/com/serial/util/` mirrors `src/main/java/com/serial/modbus/` — the `util` package is superseded; use `com.serial.modbus.*` for any new code.
Several `*.java.old` / `*Old.java` / `*TestTool*.java` files exist at the root of `com.serial` — these are scratch/legacy; do not modify or import from them.

## Cross-Platform Notes

- Target: JDK 21, Windows primary; must remain compatible with Linux and Raspberry Pi.
- A future C port for ESP32 is planned — keep Modbus framing logic self-contained and free of Java-specific idioms where possible.

## Agent Behaviour Rules

These rules were established by the project owner and apply to all agents working in this repository:

- **Do not assume** when multiple solutions are possible — explain the alternatives and ask how to proceed.
- **Do not drop existing implementation** — prefer enhancing it; if large removal seems necessary, ask first.
- **Follow per-iteration instructions carefully** — additional context and constraints are provided in each `Iteration N.md` file; read the latest one before acting.
- **Verify library currency** — ensure all suggested code and third-party libraries are up-to-date and actively maintained before recommending them.
- **Plan before implementing** — carefully plan every change before starting implementation.

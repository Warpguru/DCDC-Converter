# Project Documentation Rules (Non-Obvious Only)

- **`Agent.md`** (project root) contains the original project brief and the critical note that Maven/Java env scripts must be sourced before any build command — read it for project background.
- **`Iteration N.md`** files in the project root document per-iteration requirements; check the latest one for current scope.
- **`src/main/java/com/serial/util/`** mirrors `com.serial.modbus` but is superseded; it exists only as historical reference — documentation about Modbus should reference `com.serial.modbus.*`.
- **Register maps live in `com.serial.device.*Registers.java`** (e.g. `SinilinkRegisters`), not in the device driver classes — look there for address/scaling reference.
- **`DC2DCConverter`** is the canonical API contract for all power supply devices; it is the right place to look for supported operations.
- **OpenAPI / Swagger** is served at `/swagger` when the app is running; it reflects the `@OpenApi` annotations in `SerialControllerApp`.
- **No test documentation exists** — the project explicitly defers testing until a physical device is available.

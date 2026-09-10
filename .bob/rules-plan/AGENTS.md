# Project Architecture Rules (Non-Obvious Only)

- **`DeviceService` owns the serial port exclusively** - only one `ModbusTransport` instance may exist per port at runtime. Any code that constructs its own `ModbusTransport` will conflict and fail.
- **`DC2DCConverter.getVoltage()` is VOUT (measured), not VSET (setpoint)** - there is no `getVoltageSet()` on the interface. Setpoints are read by casting to the concrete driver in `DeviceService.readSetpoints()`.
- **`ConverterState` limit fields are set exactly once** after device detection; their setters are package-private. Nothing outside `DeviceService` may change them.
- **Threading constraint is architectural, not stylistic** - exactly two background threads (`modbus-poller`, `ws-broadcaster`); must map to two FreeRTOS tasks on the planned ESP32 C port. No additional threads, no Java-specific concurrency primitives.
- **Device detection order is fixed:** Sinilink → RidenRD50xx → RidenRD60xx. New device support must be added to this chain in `DeviceService.detectDevice()`.
- **Setpoints are polled every cycle, not cached** - Modbus RTU is master-only; the device never pushes data. Front-panel knob/button changes are detected only via polling.
- **`SerialControllerApp` is wiring-only** - it must not contain business logic; all logic lives in the `com.serial.service` package.
- **`DeviceRegister.REGISTRY` is a global side-effect** - all `DeviceRegister` instances auto-register on construction into a static `ConcurrentHashMap` keyed by address. Creating two registers at the same address silently overwrites the first.
- **OpenAPI annotation processor** (`io.javalin.community.openapi:openapi-annotation-processor`) runs at compile time - `@OpenApi` annotations generate the spec at build time. Changing annotations requires a rebuild; the running JAR will not reflect changes otherwise.
- **Application lifetime is hard-coded at 600 s** via `shutdownLock.wait(600_000L)` in `SerialControllerApp.process()`. The only exits are `POST /api/exit` (calls `notifyAll()`) or process kill.
- **jSerialComm `SerialPort` object is not reusable after disconnect** - the OS-level port handle is invalidated. Recovery requires `ModbusTransport.reconnect()` which calls `SerialPort.getCommPort()` fresh. The `ModbusDevice.reconnect()` and `DC2DCConverter.reconnect()` chain delegates to this.
- **Serial timeout → immediate Offline + reconnect** - `"Serial timeout"` (matched via `ERR_SERIAL_TIMEOUT` constant) bypasses the `MAX_CONSECUTIVE_FAILURES` counter; any other exception still requires 3 consecutive failures.
- **Going Online requires only 1 successful poll** (`MAX_CONSECUTIVE_SUCCESSES = 1`) - a complete poll cycle already validates every register read; no further confirmation needed.
- **`writeVerified()` has ~600 ms latency** due to its read-back retry loop - it is NOT used for voltage/current setpoints in `DeviceService`; plain `write()` is used instead. Reserve `writeVerified()` only for one-shot configuration writes where latency is acceptable.
- **Riden RD50xx scale correction** - `ISET`/`IOUT` scale is 100 (A×100), `POUT` scale is 100 (W×100); original register comments said 1000 and were wrong. RD60xx uses 1000 for current.

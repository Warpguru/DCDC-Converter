# Project Documentation Context (Non-Obvious Only)

- **`com.serial.util.*`** looks like the Modbus implementation but all `.java` files are renamed `.txt` stubs - the real implementation is in `com.serial.modbus.*`.
- **`DC2DCConverter` interface** is NOT in `com.serial.devices` - it is in `com.serial.devices.ifc.DC2DCConverter`.
- **`ConverterState` JSON field names** used by REST and WebSocket clients: `voltageOut`, `currentOut`, `powerOut`, `voltageIn`, `temperatureCelsius`, `voltageSet`, `currentSet`, `outputEnabled`, `keypadLocked`, `protectionState`, `cvMode`, `deviceOnline`. Note `getVoltage()` is NOT a JSON key.
- **`/api/limits`** returns a `LimitsResponse` DTO (a subset of `ConverterState`), not the full state. Full state is at `/api/state`.
- **Iteration files (`Iteration N.md`)** are the authoritative spec for each development phase - read the latest before acting on any code change.
- **`log4j2.xml`** (not `.properties`) is the logging config - `src/main/resources/log4j2.xml`; outputs to `./SerialController.log` and console.
- **`serial-controller.properties`** is an external runtime file placed next to the JAR - it is NOT in the source tree. Required only for `/api/exit` Basic Auth (keys: `exit.username`, `exit.password`).
- **`HtmlService.java`** is a stub - it contains no implementation; `index.html` is served directly as a static file from `src/main/resources/public/`.
- **Application auto-shuts down after 600 s** - controlled by `shutdownLock.wait(600_000L)` in `SerialControllerApp.process()`; early exit via `POST /api/exit` which calls `notifyAll()`.
- **Sinilink `REG_MODE`**: 0 = CV, 1 = CC - `isCvMode()` returns `true` when register reads 0.
- **Riden RD50xx current/power scale is 100**, not 1000 - historical register comments were wrong; this was corrected in the driver source.
- **jSerialComm reconnect gotcha**: after USB unplug the `SerialPort` object becomes permanently invalid; only a fresh `SerialPort.getCommPort()` call (in `ModbusTransport.reconnect()`) restores communication.

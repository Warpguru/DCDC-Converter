# Iteration 5

The goal of this iteration is to establish `ConverterState` as the single source of truth for all converter data, introduce device-capability limits sourced from a properties file, and move all business logic and state out of `SerialControllerApp` into `DeviceService`.

## Context

Up to Iteration 4, `SerialControllerApp` owns state directly (`currentSetting` volatile, `objectMapper`, WebSocket client set) and performs device detection inline. This iteration introduces the service layer so that subsequent iterations (REST API, WebSocket refactor, webpage enhancement) have a clean, shared foundation.

The device capability limits (maxVoltage, maxCurrent, maxPower, etc.) will be read from a per-device properties file under `src/main/resources/devices/`, one file per supported device model, with values taken from the device datasheet.

## Scope

This iteration is **Java / service-layer only** — no changes to `index.html` and no new REST endpoints.

## Tasks

### 1. Implement `ConverterState`

File: `src/main/java/com/serial/service/ConverterState.java`

`ConverterState` is the thread-safe holder of all converter state. It contains three groups of fields:

**Measured values** (updated by the polling thread in `DeviceService`):
- `double voltageOut` — measured output voltage (V)
- `double currentOut` — measured output current (A)
- `double powerOut` — measured output power (W)
- `double voltageIn` — measured input voltage (V)
- `double temperatureCelsius` — internal temperature (°C)
- `boolean outputEnabled` — current output on/off state
- `int protectionState` — current protection state code (0 = normal; see `SinilinkRegisters` for codes)
- `boolean cvMode` — `true` = CV (constant voltage), `false` = CC (constant current)

**Setpoints** (written by the user via WebPage or REST):
- `double voltageSet` — voltage setpoint (V)
- `double currentSet` — current setpoint (A)

**Device limits** (read-only after initialisation from properties file):
- `String deviceName` — e.g. `"XY6008"`, `"RD5020"`
- `String manufacturer` — e.g. `"Sinilink"`, `"Riden"`
- `double maxVoltage` — maximum output voltage (V)
- `double minVoltage` — minimum output voltage (V)
- `double maxCurrent` — maximum output current (A)
- `double minCurrent` — minimum output current (A)
- `double maxPower` — maximum output power (W)

All mutable fields must be `volatile`. Provide full Javadoc. Provide getters and setters for all fields; setters for limits are package-private (only `DeviceService` sets them).

### 2. Create Device Properties Files

Directory: `src/main/resources/devices/`

Create one `.properties` file per supported device, named exactly after the device name string returned by `ModbusDevice.device` after detection.

**`XY6008.properties`** (Sinilink XY6008, from datasheet):
```
device.name=XY6008
device.manufacturer=Sinilink
device.maxVoltage=60.0
device.minVoltage=0.0
device.maxCurrent=8.0
device.minCurrent=0.0
device.maxPower=480.0
```

**`RD5020.properties`** (Riden RD5020, from datasheet):
```
device.name=RD5020
device.manufacturer=Riden
device.maxVoltage=50.0
device.minVoltage=0.0
device.maxCurrent=20.0
device.minCurrent=0.0
device.maxPower=1000.0
```

**`RD6020.properties`** (Riden RD6020, from datasheet):
```
device.name=RD6020
device.manufacturer=Riden
device.maxVoltage=60.0
device.minVoltage=0.0
device.maxCurrent=20.0
device.minCurrent=0.0
device.maxPower=1200.0
```

Add further files for other Riden models as needed (the naming convention is `<deviceName>.properties` matching `ModbusDevice.device` exactly).

If no matching properties file exists for a detected device, `DeviceService` logs a warning and uses safe defaults (0 for all limits, so no writes are accepted until limits are known).

### 3. Implement `DeviceService`

File: `src/main/java/com/serial/service/DeviceService.java`

`DeviceService` is responsible for:
- Detecting the converter on the given port (tries Sinilink, RidenRD50xx, RidenRD60xx in order).
- Loading device limits from the matching properties file.
- Populating and maintaining `ConverterState`.
- Running the background polling thread that reads **all** register values from the device every second and updates `ConverterState`.

> **Note — Modbus RTU is strictly master/slave.** The device never transmits unsolicited data. When the user changes voltage or current using the physical buttons/wheel on the front panel, the device updates its internal registers silently. The PC discovers the change only on the next poll. There is no listener or interrupt mechanism available. The polling thread must therefore read both measured values **and** setpoints (VSET, ISET) every cycle, so that front-panel changes are reflected in `ConverterState` and propagated to the webpage and REST API automatically.

**Constructor:** `DeviceService(final String portName)`

On construction:
1. Run device detection (try each driver class in order until `isDeviceDetected()` returns `true`).
2. Load the matching `.properties` file from `devices/<deviceName>.properties` on the classpath.
3. Populate `ConverterState` limits and identity fields.
4. Read the current setpoints from the device (registers VSET, ISET) and store in `ConverterState` so the initial state is accurate before the first poll cycle.

**Threading model and ESP32 portability constraint:**

> This application will eventually be ported to an ESP32, which has exactly two application tasks: one for the web server and one for device control. The Java threading model must reflect this constraint — no Java-specific concurrency abstractions (no `ExecutorService`, no `CompletableFuture`, no thread pools in the service layer).
>
> The design uses **one application-owned background thread only** — the Modbus poller in `DeviceService`. Javalin's internal thread pool is outside our control and has no ESP32 equivalent, but the service layer must stay single-threaded on the device-control side.
>
> All write methods (`setVoltage`, `setCurrent`, `setOutput`, `clearProtection`) are `synchronized` on the `DeviceService` instance. This ensures writes from REST handlers or WebSocket message handlers never overlap with the poll cycle or with each other. In the ESP32 C port, `synchronized` maps directly to a FreeRTOS mutex (`xSemaphoreTake` / `xSemaphoreGive`).

**Polling thread:** started by `DeviceService.start()`, called from `SerialControllerApp` after Javalin is up. The poll method is also `synchronized` on `DeviceService`. Every second, reads the following from the device and updates `ConverterState`:
- Measured: `voltageOut`, `currentOut`, `powerOut`, `voltageIn`, `temperatureCelsius`, `outputEnabled`, `protectionState`, `cvMode`
- Setpoints: `voltageSet`, `currentSet` — **must be polled** so that front-panel changes made directly on the device are picked up and reflected in the webpage and REST state.

**Public API:**
- `ConverterState getState()` — returns the `ConverterState` instance (read-only; no lock needed as all fields are `volatile`).
- `synchronized void setVoltage(double volts)` — validates and writes; blocks poller until complete.
- `synchronized void setCurrent(double amperes)` — validates and writes; blocks poller until complete.
- `synchronized void setOutput(boolean on)` — writes; blocks poller until complete.
- `synchronized void clearProtection()` — writes; blocks poller until complete.
- `void start()` — starts the polling thread.
- `void stop()` — stops the polling thread and closes the transport.

### 4. Refactor `SerialControllerApp`

File: `src/main/java/com/serial/SerialControllerApp.java`

Remove from `SerialControllerApp`:
- `private static volatile double currentSetting` — now in `ConverterState`.
- `private static final ObjectMapper objectMapper` — move to `DeviceService` or `RestService`.
- `private static final Set<WsContext> clients` — remains for now (moved in Iteration 7).
- The inline device-detection call inside `demoVoltages()` — `demoVoltages()` stays `@Deprecated` and is **not called** from `process()` for now (gate it with a `false` condition or comment out the call — do not delete the method).

Add to `SerialControllerApp`:
- Instantiate `DeviceService` with the port argument from `args[0]`.
- Call `deviceService.start()` after Javalin is initialised.
- Call `deviceService.stop()` before `javalin.stop()`.

The WebSocket broadcast thread in `SerialControllerApp` is **temporarily updated** to read `deviceService.getState()` instead of the local `currentSetting` variable, so it continues to work until Iteration 7 fully refactors it.

### 5. Build Verification

Run:
```bat
mvn clean package
```

Expected: BUILD SUCCESS with no compilation errors or warnings related to the new service classes.

## Acceptance Criteria

- `ConverterState` compiles and all fields are `volatile` with Javadoc.
- Properties files exist for XY6008, RD5020, RD6020 under `src/main/resources/devices/`.
- `DeviceService` loads limits correctly (verifiable by logging the loaded values at INFO level on startup).
- `SerialControllerApp` no longer declares `currentSetting` or uses it directly.
- `mvn clean package` produces `target/SerialController.jar` with no errors.

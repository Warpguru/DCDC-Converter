# Iteration 6

The goal of this iteration is to implement `RestService` containing all RESTful CRUD endpoints for controlling the DC/DC converter, document all endpoints with OpenAPI annotations visible in Swagger, and wire them into `SerialControllerApp` through `DeviceService` and `ConverterState`.

## Context

After Iteration 5, `DeviceService` owns the converter instance and `ConverterState` holds all state. This iteration introduces `RestService` as the HTTP handler layer so the converter can be controlled programmatically (e.g. via Postman) independently of the webpage.

All REST handlers must validate input against the device limits stored in `ConverterState` before writing to the device.

## Scope

Java service layer and REST API only. No changes to `index.html` in this iteration.

## Tasks

### 1. Add Write Methods to `DeviceService`

Before `RestService` can be implemented, `DeviceService` must expose write operations that update both the device (via `DC2DCConverter`) and `ConverterState`. Add the following public methods to `DeviceService`:

- `void setVoltage(final double volts) throws Exception`
  Validates against `ConverterState.minVoltage` / `maxVoltage`, calls `dc2dcConverter.setVoltageVerified()`, updates `ConverterState.voltageSet`.

- `void setCurrent(final double amperes) throws Exception`
  Validates against `ConverterState.minCurrent` / `maxCurrent`, calls `dc2dcConverter.setCurrentVerified()`, updates `ConverterState.currentSet`.

- `void setOutput(final boolean on) throws Exception`
  Calls `dc2dcConverter.setOutput()`, updates `ConverterState.outputEnabled`.

- `void clearProtection() throws Exception`
  Calls `dc2dcConverter.setProtectionState(false)` to reset a tripped protection state, updates `ConverterState.protectionState` to 0.

Each method logs the operation at INFO level.

### 2. Implement `RestService`

File: `src/main/java/com/serial/service/RestService.java`

Constructor: `RestService(final DeviceService deviceService)`

Implement the following Javalin handler methods, each annotated with `@OpenApi`. Use `// @formatter:off` / `// @formatter:on` around each `@OpenApi` block (project convention):

#### `GET /api/state`
Returns the full `ConverterState` as JSON.

Response `200`: `ConverterState` serialised by Jackson.

#### `GET /api/limits`
Returns only the device limit fields from `ConverterState` (deviceName, manufacturer, maxVoltage, minVoltage, maxCurrent, minCurrent, maxPower) as JSON.

Response `200`: JSON object with limit fields.

#### `POST /api/voltage`
Request body: `{"voltage": 5.0}`

Validates that `voltage` is within `[minVoltage, maxVoltage]`. On success calls `deviceService.setVoltage()`.

Response `200`: updated `ConverterState` as JSON.
Response `400`: `{"error": "Voltage out of range: <value> (min=<min>, max=<max>)"}`.

#### `POST /api/current`
Request body: `{"current": 2.0}`

Validates that `current` is within `[minCurrent, maxCurrent]`. On success calls `deviceService.setCurrent()`.

Response `200`: updated `ConverterState` as JSON.
Response `400`: `{"error": "Current out of range: <value> (min=<min>, max=<max>)"}`.

#### `POST /api/output`
Request body: `{"output": true}`

Calls `deviceService.setOutput()`.

Response `200`: updated `ConverterState` as JSON.

#### `POST /api/protection`
Request body: `{"protection": false}` — `false` clears a tripped protection; `true` has no effect on Modbus devices (protection trips are hardware-triggered).

Calls `deviceService.clearProtection()` when value is `false`.

Response `200`: updated `ConverterState` as JSON.
Response `400`: `{"error": "Only clearing protection (false) is supported"}` if value is `true`.

### 3. Register Routes in `SerialControllerApp`

Replace the existing stub `GET /init` and `GET /quit` route registrations in `SerialControllerApp` with the `RestService` routes. Register using the Javalin config routes API:

```java
RestService restService = new RestService(deviceService);
config.routes.get("/api/state",      restService::getState);
config.routes.get("/api/limits",     restService::getLimits);
config.routes.post("/api/voltage",   restService::setVoltage);
config.routes.post("/api/current",   restService::setCurrent);
config.routes.post("/api/output",    restService::setOutput);
config.routes.post("/api/protection",restService::clearProtection);
```

The existing `init()` and `quit()` methods in `SerialControllerApp` may be kept (they are harmless stubs) or removed — do not remove without confirming first.

### 4. Build and Manual Verification

Run:
```bat
mvn clean package
java -jar target/SerialController.jar <port>
```

Verify with Postman or curl:
- `GET  http://localhost:8000/api/state`   → JSON with all fields
- `GET  http://localhost:8000/api/limits`  → JSON with limit fields
- `POST http://localhost:8000/api/voltage` body `{"voltage":5.0}` → 200
- `POST http://localhost:8000/api/voltage` body `{"voltage":999}` → 400
- `GET  http://localhost:8000/swagger`     → Swagger UI showing all 6 endpoints

## Acceptance Criteria

- All 6 endpoints respond correctly to valid and invalid input.
- Swagger UI at `/swagger` shows all endpoints with request/response schemas.
- Input values outside device limits are rejected with HTTP 400 and a descriptive message.
- No Modbus calls exist anywhere outside `DeviceService` and the device driver classes.
- `mvn clean package` produces `target/SerialController.jar` with no errors.

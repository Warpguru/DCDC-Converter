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
Returns only the device limit fields from `ConverterState` (`manufacturer`, `deviceName`, `minVoltage`, `maxVoltage`, `minCurrent`, `maxCurrent`, `maxPower`) as a `LimitsResponse` JSON object.

Response `200`: JSON object with limit fields.

#### `PUT /api/voltage`
Request body: `{"voltage": 5.0}`

Validates that `voltage` is within `[minVoltage, maxVoltage]` (via `DeviceService.validateRange()`). On success calls `deviceService.setVoltage()`.

Response `204`: voltage applied successfully.
Response `400`: plain-text range error message.
Response `503`: no device connected.

> **Note:** `PUT` is used (not `POST`) because the operation is idempotent — sending the same voltage value twice has the same effect.

#### `PUT /api/current`
Request body: `{"current": 2.0}`

Response `204`: current applied successfully.
Response `400`: plain-text range error message.
Response `503`: no device connected.

#### `PUT /api/output`
Request body: `{"outputEnable": true}`

Calls `deviceService.setOutput()`.

Response `204`: output state applied successfully.
Response `503`: no device connected.

#### `POST /api/protection/clear`
No request body.

Calls `deviceService.clearProtection()`.

Response `204`: protection cleared successfully.
Response `503`: no device connected.

#### `POST /api/exit` *(extra — beyond original spec)*
No request body. Requires HTTP Basic Authentication.

Credentials read from `serial-controller.properties` next to the JAR (`exit.username`, `exit.password`). Triggers a clean application shutdown after committing the response.

Response `204`: shutdown initiated.
Response `401`: missing or invalid credentials.
Response `503`: credentials not configured.

### 3. Register Routes in `SerialControllerApp`

Routes are registered via `restService.registerRoutes(config.routes)` inside the Javalin config lambda. `SerialControllerApp` also calls `restService.setShutdown(javalin, shutdownCallback)` after Javalin starts to give the `/api/exit` handler a reference for clean shutdown. ✅ **Already implemented.**

### 4. Manual Verification

With the application running (`java -jar target/SerialController.jar <port>`), verify with Postman or curl:

| Request | Expected |
|---|---|
| `GET  /api/state` | JSON with all `ConverterState` fields |
| `GET  /api/limits` | JSON with 7 limit fields only |
| `PUT  /api/voltage` body `{"voltage":5.0}` | 204 |
| `PUT  /api/voltage` body `{"voltage":999}` | 400 + range message |
| `PUT  /api/current` body `{"current":1.0}` | 204 |
| `PUT  /api/output` body `{"outputEnable":true}` | 204 |
| `POST /api/protection/clear` | 204 |
| `GET  /swagger` | Swagger UI showing all 7 endpoints |

## Acceptance Criteria

- All 7 endpoints (`state`, `limits`, `voltage`, `current`, `output`, `protection/clear`, `exit`) respond correctly.
- Swagger UI at `/swagger` shows all endpoints with request/response schemas.
- Input values outside device limits are rejected with HTTP 400 and a descriptive message.
- No Modbus calls exist anywhere outside `DeviceService` and the device driver classes.
- `mvn clean package` produces `target/SerialController.jar` with no errors.

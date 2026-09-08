# Iteration 7

The goal of this iteration is to refactor the WebSocket handler out of `SerialControllerApp` into a dedicated `WebSocketService`, route all WebSocket-driven writes through `DeviceService`, and replace the simulated data push with real data from `ConverterState`.

## Context

After Iterations 5 and 6, `DeviceService` owns the converter and `ConverterState` is the single source of truth. However, the WebSocket handler in `SerialControllerApp` still references state directly (the local `currentSetting` volatile or the temporary patch from Iteration 5). The push thread still uses `Math.random()` simulation. This iteration completes the service-layer refactor so the WebPage and REST API share exactly the same state path.

## Scope

Java service layer and WebSocket handler refactor only. No changes to `index.html` in this iteration — the webpage already handles `voltage` and `current` JSON fields; the extended payload fields will be consumed in Iteration 8.

## Tasks

### 1. Create `WebSocketService`

File: `src/main/java/com/serial/service/WebSocketService.java`

Constructor: `WebSocketService(final DeviceService deviceService, final ObjectMapper objectMapper)`

Responsibilities:
- Maintain the set of connected WebSocket clients (`ConcurrentHashMap.newKeySet()`).
- Handle `onConnect`, `onMessage`, `onClose`, `onError` callbacks.
- Run the background push thread (1-second interval) that serialises `deviceService.getState()` to JSON and broadcasts to all clients.

**`onMessage` handling** — parse incoming JSON and act on the following keys (ignore unrecognised keys at DEBUG level, not WARN):

| Key | Action |
|---|---|
| `setCurrent` | Validate and call `deviceService.setCurrent()` |
| `setVoltage` | Validate and call `deviceService.setVoltage()` |
| `setOutput` | Call `deviceService.setOutput()` |

**Push thread** — replace the `Math.random()` simulation with:
```java
String json = objectMapper.writeValueAsString(deviceService.getState());
```
Broadcast to all clients. Handle send failures by removing the failing client from the set (existing pattern).

**Public API:**
- `void start()` — starts the push thread.
- `void stop()` — interrupts the push thread.
- `void onConnect(WsContext ctx)`, `void onMessage(WsMessageContext ctx)`, `void onClose(WsContext ctx)`, `void onError(WsErrorContext ctx)` — public handler methods called from `SerialControllerApp`.

### 2. Add `ObjectMapper` to `DeviceService`

`DeviceService` owns a single `ObjectMapper` instance (Jackson is expensive to construct — one instance per application). Expose it via `getObjectMapper()`. Both `RestService` and `WebSocketService` use this same instance.

### 3. Refactor `SerialControllerApp`

**Remove** from `SerialControllerApp`:
- The `clients` static field.
- The inline `config.routes.ws(...)` lambda body.
- The `startUpdateThread()` static method.
- The `objectMapper` static field.
- Any remaining `currentSetting` volatile field or reference.

**Add** to `SerialControllerApp`:
```java
WebSocketService webSocketService = new WebSocketService(deviceService, deviceService.getObjectMapper());
config.routes.ws("/ws/data", ws -> {
    ws.onConnect(webSocketService::onConnect);
    ws.onMessage(webSocketService::onMessage);
    ws.onClose(webSocketService::onClose);
    ws.onError(webSocketService::onError);
});
```

Call `webSocketService.start()` alongside `deviceService.start()`, and `webSocketService.stop()` alongside `deviceService.stop()`.

After this refactor, `SerialControllerApp` contains only: logger, `main()`, `process()` (Javalin wiring — all routes delegated to service classes), `sleepSeconds()`, `printPortDetails()`, `valueOrNA()`, `demoVoltages()` (deprecated, call gated off).

### 4. Build and Verification

```bat
mvn clean package
java -jar target/SerialController.jar <port>
```

Verify:
- Browser at `http://localhost:8000` — voltage and current update every second with real device values (or zeros if no device attached).
- Moving the current slider sends `{"setCurrent": x.x}` and is reflected in the next server push.
- `POST /api/current` via Postman changes `currentSet` — visible in the next WebSocket broadcast.
- `GET /api/state` returns `currentSet` matching the value set via the slider.

## Acceptance Criteria

- `SerialControllerApp` contains no `clients`, `objectMapper`, `currentSetting`, or `startUpdateThread` declarations.
- WebSocket `onMessage` routes `setCurrent`, `setVoltage`, `setOutput` through `DeviceService` — same code path as REST handlers.
- Push payload is the full `ConverterState` JSON serialised by Jackson.
- A REST write is visible in the next WebSocket broadcast (shared state confirmed).
- `mvn clean package` produces `target/SerialController.jar` with no errors.

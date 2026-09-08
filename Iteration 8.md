# Iteration 8

The goal of this iteration is to enhance `index.html` to display and control all relevant `ConverterState` fields using the full JSON payload now pushed by `WebSocketService`, while strictly following the project web interface rules: plain HTML, minimal CSS, JavaScript only where HTML alone is insufficient, no JS libraries.

## Context

After Iteration 7, `WebSocketService` broadcasts the full `ConverterState` JSON every second. The current `index.html` only consumes `voltage` and `current` fields and only controls `setCurrent`. This iteration extends the page to surface all useful converter state and adds controls for voltage setpoint and output on/off.

## Scope

`index.html` and the server-side `WebSocketService.onMessage()` handler (to accept `setVoltage` and `setOutput` messages that the new controls will send). No changes to Java service classes other than `WebSocketService.onMessage()`.

## Web Interface Rules (mandatory)

- Use plain HTML wherever possible.
- Add CSS only when layout or legibility genuinely requires it.
- Use JavaScript only where plain HTML cannot achieve the behaviour (live WebSocket updates and sending messages require JS — that is the justified exception).
- Do not use any JavaScript library (no jQuery, no Alpine, no htmx, etc.).

## Tasks

### 1. Update WebSocket `onMessage` in `WebSocketService`

Add handling for two new message keys (alongside the existing `setCurrent`):

| Key | Action |
|---|---|
| `setVoltage` | Validate and call `deviceService.setVoltage()` |
| `setOutput` | Call `deviceService.setOutput()` (value is boolean) |

These were already specified in Iteration 7 — confirm they are implemented. If not, implement them now.

### 2. Extend `index.html` — Read-Only Status Display

Add read-only display rows for the following fields received in the `ConverterState` JSON push:

| Field | Label | Unit |
|---|---|---|
| `voltageIn` | Input Voltage | V |
| `temperatureCelsius` | Temperature | °C |
| `cvMode` | Mode | CV / CC (display as text) |
| `protectionState` | Protection | OK / code description |
| `deviceName` + `manufacturer` | Device | e.g. "Sinilink XY6008" |

These are read-only — displayed as text, no user input required.

**Protection state display:** map the integer code to a human-readable string in JavaScript:
- 0 → "OK"
- 1 → "OVP" (over-voltage)
- 2 → "OCP" (over-current)
- 3 → "OPP" (over-power)
- 4 → "LVP" (low voltage)
- 5 → "OAH"
- 6 → "OHP" (timeout)
- 7 → "OTP" (over-temperature)
- Any other → "ERR " + code

### 3. Add Voltage Setpoint Control

Add a voltage setpoint widget matching the style of the existing current widget:
- Slider (`<input type="range">`) with `−` and `+` buttons.
- Step: `0.1 V`.
- `min` and `max` attributes set dynamically from `ConverterState.minVoltage` / `maxVoltage` on first message received (so limits are device-driven).
- Sends `{"setVoltage": x.xx}` over WebSocket on change.
- Disabled until WebSocket is connected (same pattern as current slider).

### 4. Add Output On/Off Toggle

Add a single toggle button labelled **Output ON** / **Output OFF**:
- Uses `<button>` element — no checkbox, no custom widget.
- On click: sends `{"setOutput": true}` or `{"setOutput": false}` over WebSocket (toggling the current `outputEnabled` state from `ConverterState`).
- Button label and visual state update on each incoming `ConverterState` push (reflecting the actual device state, not just the last click).
- Disabled until WebSocket is connected.

### 5. Dynamic Limit Wiring

On the first `ConverterState` message received:
- Set voltage slider `min` / `max` / `step` from `minVoltage` / `maxVoltage` (step remains 0.1).
- Set current slider `min` / `max` from `minCurrent` / `maxCurrent` (step remains 0.1).
- Update the device name display.

This ensures the controls are always within the limits of the detected device.

### 6. CSS Review

Review all existing CSS in `index.html`. Remove any rule that is unused after the layout changes. Add only the minimum CSS required to make the new rows and controls legible and consistent with the existing style.

### 7. Build and Verification

```bat
mvn clean package
java -jar target/SerialController.jar <port>
```

Open `http://localhost:8000`. Verify:
- All new read-only fields update every second.
- Voltage slider sends `setVoltage` — confirmed via browser DevTools WebSocket frame inspector or server log.
- Output button toggles and reflects device state on next push.
- Current slider `max` and voltage slider `max` match the device limits (check log for loaded properties values from Iteration 5).
- Page layout remains clean and readable; no JS library errors in browser console.

## Acceptance Criteria

- All `ConverterState` fields are either displayed or controllable from the webpage.
- Voltage setpoint widget and output toggle button work correctly.
- Slider `min`/`max` are device-limit-driven, not hard-coded.
- No JavaScript library is used.
- `WebSocketService.onMessage()` handles `setVoltage` and `setOutput`.
- `mvn clean package` produces `target/SerialController.jar` with no errors.

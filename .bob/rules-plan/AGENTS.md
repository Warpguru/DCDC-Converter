# Project Architecture Rules (Non-Obvious Only)

- **`ModbusTransport` is not stateless** — it opens the `SerialPort` in its constructor and holds `InputStream`/`OutputStream`; always call `transport.close()` after use or on failure.
- **`ModbusDevice.transport` is `protected` and set lazily** — it is `null` until `verifyDevicePresent()` runs; calling any read/write method before that throws NPE.
- **`SerialControllerApp` is a single-process monolith** — Javalin HTTP + WebSocket server and Modbus polling all run in the same JVM; there is no separate service layer.
- **WebSocket broadcast uses `ConcurrentHashMap.newKeySet()`** but `ConcurrentHashMap.remove()` inside a `forEach` is used for error eviction — safe but non-atomic; do not replace with a plain `HashSet`.
- **`currentSetting` is `volatile`** to share state between the WebSocket message thread and the update broadcast thread — preserve this if refactoring the server.
- **`demoVoltages()` in `SerialControllerApp` is `@Deprecated`** and hard-codes `Sinilink` — do not call it from new code; it is a leftover manual test scaffold.
- **Register packages are intentionally split**: `com.serial.device.*Registers` (register address constants) vs `com.serial.devices.*` (device drivers); keep this separation when adding new devices.
- **ESP32 / C portability constraint**: Modbus RTU framing in `ModbusTransport` must remain pure byte-array logic with no Java Collections or generics — this makes future C translation straightforward.
- **No dependency injection framework** — all wiring is done manually in constructors/method calls; do not introduce Spring, Guice, or similar.
- **`modbus/ModbusCRC.java` and `util/ModbusCRC.java` are duplicates** — only `com.serial.modbus.ModbusCRC` is active; the `util` copy is dead.

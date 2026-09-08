# Project Coding Rules (Non-Obvious Only)

- **Environment first**: run `D:\Development\SetupEnvMaven.cmd` and `D:\Development\SetupEnvJava21.cmd` in `cmd.exe` before invoking `mvn` or `java`.
- **`com.serial.util.*` is dead code** — use `com.serial.modbus.*` only; never import from the `util` package.
- **`*.java.old` / `*Old.java` / `*TestTool*.java`** at `com.serial` package root are scratch files — do not import or build on them.
- **`DC2DCConverter` interface is mandatory** — every new device driver must implement it, even if some methods are stubs.
- **`DeviceRegister` for all register access** — never hard-code raw scale math inline; always define a `DeviceRegister` constant with the scale and use `read()`/`write()`.
- **`writeVerified()` for safety-critical writes** — voltage/current setpoint changes should use `ModbusDevice.writeVerified()`, not plain `write()`, to ensure the device accepted the value.
- **Auto-baud probe order** (`ModbusTransport.BAUDS`): 115200 → 57600 → 38400 → 19200 → 9600. New device `verifyDevicePresent()` methods must iterate this list, not a hardcoded baud.
- **`@formatter:off` / `@formatter:on`** must be preserved around multi-line `@OpenApi` annotations.
- **`final` on all method parameters** is required by project convention.
- **Javadoc is mandatory** on all public/protected members; include `<pre>` code examples for non-trivial methods.
- **No test framework is wired** — there is no JUnit or test runner in `pom.xml`; verification requires a physical device.
- **Fat JAR target name** is `SerialController.jar` (set by `<finalName>` in `pom.xml`), not the default artifact name.
- **`SerialControllerApp` runs for 600 s** then self-terminates (`sleepSeconds(600)`); extend this for longer manual test sessions.

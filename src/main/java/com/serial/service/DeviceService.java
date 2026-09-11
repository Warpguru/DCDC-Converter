package com.serial.service;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;
import com.serial.AppConfiguration;
import com.serial.devices.RidenRD50xx;
import com.serial.devices.RidenRD60xx;
import com.serial.devices.Sinilink;
import com.serial.devices.ifc.DC2DCConverter;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

/**
 * Service layer owning the DC/DC converter instance, the Modbus polling thread, and the shared {@link ConverterState}.
 *
 * <p>
 * This class is the single point of access for all device interactions. It is responsible for:
 * </p>
 * <ol>
 * <li>Detecting the converter on the given serial port (tries Sinilink, RidenRD50xx, RidenRD60xx in order).</li>
 * <li>Loading device capability limits from a per-device properties file under
 * {@code src/main/resources/devices/<deviceName>.properties}.</li>
 * <li>Maintaining a {@link ConverterState} that reflects the current device state.</li>
 * <li>Running the background polling thread that reads all registers every second.</li>
 * </ol>
 *
 * <p>
 * <strong>Threading model:</strong> Only one application-owned background thread exists - the Modbus poller.
 * All write methods ({@link #setVoltage}, {@link #setCurrent}, {@link #setOutput}, {@link #clearProtection})
 * and the poll method are {@code synchronized} on this instance. This prevents concurrent serial port access
 * and maps directly to a FreeRTOS mutex in the planned ESP32 C port. No Java-specific concurrency abstractions
 * (e.g. {@code ExecutorService}, {@code CompletableFuture}) are used in the service layer.
 * </p>
 *
 * <p>
 * <strong>Modbus RTU is strictly master/slave:</strong> The device never transmits unsolicited data. Changes
 * made on the device's physical front panel are discovered only when the relevant registers are polled.
 * The polling thread therefore reads both measured values and setpoints every cycle.
 * </p>
 */
public class DeviceService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);

    /** Properties file directory on the classpath. */
    private static final String DEVICES_PATH = "/devices/";

    /** Properties key for the converter topology value. */
    private static final String PROP_TOPOLOGY = "device.topology";

    /**
     * Dropout voltage in volts subtracted from Vin to derive the effective maximum output voltage
     * for {@link ConverterTopology#BUCK} converters.
     */
    private static final double BUCK_DROPOUT_V = 1.0;

    /** Polling interval in milliseconds. */
    private static final int POLL_INTERVAL_MS = 1000;

    /**
     * Duration in milliseconds during which the poll loop will not overwrite a setpoint in
     * {@link ConverterState} after a user write.
     *
     * <p>
     * Immediately after {@link #setVoltage} or {@link #setCurrent} writes a value to the device,
     * the first one or two poll cycles may read back a slightly different value from the converter's
     * register (quantisation, ADC settling, firmware latency). If that transient value were
     * broadcast to the GUI it would cause a brief flicker. Suppressing the poll-overwrite for this
     * window keeps {@code ConverterState} stable until the device register has settled.
     * </p>
     */
    private static final long SETPOINT_SETTLE_MS = 2000L;

    /**
     * Shared Jackson {@link ObjectMapper} instance.
     *
     * <p>
     * A single instance is created here and shared with {@link WebSocketService} and
     * {@link RestService}. {@code ObjectMapper} is thread-safe after configuration and
     * expensive to construct - one instance per application is the correct pattern.
     * </p>
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** The detected converter instance. {@code null} if no device was found. */
    private DC2DCConverter converter;

    /** Shared state updated by the polling thread and read by REST / WebSocket layers. */
    private final ConverterState state = new ConverterState();

    /** Background polling thread. */
    private Thread pollThread;

    /** Set to {@code false} to signal the polling thread to stop. */
    private volatile boolean running;

    /**
     * Number of consecutive <em>non-timeout</em> poll failures that triggers a transport reconnect
     * and sets the device Offline.
     *
     * <p>
     * Serial timeouts bypass this counter and go Offline immediately because a timeout is
     * unambiguous evidence that the device stopped responding (see {@link #poll()}).
     * </p>
     */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    /**
     * Number of consecutive fully-successful poll cycles required before the device is declared
     * Online again after having been Offline.
     *
     * <p>
     * Set to 1: once a reconnect succeeds and the first full poll completes without error, the
     * device is communicating normally. There is no benefit to waiting for additional confirmations
     * because a completed poll already validates every register read in the cycle.
     * </p>
     */
    private static final int MAX_CONSECUTIVE_SUCCESSES = 1;

    /**
     * Exception message fragment thrown by {@link com.serial.modbus.ModbusTransport} when the
     * serial read times out. Used to distinguish a timeout from other poll failures.
     */
    private static final String ERR_SERIAL_TIMEOUT = "Serial timeout";

    /**
     * Number of consecutive poll failures since the last successful poll.
     *
     * <p>
     * Incremented on each non-timeout failed poll; reset to zero on a full success. When it
     * reaches {@link #MAX_CONSECUTIVE_FAILURES} a transport reconnect is attempted and
     * {@link ConverterState#setDeviceOnline(boolean)} is set to {@code false}.
     * Serial timeouts skip this counter and trigger an immediate Offline + reconnect.
     * </p>
     */
    private int consecutiveFailures;

    /**
     * {@link System#currentTimeMillis()} deadline before which the poll loop must not overwrite
     * {@link ConverterState#setVoltageSet} with the value read back from the device.
     *
     * <p>
     * Set to {@code System.currentTimeMillis() + SETPOINT_SETTLE_MS} whenever {@link #setVoltage}
     * writes a new setpoint so that transient device-register values are not broadcast to clients
     * during the settle window.
     * </p>
     */
    private volatile long voltagePendingUntil = 0L;

    /**
     * {@link System#currentTimeMillis()} deadline before which the poll loop must not overwrite
     * {@link ConverterState#setCurrentSet} with the value read back from the device.
     *
     * <p>
     * Set to {@code System.currentTimeMillis() + SETPOINT_SETTLE_MS} whenever {@link #setCurrent}
     * writes a new setpoint.
     * </p>
     */
    private volatile long currentPendingUntil = 0L;

    /**
     * Number of consecutive fully-successful poll cycles since the device went Offline.
     *
     * <p>
     * Only counted while the device is Offline. When it reaches {@link #MAX_CONSECUTIVE_SUCCESSES}
     * the device is declared Online and this counter is reset.
     * </p>
     */
    private int consecutiveSuccesses;

    /**
     * Constructs a new {@code DeviceService}, detects the converter on the given port, loads its capability
     * limits from a properties file, applies any operator-configured setpoint caps from
     * {@link AppConfiguration}, and reads the initial setpoints from the device.
     *
     * <p>
     * Device detection order: Sinilink → RidenRD50xx → RidenRD60xx. The first driver that successfully
     * identifies a device is used.
     * </p>
     *
     * <p>
     * If no device is detected, the service continues with {@code null} converter and zeroed limits.
     * The polling thread will skip device reads in that case.
     * </p>
     *
     * @param portName serial port name, e.g. {@code "COM3"} or {@code "/dev/ttyUSB0"}
     * @param appConfiguration   application configuration; used to read optional setpoint cap properties
     */
    public DeviceService(final String portName, final AppConfiguration appConfiguration) {
        detectDevice(portName);
        readInitialSetpoints();
        loadLimits();
        applyConfigLimits(appConfiguration);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the background Modbus polling thread.
     *
     * <p>
     * Must be called after the Javalin server is initialised so that WebSocket push can start as soon as
     * the first poll completes.
     * </p>
     */
    public void start() {
        running = true;
        pollThread = new Thread(this::pollLoop, "modbus-poller");
        pollThread.setDaemon(true);
        pollThread.start();
        logger.info("DeviceService polling thread started.");
    }

    /**
     * Stops the background polling thread and closes the serial transport.
     */
    public void stop() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        logger.info("DeviceService stopped.");
    }

    // -------------------------------------------------------------------------
    // State access
    // -------------------------------------------------------------------------

    /**
     * Returns the shared {@link ConverterState}.
     *
     * <p>
     * The returned instance is the live object updated by the polling thread. Callers may read any field
     * directly - all fields are {@code volatile}. No lock is needed for reads.
     * </p>
     *
     * @return the current converter state
     */
    public ConverterState getState() {
        return state;
    }

    /**
     * Returns the shared {@link ObjectMapper} instance.
     *
     * <p>
     * Used by {@link WebSocketService} and {@link RestService} to serialise
     * {@link ConverterState} to JSON. Sharing a single instance avoids the overhead of
     * constructing multiple mappers.
     * </p>
     *
     * @return the application-wide Jackson {@code ObjectMapper}
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Returns {@code true} if a converter was successfully detected on the serial port.
     *
     * @return {@code true} if a device is available
     */
    public boolean isDeviceDetected() {
        return converter != null;
    }

    // -------------------------------------------------------------------------
    // Write operations (synchronized - one at a time, no overlap with poll)
    // -------------------------------------------------------------------------

    /**
     * Sets the output voltage setpoint on the device and updates {@link ConverterState#setVoltageSet}.
     *
     * <p>
     * The value is validated against the device limits before writing. If the value is out of range, an
     * {@link IllegalArgumentException} is thrown and the device is not written.
     * </p>
     *
     * <p>
     * This method is {@code synchronized} to prevent concurrent serial port access from simultaneous REST
     * and WebSocket calls, and to prevent overlap with the polling thread. In the ESP32 C port this
     * corresponds to {@code xSemaphoreTake} on the Modbus mutex.
     * </p>
     *
     * @param volts voltage setpoint in volts (V)
     * @throws IllegalArgumentException if {@code volts} is outside {@code [minVoltage, maxVoltage]}
     * @throws Exception                if the Modbus write fails
     */
    public synchronized void setVoltage(final double volts) throws Exception {
        final double effectiveMax = effectiveMaxVoltage();
        validateRange("Voltage", volts, state.getMinVoltage(), effectiveMax);
        logger.info("Setting voltage to {} V", volts);
        converter.setVoltage(volts);
        state.setVoltageSet(volts);
        voltagePendingUntil = System.currentTimeMillis() + SETPOINT_SETTLE_MS;
    }

    /**
     * Sets the output current setpoint on the device and updates {@link ConverterState#setCurrentSet}.
     *
     * <p>
     * The value is validated against the device limits before writing.
     * </p>
     *
     * @param amperes current setpoint in amperes (A)
     * @throws IllegalArgumentException if {@code amperes} is outside {@code [minCurrent, maxCurrent]}
     * @throws Exception                if the Modbus write fails
     */
    public synchronized void setCurrent(final double amperes) throws Exception {
        validateRange("Current", amperes, state.getMinCurrent(), effectiveMaxCurrent());
        logger.info("Setting current to {} A", amperes);
        converter.setCurrent(amperes);
        state.setCurrentSet(amperes);
        currentPendingUntil = System.currentTimeMillis() + SETPOINT_SETTLE_MS;
    }

    /**
     * Enables or disables the converter output and updates {@link ConverterState#setOutputEnabled}.
     *
     * @param on {@code true} to enable the output, {@code false} to disable it
     * @throws Exception if the Modbus write fails
     */
    public synchronized void setOutput(final boolean on) throws Exception {
        logger.info("Setting output to {}", on ? "ON" : "OFF");
        converter.setOutput(on);
        state.setOutputEnabled(on);
    }

    /**
     * Sets the keypad (child lock) state and updates {@link ConverterState#setKeypadLocked}.
     *
     * @param locked {@code true} to lock the keypad, {@code false} to unlock it
     * @throws Exception if the Modbus write fails
     */
    public synchronized void setKeypad(final boolean locked) throws Exception {
        logger.info("Setting keypad lock to {}", locked ? "LOCKED" : "UNLOCKED");
        converter.setKeypad(locked);
        state.setKeypadLocked(locked);
    }

    /**
     * Clears a tripped protection state on the device and resets {@link ConverterState#setProtectionState} to 0.
     *
     * <p>
     * Writing {@code false} (0) to the protection register resets the protection condition so the device
     * can resume normal operation.
     * </p>
     *
     * @throws Exception if the Modbus write fails
     */
    public synchronized void clearProtection() throws Exception {
        logger.info("Clearing protection state.");
        converter.setProtectionState(false);
        state.setProtectionState(0);
    }

    // -------------------------------------------------------------------------
    // Private - device detection
    // -------------------------------------------------------------------------

    /**
     * Attempts to detect a supported DC/DC converter on the given serial port using an optimized
     * two-pass probing algorithm.
     *
     * <p>
     * <strong>Pass 1 (Primary / Fast):</strong> Probes primary baud rates ({@link ModbusTransport#PRIMARY_BAUDS}:
     * 115200 and 9600 baud) across Sinilink, RidenRD50xx, and RidenRD60xx. Testing these two predominant
     * rates detects &gt;99% of converters in &lt;2 seconds and terminates immediately on a match.
     * </p>
     *
     * <p>
     * <strong>Pass 2 (Secondary / Fallback):</strong> Executed only if Pass 1 found no device. Probes fallback
     * baud rates ({@link ModbusTransport#SECONDARY_BAUDS}: 19200, 38400, 57600 baud).
     * </p>
     *
     * @param portName serial port name
     */
    private void detectDevice(final String portName) {
        logger.info("Starting device detection on port {}", portName);

        // Pass 1: Primary fast probe (115200, 9600 baud)
        logger.info("Probing primary baud rates {}...", ModbusTransport.PRIMARY_BAUDS);
        if (probeDrivers(portName, ModbusTransport.PRIMARY_BAUDS)) {
            return;
        }

        // Pass 2: Secondary fallback probe (19200, 38400, 57600 baud)
        logger.info("No device detected in primary pass. Probing fallback baud rates {}...", ModbusTransport.SECONDARY_BAUDS);
        if (probeDrivers(portName, ModbusTransport.SECONDARY_BAUDS)) {
            return;
        }

        logger.warn("No supported device detected on port {}.", portName);
    }

    /**
     * Probes candidate driver types in order (Sinilink → RidenRD50xx → RidenRD60xx) for the given baud rates.
     *
     * @param portName serial port name
     * @param bauds    list of baud rates to probe
     * @return {@code true} if a device was successfully detected and assigned to {@link #converter}
     */
    private boolean probeDrivers(final String portName, final java.util.List<Integer> bauds) {
        // Try Sinilink
        Sinilink sinilink = new Sinilink(portName, ModbusConstants.SLAVE_ADDRESS_1);
        DC2DCConverter detected = sinilink.verifyDevicePresent(bauds);
        if (sinilink.isDeviceDetected()) {
            converter = detected;
            logger.info("Detected device: {} {} on port {}", sinilink.getManufacturer(), sinilink.getDevice(), portName);
            return true;
        }

        // Try Riden RD50xx
        RidenRD50xx ridenRD50xx = new RidenRD50xx(portName, ModbusConstants.SLAVE_ADDRESS_1);
        detected = ridenRD50xx.verifyDevicePresent(bauds);
        if (ridenRD50xx.isDeviceDetected()) {
            converter = detected;
            logger.info("Detected device: {} {} on port {}", ridenRD50xx.getManufacturer(), ridenRD50xx.getDevice(), portName);
            return true;
        }

        // Try Riden RD60xx
        RidenRD60xx ridenRD60xx = new RidenRD60xx(portName, ModbusConstants.SLAVE_ADDRESS_1);
        detected = ridenRD60xx.verifyDevicePresent(bauds);
        if (ridenRD60xx.isDeviceDetected()) {
            converter = detected;
            logger.info("Detected device: {} {} on port {}", ridenRD60xx.getManufacturer(), ridenRD60xx.getDevice(), portName);
            return true;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Private - limits loading
    // -------------------------------------------------------------------------

    /**
     * Loads device capability limits from the matching properties file on the classpath.
     *
     * <p>
     * The file is located at {@code /devices/<deviceName>.properties} where {@code deviceName} is the
     * string returned by the driver after detection (e.g. {@code "XY6008"}, {@code "RD5020"}).
     * </p>
     *
     * <p>
     * If the file is not found or cannot be parsed, a warning is logged and all limits remain at 0,
     * which prevents any write operations from being accepted until limits are known.
     * </p>
     */
    private void applyConfigLimits(final AppConfiguration config) {
        config.getMaxSetVoltage().ifPresent(cap -> {
            state.setConfigMaxVoltage(cap);
            logger.info("Operator voltage cap applied: max setpoint = {} V (device max = {} V)",
                        cap, state.getMaxVoltage());
        });
        config.getMaxSetCurrent().ifPresent(cap -> {
            state.setConfigMaxCurrent(cap);
            logger.info("Operator current cap applied: max setpoint = {} A (device max = {} A)",
                        cap, state.getMaxCurrent());
        });
    }

    private void loadLimits() {
        if (converter == null) {
            logger.warn("No device detected - skipping limits load. All limits remain at 0.");
            return;
        }

        // Determine device name from the driver
        String deviceName = null;
        if (converter instanceof Sinilink s) {
            deviceName = s.getDevice();
            state.setManufacturer(s.getManufacturer());
        } else if (converter instanceof RidenRD50xx r) {
            deviceName = r.getDevice();
            state.setManufacturer(r.getManufacturer());
            try {
                final int rawFw = r.getFirmwareVersion();
                state.setFirmwareVersion(rawFw == 0 ? "" : ("v" + String.format("%.1f", rawFw / 10.0)));
            } catch (Exception e) {
                logger.warn("Could not read RD50xx firmware version: {}", e.getMessage());
            }
        } else if (converter instanceof RidenRD60xx r) {
            deviceName = r.getDevice();
            state.setManufacturer(r.getManufacturer());
            try {
                final int rawFw = r.getFirmwareVersion();
                state.setFirmwareVersion(rawFw == 0 ? "" : ("v" + String.format("%.2f", rawFw / 100.0)));
            } catch (Exception e) {
                logger.warn("Could not read RD60xx firmware version: {}", e.getMessage());
            }
        }

        if (deviceName == null) {
            logger.warn("Could not determine device name - skipping limits load.");
            return;
        }

        state.setDeviceName(deviceName);
        String path = DEVICES_PATH + deviceName + ".properties";
        logger.info("Loading device limits from classpath: {}", path);

        try (InputStream in = DeviceService.class.getResourceAsStream(path)) {
            if (in == null) {
                logger.warn("No properties file found for device '{}' at {}. All limits remain 0.", deviceName, path);
                return;
            }
            Properties props = new Properties();
            props.load(in);

            state.setMaxVoltage(parseDouble(props, "device.maxVoltage", 0.0));
            state.setMinVoltage(parseDouble(props, "device.minVoltage", 0.0));
            state.setMaxCurrent(parseDouble(props, "device.maxCurrent", 0.0));
            state.setMinCurrent(parseDouble(props, "device.minCurrent", 0.0));
            state.setMaxPower(parseDouble(props, "device.maxPower", 0.0));
            state.setConverterTopology(parseTopology(props));

            logger.info("Device limits loaded: {} {} | topology={} V=[{}, {}] A=[{}, {}] P_max={}W",
                    state.getManufacturer(), state.getDeviceName(),
                    state.getConverterTopology(),
                    state.getMinVoltage(), state.getMaxVoltage(),
                    state.getMinCurrent(), state.getMaxCurrent(),
                    state.getMaxPower());

        } catch (Exception e) {
            logger.error("Failed to load device limits from {}: {}", path, e.getMessage());
        }
    }

    /**
     * Parses a {@code double} value from a {@link Properties} object, returning a default if missing or invalid.
     *
     * @param props        the properties to read from
     * @param key          the property key
     * @param defaultValue value to return if the key is absent or the value cannot be parsed
     * @return parsed double value or {@code defaultValue}
     */
    private double parseDouble(final Properties props, final String key, final double defaultValue) {
        String value = props.getProperty(key);
        if (value == null) {
            logger.warn("Property '{}' not found in device properties file - using default {}", key, defaultValue);
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Cannot parse property '{}' value '{}' as double - using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    // -------------------------------------------------------------------------
    // Private - initial setpoint read
    // -------------------------------------------------------------------------

    /**
     * Performs an initial bulk poll so that all {@link ConverterState} fields are populated
     * before the polling thread starts and before the first page load.
     *
     * <p>
     * Calls {@link com.serial.devices.ifc.DC2DCConverter#pollAll()} to populate the driver cache,
     * then reads all values — including the true voltage and current setpoints (VSET/ISET) via
     * {@link com.serial.devices.ifc.DC2DCConverter#getVoltageSet()} /
     * {@link com.serial.devices.ifc.DC2DCConverter#getCurrentSet()} — into {@link ConverterState}.
     * </p>
     *
     * <p>
     * If {@code pollAll()} throws, the exception is caught and logged at {@code WARN} level. In that
     * case all driver cache fields and all {@link ConverterState} fields remain at their
     * zero-initialised defaults ({@code 0} / {@code 0.0} / {@code false}) until the first
     * successful poll cycle executed by the {@code modbus-poller} thread.
     * </p>
     */
    private void readInitialSetpoints() {
        if (converter == null) {
            return;
        }
        try {
            converter.pollAll();
            state.setVoltageOut(converter.getVoltage());
            state.setCurrentOut(converter.getCurrent());
            state.setPowerOut(converter.getPower());
            state.setVoltageIn(converter.getInputVoltage());
            state.setTemperatureCelsius(converter.getTemperatureCelsius());
            state.setOutputEnabled(converter.getOutput());
            state.setKeypadLocked(converter.getKeypad());
            state.setProtectionState(converter.getProtectionState() ? 1 : 0);
            state.setCvMode(converter.isCvMode());
            state.setVoltageSet(converter.getVoltageSet());
            state.setCurrentSet(converter.getCurrentSet());
            logger.info("Initial state read: vOut={}V iOut={}A vSet={}V iSet={}A output={} keypad={}",
                    state.getVoltageOut(), state.getCurrentOut(),
                    state.getVoltageSet(), state.getCurrentSet(),
                    state.isOutputEnabled(), state.isKeypadLocked());
        } catch (Exception e) {
            logger.warn("Could not read initial state: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private - polling loop
    // -------------------------------------------------------------------------

    /**
     * Main body of the background polling thread.
     *
     * <p>
     * Runs until {@link #running} is set to {@code false} or the thread is interrupted. On each cycle,
     * calls {@link #poll()} under the instance lock, then sleeps for {@link #POLL_INTERVAL_MS}.
     * </p>
     */
    private void pollLoop() {
        while (running) {
            try {
                poll();
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        logger.info("Polling thread exiting.");
    }

    /**
     * Reads all relevant device registers and updates {@link ConverterState}.
     *
     * <p>
     * This method is {@code synchronized} to prevent concurrent serial port access from write operations
     * issued by REST handlers or WebSocket message handlers.
     * </p>
     *
     * <p>
     * A single {@link com.serial.devices.ifc.DC2DCConverter#pollAll()} call fetches the entire
     * register block in one Modbus frame, populating the driver's internal cache. All subsequent
     * getter calls in this method return the freshly cached values without additional serial I/O.
     * Setpoints (VSET/ISET) are included in the same bulk read, so front-panel changes are also
     * detected every cycle.
     * </p>
     *
     * <p>
     * If the device is not detected or a read fails, the error is logged and the poll cycle is skipped
     * without crashing the thread.
     * </p>
     */
    private synchronized void poll() {
        if (converter == null) {
            return;
        }
        try {
            // One bulk read populates the entire driver cache.
            converter.pollAll();

            state.setVoltageOut(converter.getVoltage());
            state.setCurrentOut(converter.getCurrent());
            state.setPowerOut(converter.getPower());
            state.setVoltageIn(converter.getInputVoltage());
            state.setTemperatureCelsius(converter.getTemperatureCelsius());
            state.setOutputEnabled(converter.getOutput());
            state.setKeypadLocked(converter.getKeypad());
            state.setProtectionState(converter.getProtectionState() ? 1 : 0);
            state.setCvMode(converter.isCvMode());
            // Only update setpoints from the device when outside the post-write settle window.
            // This prevents a transient register value from overwriting the just-written setpoint
            // and causing a brief flicker in the GUI.
            final long now = System.currentTimeMillis();
            if (now >= voltagePendingUntil) {
                state.setVoltageSet(converter.getVoltageSet());
            }
            if (now >= currentPendingUntil) {
                state.setCurrentSet(converter.getCurrentSet());
            }

            // Poll succeeded - update online tracking.
            consecutiveFailures = 0;
            if (!state.isDeviceOnline()) {
                // Device is currently Offline: one clean poll is enough to declare Online.
                consecutiveSuccesses++;
                if (consecutiveSuccesses >= MAX_CONSECUTIVE_SUCCESSES) {
                    consecutiveSuccesses = 0;
                    logger.info("Device communication restored - marking Online.");
                    state.setDeviceOnline(true);
                }
            } else {
                // Device is Online: a success is expected; just reset the success counter.
                consecutiveSuccesses = 0;
            }

        } catch (Exception e) {
            consecutiveSuccesses = 0;
            final boolean isTimeout = e.getMessage() != null && e.getMessage().contains(ERR_SERIAL_TIMEOUT);
            if (isTimeout) {
                // A serial timeout means the device stopped responding - go Offline immediately.
                logger.warn("Serial timeout - marking Offline and attempting reconnect.");
                consecutiveFailures = 0;
                state.setDeviceOnline(false);
                attemptReconnect();
            } else {
                consecutiveFailures++;
                logger.warn("Poll cycle failed ({}/{}): {}", consecutiveFailures, MAX_CONSECUTIVE_FAILURES, e.getMessage());
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    state.setDeviceOnline(false);
                    attemptReconnect();
                    consecutiveFailures = 0;
                }
            }
        }
    }

    /**
     * Attempts to close and reopen the serial transport after repeated poll failures.
     *
     * <p>
     * Modbus RTU defines no session-layer reconnect mechanism. After a USB-serial adapter is
     * physically disconnected, jSerialComm's {@link SerialPort} object becomes invalid and the
     * only correct recovery is to discard it and open a fresh one.  This method delegates to
     * {@link DC2DCConverter#reconnect()} which in turn calls {@link com.serial.modbus.ModbusTransport#reconnect()}.
     * </p>
     *
     * <p>
     * If the reconnect itself fails the error is logged and the next poll cycle will try again.
     * </p>
     */
    private void attemptReconnect() {
        logger.warn("Attempting serial port reconnect.");
        try {
            converter.reconnect();
            logger.info("Serial port reconnect succeeded.");
        } catch (Exception ex) {
            logger.warn("Serial port reconnect failed: {}", ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private - validation
    // -------------------------------------------------------------------------

    /**
     * Returns the effective maximum voltage setpoint for the current device and input voltage.
     *
     * <p>
     * For {@link ConverterTopology#BUCK} converters the ceiling is
     * {@code min(maxVoltage, voltageIn − BUCK_DROPOUT_V)}, because the device silently ignores
     * setpoints above that value. For all other topologies the static {@code maxVoltage} limit
     * is returned unchanged.
     * </p>
     *
     * @return effective maximum voltage in volts
     */
    private double effectiveMaxVoltage() {
        final double base;
        if (state.getConverterTopology() == ConverterTopology.BUCK) {
            final double buckCeiling = state.getVoltageIn() - BUCK_DROPOUT_V;
            base = Math.min(state.getMaxVoltage(), buckCeiling);
        } else {
            base = state.getMaxVoltage();
        }
        return (state.getConfigMaxVoltage() > 0)
                ? Math.min(base, state.getConfigMaxVoltage())
                : base;
    }

    /**
     * Returns the effective maximum current setpoint, taking the operator cap into account.
     *
     * <p>
     * When {@code serialcontroller.max.setcurrent} is configured and is lower than the device's
     * physical {@code maxCurrent}, the operator cap governs.  Otherwise the device limit is used.
     * </p>
     *
     * @return effective maximum current in amperes
     */
    private double effectiveMaxCurrent() {
        return (state.getConfigMaxCurrent() > 0)
                ? Math.min(state.getMaxCurrent(), state.getConfigMaxCurrent())
                : state.getMaxCurrent();
    }

    /**
     * Parses the {@code device.topology} property into a {@link ConverterTopology} enum constant.
     *
     * <p>
     * If the property is absent or its value does not match any constant name (case-insensitive),
     * a warning is logged and {@link ConverterTopology#BUCK_BOOST} is returned as the safe default
     * (no restriction).
     * </p>
     *
     * @param props the loaded device properties
     * @return the topology, never {@code null}
     */
    private ConverterTopology parseTopology(final Properties props) {
        final String raw = props.getProperty(PROP_TOPOLOGY);
        if (raw == null) {
            logger.warn("Property '{}' not found - defaulting to {}", PROP_TOPOLOGY, ConverterTopology.BUCK_BOOST);
            return ConverterTopology.BUCK_BOOST;
        }
        try {
            return ConverterTopology.valueOf(raw.trim().toUpperCase().replace('/', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown topology value '{}' - defaulting to {}", raw, ConverterTopology.BUCK_BOOST);
            return ConverterTopology.BUCK_BOOST;
        }
    }

    /**
     * Validates that a value is within the given range (inclusive).
     *
     * @param name  human-readable name of the value (used in error message)
     * @param value the value to validate
     * @param min   minimum allowed value (inclusive)
     * @param max   maximum allowed value (inclusive)
     * @throws IllegalArgumentException if {@code value} is outside {@code [min, max]}
     */
    private void validateRange(final String name, final double value, final double min, final double max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    String.format("%s out of range: %.3f (min=%.3f, max=%.3f)", name, value, min, max));
        }
    }
    
}

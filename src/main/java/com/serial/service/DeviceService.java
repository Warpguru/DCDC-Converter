package com.serial.service;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serial.devices.RidenRD50xx;
import com.serial.devices.RidenRD60xx;
import com.serial.devices.Sinilink;
import com.serial.devices.ifc.DC2DCConverter;
import com.serial.modbus.ModbusConstants;

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
 * <strong>Threading model:</strong> Only one application-owned background thread exists — the Modbus poller.
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

    /** Polling interval in milliseconds. */
    private static final int POLL_INTERVAL_MS = 1000;

    /** The detected converter instance. {@code null} if no device was found. */
    private DC2DCConverter converter;

    /** Shared state updated by the polling thread and read by REST / WebSocket layers. */
    private final ConverterState state = new ConverterState();

    /** Background polling thread. */
    private Thread pollThread;

    /** Set to {@code false} to signal the polling thread to stop. */
    private volatile boolean running;

    /**
     * Constructs a new {@code DeviceService}, detects the converter on the given port, loads its capability
     * limits from a properties file, and reads the initial setpoints from the device.
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
     */
    public DeviceService(final String portName) {
        detectDevice(portName);
        loadLimits();
        readInitialSetpoints();
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
     * directly — all fields are {@code volatile}. No lock is needed for reads.
     * </p>
     *
     * @return the current converter state
     */
    public ConverterState getState() {
        return state;
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
    // Write operations (synchronized — one at a time, no overlap with poll)
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
        validateRange("Voltage", volts, state.getMinVoltage(), state.getMaxVoltage());
        logger.info("Setting voltage to {} V", volts);
        converter.setVoltageVerified(volts);
        state.setVoltageSet(volts);
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
        validateRange("Current", amperes, state.getMinCurrent(), state.getMaxCurrent());
        logger.info("Setting current to {} A", amperes);
        converter.setCurrentVerified(amperes);
        state.setCurrentSet(amperes);
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
    // Private — device detection
    // -------------------------------------------------------------------------

    /**
     * Attempts to detect a supported DC/DC converter on the given serial port.
     *
     * <p>
     * Tries each driver class in order: Sinilink, RidenRD50xx, RidenRD60xx. Stops at the first
     * successful detection. If none is found, {@link #converter} remains {@code null}.
     * </p>
     *
     * @param portName serial port name
     */
    private void detectDevice(final String portName) {
        logger.info("Starting device detection on port {}", portName);

        // Try Sinilink
        Sinilink sinilink = new Sinilink(portName, ModbusConstants.SLAVE_ADDRESS_1);
        DC2DCConverter detected = sinilink.verifyDevicePresent();
        if (sinilink.isDeviceDetected()) {
            converter = detected;
            logger.info("Detected device: {} {} on port {}", sinilink.getManufacturer(), sinilink.getDevice(), portName);
            return;
        }

        // Try Riden RD50xx
        RidenRD50xx ridenRD50xx = new RidenRD50xx(portName, ModbusConstants.SLAVE_ADDRESS_1);
        detected = ridenRD50xx.verifyDevicePresent();
        if (ridenRD50xx.isDeviceDetected()) {
            converter = detected;
            logger.info("Detected device: {} {} on port {}", ridenRD50xx.getManufacturer(), ridenRD50xx.getDevice(), portName);
            return;
        }

        // Try Riden RD60xx
        RidenRD60xx ridenRD60xx = new RidenRD60xx(portName, ModbusConstants.SLAVE_ADDRESS_1);
        detected = ridenRD60xx.verifyDevicePresent();
        if (ridenRD60xx.isDeviceDetected()) {
            converter = detected;
            logger.info("Detected device: {} {} on port {}", ridenRD60xx.getManufacturer(), ridenRD60xx.getDevice(), portName);
            return;
        }

        logger.warn("No supported device detected on port {}.", portName);
    }

    // -------------------------------------------------------------------------
    // Private — limits loading
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
    private void loadLimits() {
        if (converter == null) {
            logger.warn("No device detected — skipping limits load. All limits remain at 0.");
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
        } else if (converter instanceof RidenRD60xx r) {
            deviceName = r.getDevice();
            state.setManufacturer(r.getManufacturer());
        }

        if (deviceName == null) {
            logger.warn("Could not determine device name — skipping limits load.");
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

            logger.info("Device limits loaded: {} {} | V=[{}, {}] A=[{}, {}] P_max={}W",
                    state.getManufacturer(), state.getDeviceName(),
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
            logger.warn("Property '{}' not found in device properties file — using default {}", key, defaultValue);
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Cannot parse property '{}' value '{}' as double — using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    // -------------------------------------------------------------------------
    // Private — initial setpoint read
    // -------------------------------------------------------------------------

    /**
     * Reads the current voltage and current setpoints from the device registers and stores them in
     * {@link ConverterState}.
     *
     * <p>
     * This is called once on construction so that the initial state is accurate before the first poll cycle,
     * avoiding a misleading "0.0 V / 0.0 A" display on first page load.
     * </p>
     */
    private void readInitialSetpoints() {
        if (converter == null) {
            return;
        }
        try {
            state.setVoltageSet(converter.getVoltage());
            state.setCurrentSet(converter.getCurrent());
            logger.info("Initial setpoints read: vSet={}V iSet={}A", state.getVoltageSet(), state.getCurrentSet());
        } catch (Exception e) {
            logger.warn("Could not read initial setpoints: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private — polling loop
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
     * Both measured values and setpoints are read on every cycle. Reading setpoints ensures that changes
     * made on the device's physical front panel (buttons/wheel) are picked up automatically and reflected
     * in the state visible to the webpage and REST API.
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
            // Measured values
            state.setVoltageOut(converter.getVoltage());
            state.setCurrentOut(converter.getCurrent());
            state.setPowerOut(converter.getPower());
            state.setVoltageIn(converter.getInputVoltage());
            state.setTemperatureCelsius(converter.getTemperatureCelsius());
            state.setOutputEnabled(converter.getOutput());
            state.setProtectionState(converter.getProtectionState() ? 1 : 0);

            // Setpoints — polled to detect front-panel changes
            // Note: getVoltage() reads VOUT (measured); we need VSET.
            // DC2DCConverter does not expose getVoltageSet() — read it via the cast.
            readSetpoints();

        } catch (Exception e) {
            logger.warn("Poll cycle failed: {}", e.getMessage());
        }
    }

    /**
     * Reads the voltage and current setpoints from the device registers.
     *
     * <p>
     * The {@link DC2DCConverter} interface exposes only measured output values ({@code getVoltage()} reads
     * VOUT). Reading setpoints requires driver-specific register access, handled here per driver type.
     * </p>
     *
     * @throws Exception if the Modbus read fails
     */
    private void readSetpoints() throws Exception {
        if (converter instanceof Sinilink s) {
            state.setVoltageSet(s.read(Sinilink.VSET));
            state.setCurrentSet(s.read(Sinilink.ISET));
        } else if (converter instanceof RidenRD50xx r) {
            state.setVoltageSet(r.read(RidenRD50xx.VSET));
            state.setCurrentSet(r.read(RidenRD50xx.ISET));
        } else if (converter instanceof RidenRD60xx r) {
            state.setVoltageSet(r.read(RidenRD60xx.VSET));
            state.setCurrentSet(r.read(RidenRD60xx.ISET));
        }
    }

    // -------------------------------------------------------------------------
    // Private — validation
    // -------------------------------------------------------------------------

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

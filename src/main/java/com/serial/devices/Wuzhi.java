package com.serial.devices;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serial.device.WuzhiRegisters;
import com.serial.device.base.DeviceRegister;
import com.serial.device.base.ModbusDevice;
import com.serial.devices.ifc.DC2DCConverter;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

/**
 * Driver for {@code Wuzhi ZK-series} programmable DC buck power supplies (e.g. {@code ZK-6522C}).
 *
 * <p>
 * The Wuzhi ZK-series uses a {@code Modbus RTU} register layout that is address-for-address identical to the {@link Sinilink}
 * XY-series. The single critical difference is the current scale: ISET and IOUT use {@code scale 100} (10 mA resolution, 2
 * decimal places) rather than the Sinilink XY6008-class {@code scale 1000} (1 mA, 3 decimal places). Using the wrong scale
 * produces a 10× current error. See {@code doc/Wuzhi.md} for the full register map and compatibility analysis.
 * </p>
 *
 * <p>
 * Device identification probes register {@code 0x0016} (product model number), applying the same three-step strategy as
 * {@link Sinilink}: exact match in {@link #KNOWN_MODELS} → packed {@code 0x59xx} fallback in {@link #REPORTED_MODELS} → unknown
 * high-byte warn-and-skip. The exact model-register value for the ZK-6522C has not yet been confirmed on live hardware;
 * {@link #KNOWN_MODELS} is an empty placeholder. The first WARN log from the {@code 0x59} gate will supply the raw value needed
 * to populate the map - see {@code doc/Wuzhi.md}, Outstanding Unknowns.
 * </p>
 *
 * <p>
 * Wire connections - 4-pin XH2.54-4P TTL 3.3 V serial header:
 * </p>
 * <ul>
 * <li>Pin 1 - GND → adapter GND</li>
 * <li>Pin 2 - RxD → adapter TxD (device receives)</li>
 * <li>Pin 3 - TxD → adapter RxD (device transmits)</li>
 * <li>Pin 4 - VCC → <strong>NC - do not connect</strong></li>
 * </ul>
 *
 * <p>
 * <strong>Important:</strong> for digital Modbus communication the RX/GND jumper cap <em>must be removed</em> and the board
 * power-cycled before the device will respond to Modbus frames. With the jumper in place the device operates in analog
 * potentiometer mode and ignores all serial traffic.
 * </p>
 *
 * <p>
 * <strong>Modbus vs. physical keypad - mutually exclusive:</strong> connecting the device via Modbus TTL disables the
 * physical keypad and display for the duration of the session. This is a hardware-level design of the ZK-series firmware;
 * it is not a register setting and cannot be changed in software. To use the physical keypad, disconnect the serial
 * adapter and power-cycle the device.
 * </p>
 */
public class Wuzhi extends ModbusDevice implements DC2DCConverter {

    private static final Logger logger = LoggerFactory.getLogger(Wuzhi.class);

    /** Voltage setpoint. Scaling: raw / 100 = V. */
    public static final DeviceRegister VSET = new DeviceRegister("Voltage Setpoint", "V", WuzhiRegisters.REG_VSET, 100);

    /**
     * Current setpoint.
     *
     * <p>
     * Scaling: raw / 100 = A (10 mA resolution). Scale is {@code 100}, not {@code 1000} as on the Sinilink XY6008/XY6014 class.
     * Using scale 1000 here would produce a 10× current error.
     * </p>
     */
    public static final DeviceRegister ISET = new DeviceRegister("Current Setpoint", "A", WuzhiRegisters.REG_ISET, 100);

    /** Regulation mode indicator. 0 = CV, 1 = CC. */
    public static final DeviceRegister MODE = new DeviceRegister("Regulation Mode", null, WuzhiRegisters.REG_MODE);

    /** Measured output voltage. Scaling: raw / 100 = V. */
    public static final DeviceRegister VOUT = new DeviceRegister("Output Voltage", "V", WuzhiRegisters.REG_VOUT, 100);

    /**
     * Measured output current.
     *
     * <p>
     * Scaling: raw / 100 = A (10 mA resolution). Same scale as {@link #ISET}.
     * </p>
     */
    public static final DeviceRegister IOUT = new DeviceRegister("Output Current", "A", WuzhiRegisters.REG_IOUT, 100);

    /** Measured output power. Scaling: raw / 100 = W. */
    public static final DeviceRegister POUT = new DeviceRegister("Output Power", "W", WuzhiRegisters.REG_POUT, 100);

    /** Firmware version register. Scaling: raw / 100 = version (e.g. 110 = v1.10). */
    public static final DeviceRegister FIRMWARE_VERSION = new DeviceRegister("Firmware Version", null,
            WuzhiRegisters.REG_FIRMWARE, 100);

    /** Product model number register. Used for device identification at {@code 0x0016}. */
    public static final DeviceRegister MODEL_VERSION = new DeviceRegister("Model Version", null, WuzhiRegisters.REG_MODEL);

    /** Input voltage measurement. Scaling: raw / 100 = V. */
    public static final DeviceRegister VIN = new DeviceRegister("Voltage Input", "V", WuzhiRegisters.REG_VIN, 100);

    /** Output enable control. 0 = OFF, 1 = ON. */
    public static final DeviceRegister OUTPUT_ENABLE = new DeviceRegister("Output Enable", null,
            WuzhiRegisters.REG_OUTPUT_ENABLE);

    /** Device protection state. 0 = normal; write 0 to clear. See {@link WuzhiRegisters#REG_PROTECTION_STATE}. */
    public static final DeviceRegister PROTECTION_STATE = new DeviceRegister("Protection Status", null,
            WuzhiRegisters.REG_PROTECTION_STATE);

    /** Internal temperature. Scaling: raw / 10 = °C. */
    public static final DeviceRegister TEMP_CELSIUS = new DeviceRegister("Internal Temperature Celsius", "°C",
            WuzhiRegisters.REG_TEMPERATURE_INTERNAL, 10);

    /** Keypad lock. 0 = unlocked, 1 = locked. */
    public static final DeviceRegister LOCK = new DeviceRegister("Keypad Lock", null, WuzhiRegisters.REG_KEYPAD_LOCK);

    // -------------------------------------------------------------------------
    // Poll cache - populated by pollAll(), returned by all getters
    // Block: 0x0000–0x0012 (19 registers), same layout as Sinilink
    // -------------------------------------------------------------------------

    // @formatter:off
    private volatile double cacheVoltageSet;        // offset  0 VSET      ÷100
    private volatile double cacheCurrentSet;        // offset  1 ISET      ÷100
    private volatile double cacheVoltageOut;        // offset  2 VOUT      ÷100
    private volatile double cacheCurrentOut;        // offset  3 IOUT      ÷100
    private volatile double cachePowerOut;          // offset  4 POUT      ÷100
    private volatile double cacheVoltageIn;         // offset  5 VIN       ÷100
    @SuppressWarnings("unused")
    private volatile int    cacheAhLow;             // offset  6 AH_LOW    raw
    @SuppressWarnings("unused")
    private volatile int    cacheAhHigh;            // offset  7 AH_HIGH   raw
    @SuppressWarnings("unused")
    private volatile int    cacheWhLow;             // offset  8 WH_LOW    raw
    @SuppressWarnings("unused")
    private volatile int    cacheWhHigh;            // offset  9 WH_HIGH   raw
    @SuppressWarnings("unused")
    private volatile int    cacheOutHours;          // offset 10 OUT_HOURS raw
    @SuppressWarnings("unused")
    private volatile int    cacheOutMinutes;        // offset 11 OUT_MIN   raw
    @SuppressWarnings("unused")
    private volatile int    cacheOutSeconds;        // offset 12 OUT_SEC   raw
    private volatile double cacheTemperature;       // offset 13 TEMP      ÷10
    @SuppressWarnings("unused")
    private volatile double cacheTemperatureExt;    // offset 14 TEMP_EXT  ÷10
    private volatile int    cacheLock;              // offset 15 LOCK      raw
    private volatile int    cacheProtection;        // offset 16 PROTECTION raw
    private volatile int    cacheMode;              // offset 17 MODE      raw
    private volatile int    cacheOutput;            // offset 18 OUTPUT    raw
    // @formatter:on

    /**
     * High byte of the 16-bit product model register ({@code 0x0016}) that carries the Wuzhi/Sinilink "XY" series signature on
     * modern firmware.
     *
     * <p>
     * The value {@code 0x59} is the ASCII character {@code 'Y'} - the 'Y' from the "XY" product-line prefix. Any value whose
     * high byte equals {@code 0x59} is treated as a modern packed-firmware device; the low byte encodes the board hardware
     * revision. See {@code doc/Wuzhi.md} for details.
     * </p>
     */
    private static final int WUZHI_MODEL_HIGH_BYTE = 0x59;

    /**
     * Authoritative lookup map: exact product model register values confirmed for Wuzhi ZK-series devices.
     *
     * <p>
     * <strong>Currently empty - live hardware confirmation required.</strong> The ZK-6522C model-register value at
     * {@code 0x0016} has not yet been read from a physical device. Once confirmed, add an entry here in the form:
     * </p>
     *
     * <pre>
     * Map.entry(XXXXX, "ZK6522C")
     * </pre>
     *
     * <p>
     * The string value (e.g. {@code "ZK6522C"}) must exactly match the {@code device.name} key in the corresponding
     * {@code .properties} file (e.g. {@code src/main/resources/devices/ZK6522C.properties}).
     * </p>
     *
     * <p>
     * When a Wuzhi device is connected and the WARN log fires for an unknown {@code 0x59xx} value, the raw decimal and hex
     * values logged are the values to add here. See {@code doc/Wuzhi.md}, Outstanding Unknowns.
     * </p>
     */
    // @formatter:off
    private static final Map<Integer, String> KNOWN_MODELS = Map.ofEntries(
            Map.entry( 6522, "ZK-6522C"),   // legacy flat integer
            Map.entry(10022, "ZK-10022C"),  // unverified
            Map.entry(  150, "ZK-SK150C"),  // unverified
            Map.entry( 3605, "WZ3605E"),    // unverified
            Map.entry( 5005, "WZ5005E"),    // unverified
            Map.entry( 6008, "WZ-6008")     // unverified
    );
    // @formatter:on

    /**
     * Community-reported lookup map: packed {@code 0x59xx} product model register values observed on Wuzhi hardware.
     *
     * <p>
     * <strong>NOTE - community data, not factory-confirmed.</strong> Consulted only after {@link #KNOWN_MODELS} yields no match
     * and the high byte equals {@link #WUZHI_MODEL_HIGH_BYTE} ({@code 0x59}). Entries should be promoted to
     * {@link #KNOWN_MODELS} once independently confirmed on live hardware.
     * </p>
     */
    // @formatter:off
    private static final Map<Integer, String> REPORTED_MODELS = Map.of(
            // TODO: populate with community-reported ZK-series model register values as they are discovered.
    );
    // @formatter:on

    /**
     * Constructor.
     *
     * @param portName of {@code SerialPort} used with Modbus protocol
     * @param slave    Modbus slave address to use
     */
    public Wuzhi(final String portName, final byte slave) {
        super(portName, slave);
    }

    /**
     * Verify that a {@code Wuzhi ZK-series} device is present using all standard baud rates in {@link ModbusTransport#BAUDS}.
     *
     * @return this {@link Wuzhi} instance
     */
    public DC2DCConverter verifyDevicePresent() {
        return verifyDevicePresent(ModbusTransport.BAUDS);
    }

    /**
     * Verify that a {@code Wuzhi ZK-series} device is present probing only the specified baud rates.
     *
     * <p>
     * Reads the product model register ({@code 0x0016}) at each baud rate and applies a three-step identification strategy:
     * </p>
     *
     * <ol>
     * <li><strong>KNOWN_MODELS exact match</strong> - factory-confirmed values. No warning is emitted.</li>
     * <li><strong>REPORTED_MODELS fallback</strong> - only reached when the high byte equals {@code 0x59} ('Y'), indicating a
     * modern packed firmware encoding. A {@code WARN} log is emitted; the entry should be promoted to {@link #KNOWN_MODELS}
     * once verified on live hardware.</li>
     * <li><strong>Unknown packed value</strong> - high byte is {@code 0x59} but the word is in neither map. A {@code WARN} is
     * logged (with the raw decimal and hex value) and detection is skipped; the device is not identified rather than
     * guessed.</li>
     * </ol>
     *
     * <p>
     * If detection succeeds the firmware version register ({@code 0x0017}) is also read and logged.
     * </p>
     *
     * @param bauds list of baud rates to probe in order
     * @return this {@link Wuzhi} instance
     */
    public DC2DCConverter verifyDevicePresent(final List<Integer> bauds) {
        logger.info("Checking for Wuzhi ZK-series device...");
        for (final Integer baud : bauds) {
            try {
                transport = new ModbusTransport(portName, baud);
                logger.debug("Trying baud rate {}", baud);

                // Probe product model register (0x0016) for Wuzhi ZK-series identity.
                // Step 1: exact match against KNOWN_MODELS (factory-confirmed values).
                // Step 2: if high byte = 0x59 ('Y'), consult REPORTED_MODELS (community data).
                // Step 3: if high byte = 0x59 but still no match, log a warning - do not guess.
                try {
                    final int modelVersion = getModelVersion();
                    final String hex = Integer.toHexString(modelVersion).toUpperCase();
                    logger.info("Product model register (0x0016) raw value at {} baud: {} (0x{})", baud, modelVersion, hex);

                    String modelName = KNOWN_MODELS.get(modelVersion);

                    if (modelName == null && (modelVersion >> 8) == WUZHI_MODEL_HIGH_BYTE) {
                        modelName = REPORTED_MODELS.get(modelVersion);
                        if (modelName != null) {
                            logger.warn(
                                    "Product model register 0x{} matched community-reported Wuzhi data as {} "
                                            + "-- not factory-confirmed; promote to KNOWN_MODELS once verified on hardware.",
                                    hex, modelName);
                        } else {
                            logger.warn("Product model register 0x{} has Wuzhi/Sinilink 'Y' high byte "
                                    + "but is not in KNOWN_MODELS or REPORTED_MODELS -- device not identified. "
                                    + "Add Map.entry({}, \"ZK?????\") to Wuzhi.KNOWN_MODELS once the model is confirmed.", hex,
                                    modelVersion);
                        }
                    }

                    if (modelName != null) {
                        this.manufacturer = "Wuzhi";
                        this.device = modelName;
                        int fw = 0;
                        try {
                            fw = getFirmwareVersion();
                        } catch (Exception ignored) {
                        }
                        logger.info("Detected Wuzhi ZK-series {} (product model: 0x{}, FW: {}) at {} baud.", modelName, hex, fw,
                                baud);
                    }
                } catch (Exception e) {
                    logger.debug("Product model register read failed at {} baud: {}", baud, e.getMessage());
                }

                if (!isDeviceDetected()) {
                    transport.close();
                } else {
                    break;
                }
            } catch (Exception e) {
                logger.debug("Transport error at {} baud: {}", baud, e.getMessage());
                if (transport != null) {
                    transport.close();
                }
            }
        }
        if (!isDeviceDetected()) {
            logger.info("No Wuzhi ZK-series device detected.");
        }
        return this;
    }

    /**
     * Reads the full register block ({@code 0x0000–0x0012}, 19 registers) in a single Modbus {@code 0x03} frame and populates
     * all poll-cache fields.
     *
     * <p>
     * Offset map (address − {@link WuzhiRegisters#REG_VSET}):
     * </p>
     *
     * <pre>
     *  [0]  VSET   [1]  ISET    [2]  VOUT     [3]  IOUT    [4]  POUT   [5]  VIN
     *  [6]  AH_LOW [7]  AH_HIGH [8]  WH_LOW   [9]  WH_HIGH [10] OUT_H  [11] OUT_M
     * [12]  OUT_S  [13] TEMP    [14] TEMP_EXT [15] LOCK   [16] PROTECT [17] MODE
     * [18]  OUTPUT
     * </pre>
     *
     * <p>
     * Scaling is delegated to {@link DeviceRegister#decode(int)} on each constant, so no scale factors are hardcoded here.
     * </p>
     *
     * @throws Exception if the Modbus read fails
     */
    @Override
    public void pollAll() throws Exception {
        final int[] r = readBlock(WuzhiRegisters.REG_VSET, 19);
        cacheVoltageSet = VSET.decode(r[0]);
        cacheCurrentSet = ISET.decode(r[1]);
        cacheVoltageOut = VOUT.decode(r[2]);
        cacheCurrentOut = IOUT.decode(r[3]);
        cachePowerOut = POUT.decode(r[4]);
        cacheVoltageIn = VIN.decode(r[5]);
        cacheAhLow = r[6];
        cacheAhHigh = r[7];
        cacheWhLow = r[8];
        cacheWhHigh = r[9];
        cacheOutHours = r[10];
        cacheOutMinutes = r[11];
        cacheOutSeconds = r[12];
        cacheTemperature = TEMP_CELSIUS.decode(r[13]);
        cacheTemperatureExt = TEMP_CELSIUS.decode(r[14]);
        cacheLock = r[15];
        cacheProtection = r[16];
        cacheMode = r[17];
        cacheOutput = r[18];
    }

    @Override
    public double getVoltageSet() throws Exception {
        return cacheVoltageSet;
    }

    @Override
    public double getVoltageSetVerified() throws Exception {
        return read(VSET);
    }

    @Override
    public double getCurrentSet() throws Exception {
        return cacheCurrentSet;
    }

    @Override
    public double getCurrentSetVerified() throws Exception {
        return read(ISET);
    }

    /**
     * @deprecated Not used by {@link com.serial.service.DeviceService}. The verified write path uses
     *             {@link #getVoltageSetVerified()} for read-back instead of this slower
     *             {@link com.serial.device.base.ModbusDevice#writeVerified} loop (3 × 200 ms).
     */
    @Deprecated
    @Override
    public void setVoltageVerified(final double volts) throws Exception {
        writeVerified(VSET, VOUT, volts);
    }

    @Override
    public void setVoltage(final double volts) throws Exception {
        write(VSET, volts);
    }

    @Override
    public void setVoltageCurrent(final double volts, final double amperes) throws Exception {
        writeBlock(WuzhiRegisters.REG_VSET, new int[] { VSET.encode(volts), ISET.encode(amperes) });
    }

    @Override
    public double getVoltage() throws Exception {
        return cacheVoltageOut;
    }

    /**
     * @deprecated Not used by {@link com.serial.service.DeviceService}. The verified write path uses
     *             {@link #getCurrentSetVerified()} for read-back instead of this slower
     *             {@link com.serial.device.base.ModbusDevice#writeVerified} loop (3 × 200 ms).
     */
    @Deprecated
    @Override
    public void setCurrentVerified(final double amperes) throws Exception {
        writeVerified(ISET, IOUT, amperes);
    }

    @Override
    public void setCurrent(final double amperes) throws Exception {
        write(ISET, amperes);
    }

    @Override
    public double getCurrent() throws Exception {
        return cacheCurrentOut;
    }

    @Override
    public double getPower() throws Exception {
        return cachePowerOut;
    }

    @Override
    public double getInputVoltage() throws Exception {
        return cacheVoltageIn;
    }

    @Override
    public void setOutput(final boolean on) throws Exception {
        writeInt(OUTPUT_ENABLE, (on ? ModbusConstants.STATE_ON : ModbusConstants.STATE_OFF));
    }

    @Override
    public boolean getOutput() throws Exception {
        return (cacheOutput == ModbusConstants.STATE_ON);
    }

    @Override
    public int getFirmwareVersion() throws Exception {
        return readInt(FIRMWARE_VERSION);
    }

    @Override
    public void setProtectionState(final boolean on) throws Exception {
        writeInt(PROTECTION_STATE, (on ? ModbusConstants.STATE_ON : ModbusConstants.STATE_OFF));
    }

    @Override
    public boolean getProtectionState() throws Exception {
        return (cacheProtection == ModbusConstants.STATE_ON);
    }

    @Override
    public double getTemperatureCelsius() throws Exception {
        return cacheTemperature;
    }

    @Override
    public void setKeypad(final boolean locked) throws Exception {
        writeInt(LOCK, (locked ? ModbusConstants.STATE_ON : ModbusConstants.STATE_OFF));
    }

    @Override
    public boolean getKeypad() throws Exception {
        return (cacheLock == ModbusConstants.STATE_ON);
    }

    /**
     * Returns the regulation mode from the poll cache.
     *
     * <p>
     * Register {@link WuzhiRegisters#REG_MODE}: 0 = CV (constant voltage), 1 = CC (constant current).
     * </p>
     *
     * @return {@code true} if the device is in CV mode, {@code false} if in CC mode
     * @throws Exception if reading the register fails
     */
    @Override
    public boolean isCvMode() throws Exception {
        return (cacheMode == 0);
    }

    /**
     * Recalls a stored data set (M0–M10) into the active working registers.
     *
     * <p>
     * Writes to register {@link WuzhiRegisters#REG_MEMORY_RECALL}. Valid values: 0–10. The ZK-6522C supports 11 preset groups
     * (M0–M10), one more than the 10 (M0–M9) on Sinilink and Riden devices.
     * </p>
     *
     * @param preset data-set index (0–10)
     * @throws Exception if the Modbus write fails
     */
    public void recallPreset(final int preset) throws Exception {
        write(WuzhiRegisters.REG_MEMORY_RECALL, preset);
    }

    /**
     * Returns the raw product model register value from register {@code 0x0016}.
     *
     * @return raw model register value
     * @throws Exception if the Modbus read fails
     */
    public int getModelVersion() throws Exception {
        return readInt(MODEL_VERSION);
    }

}

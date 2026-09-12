package com.serial.devices;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serial.device.base.DeviceRegister;
import com.serial.device.base.ModbusDevice;
import com.serial.device.SinilinkRegisters;
import com.serial.devices.ifc.DC2DCConverter;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

/**
 * Driver for Sinilink XY-series programmable DC power supplies (e.g. {@code XY5008}, {@code XY6008}, {@code XY6014},
 * {@code XY6020L}).
 *
 * <p>
 * Wire connections - 4-pin TTL 3.3 V serial header on the underside of the control board:
 * </p>
 * <ul>
 * <li>Pin 1 - Black  (GND) → adapter GND</li>
 * <li>Pin 2 - Green  (RxD) → adapter TxD  (device receives)</li>
 * <li>Pin 3 - Yellow (TxD) → adapter RxD  (device transmits)</li>
 * <li>Pin 4 - Red    (VCC) → <strong>NC - do not connect</strong></li>
 * </ul>
 */
public class Sinilink extends ModbusDevice implements DC2DCConverter {

    private static final Logger logger = LoggerFactory.getLogger(Sinilink.class);

    public static final DeviceRegister VSET = new DeviceRegister("Voltage Setpoint", "V", SinilinkRegisters.REG_VSET, 100);

    public static final DeviceRegister ISET = new DeviceRegister("Current Setpoint", "A", SinilinkRegisters.REG_ISET, 1000);

    public static final DeviceRegister MODE = new DeviceRegister("Regulation Mode", null, SinilinkRegisters.REG_MODE);

    public static final DeviceRegister VOUT = new DeviceRegister("Output Voltage", "V", SinilinkRegisters.REG_VOUT, 100);

    public static final DeviceRegister IOUT = new DeviceRegister("Output Current", "A", SinilinkRegisters.REG_IOUT, 1000);

    public static final DeviceRegister POUT = new DeviceRegister("Output Power", "W", SinilinkRegisters.REG_POUT, 100);

    public static final DeviceRegister FIRMWARE_VERSION = new DeviceRegister("Firmware Version", null,
            SinilinkRegisters.REG_FIRMWARE, 100);

    public static final DeviceRegister MODEL_VERSION = new DeviceRegister("Model Version", null, SinilinkRegisters.REG_MODEL);

    public static final DeviceRegister VIN = new DeviceRegister("Voltage Input", "V", SinilinkRegisters.REG_VIN, 100);

    public static final DeviceRegister OUTPUT_ENABLE = new DeviceRegister("Output Enable", null,
            SinilinkRegisters.REG_OUTPUT_ENABLE);

    public static final DeviceRegister PROTECTION_STATE = new DeviceRegister("Protection Status", null,
            SinilinkRegisters.REG_PROTECTION_STATE);

    public static final DeviceRegister TEMP_CELSIUS = new DeviceRegister("Internal temperature Celsius", "°C",
            SinilinkRegisters.REG_TEMPERATURE_INTERNAL, 10);

    public static final DeviceRegister LOCK = new DeviceRegister("Keypad Lock", null, SinilinkRegisters.REG_KEYPAD_LOCK);

    // -------------------------------------------------------------------------
    // Poll cache - populated by pollAll(), returned by all getters
    // Block: 0x0000–0x0012 (19 registers), see bulk-read-plan.md offset map
    // -------------------------------------------------------------------------

    // @formatter:off
    private volatile double cacheVoltageSet;        // offset  0 VSET      ÷100
    private volatile double cacheCurrentSet;        // offset  1 ISET      ÷1000
    private volatile double cacheVoltageOut;        // offset  2 VOUT      ÷100
    private volatile double cacheCurrentOut;        // offset  3 IOUT      ÷1000
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
     * High byte of the 16-bit product model register ({@code 0x0016}) that carries the Sinilink "XY" series signature.
     *
     * <p>
     * The register is officially named "product model number" (Chan-pin Xing-hao) in Wuzhi/Sinilink factory Modbus mapping
     * sheets, described as the unique product identification code built into the firmware. Early hardware batches returned a
     * flat integer matching the model number (e.g. {@code 6008}). Modern unified firmware packs the ASCII character
     * {@code 'Y'} ({@code 0x59}) as the upper byte and the motherboard hardware revision as the lower byte
     * (e.g. {@code 0x5912} = decimal 22802, where {@code 0x12} = revision 1.8). The value {@code 0x59} is the ASCII
     * character {@code 'Y'} - the 'Y' from the "XY" product-line prefix - and is used as the modern family gate.
     * </p>
     */
    private static final int SINILINK_MODEL_HIGH_BYTE = 0x59;

    /**
     * Authoritative lookup map: exact product model register values confirmed by factory documentation.
     *
     * <p>
     * Tried first in {@link #verifyDevicePresent(List)}. Contains both the legacy flat integers (earliest hardware batches)
     * and any packed {@code 0x59xx} values that have been independently confirmed. If a match is found here no further
     * fallback is needed.
     * </p>
     *
     * <ul>
     * <li>Legacy (flat integer = model number as decimal):</li>
     * <li>&nbsp;&nbsp;{@code 5008} → {@code "XY5008"}</li>
     * <li>&nbsp;&nbsp;{@code 6008} → {@code "XY6008"}</li>
     * <li>&nbsp;&nbsp;{@code 6014} → {@code "XY6014"}</li>
     * <li>&nbsp;&nbsp;{@code 6020} → {@code "XY6020L"}</li>
     * <li>&nbsp;&nbsp;{@code 3680} → {@code "XYH3680"}</li>
     * <li>&nbsp;&nbsp;{@code 0x5008} = 20488 → {@code "XY5008"} (alternate documented encoding)</li>
     * <li>&nbsp;&nbsp;{@code 0x6008} = 24584 → {@code "XY6008"} (alternate documented encoding)</li>
     * <li>&nbsp;&nbsp;{@code 0x6100} = 24832 → {@code "XY6020L"}</li>
     * <li>&nbsp;&nbsp;{@code 0x3607} = 13831 → {@code "XY3607F"}</li>
     * <li>&nbsp;&nbsp;{@code 0x1805} = 6149 → {@code "SK180S"}</li>
     * <li>&nbsp;&nbsp;{@code 0x2209} = 8713 → {@code "SK220S"}</li>
     * </ul>
     */
    // @formatter:off
    private static final Map<Integer, String> KNOWN_MODELS = Map.ofEntries(
            Map.entry( 5008, "XY5008"),    // legacy flat integer
            Map.entry( 6008, "XY6008"),    // legacy flat integer
            Map.entry( 6014, "XY6014"),    // legacy flat integer
            Map.entry( 6020, "XY6020L"),   // legacy flat integer
            Map.entry( 3680, "XYH3680"),   // legacy flat integer
            Map.entry(20488, "XY5008"),    // 0x5008 alternate encoding
            Map.entry(24584, "XY6008"),    // 0x6008 alternate encoding
            Map.entry(24832, "XY6020L"),   // 0x6100
            Map.entry(13831, "XY3607F"),   // 0x3607
            Map.entry( 6149, "SK180S"),    // 0x1805
            Map.entry( 8713, "SK220S")     // 0x2209
    );
    // @formatter:on

    /**
     * Community-reported lookup map: packed {@code 0x59xx} product model register values observed on real hardware.
     *
     * <p>
     * <strong>NOTE - community data, not factory-confirmed.</strong> These values have been reported in open-source projects,
     * ESPHome integrations, and raw register dumps by the hobbyist community but are not documented in any official Sinilink
     * factory sheet. The lower byte encodes the control-board hardware revision (e.g. {@code 0x12} = revision 1.8).
     * This map is consulted only after {@link #KNOWN_MODELS} yields no match and only when the high byte equals
     * {@link #SINILINK_MODEL_HIGH_BYTE} ({@code 0x59}). Entries should be promoted to {@link #KNOWN_MODELS} once
     * independently confirmed on live hardware.
     * </p>
     *
     * <ul>
     * <li>{@code 0x5908} = 22792 → {@code "XY5008"} (v0.8 variant)</li>
     * <li>{@code 0x5912} = 22802 → {@code "XY6008"} (v1.8 layout)</li>
     * <li>{@code 0x5914} = 22804 → {@code "XY6008"} (v2.0 layout, also reported for XY6020L)</li>
     * <li>{@code 0x590E} = 22798 → {@code "XY6014"} (v1.4 layout)</li>
     * <li>{@code 0x590D} = 22797 → {@code "XY6020L"} (v1.3 layout)</li>
     * <li>{@code 0x590A} = 22794 → {@code "XYH3680"} (v1.0 layout)</li>
     * </ul>
     */
    // @formatter:off
    private static final Map<Integer, String> REPORTED_MODELS = Map.of(
            22792, "XY5008",   // 0x5908 - v0.8 variant
            22802, "XY6008",   // 0x5912 - v1.8 layout
            22804, "XY6008",   // 0x5914 - v2.0 layout (also reported for XY6020L)
            22798, "XY6014",   // 0x590E - v1.4 layout
            22797, "XY6020L",  // 0x590D - v1.3 layout
            22794, "XYH3680"   // 0x590A - v1.0 layout
    );
    // @formatter:on

    /**
     * Constructor.
     *
     * @param portName of {@code SerialPort} used with Modbus protocol
     * @param slave    port to use
     */
    public Sinilink(final String portName, final byte slave) {
        super(portName, slave);
    }

    /**
     * Verify that {@code Sinilink} is present using all standard baud rates in {@link ModbusTransport#BAUDS}.
     *
     * @return this {@link Sinilink} instance
     */
    public DC2DCConverter verifyDevicePresent() {
        return verifyDevicePresent(ModbusTransport.BAUDS);
    }

    /**
     * Verify that {@code Sinilink} is present probing only the specified baud rates.
     *
     * <p>
     * Reads the product model register ({@code 0x0016}) at each baud rate and applies a three-step identification strategy:
     * </p>
     *
     * <ol>
     * <li><strong>KNOWN_MODELS exact match</strong> - factory-confirmed values (legacy flat integers such as {@code 6008},
     *     and alternate documented hex encodings such as {@code 0x6008} = 24584). No warning is emitted.</li>
     * <li><strong>REPORTED_MODELS fallback</strong> - only reached when the high byte equals {@code 0x59} ('Y'), indicating
     *     a modern packed firmware encoding. These values are community-reported and not factory-confirmed; a {@code WARN}
     *     log is emitted and the entry should be promoted to {@link #KNOWN_MODELS} once verified on live hardware.</li>
     * <li><strong>Unknown packed value</strong> - high byte is {@code 0x59} but the word is in neither map. A {@code WARN}
     *     is logged and detection is skipped; the device is not identified rather than guessed.</li>
     * </ol>
     *
     * <p>
     * If detection succeeds the firmware version register ({@code 0x0017}) is also read and logged.
     * </p>
     *
     * @param bauds list of baud rates to probe in order
     * @return this {@link Sinilink} instance
     */
    public DC2DCConverter verifyDevicePresent(final List<Integer> bauds) {
        logger.info("Checking for Sinilink device...");
        for (final Integer baud : bauds) {
            try {
                transport = new ModbusTransport(portName, baud);
                logger.debug("Trying baud rate {}", baud);

                // Probe product model register (0x0016) for Sinilink identity.
                // Step 1: exact match against KNOWN_MODELS (factory-confirmed values, legacy and packed).
                // Step 2: if high byte = 0x59 ('Y'), consult REPORTED_MODELS (community data, not factory-confirmed).
                // Step 3: if high byte = 0x59 but still no match, log a warning and skip -- do not guess.
                try {
                    final int modelVersion = getModelVersion();
                    final String hex = Integer.toHexString(modelVersion).toUpperCase();
                    logger.info("Product model register (0x0016) raw value at {} baud: {} (0x{})",
                            baud, modelVersion, hex);

                    String modelName = KNOWN_MODELS.get(modelVersion);

                    if (modelName == null && (modelVersion >> 8) == SINILINK_MODEL_HIGH_BYTE) {
                        modelName = REPORTED_MODELS.get(modelVersion);
                        if (modelName != null) {
                            logger.warn("Product model register 0x{} matched community-reported data as {} "
                                    + "-- not factory-confirmed; promote to KNOWN_MODELS once verified on hardware.",
                                    hex, modelName);
                        } else {
                            logger.warn("Product model register 0x{} has Sinilink 'Y' high byte "
                                    + "but is not in KNOWN_MODELS or REPORTED_MODELS -- device not identified.",
                                    hex);
                        }
                    }

                    if (modelName != null) {
                        this.manufacturer = "Sinilink";
                        this.device = modelName;
                        int fw = 0;
                        try {
                            fw = getFirmwareVersion();
                        } catch (Exception ignored) {
                        }
                        logger.info("Detected Sinilink {} (product model: 0x{}, FW: {}) at {} baud.",
                                modelName, hex, fw, baud);
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
            logger.info("No Sinilink detected.");
        }
        return this;
    }

    /**
     * Reads the full register block ({@code 0x0000–0x0012}, 19 registers) in a single Modbus {@code 0x03} frame and populates
     * all poll-cache fields.
     *
     * <p>
     * Offset map (address − {@link SinilinkRegisters#REG_VSET}):
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
     * Scaling is delegated to the existing {@link DeviceRegister#decode(int)} method on each constant, so no scale factors are
     * hardcoded here.
     * </p>
     *
     * @throws Exception if the Modbus read fails
     */
    @Override
    public void pollAll() throws Exception {
        final int[] r = readBlock(SinilinkRegisters.REG_VSET, 19);
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
        cacheTemperatureExt = TEMP_CELSIUS.decode(r[14]); // same scale as internal temp
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
        writeBlock(SinilinkRegisters.REG_VSET, new int[] { VSET.encode(volts), ISET.encode(amperes) });
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
     * Register value: {@code 0} = CV (constant voltage), {@code 1} = CC (constant current).
     * </p>
     *
     * @return {@code true} if the device is in CV mode, {@code false} if in CC mode
     * @throws Exception if reading the register fails
     */
    @Override
    public boolean isCvMode() throws Exception {
        return (cacheMode == 0);
    }

    public int getHardwareVersion() throws Exception {
        return readInt(FIRMWARE_VERSION);
    }

    public int getModelVersion() throws Exception {
        return readInt(MODEL_VERSION);
    }

}

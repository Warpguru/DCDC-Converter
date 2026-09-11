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
 * {@code Sinilink} (e.g. {@code XY6008}) {@code Modbus} to {@code TTL} 3.3V {@code serial} connection.
 * 
 * <ul>
 * <li>Sinilink Black: → Gnd
 * <li>Sinilink Green: → TxD
 * <li>Sinilink Yellow: → RxD
 * <li>Sinilink Red: → NC (5V)
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
            SinilinkRegisters.REG_FIRMWARE);

    public static final DeviceRegister MODEL_VERSION = new DeviceRegister("Model Version", null, SinilinkRegisters.REG_MODEL);

    public static final DeviceRegister VIN = new DeviceRegister("Voltage Input", "V", SinilinkRegisters.REG_VIN);

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
     * Lookup map from the raw model ID returned by Register 0x0016 to the retail model name.
     *
     * <p>
     * Per {@code doc/Sinilink.md}, Register 0x0016 returns a 16-bit integer whose value is the hex model code read as a decimal
     * integer. For example, the XY6008 has hex model code {@code 0x6008}, which is decimal {@code 24584}. Validating against
     * this map avoids false positives when probing non-Sinilink hardware where register 0x0016 holds unrelated data.
     * </p>
     *
     * <ul>
     * <li>{@code 0x5008} = 20488 → {@code "XY5008"}</li>
     * <li>{@code 0x6008} = 24584 → {@code "XY6008"}</li>
     * <li>{@code 0x6100} = 24832 → {@code "XY6020L"}</li>
     * <li>{@code 0x3607} = 13831 → {@code "XY3607F"}</li>
     * <li>{@code 0x1805} = 6149 → {@code "SK180S"}</li>
     * <li>{@code 0x2209} = 8713 → {@code "SK220S"}</li>
     * </ul>
     *
     * <p>
     * <strong>Note:</strong> These are the exact hex IDs documented. Whether real hardware may return a variant with a revision
     * digit (e.g. {@code 0x6009} for a later XY6008 revision) is unknown and must be confirmed by live-device observation
     * (Sub-Task 3, {@code detection-gaps-plan.md}). The TODO log promotions in {@link #verifyDevicePresent(List)} are in place
     * for that purpose.
     * </p>
     */
    // @formatter:off
    private static final Map<Integer, String> KNOWN_MODELS = Map.of(
            20488, "XY5008",   // 0x5008
            24584, "XY6008",   // 0x6008
            24832, "XY6020L",  // 0x6100
            13831, "XY3607F",  // 0x3607
             6149, "SK180S",   // 0x1805
             8713, "SK220S"    // 0x2209
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
     * Probes the hardware model register (0x0016) first and validates it against {@link #KNOWN_MODELS}. If matched, reads the
     * firmware version (0x0017) to complete detection.
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

                // Probe model register (0x0016) against whitelist
                try {
                    int modelVersion = getModelVersion();
                    // INFO level intentional: raw ID must be visible without DEBUG mode for live-device
                    // confirmation of whether 0x0016 returns exact hex IDs (e.g. 24584 for XY6008) or
                    // includes a revision digit (see Sub-Task 3, detection-gaps-plan.md).
                    // TODO: downgrade back to DEBUG once the value has been confirmed on real hardware.
                    logger.info("Model register (0x0016) raw value at {} baud: {}", baud, modelVersion);
                    String modelName = KNOWN_MODELS.get(modelVersion);
                    if (modelName != null) {
                        this.manufacturer = "Sinilink";
                        this.device = modelName;
                        int fw = 0;
                        try {
                            fw = getFirmwareVersion();
                        } catch (Exception ignored) {
                        }
                        logger.info("Detected Sinilink {} (Model: {}, FW: {}) at {} baud.", modelName, modelVersion, fw, baud);
                    }
                } catch (Exception e) {
                    logger.debug("Model version read failed at {} baud: {}", baud, e.getMessage());
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
    public double getCurrentSet() throws Exception {
        return cacheCurrentSet;
    }

    @Override
    public void setVoltageVerified(final double volts) throws Exception {
        writeVerified(VSET, VOUT, volts);
    }

    @Override
    public void setVoltage(final double volts) throws Exception {
        write(VSET, volts);
    }

    @Override
    public double getVoltage() throws Exception {
        return cacheVoltageOut;
    }

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

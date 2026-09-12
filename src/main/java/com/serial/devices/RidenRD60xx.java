package com.serial.devices;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serial.device.base.DeviceRegister;
import com.serial.device.base.ModbusDevice;
import com.serial.device.RidenRegistersRD60xx;
import com.serial.devices.ifc.DC2DCConverter;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

/**
 * Driver for Ruideng {@code RD60xx} series programmable power supplies (e.g. {@code RD6006}, {@code RD6020}, {@code RD6030}).
 *
 * <p>
 * Wire connections - 4-pin TTL 3.3 V serial header on the back of the display board:
 * </p>
 * <ul>
 * <li>Pin 1 - Black (GND) → adapter GND</li>
 * <li>Pin 2 - White (RxD) → adapter TxD  (device receives)</li>
 * <li>Pin 3 - Green (TxD) → adapter RxD  (device transmits)</li>
 * <li>Pin 4 - Red   (VCC) → <strong>NC - do not connect</strong></li>
 * </ul>
 */
public class RidenRD60xx extends ModbusDevice implements DC2DCConverter {

    private static final Logger logger = LoggerFactory.getLogger(RidenRD60xx.class);

    public static final DeviceRegister DEVICE_ID = new DeviceRegister("Model Identification", null,
            RidenRegistersRD60xx.REG_DEVICE_ID);

    public static final DeviceRegister FIRMWARE_VERSION = new DeviceRegister("Firmware Version", null,
            RidenRegistersRD60xx.REG_FIRMWARE, 100);

    public static final DeviceRegister TEMP_SIGN_CELSIUS = new DeviceRegister("Temperature Sign", null,
            RidenRegistersRD60xx.REG_TEMP_SIGN_CELSIUS);

    public static final DeviceRegister TEMP_CELSIUS = new DeviceRegister("Temperature Celsius", "°C",
            RidenRegistersRD60xx.REG_TEMP_CELSIUS);

    public static final DeviceRegister TEMP_SIGN_FAHRENHEIT = new DeviceRegister("Temperature Sign", null,
            RidenRegistersRD60xx.REG_TEMP_FAHRENHEIT);

    public static final DeviceRegister TEMP_FAHRENHEIT = new DeviceRegister("Temperature Fahrenheit", "°F",
            RidenRegistersRD60xx.REG_TEMP_FAHRENHEIT);

    // TODO(P-series): RD6006P and RD6012P use scale 1000 for voltage (raw / 1000.0 = volts),
    // not scale 100. Voltage reads/writes on a P-series device via this driver are off by 10x.
    // A dedicated RidenRD60xxP driver with the correct scaling is required. See doc/Riden.md.
    public static final DeviceRegister VSET = new DeviceRegister("Voltage Setpoint", "V", RidenRegistersRD60xx.REG_VSET, 100);

    // TODO(P-series): RD6006P and RD6012P use dynamic current scaling controlled by Register
    // 0x0002 (Current Range Status): 0 = Low Range (0–6A, scale 10000), 1 = High Range
    // (6–12A, scale 1000). This fixed scale 1000 is only correct in the high-current range.
    // Current reads/writes in the low range are off by 10x. See doc/Riden.md.
    public static final DeviceRegister ISET = new DeviceRegister("Current Setpoint", "A", RidenRegistersRD60xx.REG_ISET, 1000);

    // TODO(P-series): See VSET note above - P-series voltage scale is 1000, not 100.
    public static final DeviceRegister VOUT = new DeviceRegister("Output Voltage", "V", RidenRegistersRD60xx.REG_VOUT, 100);

    // TODO(P-series): See ISET note above - P-series current scale is dynamic (10000 or 1000).
    public static final DeviceRegister IOUT = new DeviceRegister("Output Current", "A", RidenRegistersRD60xx.REG_IOUT, 1000);

    public static final DeviceRegister AH = new DeviceRegister("Accumulated Amperehours", "Ah", RidenRegistersRD60xx.REG_AH);

    public static final DeviceRegister POUT = new DeviceRegister("Output Power", "W", RidenRegistersRD60xx.REG_POUT, 1000);

    public static final DeviceRegister VIN = new DeviceRegister("Voltage Input", "V", RidenRegistersRD60xx.REG_VIN, 100);

    public static final DeviceRegister LOCK = new DeviceRegister("Keypad Lock", null, RidenRegistersRD60xx.REG_KEYPAD_LOCK);

    public static final DeviceRegister PROTECTION_STATE = new DeviceRegister("Protection Status", null,
            RidenRegistersRD60xx.REG_PROTECTION_STATE);

    public static final DeviceRegister MODE = new DeviceRegister("CC/CV Mode", null, RidenRegistersRD60xx.REG_MODE);

    public static final DeviceRegister OUTPUT_ENABLE = new DeviceRegister("Output Enable", null,
            RidenRegistersRD60xx.REG_OUTPUT_ENABLE);

    public static final DeviceRegister PRESET = new DeviceRegister("Preset Selector", "Mx", RidenRegistersRD60xx.REG_PRESET);

    public static final DeviceRegister IRANGE = new DeviceRegister("Current Range", "A",
            RidenRegistersRD60xx.REG_CURRENT_RANGE);

    // -------------------------------------------------------------------------
    // Poll cache - populated by pollAll(), returned by all getters
    // Block: 0x0000–0x0012 (19 registers), see bulk-read-plan.md offset map
    // -------------------------------------------------------------------------

    // @formatter:off
    private volatile int    cacheDeviceId;           // offset  0 DEVICE_ID        raw
    @SuppressWarnings("unused")
    private volatile int    cacheSerialHigh;         // offset  1 SERIAL_HIGH      raw
    @SuppressWarnings("unused")
    private volatile int    cacheSerialLow;          // offset  2 SERIAL_LOW       raw
    private volatile int    cacheFirmwareRaw;        // offset  3 FIRMWARE         raw (FIRMWARE_VERSION.decode())
    private volatile int    cacheTempSignCelsius;    // offset  4 TEMP_SIGN_C      raw
    private volatile double cacheTemperature;        // offset  5 TEMP_CELSIUS     TEMP_CELSIUS.decode()
    private volatile int    cacheTempSignFahrenheit; // offset  6 TEMP_SIGN_F      raw
    private volatile int    cacheTemperatureFahr;    // offset  7 TEMP_FAHRENHEIT  raw
    private volatile double cacheVoltageSet;         // offset  8 VSET             VSET.decode()
    private volatile double cacheCurrentSet;         // offset  9 ISET             ISET.decode()
    private volatile double cacheVoltageOut;         // offset 10 VOUT             VOUT.decode()
    private volatile double cacheCurrentOut;         // offset 11 IOUT             IOUT.decode()
    private volatile int    cacheAh;                 // offset 12 AH               raw
    private volatile double cachePowerOut;           // offset 13 POUT             POUT.decode()
    private volatile double cacheVoltageIn;          // offset 14 VIN              VIN.decode()
    private volatile int    cacheLock;               // offset 15 LOCK             raw
    private volatile int    cacheProtection;         // offset 16 PROTECTION       raw
    private volatile int    cacheMode;               // offset 17 MODE             raw
    private volatile int    cacheOutput;             // offset 18 OUTPUT           raw
    // @formatter:on

    /**
     * Lookup map from the raw 5-digit model ID returned by Register 0x0000 to the retail model name.
     *
     * <p>
     * Per {@code doc/Riden.md}, Register 0x0000 on all RD60x/RK60x units returns a 5-digit integer whose first 4 digits encode
     * the model family and whose last digit encodes a hardware revision, region variant, or Wi-Fi board option. For example:
     * </p>
     *
     * <ul>
     * <li>60060–60064 → {@code "RD6006"}</li>
     * <li>60065 → {@code "RD6006P"} (high-precision variant)</li>
     * <li>60066 → {@code "RK6006"}</li>
     * <li>60120–60124 → {@code "RD6012"}</li>
     * <li>60125 → {@code "RD6012P"} (high-precision variant)</li>
     * <li>60180–60184 → {@code "RD6018"}</li>
     * <li>60240–60244 → {@code "RD6024"}</li>
     * <li>60300–60304 → {@code "RD6030"}</li>
     * </ul>
     *
     * <p>
     * Using an explicit map instead of arithmetic (e.g. {@code id / 10}) correctly handles P-series variants such as 60065 →
     * {@code "RD6006P"}, which arithmetic would wrongly map to {@code "RD6006"}. Validating against this map also prevents
     * false positives on Sinilink hardware where Register 0x0000 holds {@code VSET} and could return any voltage value.
     * </p>
     */
    // @formatter:off
    private static final Map<Integer, String> KNOWN_DEVICE_IDS = Map.ofEntries(
            Map.entry(60060, "RD6006"),
            Map.entry(60061, "RD6006"),
            Map.entry(60062, "RD6006"),
            Map.entry(60063, "RD6006"),
            Map.entry(60064, "RD6006"),
            Map.entry(60065, "RD6006P"),
            Map.entry(60066, "RK6006"),
            Map.entry(60120, "RD6012"),
            Map.entry(60121, "RD6012"),
            Map.entry(60122, "RD6012"),
            Map.entry(60123, "RD6012"),
            Map.entry(60124, "RD6012"),
            Map.entry(60125, "RD6012P"),
            Map.entry(60180, "RD6018"),
            Map.entry(60181, "RD6018"),
            Map.entry(60182, "RD6018"),
            Map.entry(60183, "RD6018"),
            Map.entry(60184, "RD6018"),
            Map.entry(60240, "RD6024"),
            Map.entry(60241, "RD6024"),
            Map.entry(60242, "RD6024"),
            Map.entry(60243, "RD6024"),
            Map.entry(60244, "RD6024"),
            Map.entry(60300, "RD6030"),
            Map.entry(60301, "RD6030"),
            Map.entry(60302, "RD6030"),
            Map.entry(60303, "RD6030"),
            Map.entry(60304, "RD6030")
    );
    // @formatter:on

    /**
     * Constructor.
     *
     * @param portName of {@code SerialPort} used with Modbus protocol
     * @param slave    port to use
     */
    public RidenRD60xx(final String portName, final byte slave) {
        super(portName, slave);
    }

    /**
     * Verify that {@code Riden RD60xx} is present using all standard baud rates in {@link ModbusTransport#BAUDS}.
     *
     * @return this {@link RidenRD60xx} instance
     */
    public DC2DCConverter verifyDevicePresent() {
        return verifyDevicePresent(ModbusTransport.BAUDS);
    }

    /**
     * Verify that {@code Riden RD60xx} is present probing only the specified baud rates.
     *
     * <p>
     * Probes the device ID register (0x0000) first and validates it against {@link #KNOWN_DEVICE_IDS}. Register 0x0000 returns
     * a 5-digit integer (e.g. 60062 for an RD6006 revision 2). The full 5-digit value is looked up in the map to obtain the
     * correct retail model name, which handles P-series variants (e.g. 60065 → {@code "RD6006P"}) that arithmetic would
     * misidentify. If matched, reads the firmware version register (0x0003) to complete detection.
     * </p>
     *
     * @param bauds list of baud rates to probe in order
     * @return this {@link RidenRD60xx} instance
     */
    public DC2DCConverter verifyDevicePresent(final List<Integer> bauds) {
        logger.info("Checking for Riden RD60xx device...");
        for (final Integer baud : bauds) {
            try {
                transport = new ModbusTransport(portName, baud);
                logger.debug("Trying baud rate {}", baud);

                // Probe Device ID register (0x0000) - returns a 5-digit model+revision code
                try {
                    int deviceId = getDeviceId();
                    // INFO level intentional: raw ID must be visible without DEBUG mode for live-device
                    // confirmation of the 5-digit scheme (see Sub-Task 3, detection-gaps-plan.md).
                    // TODO: downgrade back to DEBUG once the value has been confirmed on real hardware.
                    logger.info("Device ID register (0x0000) raw value at {} baud: {}", baud, deviceId);
                    final String modelName = KNOWN_DEVICE_IDS.get(deviceId);
                    if (modelName != null) {
                        int fw = 0;
                        try {
                            fw = getFirmwareVersion();
                        } catch (Exception ignored) {
                        }
                        this.manufacturer = "Riden";
                        this.device = modelName;
                        logger.info("Detected Riden RD60xx (Model: {}, ID: {}, FW: {}) at {} baud.", this.device, deviceId, fw,
                                baud);
                    }
                } catch (Exception e) {
                    logger.debug("Device ID read failed at {} baud: {}", baud, e.getMessage());
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
            logger.info("No Riden RD60xx detected.");
        }
        return this;
    }

    /**
     * Reads the full register block ({@code 0x0000–0x0012}, 19 registers) in a single Modbus {@code 0x03} frame and populates
     * all poll-cache fields.
     *
     * <p>
     * Offset map (address − {@link RidenRegistersRD60xx#REG_DEVICE_ID}):
     * </p>
     * 
     * <pre>
     *  [0]  DEVICE_ID   [1]  SERIAL_HIGH [2]  SERIAL_LOW   [3]  FIRMWARE
     *  [4]  TEMP_SIGN_C [5] TEMP_C       [6]  TEMP_SIGN_F  [7]  TEMP_F
     *  [8]  VSET        [9] ISET         [10] VOUT         [11] IOUT
     * [12]  AH          [13] POUT        [14] VIN          [15] LOCK
     * [16]  PROTECTION  [17] MODE        [18] OUTPUT
     * </pre>
     *
     * <p>
     * Scaling is delegated to {@link DeviceRegister#decode(int)} on each constant.
     * </p>
     *
     * @throws Exception if the Modbus read fails
     */
    @Override
    public void pollAll() throws Exception {
        final int[] r = readBlock(RidenRegistersRD60xx.REG_DEVICE_ID, 19);
        cacheDeviceId = r[0];
        cacheSerialHigh = r[1];
        cacheSerialLow = r[2];
        cacheFirmwareRaw = r[3];
        cacheTempSignCelsius = r[4];
        cacheTemperature = TEMP_CELSIUS.decode(r[5]);
        cacheTempSignFahrenheit = r[6];
        cacheTemperatureFahr = r[7];
        cacheVoltageSet = VSET.decode(r[8]);
        cacheCurrentSet = ISET.decode(r[9]);
        cacheVoltageOut = VOUT.decode(r[10]);
        cacheCurrentOut = IOUT.decode(r[11]);
        cacheAh = r[12];
        cachePowerOut = POUT.decode(r[13]);
        cacheVoltageIn = VIN.decode(r[14]);
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
        return (int) FIRMWARE_VERSION.decode(cacheFirmwareRaw);
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
     * Register {@link RidenRegistersRD60xx#REG_MODE}: 0 = CV, 1 = CC.
     * </p>
     *
     * @return {@code true} for CV mode, {@code false} for CC mode
     * @throws Exception if reading the register fails
     */
    @Override
    public boolean isCvMode() throws Exception {
        return (cacheMode == 0);
    }

    /**
     * Returns the internal temperature in degrees Celsius from the poll cache.
     *
     * <p>
     * The RD60xx represents temperature as a magnitude in {@link RidenRegistersRD60xx#REG_TEMP_CELSIUS} and a separate sign in
     * {@link RidenRegistersRD60xx#REG_TEMP_SIGN_CELSIUS} ({@code 0} = positive, {@code 1} = negative). Both are populated by
     * {@link #pollAll()}; the sign is applied here before returning.
     * </p>
     *
     * @return temperature in °C; negative values indicate below-zero readings
     * @throws Exception if the poll cache has not yet been populated
     */
    @Override
    public double getTemperatureCelsius() throws Exception {
        return (cacheTempSignCelsius == ModbusConstants.STATE_ON) ? -cacheTemperature : cacheTemperature;
    }

    public int getDeviceId() throws Exception {
        return cacheDeviceId;
    }

    public int getTemperatureSignCelsius() throws Exception {
        return cacheTempSignCelsius;
    }

    public int getTemperatureSignFahrenheit() throws Exception {
        return cacheTempSignFahrenheit;
    }

    public int getTemperatureFahrenheit() throws Exception {
        return cacheTemperatureFahr;
    }

    public double getAmpereHours() throws Exception {
        return AH.decode(cacheAh);
    }

    public void setPreset(final int preset) throws Exception {
        writeInt(PRESET, preset);
    }

    public int getPreset() throws Exception {
        return readInt(PRESET);
    }

    public void setCurrentRange(final int range) throws Exception {
        writeInt(IRANGE, range);
    }

    public int getCurrentRange() throws Exception {
        return readInt(IRANGE);
    }

}

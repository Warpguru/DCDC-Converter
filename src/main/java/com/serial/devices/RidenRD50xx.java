package com.serial.devices;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serial.device.DeviceRegister;
import com.serial.device.ModbusDevice;
import com.serial.device.RidenRegistersRD50xx;
import com.serial.devices.ifc.DC2DCConverter;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

/**
 * Driver for Ruideng {@code DPS/RD50xx} series programmable power supplies (e.g. {@code DPS5020}).
 *
 * <p>
 * The DPS series (DPS5005, DPS5010, DPS5020) is an older Ruideng product line that shares the same
 * electrical specs as the modern RD50xx naming but uses a different Modbus register map from the
 * RD60xx series. Key architectural differences vs. RD60xx:
 * </p>
 *
 * <ul>
 * <li>Default baud rate: 9600 baud (RD60xx defaults to 115200 baud).</li>
 * <li>Model ID at Register 0x000B - 4-digit short code (e.g. {@code 5020}).
 *     On the RD60xx, Register 0x0000 is the model register; on the DPS series, Register 0x0000
 *     is {@code VSET}.</li>
 * <li>Firmware at Register 0x000C ({@code VERSON}), raw value / 100.0 = version (e.g.
 *     {@code 170} = v1.70). Several DPS5020 factory batches always return {@code 0} - this
 *     is a known hardware limitation, not a protocol or scaling bug.</li>
 * </ul>
 *
 * <p>
 * Detected devices report {@code manufacturer = "Ruideng"} and use the {@code RD50xx} properties
 * files (e.g. {@code RD5020.properties}) since the electrical limits are identical between the
 * DPS and RD designations of the same model.
 * </p>
 *
 * <p>Wire connections (TTL 3.3V serial):</p>
 * <ul>
 * <li>Black  → GND</li>
 * <li>Yellow → TxD</li>
 * <li>Blue   → RxD</li>
 * <li>Red    → NC (5V)</li>
 * </ul>
 */
public class RidenRD50xx extends ModbusDevice implements DC2DCConverter {

    private static final Logger logger = LoggerFactory.getLogger(RidenRD50xx.class);

    public static final DeviceRegister VSET = new DeviceRegister("Voltage Setpoint", "V", RidenRegistersRD50xx.REG_VSET, 100);

    public static final DeviceRegister ISET = new DeviceRegister("Current Setpoint", "A", RidenRegistersRD50xx.REG_ISET, 100);

    public static final DeviceRegister VOUT = new DeviceRegister("Output Voltage", "V", RidenRegistersRD50xx.REG_VOUT, 100);

    public static final DeviceRegister IOUT = new DeviceRegister("Output Current", "A", RidenRegistersRD50xx.REG_IOUT, 100);

    public static final DeviceRegister POUT = new DeviceRegister("Output Power", "W", RidenRegistersRD50xx.REG_POUT, 100);

    public static final DeviceRegister VIN = new DeviceRegister("Voltage Input", "V", RidenRegistersRD50xx.REG_VIN, 100);

    public static final DeviceRegister LOCK = new DeviceRegister("Keypad Lock", null, RidenRegistersRD50xx.REG_KEYPAD_LOCK);

    public static final DeviceRegister PROTECTION_STATE = new DeviceRegister("Protection Status", null,
            RidenRegistersRD50xx.REG_PROTECTION_STATE);

    public static final DeviceRegister MODE = new DeviceRegister("CC/CV Mode", null, RidenRegistersRD50xx.REG_MODE);

    public static final DeviceRegister OUTPUT_ENABLE = new DeviceRegister("Output Enable", null,
            RidenRegistersRD50xx.REG_OUTPUT_ENABLE);

    /** B_LED - Backlight brightness level (0 = darkest, 5 = brightest). */
    public static final DeviceRegister BACKLIGHT = new DeviceRegister("Backlight Level", null,
            RidenRegistersRD50xx.REG_BACKLIGHT);

    public static final DeviceRegister DEVICE_ID = new DeviceRegister("Model Identification", null,
            RidenRegistersRD50xx.REG_DEVICE_ID);

    /**
     * Firmware version register.
     *
     * <p>
     * Raw register value / 10.0 = firmware version (e.g. {@code 17} = v1.7, {@code 19} = v1.9).
     * Several DPS5020 factory batches always return {@code 0} from this register - this is a known
     * hardware limitation, not a scaling bug. A result of {@code 0} should be treated as "firmware
     * version unknown" rather than "v0.0".
     * </p>
     */
    public static final DeviceRegister FIRMWARE_VERSION = new DeviceRegister("Firmware Version", null,
            RidenRegistersRD50xx.REG_FIRMWARE, 10);

    // -------------------------------------------------------------------------
    // Poll cache — populated by pollAll(), returned by all getters
    // Block: 0x0000–0x000C (13 registers), see bulk-read-plan.md offset map
    // -------------------------------------------------------------------------

    private volatile double cacheVoltageSet;    // offset  0 VSET      VSET.decode()
    private volatile double cacheCurrentSet;    // offset  1 ISET      ISET.decode()
    private volatile double cacheVoltageOut;    // offset  2 VOUT      VOUT.decode()
    private volatile double cacheCurrentOut;    // offset  3 IOUT      IOUT.decode()
    private volatile double cachePowerOut;      // offset  4 POUT      POUT.decode()
    private volatile double cacheVoltageIn;     // offset  5 VIN       VIN.decode()
    private volatile int    cacheLock;          // offset  6 LOCK      raw
    private volatile int    cacheProtection;    // offset  7 PROTECTION raw
    private volatile int    cacheMode;          // offset  8 MODE      raw
    private volatile int    cacheOutput;        // offset  9 OUTPUT    raw
    private volatile int    cacheBacklight;     // offset 10 BACKLIGHT raw
    private volatile int    cacheDeviceId;      // offset 11 DEVICE_ID raw
    private volatile int    cacheFirmwareRaw;   // offset 12 FIRMWARE  raw (÷10 in getter)

    /**
     * Lookup map from the 4-digit model code returned by Register 0x000B to the retail model name.
     *
     * <p>
     * Register 0x000B is the Product Model Register in the DPS series Modbus protocol. It returns a
     * short 4-digit integer identifying the model (e.g. {@code 5020} for the DPS5020 / RD5020).
     * Confirmed on real hardware: a DPS5020 returns exactly {@code 5020} at 9600 baud.
     * </p>
     *
     * <p>
     * The device name is stored as {@code "RD50xx"} (e.g. {@code "RD5020"}) rather than the DPS
     * prefix so that both DPS and RD variants of the same model share the same properties file
     * (e.g. {@code RD5020.properties}). The manufacturer field is set to {@code "Ruideng"} to
     * correctly identify the DPS origin. Validating against this map also prevents false detection
     * on Sinilink hardware where Register 0x000B is the output timer minutes counter.
     * </p>
     */
    private static final Map<Integer, String> KNOWN_DEVICE_IDS = Map.of(
            5005, "DPS5005",
            5010, "DPS5010",
            5020, "DPS5020"
    );

    /**
     * Constructor.
     *
     * @param portName of {@code SerialPort} used with Modbus protocol
     * @param slave    port to use
     */
    public RidenRD50xx(final String portName, final byte slave) {
        super(portName, slave);
    }

    /**
     * Verify that {@code Riden RD50xx} is present using all standard baud rates in {@link ModbusTransport#BAUDS}.
     *
     * @return this {@link RidenRD50xx} instance
     */
    public DC2DCConverter verifyDevicePresent() {
        return verifyDevicePresent(ModbusTransport.BAUDS);
    }

    /**
     * Verify that a Ruideng DPS/RD50xx device is present probing only the specified baud rates.
     *
     * <p>
     * Probes the model register (0x000B) first and validates against {@link #KNOWN_DEVICE_IDS}.
     * If matched, reads the firmware version register (0x000C) to complete detection.
     * </p>
     *
     * @param bauds list of baud rates to probe in order
     * @return this {@link RidenRD50xx} instance
     */
    public DC2DCConverter verifyDevicePresent(final List<Integer> bauds) {
        logger.info("Checking for Ruideng DPS/RD50xx device...");
        for (final Integer baud : bauds) {
            try {
                transport = new ModbusTransport(portName, baud);
                logger.debug("Trying baud rate {}", baud);

                // Probe Device ID register (0x000B)
                try {
                    int deviceId = getDeviceId();
                    // Register 0x000B returns a short 4-digit model code (e.g. 5020 for RD5020),
                    // confirmed on real hardware. This differs from the RD60xx layout where Register
                    // 0x0000 returns 5-digit IDs; the RD50xx has a different register map.
                    logger.debug("Device ID register (0x000B) raw value at {} baud: {}", baud, deviceId);
                    final String modelName = KNOWN_DEVICE_IDS.get(deviceId);
                    if (modelName != null) {
                        int fw = 0;
                        try {
                            fw = getFirmwareVersion();
                        } catch (Exception ignored) {
                        }
                        this.manufacturer = "Ruideng";
                        this.device = modelName;
                        // fw == 0 is normal on DPS5020 factory batches - not a read error.
                        final String fwStr = (fw == 0) ? "unknown" : ("v" + String.format("%.1f", fw / 10.0));
                        logger.info("Detected Ruideng DPS/RD50xx (Model: {}, FW: {}) at {} baud.", this.device, fwStr, baud);
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
            logger.info("No Ruideng DPS/RD50xx detected.");
        }
        return this;
    }

    /**
     * Reads the full register block ({@code 0x0000–0x000C}, 13 registers) in a single Modbus
     * {@code 0x03} frame and populates all poll-cache fields.
     *
     * <p>Offset map (address − {@link RidenRegistersRD50xx#REG_VSET}):</p>
     * <pre>
     *  [0] VSET  [1] ISET  [2] VOUT  [3] IOUT  [4] POUT  [5] VIN
     *  [6] LOCK  [7] PROTECT [8] MODE [9] OUTPUT [10] BACKLIGHT
     * [11] DEVICE_ID  [12] FIRMWARE
     * </pre>
     *
     * <p>Scaling is delegated to {@link DeviceRegister#decode(int)} on each constant.
     * Temperature is not available on this device family; {@link #getTemperatureCelsius()}
     * always returns {@code -999.0}.</p>
     *
     * @throws Exception if the Modbus read fails
     */
    @Override
    public void pollAll() throws Exception {
        final int[] r = readBlock(RidenRegistersRD50xx.REG_VSET, 13);
        cacheVoltageSet  = VSET.decode(r[0]);
        cacheCurrentSet  = ISET.decode(r[1]);
        cacheVoltageOut  = VOUT.decode(r[2]);
        cacheCurrentOut  = IOUT.decode(r[3]);
        cachePowerOut    = POUT.decode(r[4]);
        cacheVoltageIn   = VIN.decode(r[5]);
        cacheLock        = r[6];
        cacheProtection  = r[7];
        cacheMode        = r[8];
        cacheOutput      = r[9];
        cacheBacklight   = r[10];
        cacheDeviceId    = r[11];
        cacheFirmwareRaw = r[12];
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
        return cacheFirmwareRaw;
    }

    @Override
    public void setProtectionState(final boolean on) throws Exception {
        writeInt(PROTECTION_STATE, (on ? ModbusConstants.STATE_ON : ModbusConstants.STATE_OFF));
    }

    @Override
    public boolean getProtectionState() throws Exception {
        return (cacheProtection == ModbusConstants.STATE_ON);
    }

    /**
     * The DPS50xx series does not expose a temperature register in its Modbus protocol.
     * Returns {@code -999.0} to indicate that temperature is unavailable on this device.
     *
     * <p>
     * {@code NaN} cannot be used because Jackson serialises it as a non-finite float, which is
     * invalid JSON and would cause every WebSocket broadcast to fail. {@code -999.0} is chosen
     * as an unambiguous sentinel: it is physically impossible for any semiconductor device
     * (absolute zero is −273.15 °C), so it can never be a real reading.
     * </p>
     *
     * @return {@code -999.0} (temperature not available)
     */
    @Override
    public double getTemperatureCelsius() throws Exception {
        return -999.0;
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
     * <p>Register {@link RidenRegistersRD50xx#REG_MODE}: 0 = CV, 1 = CC.</p>
     *
     * @return {@code true} for CV mode, {@code false} for CC mode
     * @throws Exception if reading the register fails
     */
    @Override
    public boolean isCvMode() throws Exception {
        return (cacheMode == 0);
    }

    /**
     * Recalls a stored data set (M0–M9) into the active working registers.
     *
     * <p>Writes to register {@code EXTRACT_M} (0x0023). Valid values: 0–9.</p>
     *
     * @param preset data-set index (0–9)
     * @throws Exception if the Modbus write fails
     */
    public void recallPreset(final int preset) throws Exception {
        write(RidenRegistersRD50xx.REG_EXTRACT_M, preset);
    }

    /**
     * Returns the current model identification code from Register 0x000B.
     *
     * @return raw model code (e.g. {@code 5020})
     * @throws Exception if the Modbus read fails
     */
    public int getDeviceId() throws Exception {
        return readInt(DEVICE_ID);
    }

    /**
     * Sets the backlight brightness level (0 = darkest, 5 = brightest).
     *
     * @param level brightness level (0–5)
     * @throws Exception if the Modbus write fails
     */
    public void setBacklight(final int level) throws Exception {
        writeInt(BACKLIGHT, level);
    }

    /**
     * Returns the current backlight brightness level.
     *
     * @return brightness level (0–5)
     * @throws Exception if the Modbus read fails
     */
    public int getBacklight() throws Exception {
        return readInt(BACKLIGHT);
    }

}

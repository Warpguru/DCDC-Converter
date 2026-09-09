package com.serial.devices;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serial.device.DeviceRegister;
import com.serial.device.ModbusDevice;
import com.serial.device.RidenRegistersRD60xx;
import com.serial.devices.ifc.DC2DCConverter;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

/**
 * {@code RidenRD60xx} (e.g. {@code RD6030}) {@code Modbus} to {@code TTL} 3.3V {@code serial} connection.
 * 
 * <ul>
 * <li>RidenRD60xx Black: → Gnd
 * <li>RidenRD60xx ?: → TxD
 * <li>RidenRD60xx ?: → RxD
 * <li>RidenRD60xx Red: → NC (5V)
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

    public static final DeviceRegister VSET = new DeviceRegister("Voltage Setpoint", "V", RidenRegistersRD60xx.REG_VSET, 100);

    public static final DeviceRegister ISET = new DeviceRegister("Current Setpoint", "A", RidenRegistersRD60xx.REG_ISET, 1000);

    public static final DeviceRegister VOUT = new DeviceRegister("Output Voltage", "V", RidenRegistersRD60xx.REG_VOUT, 100);

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

    /**
     * Whitelist of known RD60xx series device IDs (e.g. 6006, 6012, 6018, 6024, 6030, 60062, 60302).
     *
     * <p>
     * Register 0x0000 returns the device model signature. Validating against this whitelist
     * prevents false positives on devices like Sinilink (where register 0x0000 is VSET).
     * </p>
     */
    private static final Set<Integer> KNOWN_DEVICE_IDS = Set.of(
            6006, 6012, 6018, 6024, 6030, 60062, 60302
    );

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
     * Probes the device ID register (0x0000) first and validates it against {@link #KNOWN_DEVICE_IDS}.
     * If matched, reads the firmware version (0x0003) to cross-check and complete detection.
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

                // Probe Device ID register (0x0000)
                try {
                    int deviceId = getDeviceId();
                    logger.debug("Device ID register read at {} baud: {}", baud, deviceId);
                    if (KNOWN_DEVICE_IDS.contains(deviceId)) {
                        int fw = 0;
                        try {
                            fw = getFirmwareVersion();
                        } catch (Exception ignored) {
                        }
                        this.manufacturer = "Riden";
                        // Standard models format as RD60xx, precision models (e.g. 60062) format as RD6006P/RD60xx
                        int baseModel = (deviceId > 10000) ? (deviceId / 10) : deviceId;
                        this.device = String.format("RD%04d", baseModel);
                        logger.info("Detected Riden RD60xx (Model: {}, Signature: {}, FW: {}) at {} baud.", this.device, deviceId, fw, baud);
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
        return read(VOUT);
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
        return read(IOUT);
    }

    @Override
    public double getPower() throws Exception {
        return read(POUT);
    }

    @Override
    public double getInputVoltage() throws Exception {
        return read(VIN);
    }

    @Override
    public void setOutput(boolean on) throws Exception {
        writeInt(OUTPUT_ENABLE, (on ? ModbusConstants.STATE_ON : ModbusConstants.STATE_OFF));
    }

    @Override
    public boolean getOutput() throws Exception {
        return (readInt(OUTPUT_ENABLE) == ModbusConstants.STATE_ON);
    }

    @Override
    public int getFirmwareVersion() throws Exception {
        return readInt(FIRMWARE_VERSION);
    }

    @Override
    public void setProtectionState(boolean on) throws Exception {
        writeInt(PROTECTION_STATE, (on ? ModbusConstants.STATE_ON : ModbusConstants.STATE_OFF));
    }

    @Override
    public boolean getProtectionState() throws Exception {
        return (readInt(PROTECTION_STATE) == ModbusConstants.STATE_ON);
    }

    @Override
    public void setKeypad(final boolean locked) throws Exception {
        writeInt(LOCK, (locked ? ModbusConstants.STATE_ON : ModbusConstants.STATE_OFF));
    }

    @Override
    public boolean getKeypad() throws Exception {
        return (readInt(LOCK) == ModbusConstants.STATE_ON);
    }

    /**
     * Returns the regulation mode.
     *
     * <p>Register {@link RidenRegistersRD60xx#REG_MODE}: 0 = CV, 1 = CC.</p>
     *
     * @return {@code true} for CV mode, {@code false} for CC mode
     * @throws Exception if reading the register fails
     */
    @Override
    public boolean isCvMode() throws Exception {
        return (readInt(MODE) == 0);
    }

    @Override
    public double getTemperatureCelsius() throws Exception {
        return read(TEMP_CELSIUS);
    }

    public int getDeviceId() throws Exception {
        return readInt(DEVICE_ID);
    }

    public int getTemperatureSignCelsius() throws Exception {
        return readInt(TEMP_SIGN_CELSIUS);
    }

    public int getTemperatureSignFahrenheit() throws Exception {
        return readInt(TEMP_SIGN_FAHRENHEIT);
    }

    public int getTemperatureFahrenheit() throws Exception {
        return readInt(TEMP_FAHRENHEIT);
    }

    public double getAmpereHours() throws Exception {
        return read(AH);
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

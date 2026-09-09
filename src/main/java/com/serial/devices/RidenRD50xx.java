package com.serial.devices;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serial.device.DeviceRegister;
import com.serial.device.ModbusDevice;
import com.serial.device.RidenRegistersRD50xx;
import com.serial.devices.ifc.DC2DCConverter;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

/**
 * {@code RidenRD50xx} (e.g. {@code RD5020}) {@code Modbus} to {@code TTL} 3.3V {@code serial} connection.
 * 
 * <ul>
 * <li>RidenRD50xx Black: → Gnd
 * <li>RidenRD50xx Yellow: → TxD
 * <li>RidenRD50xx Blue: → RxD
 * <li>RidenRD50xx Red: → NC (5V)
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

    public static final DeviceRegister PRESET = new DeviceRegister("Preset Selector", "Mx", RidenRegistersRD50xx.REG_PRESET);

    public static final DeviceRegister DEVICE_ID = new DeviceRegister("Model Identification", null,
            RidenRegistersRD50xx.REG_DEVICE_ID);

    public static final DeviceRegister AH_HIGH = new DeviceRegister("Accumulated Amperehours high", "Ah",
            RidenRegistersRD50xx.REG_AH_HIGH);

    public static final DeviceRegister AH_LOW = new DeviceRegister("Accumulated Amperehours low", "Ah",
            RidenRegistersRD50xx.REG_AH_LOW);

    public static final DeviceRegister WH_HIGH = new DeviceRegister("Accumulated Watthours high", "Wh",
            RidenRegistersRD50xx.REG_WH_HIGH);

    public static final DeviceRegister WH_LOW = new DeviceRegister("Accumulated Watthours low", "Wh",
            RidenRegistersRD50xx.REG_WH_LOW);

    public static final DeviceRegister TEMP_SIGN_CELSIUS = new DeviceRegister("Temperature Sign", null,
            RidenRegistersRD50xx.REG_TEMP_SIGN_CELSIUS);

    public static final DeviceRegister TEMP_CELSIUS = new DeviceRegister("Temperature Celsius", "°C",
            RidenRegistersRD50xx.REG_TEMP_CELSIUS);

    public static final DeviceRegister SERIAL_HIGH = new DeviceRegister("Serial Number high", "Wh",
            RidenRegistersRD50xx.REG_SERIAL_HIGH);

    public static final DeviceRegister SERIAL_LOW = new DeviceRegister("Serial Number low", "Wh",
            RidenRegistersRD50xx.REG_SERIAL_LOW);

    public static final DeviceRegister FIRMWARE_VERSION = new DeviceRegister("Firmware Version", null,
            RidenRegistersRD50xx.REG_FIRMWARE, 100);

    /**
     * Whitelist of known RD50xx series device IDs (e.g. 5005, 5010, 5020).
     *
     * <p>
     * Register 0x000B returns the 4-digit device ID on RD50xx units. Validating against this
     * whitelist prevents false detection on other hardware (such as Sinilink where register 0x000B
     * is the output timer minutes counter).
     * </p>
     */
    private static final Set<Integer> KNOWN_DEVICE_IDS = Set.of(
            5005, 5010, 5020
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
     * Verify that {@code Riden RD50xx} is present probing only the specified baud rates.
     *
     * <p>
     * Probes the device ID register (0x000B) first and validates it against {@link #KNOWN_DEVICE_IDS}.
     * If matched, reads the firmware version (0x0014) to confirm and complete detection.
     * </p>
     *
     * @param bauds list of baud rates to probe in order
     * @return this {@link RidenRD50xx} instance
     */
    public DC2DCConverter verifyDevicePresent(final List<Integer> bauds) {
        logger.info("Checking for Riden RD50xx device...");
        for (final Integer baud : bauds) {
            try {
                transport = new ModbusTransport(portName, baud);
                logger.debug("Trying baud rate {}", baud);

                // Probe Device ID register (0x000B)
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
                        this.device = String.format("RD%04d", deviceId);
                        logger.info("Detected Riden RD50xx (Model: {}, FW: {}) at {} baud.", this.device, fw, baud);
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
            logger.info("No Riden RD50xx detected.");
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
        double firmwareVersion = read(FIRMWARE_VERSION);
        return (int) firmwareVersion;
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
    public double getTemperatureCelsius() throws Exception {
        return read(TEMP_CELSIUS);
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
     * <p>Register {@link RidenRegistersRD50xx#REG_MODE}: 0 = CV, 1 = CC.</p>
     *
     * @return {@code true} for CV mode, {@code false} for CC mode
     * @throws Exception if reading the register fails
     */
    @Override
    public boolean isCvMode() throws Exception {
        return (readInt(MODE) == 0);
    }

    public void setPreset(final int preset) throws Exception {
        writeInt(PRESET, preset);
    }

    public int getPreset() throws Exception {
        return readInt(PRESET);
    }

    public int getDeviceId() throws Exception {
        return readInt(DEVICE_ID);
    }

    public double getAmpereHours() throws Exception {
        int ahHigh = readInt(AH_HIGH);
        int ahLow = readInt(AH_LOW);
        return (ahHigh * 100 + ahLow) / 100;
    }

    public double getWattHours() throws Exception {
        int whHigh = readInt(WH_HIGH);
        int whLow = readInt(WH_LOW);
        return (whHigh * 100 + whLow) / 100;
    }

    public int getTemperatureSignCelsius() throws Exception {
        return readInt(TEMP_SIGN_CELSIUS);
    }

    public double getSerial() throws Exception {
        int serialHigh = readInt(SERIAL_HIGH);
        int serialLow = readInt(SERIAL_LOW);
        return (serialHigh * 100 + serialLow) / 100;
    }

}

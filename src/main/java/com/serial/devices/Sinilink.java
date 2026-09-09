package com.serial.devices;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serial.device.DeviceRegister;
import com.serial.device.ModbusDevice;
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

    public static final DeviceRegister LOCK = new DeviceRegister("Keypad Lock", null,
            SinilinkRegisters.REG_KEYPAD_LOCK);

    /**
     * Whitelist mapping known Sinilink model register raw values to their model names.
     *
     * <p>
     * 22802 (0x5912) corresponds to XY6008. Validating against this whitelist avoids false positives
     * when probing non-Sinilink hardware where register 0x0016 holds unrelated data.
     * </p>
     */
    private static final Map<Integer, String> KNOWN_MODELS = Map.of(
            22802, "XY6008",
            22804, "XY6014",
            22805, "XY6020",
            19208, "XY5008"
    );

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
     * Probes the hardware model register (0x0016) first and validates it against {@link #KNOWN_MODELS}.
     * If matched, reads the firmware version (0x0017) to complete detection.
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
                    logger.debug("Model version register read at {} baud: {}", baud, modelVersion);
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
     * Set output voltage verified.
     * 
     * @param volts
     * @throws Exception
     */
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
     * Returns the regulation mode by reading {@link SinilinkRegisters#REG_MODE}.
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
        return (readInt(MODE) == 0);
    }

    public int getHardwareVersion() throws Exception {
        return readInt(FIRMWARE_VERSION);
    }

    public int getModelVersion() throws Exception {
        return readInt(MODEL_VERSION);
    }

}

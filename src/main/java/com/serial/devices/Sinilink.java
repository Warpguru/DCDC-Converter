package com.serial.devices;

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
     * Constructor.
     * 
     * @param portName of {@code SerialPort} used with Modbus protocol
     * @param slave    port to use
     */
    public Sinilink(final String portName, final byte slave) {
        super(portName, slave);
    }

    /**
     * Verify that {@code Sinilink} is present.
     * 
     * @return {@link Sinilink} instance or {@code Null}
     */
    public DC2DCConverter verifyDevicePresent() {
        logger.info("Checking for Sinilink device...");
        // Sinilink defaults to 115200 Baud
        for (final Integer baud : ModbusTransport.BAUDS) {
            try {
                // Initialize with current Baud rate
                transport = new ModbusTransport(portName, baud);
                logger.debug("Trying baud rate {}", baud);
                // Try firmware register
                try {
                    int firmwareVersion = getFirmwareVersion();
                    logger.info("Firmware version register read: {}", firmwareVersion);
                    if (firmwareVersion > 0 && firmwareVersion < 65535) {
                        logger.info("Device detected via firmware version register.");
                        if (firmwareVersion == 110) {
                            this.manufacturer = "Sinilink";
                            this.device = "XY6008";
                        }
                    }
                } catch (Exception e) {
                    // Probably wrong Baud rate
                    logger.debug("Firmware version read failed at {} baud: {}", baud, e.getMessage());
                }
                // Try hardware register
                try {
                    int modelVersion = getModelVersion();
                    logger.info("Model version register read: {}", modelVersion);
                    if (modelVersion > 0 && modelVersion < 65535) {
                        logger.info("Device detected via model version register.");
                        if (modelVersion == 22802) {
                            this.manufacturer = "Sinilink";
                            this.device = "XY6008";
                        }
                    }
                } catch (Exception e) {
                    // Probably wrong Baud rate
                    logger.debug("Model version read failed at {} baud: {}", baud, e.getMessage());
                }
                if (!isDeviceDetected()) {
                    // Probably still wrong Baud rate, retry with next Baud rate
                    transport.close();
                } else {
                    // Device detected
                    break;
                }
            } catch (Exception e) {
                logger.debug("Transport error at {} baud: {}", baud, e.getMessage());
                transport.close();
            }
        }
        // Check for detected device
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

    public int getHardwareVersion() throws Exception {
        return readInt(FIRMWARE_VERSION);
    }

    public int getModelVersion() throws Exception {
        return readInt(MODEL_VERSION);
    }

}

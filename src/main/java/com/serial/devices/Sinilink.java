package com.serial.devices;

import com.serial.device.DeviceRegister;
import com.serial.device.ModbusDevice;
import com.serial.device.SinilinkRegisters;
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

    public static final DeviceRegister VSET = new DeviceRegister("Voltage Setpoint", "V", SinilinkRegisters.REG_VSET, 100);

    public static final DeviceRegister ISET = new DeviceRegister("Current Setpoint", "A", SinilinkRegisters.REG_ISET, 1000);

    public static final DeviceRegister VOUT = new DeviceRegister("Output Voltage", "V", SinilinkRegisters.REG_VOUT, 100);

    public static final DeviceRegister IOUT = new DeviceRegister("Output Current", "A", SinilinkRegisters.REG_IOUT, 1000);

    public static final DeviceRegister POUT = new DeviceRegister("Output Power", "W", SinilinkRegisters.REG_POUT, 100);

    public static final DeviceRegister FIRMWARE_VERSION = new DeviceRegister("Firmware Version", null,
            SinilinkRegisters.REG_FIRMWARE);

    public static final DeviceRegister HARDWARE_VERSION = new DeviceRegister("Hardware Version", null,
            SinilinkRegisters.REG_HARDWARE);

    public static final DeviceRegister VIN = new DeviceRegister("Voltage Input", "V", SinilinkRegisters.REG_VIN);

    public static final DeviceRegister OUTPUT_ENABLE = new DeviceRegister("Output Enable", null,
            SinilinkRegisters.REG_OUTPUT_ENABLE);

    public static final DeviceRegister PROTECTION_STATE = new DeviceRegister("Protection Status", null,
            SinilinkRegisters.REG_PROTECTION_STATE);

    public static final DeviceRegister TEMP_CELSIUS = new DeviceRegister("Temperature Celsius", "°C",
            SinilinkRegisters.REG_TEMPERATURE_CELSIUS);

    public static final DeviceRegister MODEL = new DeviceRegister("Model", null, SinilinkRegisters.REG_MODEL);
    public static final DeviceRegister VERSION = new DeviceRegister("Model", null, SinilinkRegisters.REG_VERSION);

    
    public Sinilink(final ModbusTransport transport, final byte slave) {
        super(transport, slave);
    }

    /**
     * Verify that {@code Sinilink} is present.
     * 
     * @return true or false
     */
    public boolean verifyDevicePresent() {
        System.out.println("Checking for Sinilink device...");
        boolean devicePresent = false;
        // Try firmware register
        try {
            // int firmware = getFirmwareVersion();
            int firmware = getVersion();
            System.out.println("Firmware version register read: " + firmware);
            if (firmware > 0 && firmware < 10000) {
                System.out.println("Device detected via firmware register.");
                devicePresent = true;
                if (firmware == 1234) {
                    this.device = "Sinilink XY6008";
                }
            }
        } catch (Exception e) {
            System.out.println("Firmware register read failed: " + e.getMessage());
        }
        // Try hardware register
        try {
            // int hardware = getHardwareVersion();
            int hardware = getModel();
            System.out.println("Hardware version register read: " + hardware);
            if (hardware > 0 && hardware < 10000) {
                System.out.println("Device detected via hardware register.");
                devicePresent = true;
                this.device = "Sinilink";
            }
        } catch (Exception e) {
            System.out.println("Hardware register read failed: " + e.getMessage());
        }
        if (devicePresent == false) {
            System.out.println("No Sinilink detected.");
        } else {
            // Device detected
        }
        return devicePresent;
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
        writeInt(OUTPUT_ENABLE, (on ? 1 : 0));
    }

    @Override
    public boolean getOutput() throws Exception {
        return (readInt(OUTPUT_ENABLE) > 0 ? true : false);
    }

    @Override
    public int getFirmwareVersion() throws Exception {
        return readInt(FIRMWARE_VERSION);
    }

    @Override
    public void setProtectionState(boolean on) throws Exception {
        writeInt(PROTECTION_STATE, (on ? 1 : 0));
    }

    @Override
    public boolean getProtectionState() throws Exception {
        return (readInt(PROTECTION_STATE) > 0 ? true : false);
    }

    @Override
    public double getTemperatureCelsius() throws Exception {
        return read(TEMP_CELSIUS);
    }

    public int getHardwareVersion() throws Exception {
        return readInt(HARDWARE_VERSION);
    }

    public int getModel() throws Exception {
        return readInt(MODEL);
    }
    
    public int getVersion() throws Exception {
        return readInt(VERSION);
    }
    
}

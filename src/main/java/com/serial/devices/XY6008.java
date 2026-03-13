package com.serial.devices;

import com.serial.device.DeviceRegister;
import com.serial.device.ModbusDevice;
import com.serial.device.XY6008Registers;
import com.serial.modbus.ModbusTransport;

public class XY6008 extends ModbusDevice implements DC2DCConverter {

    public static final DeviceRegister VSET = new DeviceRegister("Voltage Setpoint", XY6008Registers.REG_VSET, 100);

    public static final DeviceRegister ISET = new DeviceRegister("Current Setpoint", XY6008Registers.REG_ISET, 1000);

    public static final DeviceRegister VOUT = new DeviceRegister("Output Voltage", XY6008Registers.REG_VOUT, 100);

    public static final DeviceRegister IOUT = new DeviceRegister("Output Current", XY6008Registers.REG_IOUT, 1000);

    public static final DeviceRegister POUT = new DeviceRegister("Output Power", XY6008Registers.REG_POUT, 100);

    public static final DeviceRegister FIRMWARE_VERSION = new DeviceRegister("Firmware Version", XY6008Registers.REG_FIRMWARE);

    public static final DeviceRegister HARDWARE_VERSION = new DeviceRegister("Hardware Version", XY6008Registers.REG_HARDWARE);

    public static final DeviceRegister VIN = new DeviceRegister("Voltage Input", XY6008Registers.REG_VIN);
    
    public static final DeviceRegister OUTPUT_ENABLE = new DeviceRegister("Output Enable", XY6008Registers.REG_OUTPUT_ENABLE);

    public static final DeviceRegister PROTECTION_STATE = new DeviceRegister("Protection Status", XY6008Registers.REG_PROTECTION_STATE);

    public static final DeviceRegister TEMP_CELSIUS = new DeviceRegister("Temperature Celsius",
            XY6008Registers.REG_TEMPERATURE_CELSIUS);

    public XY6008(final ModbusTransport transport, final byte slave) {
        super(transport, slave);
    }

    /**
     * Verify that {@code XY6008} is present.
     * 
     * @return true or false
     */
    public boolean verifyDevicePresent() {
        System.out.println("Checking for XY6008 device...");
        boolean devicePresent = false;
        // Try firmware register
        try {
            int firmware = getFirmwareVersion();
            System.out.println("Firmware version register read: " + firmware);
            if (firmware >= 0 && firmware < 10000) {
                System.out.println("Device detected via firmware register.");
                devicePresent = true;
            }
        } catch (Exception e) {
            System.out.println("Firmware register read failed: " + e.getMessage());
        }
        // Try hardware register
        try {
            int hardware = getHardwareVersion();
            System.out.println("Hardware version register read: " + hardware);
            if (hardware >= 0 && hardware < 10000) {
                System.out.println("Device detected via hardware register.");
                devicePresent = true;
            }
        } catch (Exception e) {
            System.out.println("Hardware register read failed: " + e.getMessage());
        }
        if (devicePresent == false) {
            System.out.println("No XY6008 detected.");
        } else {
            // Device detected
        }
        return devicePresent;
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
    
}

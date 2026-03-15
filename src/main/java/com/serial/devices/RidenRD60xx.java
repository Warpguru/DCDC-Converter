package com.serial.devices;

import com.serial.device.DeviceRegister;
import com.serial.device.ModbusDevice;
import com.serial.device.RidenRegistersRD60xx;
import com.serial.modbus.ModbusTransport;

/**
 * {@code RidenRD60xx} (e.g. {@code RD6030}) {@code Modbus} to {@code TTL} 3.3V {@code serial} connection.
 * 
 * <ul>
 * <li>RidenRD60xx Black:  → Gnd
 * <li>RidenRD60xx ?:  → TxD
 * <li>RidenRD60xx ?: → RxD
 * <li>RidenRD60xx Red:    → NC (5V)
 * </ul>
 */
public class RidenRD60xx extends ModbusDevice implements DC2DCConverter {

    public static final DeviceRegister DEVICE_ID = new DeviceRegister("Model Identification", null, RidenRegistersRD60xx.REG_DEVICE_ID);

    public static final DeviceRegister FIRMWARE_VERSION = new DeviceRegister("Firmware Version", null, RidenRegistersRD60xx.REG_FIRMWARE, 100);

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

    public static final DeviceRegister OUTPUT_ENABLE = new DeviceRegister("Output Enable", null, RidenRegistersRD60xx.REG_OUTPUT_ENABLE);

    public static final DeviceRegister PRESET = new DeviceRegister("Preset Selector", "Mx", RidenRegistersRD60xx.REG_PRESET);

    public static final DeviceRegister IRANGE = new DeviceRegister("Current Range", "A", RidenRegistersRD60xx.REG_CURRENT_RANGE);

    public RidenRD60xx(ModbusTransport transport, byte slave) {
        super(transport, slave);
    }

    /**
     * Verify that {@code RidenRD60xx} is present.
     * 
     * @return true or false
     */
    public boolean verifyDevicePresent() {
        System.out.println("Checking for RidenRD60xx device...");
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
        // Try device Id
        try {
            int deviceId = getDeviceId();
            System.out.println("Device Id register read: " + deviceId);
            if (deviceId >= 0 && deviceId < 10000) {
                System.out.println("Device detected via hardware register.");
                devicePresent = true;
            }
        } catch (Exception e) {
            System.out.println("Hardware register read failed: " + e.getMessage());
        }
        if (devicePresent == false) {
            System.out.println("No RidenRD60xx detected.");
        } else {
            // Device detected
        }
        return devicePresent;
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

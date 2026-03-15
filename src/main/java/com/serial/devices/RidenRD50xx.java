package com.serial.devices;

import com.serial.device.DeviceRegister;
import com.serial.device.ModbusDevice;
import com.serial.device.RidenRegistersRD50xx;
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

    public static final DeviceRegister VSET = new DeviceRegister("Voltage Setpoint", "V", RidenRegistersRD50xx.REG_VSET, 100);

    public static final DeviceRegister ISET = new DeviceRegister("Current Setpoint", "A", RidenRegistersRD50xx.REG_ISET, 1000);

    public static final DeviceRegister VOUT = new DeviceRegister("Output Voltage", "V", RidenRegistersRD50xx.REG_VOUT, 100);

    public static final DeviceRegister IOUT = new DeviceRegister("Output Current", "A", RidenRegistersRD50xx.REG_IOUT, 1000);

    public static final DeviceRegister POUT = new DeviceRegister("Output Power", "W", RidenRegistersRD50xx.REG_POUT, 1000);

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

    public RidenRD50xx(ModbusTransport transport, byte slave) {
        super(transport, slave);
    }

    /**
     * Verify that {@code RidenRD60xx} is present.
     * 
     * @return true or false
     */
    public boolean verifyDevicePresent() {
        System.out.println("Checking for RidenRD50xx device...");
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
            System.out.println("No RidenRD50xx detected.");
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
        double firmwareVersion = read(FIRMWARE_VERSION);
        return (int) firmwareVersion;
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

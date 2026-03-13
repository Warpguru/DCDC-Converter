package com.serial;

import com.serial.TestXY6008.XY6008Registers;
import com.serial.util.ModbusTransport;

/**
 * High level Java driver for the XY6008Old power supply.
 */
@Deprecated
public class XY6008Old {

    /** Retry counter. */
    public static final int MAX_RETRY = 3;
    
    /** {@link ModbusTransport} set. */
    private final ModbusTransport transport;

    /**
     * Constructor.
     * 
     * @param transport to use
     */
    public XY6008Old(final ModbusTransport transport) {
        this.transport = transport;
    }

    /**
     * Verify that {@code XY6008Old} is present.
     * 
     * @return true or false
     */
    public boolean verifyDevicePresent() {
        System.out.println("Checking for XY6008Old device...");
        boolean devicePresent = false;
        // Try firmware register
        try {
            int firmware = transport.readRegister(XY6008Registers.REG_FIRMWARE);
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
            int hardware = transport.readRegister(XY6008Registers.REG_HARDWARE);
            System.out.println("Hardware version register read: " + hardware);
            if (hardware >= 0 && hardware < 10000) {
                System.out.println("Device detected via hardware register.");
                devicePresent = true;
            }
        } catch (Exception e) {
            System.out.println("Hardware register read failed: " + e.getMessage());
        }
        if (devicePresent == false) {
            System.out.println("No XY6008Old detected.");
        } else {
            // Device detected
        }
        return devicePresent;
    }

    /**
     * Sets the voltage setpoint of the XY6008Old.
     *
     * <p>
     * The device expects the voltage value as an integer with a scaling factor of 100.
     * </p>
     *
     * <p>
     * Example conversions:
     * </p>
     *
     * <pre>
     * 5.00 V -> 500
     * 3.30 V -> 330
     * 12.00 V -> 1200
     * </pre>
     *
     * @param volts desired output voltage in volts
     * @throws Exception if communication fails
     */
    public void setOutputVoltage(final double volts) throws Exception {
        int raw = (int) Math.round(volts * 100);
        transport.writeRegister(XY6008Registers.REG_VSET, raw);
    }

    /**
     * @see XY6008Old#setOutputVoltage(double)
     * 
     * @param volts desired output voltage in volts
     * @throws Exception if communication fails
     */
    public void setOutputVoltageVerified(final double volts) throws Exception {
        int raw = (int) Math.round(volts * 100);
        System.out.println("\nSetting voltage → " + volts + " V");
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            transport.writeRegister(XY6008Registers.REG_VSET, raw);
            Thread.sleep(200);
            int readSet = transport.readRegister(XY6008Registers.REG_VSET);
            int readOut = transport.readRegister(XY6008Registers.REG_VOUT);
            double setV = readSet / 100.0;
            double outV = readOut / 100.0;
            System.out.println("Attempt " + attempt + " SET=" + setV + "V OUT=" + outV + "V");
            if (readSet == raw) {
                System.out.println("Voltage verified");
                return;
            }
        }
        throw new RuntimeException("Failed to set voltage");
    }
    
    /**
     * Returns the configured voltage setpoint.
     *
     * @return voltage in volts
     * @throws Exception if communication fails
     */
    public double getOutputVoltage() throws Exception {
        int raw = transport.readRegister(XY6008Registers.REG_VOUT);
        return raw / 100.0;
    }

    /**
     * Sets the current limit of the power supply.
     *
     * <p>
     * The XY6008Old expects current values scaled by 1000.
     * </p>
     *
     * <pre>
     * 1.000 A -> 1000
     * 2.500 A -> 2500
     * </pre>
     *
     * @param amps current limit in amperes
     * @throws Exception if communication fails
     */
    public void setOutputCurrent(final double amps) throws Exception {
        int raw = (int) Math.round(amps * 1000);
        transport.writeRegister(XY6008Registers.REG_ISET, raw);
    }

    /**
     * @see XY6008Old#setOutputCurrent(double)
     * 
     * @param amps current limit in amperes
     * @throws Exception if communication fails
     */
    public void setOutputCurrentVerified(final double amps) throws Exception {
        int raw = (int) Math.round(amps * 100);
        System.out.println("\nSetting current → " + amps + " A");
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            transport.writeRegister(XY6008Registers.REG_ISET, raw);
            Thread.sleep(200);
            int readSet = transport.readRegister(XY6008Registers.REG_ISET);
            int readOut = transport.readRegister(XY6008Registers.REG_IOUT);
            double setA = readSet / 100.0;
            double outA = readOut / 100.0;
            System.out.println("Attempt " + attempt + " SET=" + setA + "A OUT=" + outA + "A");
            if (readSet == raw) {
                System.out.println("Current verified");
                return;
            }
        }
        throw new RuntimeException("Failed to set voltage");
    }
    
    /**
     * Reads the measured output current.
     *
     * @return current in amperes
     * @throws Exception if communication fails
     */
    public double getOutputCurrent() throws Exception {
        int raw = transport.readRegister(XY6008Registers.REG_IOUT);
        return raw / 1000.0;
    }

    /**
     * Reads the measured output power.
     *
     * @return power in watts
     * @throws Exception if communication fails
     */
    public double getOutputPower() throws Exception {
        int raw = transport.readRegister(XY6008Registers.REG_POUT);
        return raw / 100.0;
    }
    
    /**
     * Reads the firmware version of the device.
     *
     * @return firmware version number
     * @throws Exception if communication fails
     */
    public int getFirmwareVersion() throws Exception {
        return transport.readRegister(XY6008Registers.REG_FIRMWARE);
    }

    /**
     * Reads the hardware version of the device.
     *
     * @return hardware version number
     * @throws Exception if communication fails
     */
    public int getHardwareVersion() throws Exception {
        return transport.readRegister(XY6008Registers.REG_HARDWARE);
    }    

    /**
     * Reads the input voltage supplied to the power module.
     *
     * @return input voltage in volts
     * @throws Exception if communication fails
     */
    public double getInputVoltage() throws Exception {
        int raw = transport.readRegister(XY6008Registers.REG_VIN);
        return raw / 100.0;
    }

    /**
     * Enables or disables the power supply output.
     *
     * <p>
     * Register values:
     * </p>
     *
     * <ul>
     * <li>0 = output disabled</li>
     * <li>1 = output enabled</li>
     * </ul>
     *
     * @param enabled true to enable output
     * @throws Exception if communication fails
     */
    public void setOutputEnabled(final boolean enabled) throws Exception {
        transport.writeRegister(XY6008Registers.REG_OUTPUT_ENABLE, enabled ? 1 : 0);
    }

    /**
     * Returns the current output enable state.
     *
     * @return true if output is enabled
     * @throws Exception if communication fails
     */
    public boolean isOutputEnabled() throws Exception {
        int val = transport.readRegister(XY6008Registers.REG_OUTPUT_ENABLE);
        return val != 0;
    }

    /**
     * Returns the protection state of the device.
     *
     * Possible values:
     *
     * <ul>
     * <li>0 = normal</li>
     * <li>1 = over-voltage protection</li>
     * <li>2 = over-current protection</li>
     * <li>3 = over-power protection</li>
     * <li>4 = over-temperature protection</li>
     * </ul>
     *
     * @return protection state code
     * @throws Exception if communication fails
     */
    public int getProtectionState() throws Exception {
        return transport.readRegister(XY6008Registers.REG_PROTECTION_STATE);
    }
    
    /**
     * Returns the internal device temperature.
     *
     * <p>
     * The value is scaled by 10.
     * </p>
     *
     * <pre>
     * 350 -> 35.0 °C
     * </pre>
     *
     * @return temperature in °C
     * @throws Exception if communication fails
     */
    public double getTemperature() throws Exception {
        int raw = transport.readRegister(XY6008Registers.REG_TEMPERATURE);
        return raw / 10.0;
    }
    
}

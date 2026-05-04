package com.serial.device;

import com.serial.devices.RidenRD60xx;

/**
 * Register map for {@code Sinilink} programmable DC power supply.
 *
 * <p>
 * The Sinilink exposes a smaller {@code Modbus} register map than the {@link RidenRD60xx} It provides no information such as serial numbers,
 * energy counters, CV/CC state, keypad lock, and battery mode registers.
 * </p>
 *
 * <p>
 * Scaling factors are typically identical to other Sinilink supplies:
 * </p>
 *
 * <ul>
 * <li>Voltage values: raw / 100 = volts</li>
 * <li>Current values: raw / 1000 = amperes</li>
 * <li>Power values: raw / 100 = watts</li>
 * </ul>
 */
public final class SinilinkRegisters {

    /**
     * Constructor.
     */
    private SinilinkRegisters() {
    }

    /**
     * Voltage setpoint register.
     *
     * Read/Write
     *
     * Scaling: raw / 100 = volts
     *
     * Examples:
     * 
     * <pre>
     * 5.00V -> 500 
     * 3.30V -> 330 
     * 12.00V -> 1200
     * </pre>
     *
     * Typical range: 0 – 6000 (0.00V – 60.00V)
     */
    public static final int REG_VSET = 0x0000;

    /**
     * Current setpoint register.
     *
     * Read/Write
     *
     * Scaling: raw / 1000 = amperes
     *
     * Examples:
     * 
     * <pre>
     * 1.000A -> 1000
     * 2.500A -> 2500
     * 5.000A -> 5000
     * </pre>
     * 
     * s
     *
     * Typical range: 0 – 8000 (0 – 8A)
     */
    public static final int REG_ISET = 0x0001;

    /**
     * Measured output voltage.
     *
     * Read only.
     *
     * Scaling: raw / 100 = volts
     *
     * Example:
     * 
     * <pre>
     * 503 -> 5.03V
     * </pre>
     */
    public static final int REG_VOUT = 0x0002;

    /**
     * Measured output current.
     *
     * Read only.
     *
     * Scaling: raw / 1000 = amperes
     *
     * Example:
     * 
     * <pre>
     * 1520 -> 1.520A
     * </pre>
     */
    public static final int REG_IOUT = 0x0003;

    /**
     * Measured output power.
     *
     * Read only.
     *
     * Scaling: raw / 100 = watts
     *
     * Example:
     * 
     * <pre>
     * 123 -> 1.23W
     * </pre>
     */
    public static final int REG_POUT = 0x0004;

    /**
     * Firmware version register.
     *
     * Read only.
     *
     * Used for device identification.
     *
     * Typical values: 10 – 100
     *
     * Used to confirm that the attached serial device is an Sinilink compatible unit.
     */
    public static final int REG_FIRMWARE = 0x0005;

    /**
     * Hardware version register.
     *
     * Read only.
     *
     * Alternative device identification register to confirm that the attached serial device is an Sinilink compatible unit.
     */
    public static final int REG_HARDWARE = 0x0006;

    /**
     * Input voltage measurement.
     *
     * Read only.
     *
     * Voltage supplied to the converter input.
     *
     * Scaling: raw / 100 = volts
     */
    public static final int REG_VIN = 0x0007;

    /**
     * Output enable control.
     *
     * Read/Write.
     *
     * Values:
     * <ul>
     * <li>0 -> output OFF
     * <li>1 -> output ON
     * </ul>
     *
     * Example:
     * 
     * <pre>
     * writeRegister(REG_OUTPUT_ENABLE, 1);
     * </pre>
     */
    //TODO: does not work: public static final int REG_OUTPUT_ENABLE = 0x0008;
    public static final int REG_OUTPUT_ENABLE = 0x0012;

    /**
     * Device protection state.
     *
     * Read only.
     *
     * Values:
     * <ul>
     * <li>0 -> normal
     * <li>1 -> over-voltage protection
     * <li>2 -> over-current protection
     * <li>3 -> over-power protection
     * <li>4 -> over-temperature protection
     * </ul>
     */
    public static final int REG_PROTECTION_STATE = 0x0009;

    /**
     * Device temperature.
     *
     * Read only.
     *
     * Scaling: raw / 10 = degrees Celsius
     *
     * Example:
     * 
     * <pre>
     * 350 -> 35.0°C
     * </pre>
     */
    public static final int REG_TEMPERATURE_CELSIUS = 0x000A;

    //TODO: This IS partly incorrect (verified that switch output is 0x0012), e.g. 0x0019 returns 6 which meansh 115200 Baud
    //TODO: Probably XY6020 registers are compatible: https://github.com/creepystefan/esphome-XY6020/blob/main/doc/XY6020L-Modbus-Interface.pdf
    
    public static final int REG_MODEL = 0x0016;
    public static final int REG_VERSION = 0x0017;
}

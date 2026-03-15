package com.serial.device;

import com.serial.devices.Sinilink;

/**
 * Register map for {@code Riden} programmable DC power supply.
 *
 * <p>
 * The Riden exposes a larger {@code Modbus} register map than the {@link Sinilink}. It provides additional information such as serial numbers,
 * energy counters, CV/CC state, keypad lock, and battery mode registers.
 * </p>
 *
 * <p>
 * Scaling factors are typically identical to other Riden supplies:
 * </p>
 *
 * <ul>
 * <li>Voltage values: raw / 100 = volts</li>
 * <li>Current values: raw / 1000 = amperes</li>
 * <li>Power values: raw / 100 = watts</li>
 * </ul>
 */
public final class RidenRegisters {

    /**
     * Constructor.
     */
    private RidenRegisters() {
    }

    /**
     * Device model identification.
     *
     * Read only.
     *
     * Example values:
     *
     * <pre>
     * 6030 -> Riden
     * </pre>
     */
    public static final int REG_DEVICE_ID = 0x0000;

    /**
     * Serial number high word.
     *
     * Read only.
     */
    public static final int REG_SERIAL_HIGH = 0x0001;

    /**
     * Serial number low word.
     *
     * Read only.
     */
    public static final int REG_SERIAL_LOW = 0x0002;

    /**
     * Firmware version.
     *
     * Read only.
     */
    public static final int REG_FIRMWARE = 0x0003;

    /**
     * Internal temperature sign.
     *
     * Read only.
     *
     * Values:
     * <ul>
     * <li>0 -> positive</li>
     * <li>1 -> negative</li>
     * </ul>
     */
    public static final int REG_TEMP_SIGN_CELSIUS = 0x0004;

    /**
     * Internal temperature value.
     *
     * Read only.
     *
     * Units: degrees Celsius.
     */
    public static final int REG_TEMP_CELSIUS = 0x0005;

    /**
     * Temperature sign in Fahrenheit.
     *
     * Read only.
     */
    public static final int REG_TEMP_SIGN_FAHRENHEIT = 0x0006;

    /**
     * Temperature value in Fahrenheit.
     *
     * Read only.
     */
    public static final int REG_TEMP_FAHRENHEIT = 0x0007;

    /**
     * Voltage setpoint.
     *
     * Read/Write
     *
     * Scaling: raw / 100 = volts
     *
     * Example:
     *
     * <pre>
     * 5.00V -> 500
     * 12.00V -> 1200
     * </pre>
     */
    public static final int REG_VSET = 0x0008;

    /**
     * Current setpoint.
     *
     * Read/Write
     *
     * Scaling: raw / 1000 = amperes
     */
    public static final int REG_ISET = 0x0009;

    /**
     * Measured output voltage.
     *
     * Read only.
     *
     * Scaling: raw / 100 = volts
     */
    public static final int REG_VOUT = 0x000A;

    /**
     * Measured output current.
     *
     * Read only.
     *
     * Scaling: raw / 1000 = amperes
     */
    public static final int REG_IOUT = 0x000B;

    /**
     * Accumulated ampere-hours.
     *
     * Read only.
     */
    public static final int REG_AH = 0x000C;

    /**
     * Measured output power.
     *
     * Read only.
     *
     * Scaling: raw / 100 = watts
     */
    public static final int REG_POUT = 0x000D;

    /**
     * Input voltage measurement.
     *
     * Read only.
     *
     * Scaling: raw / 100 = volts
     */
    public static final int REG_VIN = 0x000E;

    /**
     * Keypad lock state.
     *
     * Read/Write
     *
     * Values:
     * <ul>
     * <li>0 -> unlocked</li>
     * <li>1 -> locked</li>
     * </ul>
     */
    public static final int REG_KEYPAD_LOCK = 0x000F;

    /**
     * Protection status.
     *
     * Read only.
     *
     * Values:
     * <ul>
     * <li>0 -> normal</li>
     * <li>1 -> OVP</li>
     * <li>2 -> OCP</li>
     * </ul>
     */
    public static final int REG_PROTECTION_STATE = 0x0010;

    /**
     * Regulation mode.
     *
     * Read only.
     *
     * Values:
     * <ul>
     * <li>0 -> CV (constant voltage)</li>
     * <li>1 -> CC (constant current)</li>
     * </ul>
     */
    public static final int REG_MODE = 0x0011;

    /**
     * Output enable control.
     *
     * Read/Write
     *
     * Values:
     * <ul>
     * <li>0 -> output OFF</li>
     * <li>1 -> output ON</li>
     * </ul>
     */
    public static final int REG_OUTPUT_ENABLE = 0x0012;

    /**
     * Preset memory selector.
     *
     * Read/Write
     *
     * Values typically 1–9.
     */
    public static final int REG_PRESET = 0x0013;

    /**
     * Current range selection (model dependent).
     *
     * Example values:
     *
     * <ul>
     * <li>0 -> low current range</li>
     * <li>1 -> high current range</li>
     * </ul>
     */
    public static final int REG_CURRENT_RANGE = 0x0014;

}

package com.serial.device;

import com.serial.devices.Sinilink;

/**
 * Register map for the older {@code RidenRD50xx} programmable DC power supply (e.g. {@code RD5020)}.
 *
 * <p>
 * The RidenRD60xx exposes a larger {@code Modbus} register map than the {@link Sinilink}. It provides additional information such as
 * serial numbers, energy counters, CV/CC state, keypad lock, and battery mode registers.
 * </p>
 *
 * <p>
 * Scaling factors are typically identical to other RidenRD60xx supplies:
 * </p>
 *
 * <ul>
 * <li>Voltage values: raw / 100 = volts</li>
 * <li>Current values: raw / 1000 = amperes</li>
 * <li>Power values: raw / 100 = watts</li>
 * </ul>
 */
public final class RidenRegistersRD50xx {

    /**
     * Constructor.
     */
    private RidenRegistersRD50xx() {
    }

    /*
     * Runtime registers
     */

    /** Voltage setpoint (V * 100) */
    public static final int REG_VSET = 0x0000;

    /** Current setpoint (A * 1000) */
    public static final int REG_ISET = 0x0001;

    /** Measured output voltage (V * 100) */
    public static final int REG_VOUT = 0x0002;

    /** Measured output current (A * 1000) */
    public static final int REG_IOUT = 0x0003;

    /** Output power (W * 100) */
    public static final int REG_POUT = 0x0004;

    /** Input voltage (V * 100) */
    public static final int REG_VIN = 0x0005;

    /** Key lock status (0=unlocked, 1=locked) */
    public static final int REG_KEYPAD_LOCK = 0x0006;

    /** Protection status */
    public static final int REG_PROTECTION_STATE = 0x0007;

    /** Regulation mode (0=CV, 1=CC) */
    public static final int REG_MODE = 0x0008;

    /** Output enable (0=OFF, 1=ON) */
    public static final int REG_OUTPUT_ENABLE = 0x0009;

    /** Active preset memory slot */
    public static final int REG_PRESET = 0x000A;

    /*
     * System information
     */

    /** Device model (e.g. 5020) */
    public static final int REG_DEVICE_ID = 0x000B;

    /*
     * Energy counters (32-bit values)
     */

    /** Accumulated Ah high word */
    public static final int REG_AH_HIGH = 0x000C;

    /** Accumulated Ah low word */
    public static final int REG_AH_LOW = 0x000D;

    /** Accumulated Wh high word */
    public static final int REG_WH_HIGH = 0x000E;

    /** Accumulated Wh low word */
    public static final int REG_WH_LOW = 0x000F;

    /*
     * Temperature / device info
     */

    /** Internal temperature sign (0=+,1=-) */
    public static final int REG_TEMP_SIGN_CELSIUS = 0x0010;

    /** Internal temperature value */
    public static final int REG_TEMP_CELSIUS = 0x0011;

    /** Serial number high */
    public static final int REG_SERIAL_HIGH = 0x0012;

    /** Serial number low */
    public static final int REG_SERIAL_LOW = 0x0013;

    /** Firmware version (value /100) */
    public static final int REG_FIRMWARE = 0x0014;

}

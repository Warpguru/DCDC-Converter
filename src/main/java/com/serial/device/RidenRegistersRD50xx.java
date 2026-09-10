package com.serial.device;

import com.serial.devices.Sinilink;

/**
 * Register map for the Ruideng {@code DPS50xx} series programmable DC power supply (e.g. {@code DPS5020}).
 *
 * <p>
 * Source: <em>Hangzhou Rui Deng Technology Co., Ltd - DPS5020 Digital power communication protocol V1.2</em>
 * ({@code doc/DPS5020 communication protocol V1.2.pdf}).
 * </p>
 *
 * <p>
 * The DPS50xx register layout is distinct from both the {@link Sinilink} and the RD60xx series.
 * Notable differences from the RD60xx:
 * </p>
 *
 * <ul>
 * <li>Register 0x0000 is {@code U-SET} (voltage setpoint) - not a model ID.</li>
 * <li>Register 0x000B is {@code MODEL} (product number) - model ID lives here, not at 0x0000.</li>
 * <li>Register 0x000C is {@code VERSON} (firmware version) - not an energy counter.</li>
 * <li>Register 0x000A is {@code B_LED} (backlight level, 0–5) - not a preset selector.</li>
 * <li>Register 0x0023 is {@code EXTRACT_M} (data-set recall, write 0–9).</li>
 * <li>Energy counters, temperature registers, and serial-number registers present in the
 *     RD60xx do <strong>not</strong> exist in the DPS50xx register map.</li>
 * </ul>
 *
 * <p>
 * Scaling factors (2 decimal places throughout):
 * </p>
 *
 * <ul>
 * <li>Voltage: raw / 100 = volts</li>
 * <li>Current: raw / 100 = amperes</li>
 * <li>Power:   raw / 100 = watts</li>
 * </ul>
 */
public final class RidenRegistersRD50xx {

    /**
     * Constructor.
     */
    private RidenRegistersRD50xx() {
    }

    // -------------------------------------------------------------------------
    // Runtime registers (0x0000 – 0x000C)
    // -------------------------------------------------------------------------

    /** U-SET - Voltage setpoint. 2 dp. R/W. Scale: raw / 100 = volts. */
    public static final int REG_VSET = 0x0000;

    /** I-SET - Current setpoint. 2 dp. R/W. Scale: raw / 100 = amperes. */
    public static final int REG_ISET = 0x0001;

    /** UOUT - Measured output voltage. 2 dp. R. Scale: raw / 100 = volts. */
    public static final int REG_VOUT = 0x0002;

    /** IOUT - Measured output current. 2 dp. R. Scale: raw / 100 = amperes. */
    public static final int REG_IOUT = 0x0003;

    /** POWER - Measured output power. 2 dp. R. Scale: raw / 100 = watts. */
    public static final int REG_POUT = 0x0004;

    /** UIN - Input voltage. 2 dp. R. Scale: raw / 100 = volts. */
    public static final int REG_VIN = 0x0005;

    /** LOCK - Keypad lock. 0 = unlocked, 1 = locked. R/W. */
    public static final int REG_KEYPAD_LOCK = 0x0006;

    /**
     * PROTECT - Protection state. R.
     *
     * <ul>
     * <li>0 = normal</li>
     * <li>1 = OVP (over-voltage)</li>
     * <li>2 = OCP (over-current)</li>
     * <li>3 = OPP (over-power)</li>
     * </ul>
     */
    public static final int REG_PROTECTION_STATE = 0x0007;

    /** CVCC - Regulation mode. 0 = CV (constant voltage), 1 = CC (constant current). R. */
    public static final int REG_MODE = 0x0008;

    /** ONOFF - Output switch. 0 = OFF, 1 = ON. R/W. */
    public static final int REG_OUTPUT_ENABLE = 0x0009;

    /** B_LED - Backlight brightness level (0 = darkest, 5 = brightest). R/W. */
    public static final int REG_BACKLIGHT = 0x000A;

    /** MODEL - Product model number (e.g. 5020 for DPS5020). R. */
    public static final int REG_DEVICE_ID = 0x000B;

    /**
     * VERSON - Firmware version. R.
     *
     * <p>Raw register value / 10.0 = firmware version (e.g. {@code 17} = v1.7).
     * Several DPS5020 factory batches return {@code 0} - this is a known hardware limitation.</p>
     */
    public static final int REG_FIRMWARE = 0x000C;

    // -------------------------------------------------------------------------
    // Data-set control register
    // -------------------------------------------------------------------------

    /**
     * EXTRACT_M - Data-set recall. W.
     *
     * <p>Writing a value 0–9 recalls the corresponding preset data set (M0–M9) into
     * the active working registers.</p>
     */
    public static final int REG_EXTRACT_M = 0x0023;

}

package com.serial.device;

import com.serial.devices.Wuzhi;
import com.serial.modbus.ModbusFunctionCodes;

/**
 * Register map for {@code Wuzhi ZK-series} programmable DC buck power supplies (e.g. {@code ZK-6522C}).
 *
 * <p>
 * The Wuzhi ZK-series exposes a {@code Modbus RTU} register map that is address-for-address identical to the
 * {@link SinilinkRegisters} layout. All registers are 16-bit holding registers (Modbus function
 * {@link ModbusFunctionCodes#READ_HOLDING_REGISTERS} (0x03) /
 * {@link ModbusFunctionCodes#WRITE_SINGLE_REGISTER} (0x06) /
 * {@link ModbusFunctionCodes#WRITE_MULTIPLE_REGISTERS} (0x10)). Addresses are zero-based.
 * </p>
 *
 * <p>
 * <strong>Key scaling difference vs. Sinilink XY6008-class:</strong> current registers ({@link #REG_ISET},
 * {@link #REG_IOUT}) use scale {@code 100} (10 mA resolution, 2 decimal places) - not scale {@code 1000} as on the
 * Sinilink XY6008/XY6014. This matches the XY6020L / XYH3680 class. Using the wrong scale results in a 10×
 * current error. See {@link Wuzhi} driver and {@code doc/Wuzhi.md}.
 * </p>
 *
 * <p>
 * Scaling summary:
 * </p>
 *
 * <ul>
 * <li>Voltage values: raw / 100 = volts</li>
 * <li>Current values: raw / 100 = amperes (10 mA resolution)</li>
 * <li>Power values: raw / 100 = watts</li>
 * <li>Temperature: raw / 10 = °C</li>
 * </ul>
 *
 * <p>
 * Memory model:
 * </p>
 *
 * <ul>
 * <li>M0 (0x0050) = active working set</li>
 * <li>M1–M10 = stored presets (11 groups total; one more than Sinilink/Riden)</li>
 * <li>Recalling a preset copies it into M0</li>
 * <li>Registers 0x0000/0x0001 mirror 0x0050/0x0051</li>
 * </ul>
 *
 * @see <a href="https://www.scribd.com/document/921070101/XY6020L-Modbus-Interface">XY6020L Modbus Interface
 *      Documentation - closest confirmed register map</a>
 * @see Wuzhi
 */
public final class WuzhiRegisters {

    /**
     * Constructor.
     */
    private WuzhiRegisters() {
    }

    /**
     * Voltage setpoint register.
     *
     * <p>
     * Read/Write
     * </p>
     *
     * <p>
     * Scaling: raw / 100 = volts
     * </p>
     *
     * <pre>
     * 5.00V  -&gt; 500
     * 12.00V -&gt; 1200
     * 65.00V -&gt; 6500
     * </pre>
     *
     * <p>
     * Typical range: 0 – 6500 (0.00 V – 65.00 V)
     * </p>
     */
    public static final int REG_VSET = 0x0000;

    /**
     * Current setpoint register.
     *
     * <p>
     * Read/Write
     * </p>
     *
     * <p>
     * Scaling: raw / 100 = amperes (10 mA resolution).
     * </p>
     *
     * <pre>
     * 1.00A  -&gt; 100
     * 2.50A  -&gt; 250
     * 22.00A -&gt; 2200
     * </pre>
     *
     * <p>
     * Typical range: 0 – 2200 (0.00 A – 22.00 A)
     * </p>
     *
     * <p>
     * <strong>Note:</strong> scale is {@code 100} (10 mA resolution), not {@code 1000} as used on the Sinilink
     * XY6008/XY6014 class. Using scale 1000 would produce a 10× current error.
     * </p>
     */
    public static final int REG_ISET = 0x0001;

    /**
     * Measured output voltage.
     *
     * <p>
     * Read only.
     * </p>
     *
     * <p>
     * Scaling: raw / 100 = volts
     * </p>
     *
     * <pre>
     * 503 -&gt; 5.03 V
     * </pre>
     */
    public static final int REG_VOUT = 0x0002;

    /**
     * Measured output current.
     *
     * <p>
     * Read only.
     * </p>
     *
     * <p>
     * Scaling: raw / 100 = amperes (10 mA resolution).
     * </p>
     *
     * <pre>
     * 150 -&gt; 1.50 A
     * </pre>
     */
    public static final int REG_IOUT = 0x0003;

    /**
     * Measured output power.
     *
     * <p>
     * Read only.
     * </p>
     *
     * <p>
     * Scaling: raw / 100 = watts
     * </p>
     *
     * <pre>
     * 123 -&gt; 1.23 W
     * </pre>
     */
    public static final int REG_POUT = 0x0004;

    /**
     * Input voltage measurement.
     *
     * <p>
     * Read only. Voltage supplied to the converter input.
     * </p>
     *
     * <p>
     * Scaling: raw / 100 = volts
     * </p>
     *
     * <pre>
     * 1558 -&gt; 15.58 V
     * </pre>
     */
    public static final int REG_VIN = 0x0005;

    /**
     * Accumulated output capacity (mAh) - low 16 bits.
     *
     * <p>
     * Read only.
     * </p>
     */
    public static final int REG_AH_LOW = 0x0006;

    /**
     * Accumulated output capacity (mAh) - high 16 bits.
     *
     * <p>
     * Read only.
     * </p>
     */
    public static final int REG_AH_HIGH = 0x0007;

    /**
     * Accumulated output energy (mWh) - low 16 bits.
     *
     * <p>
     * Read only.
     * </p>
     */
    public static final int REG_WH_LOW = 0x0008;

    /**
     * Accumulated output energy (mWh) - high 16 bits.
     *
     * <p>
     * Read only.
     * </p>
     */
    public static final int REG_WH_HIGH = 0x0009;

    /**
     * Output ON time (hours).
     *
     * <p>
     * Read only.
     * </p>
     */
    public static final int REG_OUT_HOURS = 0x000A;

    /**
     * Output ON time (minutes).
     *
     * <p>
     * Read only.
     * </p>
     */
    public static final int REG_OUT_MINUTES = 0x000B;

    /**
     * Output ON time (seconds).
     *
     * <p>
     * Read only.
     * </p>
     */
    public static final int REG_OUT_SECONDS = 0x000C;

    /**
     * Internal temperature.
     *
     * <p>
     * Read only.
     * </p>
     *
     * <p>
     * Scaling: raw / 10 = degrees Celsius
     * </p>
     *
     * <pre>
     * 350 -&gt; 35.0 °C
     * </pre>
     */
    public static final int REG_TEMPERATURE_INTERNAL = 0x000D;

    /**
     * External temperature (if probe present).
     *
     * <p>
     * Read only.
     * </p>
     *
     * <p>
     * Scaling: raw / 10 = degrees Celsius
     * </p>
     */
    public static final int REG_TEMPERATURE_EXTERNAL = 0x000E;

    /**
     * Keypad lock.
     *
     * <p>
     * Read/Write.
     * </p>
     *
     * <ul>
     * <li>0 = unlocked</li>
     * <li>1 = locked</li>
     * </ul>
     */
    public static final int REG_KEYPAD_LOCK = 0x000F;

    /**
     * Device protection state.
     *
     * <p>
     * Read/Write. Write {@code 0} to clear a tripped protection.
     * </p>
     *
     * <ul>
     * <li>0 = normal</li>
     * <li>1 = OVP (output over-voltage)</li>
     * <li>2 = OCP (output over-current)</li>
     * <li>3 = OPP (output over-power)</li>
     * <li>4 = LVP (input under-voltage)</li>
     * <li>5 = OAH (over-capacity)</li>
     * <li>6 = OHP (timeout)</li>
     * <li>7 = OTP (over-temperature)</li>
     * <li>8 = OEP</li>
     * <li>9 = OWH (over-energy)</li>
     * <li>10 = ICP</li>
     * </ul>
     */
    public static final int REG_PROTECTION_STATE = 0x0010;

    /**
     * Regulation mode (CV/CC) indicator.
     *
     * <p>
     * Read only.
     * </p>
     *
     * <ul>
     * <li>0 = CV (constant voltage)</li>
     * <li>1 = CC (constant current)</li>
     * </ul>
     */
    public static final int REG_MODE = 0x0011;

    /**
     * Output enable control.
     *
     * <p>
     * Read/Write.
     * </p>
     *
     * <ul>
     * <li>0 = output OFF</li>
     * <li>1 = output ON</li>
     * </ul>
     */
    public static final int REG_OUTPUT_ENABLE = 0x0012;

    /**
     * Temperature unit selection.
     *
     * <p>
     * Read/Write.
     * </p>
     *
     * <ul>
     * <li>0 = °C</li>
     * <li>1 = °F</li>
     * </ul>
     */
    public static final int REG_TEMP_UNIT = 0x0013;

    /**
     * Backlight brightness.
     *
     * <p>
     * Read/Write. Range: 0–5.
     * </p>
     */
    public static final int REG_BACKLIGHT = 0x0014;

    /**
     * Display sleep timeout (minutes).
     *
     * <p>
     * Read/Write.
     * </p>
     */
    public static final int REG_SLEEP = 0x0015;

    /**
     * Product model number register.
     *
     * <p>
     * Read only. Used by {@link Wuzhi#verifyDevicePresent} for device identification. The ZK-series uses the same
     * {@code 0x59xx} packed encoding as modern Sinilink firmware (high byte = {@code 0x59}, ASCII {@code 'Y'}); the
     * low byte encodes the board hardware revision. The exact value for the ZK-6522C has not yet been confirmed on
     * live hardware - see {@code doc/Wuzhi.md}, Outstanding Unknowns.
     * </p>
     */
    public static final int REG_MODEL = 0x0016;

    /**
     * Firmware version register.
     *
     * <p>
     * Read only. Scaling: raw / 100 = version number.
     * </p>
     *
     * <pre>
     * 110 -&gt; v1.10
     * </pre>
     */
    public static final int REG_FIRMWARE = 0x0017;

    /**
     * Modbus slave address.
     *
     * <p>
     * Read/Write.
     * </p>
     */
    public static final int REG_SLAVE_ADDRESS = 0x0018;

    /**
     * Baud rate selector.
     *
     * <p>
     * Read/Write.
     * </p>
     */
    public static final int REG_BAUDRATE = 0x0019;

    /**
     * Memory recall (M0–M10).
     *
     * <p>
     * Read/Write. Writing a value 1–10 loads the corresponding preset into the active M0 working set. The ZK-6522C
     * supports 11 groups (M0–M10), one more than the 10 groups on Sinilink and Riden devices.
     * </p>
     */
    public static final int REG_MEMORY_RECALL = 0x001D;

    // -------------------------------------------------------------------------
    // M0 active working set preset registers (base 0x0050)
    // -------------------------------------------------------------------------

    /** Base address of active memory (M0) - Voltage setting. Read/Write. */
    public static final int REG_MEMORY_M0_VSET = 0x0050;

    /** Base address of active memory (M0) - Current setting. Read/Write. */
    public static final int REG_MEMORY_M0_ISET = 0x0051;

    /** Base address of active memory (M0) - Input under-voltage protection (LVP). Read/Write. */
    public static final int REG_MEMORY_M0_LVP = 0x0052;

    /** Base address of active memory (M0) - Output over-voltage protection (OVP). Read/Write. */
    public static final int REG_MEMORY_M0_OVP = 0x0053;

    /** Base address of active memory (M0) - Output over-current protection (OCP). Read/Write. */
    public static final int REG_MEMORY_M0_OCP = 0x0054;

    /** Base address of active memory (M0) - Output over-power protection (OPP). Read/Write. */
    public static final int REG_MEMORY_M0_OPP = 0x0055;

    /** Base address of active memory (M0) - Timeout protection hours (OHP). Read/Write. */
    public static final int REG_MEMORY_M0_OHP_HOURS = 0x0056;

    /** Base address of active memory (M0) - Timeout protection minutes (OHP). Read/Write. */
    public static final int REG_MEMORY_M0_OHP_MINUTES = 0x0057;

    /** Base address of active memory (M0) - Over-capacity protection low 16 bits (OAH). Read/Write. */
    public static final int REG_MEMORY_M0_OAH_LOW = 0x0058;

    /** Base address of active memory (M0) - Over-capacity protection high 16 bits (OAH). Read/Write. */
    public static final int REG_MEMORY_M0_OAH_HIGH = 0x0059;

    /** Base address of active memory (M0) - Over-energy protection low 16 bits (OWH). Read/Write. */
    public static final int REG_MEMORY_M0_OWH_LOW = 0x005A;

    /** Base address of active memory (M0) - Over-energy protection high 16 bits (OWH). Read/Write. */
    public static final int REG_MEMORY_M0_OWH_HIGH = 0x005B;

    /** Base address of active memory (M0) - Over-temperature protection (OTP). Read/Write. */
    public static final int REG_MEMORY_M0_OTP = 0x005C;

    /** Base address of active memory (M0) - Power-on output switch default. Read/Write. */
    public static final int REG_MEMORY_M0_OUTPUT_ENABLE = 0x005D;

}

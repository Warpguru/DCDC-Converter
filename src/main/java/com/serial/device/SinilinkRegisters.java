package com.serial.device;

import com.serial.devices.RidenRD60xx;
import com.serial.modbus.ModbusFunctionCodes;

/**
 * Register map for {@code Sinilink} programmable DC power supply.
 *
 * <p>
 * The Sinilink exposes a smaller {@code Modbus} register map than the {@link RidenRD60xx}. All registers are 16-bit holding
 * registers (Modbus function {@link ModbusFunctionCodes#READ_HOLDING_REGISTERS} (0x03) /
 * {@link ModbusFunctionCodes#WRITE_SINGLE_REGISTER} (0x06) / {@link ModbusFunctionCodes#WRITE_MULTIPLE_REGISTERS} (0x10)).
 * Addresses are zero-based.
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
 * <li>Temperature: raw / 10 = °C</li>
 * </ul>
 * 
 * Memory model:
 * 
 * <ul>
 * <li>M0 (0x0050) = active working set</li>
 * <li>M1–M9 = stored presets</li>
 * <li>Calling memory copies into M0</li>
 * <li>Registers 0x0000/0x0001 mirror 0x0050/0x0051</li>
 * </ul>
 * 
 * @see <a href="https://www.laskakit.cz/user/related_files/xy6020l-modbus-interface.pdf">XY6020L 20A / 1200W Programmable Power Supply - Modbus Interface Documentation</href>
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
     * Input voltage measurement.
     *
     * Read only.
     *
     * Voltage supplied to the converter input.
     *
     * Scaling: raw / 100 = volts
     */
    public static final int REG_VIN = 0x0005;

    /**
     * Accumulated output capacity (mAh) - low 16 bits.
     * 
     * Read only.
     */
    public static final int REG_AH_LOW = 0x0006;

    /**
     * Accumulated output capacity (mAh) - high 16 bits.
     * 
     * Read only.
     */
    public static final int REG_AH_HIGH = 0x0007;

    /**
     * Accumulated output energy (mWh) - low 16 bits.
     * 
     * Read only.
     */
    public static final int REG_WH_LOW = 0x0008;

    /**
     * Accumulated output energy (mWh) - high 16 bits.
     * 
     * Read only.
     */
    public static final int REG_WH_HIGH = 0x0009;

    /**
     * Output ON time (hours).
     * 
     * Read only.
     */
    public static final int REG_OUT_HOURS = 0x000A;

    /**
     * Output ON time (minutes).
     * 
     * Read only.
     */
    public static final int REG_OUT_MINUTES = 0x000B;

    /**
     * Output ON time (seconds).
     * 
     * Read only.
     */
    public static final int REG_OUT_SECONDS = 0x000C;

    /**
     * Internal temperature.
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
    public static final int REG_TEMPERATURE_INTERNAL = 0x000D;

    /**
     * External temperature (if probe present).
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
    public static final int REG_TEMPERATURE_EXTERNAL = 0x000E;

    /**
     * Keypad lock.
     *
     * Read/write.
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
     * Read/write.
     *
     * Values:
     * <ul>
     * <li>0 -> normal
     * <li>1 -> over-voltage protection
     * <li>2 -> over-current protection
     * <li>3 -> over-power protection
     * <li>4 -> over-temperature protection
     * <li>0 -> normal</li>
     * <li>1 -> OVP (over-voltage)</li>
     * <li>2 -> OCP (over-current)</li>
     * <li>3 -> OPP (over-power)</li>
     * <li>4 -> LVP (low voltage)</li>
     * <li>5 -> OAH</li>
     * <li>6 -> OHP (timeout)</li>
     * <li>7 -> OTP (over-temp)</li>
     * <li>8 -> OEP</li>
     * <li>9 -> OWH</li>
     * <li>10 -> ICP</li>
     * </ul>
     * 
     * Protection state may be reset by clearing register {@link SinilinkRegisters#REG_PROTECTION_STATE}.
     */
    public static final int REG_PROTECTION_STATE = 0x0010;

    /**
     * Regulation mode (CV/CC) indicator.
     * 
     * Read only.
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
    public static final int REG_OUTPUT_ENABLE = 0x0012;

    /**
     * Temperature unit selection.
     * 
     * Read/write.
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
     * Read/write.
     *
     * Range: 0–5
     */
    public static final int REG_BACKLIGHT = 0x0014;

    /**
     * Display sleep timeout (minutes).
     * 
     * Read/write.
     */
    public static final int REG_SLEEP = 0x0015;

    /**
     * Product number register.
     *
     * Read only.
     *
     * Alternative device identification register to confirm that the attached serial device is an Sinilink compatible unit.
     */
    public static final int REG_MODEL = 0x0016;

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
    public static final int REG_FIRMWARE = 0x0017;

    /**
     * Modbus slave address.
     * 
     * Read/write.
     */
    public static final int REG_SLAVE_ADDRESS = 0x0018;

    /**
     * Baud rate.
     * 
     * Read/write.
     *
     * Example:
     * 
     * <pre>
     * 6 -> 115200 baud
     * </pre>
     */
    public static final int REG_BAUDRATE = 0x0019;

    /**
     * Internal temperature calibration offset.
     * 
     * Read/write.
     */
    public static final int REG_TEMPERATURE_INTERNAL_OFFSET = 0x001A;

    /**
     * External temperature calibration offset.
     * 
     * Read/write.
     */
    public static final int REG_TEMPERATURE_EXTERNAL_OFFSET = 0x001B;

    /**
     * Buzzer enable (may not be implemented).
     * 
     * Read/write.
     */
    public static final int REG_BUZZER = 0x001C;

    /**
     * Memory recall (M0–M9).
     * 
     * Read/write.
     *
     * Writing 1–9 loads preset into active M0.
     */
    public static final int REG_MEMORY_RECALL = 0x001D;

    /**
     * Device status (undefined / often not implemented).
     * 
     * Read/write.
     */
    public static final int REG_DEVICE_STATUS = 0x001E;

    /**
     * Wifi module - Host type (0x3B3A: WIFI, others to be determined).
     * 
     * Read/write.
     */
    public static final int REG_WIFI_MASTER = 0x0030;
    
    /**
     * Wifi module - WIFI pairing status.
     * 
     * Read/write.
     * 
     * Values:
     * <ul>
     * <li>0 -> Invalid
     * <li>1 -> Touch pairing
     * <li>2 -> AP pairing
     * </ul>
     */
    public static final int REG_WIFI_CONFIG = 0x0031;

    /**
     * Wifi module - WIFI status 
     * 
     * Read/write.
     * 
     * Values:
     * <ul>
     * <li>0 -> Invalid networt
     * <li>1 -> Connected to the router
     * <li>2 -> Successfully connected to the server
     * <li>3 -> Touch pairing
     * <li>4 -> AP pairing
     * </ul>
     */
    public static final int REG_WIFI_STATUS = 0x0032;
    
    /**
     * Wifi module - IP address, e.g. 192.168.1.8, the first two bytes are 0xC0A8
     * 
     * Read/write.
     */
    public static final int REG_WIFI_IP_HIGH = 0x0033;
    
    /**
     * Wifi module - IP address, 192.168.1.8, the last two bytes are 0x0108
     * 
     * Read/write.
     */
    public static final int REG_WIFI_IP_LOW = 0x0034;
    
    /**
     * Base address of active memory (M0) - Voltage setting.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_REG_VSET = 0x0050;
    
    /**
     * Base address of active memory (M0) - Current setting.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_REG_ISET = 0x0051;

    /**
     * Base address of active memory (M0) - Low voltage protection value.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_LVP = 0x0052;
    
    /**
     * Base address of active memory (M0) - Overvoltage protection value.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OVP = 0x0053;
    
    /**
     * Base address of active memory (M0) - Overcurrent protection value.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OCP = 0x0054;
    
    /**
     * Base address of active memory (M0) - Over power protection value.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OPP = 0x0055;
    
    /**
     * Base address of active memory (M0) - Maximum output time (hours).
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OHP_HOURS = 0x0056;

    /**
     * Base address of active memory (M0) - Maximum output duration (minutes).
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OHP_MINUTES = 0x0057;
    
    /**
     * Base address of active memory (M0) - Maximum output AH low 16 bits.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OAH_LOW = 0x0058;
    
    /**
     * Base address of active memory (M0) - Maximum output AH high 16 bits.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OAH_HIGH = 0x0059;

    /**
     * Base address of active memory (M0) - Maximum output WH low 16 bits.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OWH_LOW = 0x005A;

    /**
     * Base address of active memory (M0) - Maximum output WH high 16 bits.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OWH_HIGH = 0x005B;

    /**
     * Base address of active memory (M0) - Over temperature protection value.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OTP = 0x005C;

    /**
     * Base address of active memory (M0) - Power-on output switch.
     * 
     * Read/write.
     */
    public static final int REG_MEMORY_M0_SET_OUTPUT_ENABLE = 0x005D;

}

package com.serial.device;

/**
 * Describes a {@code Modbus} register used by a device driver.
 *
 * <p>
 * This class encapsulates the metadata required to convert between human-readable engineering values (such as volts or amperes)
 * and the raw integer values stored in Modbus registers.
 * </p>
 *
 * <p>
 * Many laboratory devices, including programmable power supplies such as XY6008Old or RidenRD60xx modules, represent values in
 * registers using fixed-point scaling. For example:
 * </p>
 *
 * <ul>
 * <li>A voltage of {@code 5.00 V} may be stored as {@code 500}</li>
 * <li>A current of {@code 1.250 A} may be stored as {@code 1250}</li>
 * </ul>
 *
 * <p>
 * The {@code scale} factor defines how values are converted:
 * </p>
 *
 * <pre>
 * raw_value = engineering_value * scale
 * engineering_value = raw_value / scale
 * </pre>
 *
 * <p>
 * Example register definitions:
 * </p>
 *
 * <pre>
 * public static final DeviceRegister VSET = new DeviceRegister(0x0000, 100);
 *
 * public static final DeviceRegister ISET = new DeviceRegister(0x0001, 1000);
 * </pre>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * double voltage = device.read(VSET);
 * device.write(VSET, 5.0);
 * </pre>
 *
 * <p>
 * This abstraction allows device drivers to define register semantics declaratively and prevents duplication of scaling logic
 * across drivers.
 * </p>
 */
public class DeviceRegister {

    /**
     * {@code Modbus} register name.
     */
    public final String name;

    /**
     * {@code Modubus} register unit.
     * 
     * <p>
     * Example:
     * </p>
     *
     * <pre>
     * V -> Volts
     * A -> Amperes
     * W -> Watts
     * </pre>
     */
    public final String unit;

    /**
     * {@code Modbus} register address.
     *
     * <p>
     * This is the register index used in Modbus read/write operations.
     * </p>
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>
     * 0x0000 -> voltage setpoint
     * 0x0001 -> current limit
     * </pre>
     */
    public final int address;

    /**
     * Scaling factor used to convert between engineering values and raw {@code Modbus} register values.
     *
     * <p>
     * The scale defines the fixed-point representation used by the device.
     * </p>
     *
     * <p>
     * Examples:
     * </p>
     *
     * <ul>
     * <li>{@code 100} → value stored with two decimal places (e.g. volts)</li>
     * <li>{@code 1000} → value stored with three decimal places (e.g. amperes)</li>
     * </ul>
     */
    public final double scale;

    /**
     * Creates a register description.
     *
     * @param name    Modbus register name
     * @param unit    Modbus register unit
     * @param address Modbus register address
     * @param scale   scaling factor used for encoding and decoding values
     */
    public DeviceRegister(final String name, final String unit, final int address, final double scale) {
        this.name = name;
        this.unit = unit;
        this.address = address;
        this.scale = scale;
    }

    /**
     * Creates a register description.
     *
     * @param name    Modbus register name
     * @param unit    Modbus register unit
     * @param address Modbus register address
     */
    public DeviceRegister(final String name, final String unit, final int address) {
        this.name = name;
        this.unit = unit;
        this.address = address;
        this.scale = 1;
    }

    /**
     * Converts an engineering value into the raw register value expected by the device.
     *
     * <p>
     * The conversion uses the following formula:
     * </p>
     *
     * <pre>
     * raw = round(value * scale)
     * </pre>
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>
     * scale = 100
     * value = 5.00 V
     *
     * raw = 500
     * </pre>
     *
     * @param value engineering value (e.g. volts or amperes)
     * @return integer value suitable for writing to the Modbus register
     */
    public int encode(final double value) {
        return (int) Math.round(value * scale);
    }

    /**
     * Converts a raw Modbus register value into an engineering value.
     *
     * <p>
     * The conversion uses the following formula:
     * </p>
     *
     * <pre>
     * value = raw / scale
     * </pre>
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>
     * raw = 500
     * scale = 100
     *
     * value = 5.00 V
     * </pre>
     *
     * @param raw raw register value read from the device
     * @return decoded engineering value
     */
    public double decode(final int raw) {
        return raw / scale;
    }

}

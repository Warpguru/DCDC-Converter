package com.serial.modbus;

/**
 * Modbus RTU function codes used by the XY6008Old.
 *
 * <p>
 * The device implements a subset of the Modbus RTU protocol. Only the following commands are required for normal operation.
 * </p>
 */
public final class ModbusFunctionCodes {

    private ModbusFunctionCodes() {
    }

    /**
     * Modbus function code for reading holding registers.
     *
     * <p>
     * Command: 0x03
     * </p>
     *
     * <p>
     * This command reads one or more 16-bit registers from the device.
     * </p>
     *
     * <p>
     * Example request frame:
     * </p>
     *
     * <pre>
     * [slave][0x03][start_hi][start_lo][count_hi][count_lo][crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * Example response:
     * </p>
     *
     * <pre>
     * [slave][0x03][byte_count][data...][crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * This function is used for retrieving values such as:
     * </p>
     *
     * <ul>
     * <li>Output voltage</li>
     * <li>Output current</li>
     * <li>Power measurement</li>
     * <li>Device firmware version</li>
     * </ul>
     */
    public static final byte READ_HOLDING_REGISTERS = 0x03;

    /**
     * Modbus function code for writing a single register.
     *
     * <p>
     * Command: 0x06
     * </p>
     *
     * <p>
     * This command writes a 16-bit value to a register.
     * </p>
     *
     * <p>
     * Example request frame:
     * </p>
     *
     * <pre>
     * [slave][0x06][reg_hi][reg_lo][value_hi][value_lo][crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * The device echoes the same frame if the write succeeded.
     * </p>
     *
     * <p>
     * This function is used for operations such as:
     * </p>
     *
     * <ul>
     * <li>Setting voltage setpoint</li>
     * <li>Setting current limit</li>
     * <li>Enabling/disabling output</li>
     * </ul>
     */
    public static final byte WRITE_SINGLE_REGISTER = 0x06;

}

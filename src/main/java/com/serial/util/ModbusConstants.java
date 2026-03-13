package com.serial.util;

/**
 * Modbus protocol constants used by the driver.
 */
@Deprecated
public final class ModbusConstants {

    private ModbusConstants() {
    }

    /** Default baud rate used by XY6008Old. */
    public static final int BAUD = 115200;
    
    /** Default slave address used by XY6008Old. */
    public static final byte SLAVE_ADDRESS = 0x01;

    /** Modbus function code: read holding registers. */
    public static final byte READ_HOLDING_REGISTERS = 0x03;

    /** Modbus function code: write single register. */
    public static final byte WRITE_SINGLE_REGISTER = 0x06;
    
}

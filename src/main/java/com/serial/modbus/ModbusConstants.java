package com.serial.modbus;

/**
 * Modbus protocol constants used by the driver.
 */
public final class ModbusConstants {

    private ModbusConstants() {
    }

    /** Default baud rate used by {@code Modbus} device. */
    public static final int BAUD_9600 = 9600;
    
    /** Default baud rate used by {@code Modbus} device. */
    public static final int BAUD_115200 = 115200;
    
    /** Default slave address used by {@code Modbus} device. */
    public static final byte SLAVE_ADDRESS_1 = 0x01;

    /** {@code Modbus} device function code: read holding registers. */
    public static final byte READ_HOLDING_REGISTERS = 0x03;

    /** {@code Modbus} device function code: write single register. */
    public static final byte WRITE_SINGLE_REGISTER = 0x06;
    
}

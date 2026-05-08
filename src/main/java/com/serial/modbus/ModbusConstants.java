package com.serial.modbus;

/**
 * Modbus protocol constants used by the driver.
 */
public final class ModbusConstants {

    private ModbusConstants() {
    }

    /** Default data bits by {@code Modbus} device. */
    public static final int DATABITS_8 = 8;
    
    /** Default baud rate used by {@code Modbus} device. */
    public static final int BAUD_9600 = 9600;

    /** Non default baud rate used by {@code Modbus} device. */
    public static final int BAUD_19200 = 19200;
    
    /** Non default baud rate used by {@code Modbus} device. */
    public static final int BAUD_38400 = 38400;
    
    /** Non default baud rate used by {@code Modbus} device. */
    public static final int BAUD_57600 = 57600;
    
    /** Default baud rate used by {@code Modbus} device. */
    public static final int BAUD_115200 = 115200;
    
    /** Default slave address used by {@code Modbus} device. */
    public static final byte SLAVE_ADDRESS_1 = 0x01;

    /** Read timeout [ms]. */
    public static final int READ_TIMEOUT_MS = 1000;
    
    /** Write timeout [ms]. */
    public static final int WRITE_TIMEOUT_MS = 0;
    
}

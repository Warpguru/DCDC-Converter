package com.serial.device;

import com.serial.modbus.ModbusTransport;

/**
 * Abstract base class for devices communicating via the Modbus protocol.
 *
 * <p>
 * This class provides a generic abstraction for Modbus-based devices by encapsulating the common functionality required to read
 * and write device registers. It relies on a {@link ModbusTransport} instance for the actual communication over a serial
 * connection.
 * </p>
 *
 * <p>
 * The class supports both low-level register access and higher-level operations using {@link DeviceRegister} descriptors that
 * automatically convert between raw Modbus register values and engineering units.
 * </p>
 *
 * <p>
 * Typical usage involves creating a device-specific subclass (for example {@code Sinilink} or {@code RidenRD60xx}) that defines
 * register descriptors and exposes convenient domain-specific methods such as {@code setVoltage()} or {@code getCurrent()}.
 * </p>
 *
 * <p>
 * Example subclass usage:
 * </p>
 *
 * <pre>
 * ModbusTransport transport = new ModbusTransport("/dev/ttyUSB0", 115200);
 *
 * Sinilink psu = new Sinilink(transport, (byte) 1);
 *
 * psu.write(Sinilink.VSET, 5.0); // set voltage to 5 V
 * double v = psu.read(Sinilink.VOUT); // read measured output voltage
 * </pre>
 *
 * <p>
 * This abstraction allows multiple device drivers to share the same Modbus transport implementation while keeping
 * device-specific logic minimal and maintainable.
 * </p>
 */
public abstract class ModbusDevice {

    protected static final int MAX_RETRY = 3;

    /**
     * Transport layer used for Modbus communication.
     *
     * <p>
     * This object is responsible for sending and receiving Modbus frames over the underlying communication medium (typically a
     * serial port using Modbus RTU).
     * </p>
     */
    protected final ModbusTransport transport;

    /**
     * Modbus slave address of the device.
     *
     * <p>
     * Each Modbus device on a bus must have a unique slave address. Most standalone power supplies use the default address
     * {@code 1}.
     * </p>
     */
    protected final byte slave;

    /**
     * Device type as {@link String} retrieved via ModBus.
     */
    protected String device;
    
    /**
     * Creates a new Modbus device instance.
     *
     * @param transport the transport implementation used for Modbus communication
     * @param slave     the Modbus slave address of the device
     */
    public ModbusDevice(final ModbusTransport transport, final byte slave) {
        this.transport = transport;
        this.slave = slave;
    }
    
    /**
     * Retrieve device string.
     * 
     * @return
     */
    /**
     * {@inheritDoc}
     */
    public String getDevice() {
        return device;
    }

    /**
     * Reads a value from a device register using a {@link DeviceRegister} descriptor.
     *
     * <p>
     * This method performs the following steps:
     * </p>
     *
     * <ol>
     * <li>Reads the raw integer value from the specified Modbus register</li>
     * <li>Decodes the value using the register's scaling factor</li>
     * <li>Returns the value in engineering units (e.g. volts or amperes)</li>
     * </ol>
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>
     * double voltage = device.read(Sinilink.VOUT);
     * </pre>
     *
     * @param reg the register descriptor containing address and scaling
     * @return the decoded value in engineering units
     * @throws Exception if communication with the device fails
     */
    public double read(final DeviceRegister reg) throws Exception {
        int raw = read(reg.address);
        return reg.decode(raw);
    }

    /**
     * Reads a value from a device register using a {@link DeviceRegister} descriptor without any conversion.
     * 
     * @param reg the register descriptor containing address and scaling
     * @return the decoded value in engineering units
     * @throws Exception if communication with the device fails
     */
    public int readInt(final DeviceRegister reg) throws Exception {
        return read(reg.address);
    }

    /**
     * Writes a value to a device register using a {@link DeviceRegister} descriptor.
     *
     * <p>
     * The provided value is first converted into the raw integer representation required by the device using the register's
     * scaling factor.
     * </p>
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>
     * device.write(Sinilink.VSET, 5.0);
     * </pre>
     *
     * <p>
     * If the register uses a scaling factor of {@code 100}, the value {@code 5.0} will be converted to {@code 500} before being
     * written to the Modbus register.
     * </p>
     *
     * @param reg   the register descriptor
     * @param value the engineering value to write
     * @throws Exception if communication with the device fails
     */
    public void write(final DeviceRegister reg, final double value) throws Exception {
        write(reg.address, reg.encode(value));
    }

    /**
     * Writes a value to a device register using a {@link DeviceRegister} descriptor and verify by reading a
     * {@link DeviceRegister}.
     * 
     * @param regSet
     * @param regVOut
     * @param value
     * @throws Exception
     */
    public void writeVerified(final DeviceRegister regSet, final DeviceRegister regOut, final double value) throws Exception {
        System.out.println("\nSetting value → " + value + " " + regSet.unit);
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            // writeRegister(XY6008Registers.REG_VSET, raw);
            write(regSet, value);
            Thread.sleep(200);
            double readSet = read(regSet);
            double readOut = read(regOut);
            System.out.println(String.format("Attempt %d SET=%.2f%s OUT=%.2f%s", attempt, readSet, regSet.unit, readOut, regOut.unit));
            if (readSet == value) {
                System.out.println(regSet.name + " verified");
                return;
            }
        }
        throw new RuntimeException("Failed to set " + regSet.name);
    }

    /**
     * Writes a value to a device register using a {@link DeviceRegister} descriptor without any conversion.
     * 
     * @param reg   the register descriptor
     * @param value the engineering value to write
     * @throws Exception if communication with the device fails
     */
    public void writeInt(final DeviceRegister reg, final int value) throws Exception {
        write(reg, value);
    }

    /**
     * Reads a raw Modbus register value.
     *
     * <p>
     * This method performs a low-level Modbus read operation without applying any scaling or interpretation.
     * </p>
     *
     * <p>
     * Subclasses typically use this method internally when implementing device-specific functionality.
     * </p>
     *
     * @param register Modbus register address
     * @return raw 16-bit register value
     * @throws Exception if communication fails
     */
    protected int read(final int register) throws Exception {
        return transport.readRegister(slave, register);
    }

    /**
     * Writes a raw value to a Modbus register.
     *
     * <p>
     * This method performs a low-level Modbus write operation without applying any scaling or interpretation.
     * </p>
     *
     * <p>
     * Higher-level methods should usually use the {@link #write(DeviceRegister, double)} method instead.
     * </p>
     *
     * @param register Modbus register address
     * @param value    raw integer value to write
     * @throws Exception if communication fails
     */
    protected void write(final int register, final int value) throws Exception {
        transport.writeRegister(slave, register, value);
    }

}

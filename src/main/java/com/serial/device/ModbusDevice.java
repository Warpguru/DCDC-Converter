package com.serial.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(ModbusDevice.class);

    /** Retry counter. */
    protected static final int MAX_RETRY = 3;

    /**
     * {@code SerialPort} device name used with Modbus protocol.
     */
    protected String portName;
    
    /**
     * Transport layer used for Modbus communication.
     *
     * <p>
     * This object is responsible for sending and receiving Modbus frames over the underlying communication medium (typically a
     * serial port using Modbus RTU).
     * </p>
     */
    protected ModbusTransport transport;

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
     * Device manufacturer as {@link String} retrieved via ModBus.
     */
    protected String manufacturer;
    
    /**
     * Device type as {@link String} retrieved via ModBus.
     */
    protected String device;
    
    /**
     * Creates a new Modbus device instance.
     *
     * @param portName  of {@code SerialPort} used with Modbus protocol
     * @param slave     the Modbus slave address of the device
     */
    public ModbusDevice(final String portName, final byte slave) {
        this.portName = portName;
        this.slave = slave;
    }
    
    /**
     * Retrieve {@code SerialPort} device name used with Modbus protocol.
     *
     * @return {@code portName}
     */
    public String getPortName() {
        return portName;
    }

    /**
     * Closes and re-opens the serial transport at the same port and baud rate.
     *
     * <p>
     * Delegates to {@link ModbusTransport#reconnect()}. Called by {@link com.serial.service.DeviceService}
     * after a configurable number of consecutive poll failures, which indicates the USB-to-serial
     * adapter was physically disconnected and reconnected.
     * </p>
     *
     * @throws Exception if the transport cannot be re-opened
     */
    public void reconnect() throws Exception {
        if (transport != null) {
            transport.reconnect();
        }
    }

    /**
     * Check if a known {@code Modbus} device was found on {@code SerialPort}.
     * 
     * @return {@code true} or {@code false}
     */
    public boolean isDeviceDetected() {
        return ((manufacturer != null) && (device != null));
    }
    
    /**
     * Retrieve manufacturer string.
     * 
     * @return manufacturer
     */
    public String getManufacturer() {
        return manufacturer;
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
        double value = reg.decode(raw);
        logger.trace("    -> {}: {} {}", reg.name, formatValue(value, reg.scale), reg.unit);
        return value;
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
        logger.trace("    -> {}: {} {} (write)", reg.name, formatValue(value, reg.scale), reg.unit);
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
        logger.info("writeVerified {} -> {} {}", regSet.name, formatValue(value, regSet.scale), regSet.unit);
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            write(regSet, value);
            Thread.sleep(200);
            double readSet = read(regSet);
            double readOut = read(regOut);
            logger.info("  attempt {}: SET={} {} OUT={} {}",
                    attempt,
                    formatValue(readSet, regSet.scale), regSet.unit,
                    formatValue(readOut, regOut.scale), regOut.unit);
            if (readSet == value) {
                logger.info("  {} verified", regSet.name);
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
     * Reads a contiguous block of raw 16-bit Modbus register values in a single frame.
     *
     * <p>
     * Delegates to {@link ModbusTransport#readRegisters(byte, int, int)}, which sends one
     * Modbus {@code 0x03} request for all {@code count} registers and returns them in a single
     * serial round-trip. The returned array is indexed by offset from {@code startAddress}:
     * element {@code [0]} is the value at {@code startAddress}, element {@code [1]} at
     * {@code startAddress + 1}, and so on.
     * </p>
     *
     * <p>
     * Values are raw (unscaled). Callers are responsible for applying the appropriate scale
     * factor for each offset.
     * </p>
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>
     * int[] block = readBlock(SinilinkRegisters.REG_VSET, 19);
     * double volts = block[0] / 100.0;  // VSET at offset 0
     * double amps  = block[1] / 1000.0; // ISET at offset 1
     * </pre>
     *
     * @param startAddress starting Modbus register address
     * @param count        number of consecutive registers to read (1–32)
     * @return raw 16-bit values at offsets {@code 0..(count-1)} from {@code startAddress}
     * @throws Exception if communication with the device fails
     */
    protected int[] readBlock(final int startAddress, final int count) throws Exception {
        return transport.readRegisters(slave, startAddress, count);
    }

    /**
     * Writes raw 16-bit values to a contiguous block of Modbus registers in a single frame.
     *
     * <p>
     * Delegates to {@link ModbusTransport#writeRegisters(byte, int, int[])}, which sends one
     * Modbus {@code 0x10} request covering all {@code values.length} registers starting at
     * {@code startAddress}. This uses a single serial round-trip regardless of how many
     * registers are written.
     * </p>
     *
     * <p>
     * Values must already be scaled to raw register representation. Element {@code [0]}
     * is written to {@code startAddress}, element {@code [1]} to {@code startAddress + 1},
     * and so on.
     * </p>
     *
     * <p>
     * Example — write VSET and ISET atomically on a Sinilink:
     * </p>
     *
     * <pre>
     * writeBlock(SinilinkRegisters.REG_VSET, new int[] { 500, 2500 }); // 5.00 V, 2.500 A
     * </pre>
     *
     * @param startAddress starting Modbus register address
     * @param values       raw 16-bit values to write, one per register in address order (1–32 elements)
     * @throws Exception if communication with the device fails
     */
    protected void writeBlock(final int startAddress, final int[] values) throws Exception {
        transport.writeRegisters(slave, startAddress, values);
    }

    /**
     * Formats a numeric engineering value with the precision implied by the register's scale factor.
     *
     * <p>
     * The number of decimal places is derived from the scale: log10(scale). For example:
     * </p>
     * <ul>
     * <li>scale 1    → 0 decimal places (e.g. integers)</li>
     * <li>scale 10   → 1 decimal place  (e.g. temperature: 35.0 °C)</li>
     * <li>scale 100  → 2 decimal places (e.g. voltage: 5.00 V)</li>
     * <li>scale 1000 → 3 decimal places (e.g. current: 1.000 A)</li>
     * </ul>
     *
     * @param value the engineering value to format
     * @param scale the register's scale factor
     * @return formatted string with the appropriate number of decimal places
     */
    private static String formatValue(final double value, final double scale) {
        int decimals = (scale > 1) ? (int) Math.round(Math.log10(scale)) : 0;
        return String.format("%." + decimals + "f", value);
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

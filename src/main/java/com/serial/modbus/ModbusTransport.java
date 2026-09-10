package com.serial.modbus;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;
import com.serial.device.DeviceRegister;

/**
 * Handles low-level Modbus RTU communication over serial.
 */
public class ModbusTransport {

    private static final Logger logger = LoggerFactory.getLogger(ModbusTransport.class);

    /**
     * Primary baud rates for fast first-pass device detection.
     *
     * <p>
     * Sinilink devices default to 115200 baud while Riden devices default to 9600 baud.
     * Testing these two rates first allows &gt;99% of connected hardware to be identified
     * in under 2 seconds.
     * </p>
     */
    public static final List<Integer> PRIMARY_BAUDS = List.of(
            ModbusConstants.BAUD_115200,
            ModbusConstants.BAUD_9600);

    /**
     * Secondary fallback baud rates used only if the primary pass fails.
     *
     * <p>
     * Riden units support 19200 baud configuration, which takes precedence over 38400 and 57600.
     * </p>
     */
    public static final List<Integer> SECONDARY_BAUDS = List.of(
            ModbusConstants.BAUD_19200,
            ModbusConstants.BAUD_38400,
            ModbusConstants.BAUD_57600);

    /**
     * Complete list of all supported baud rates in probing order, combining {@link #PRIMARY_BAUDS}
     * and {@link #SECONDARY_BAUDS}.
     */
    public static final List<Integer> BAUDS = Stream.concat(
            PRIMARY_BAUDS.stream(),
            SECONDARY_BAUDS.stream()
    ).toList();

    /** {@link SerialPort} device name the {@code Modbus} device is connected to. */
    private final String portName;

    /** Baud rate used when the port was opened; required for reconnection. */
    private final int baud;

    /** {@link SerialPort} the {@code Modbus} device is connected to. */
    private SerialPort port;

    /** {@link InputStream} reading from {@link ModbusTransport#port}. */
    private InputStream in;

    /** {@link InputStream} writing to {@link ModbusTransport#port}. */
    private OutputStream out;

    /**
     * Constructor.
     * 
     * @param portName of port to open for attached {@code Modbus} device
     * @param baud     to set (typically {@code 115200} baud)
     * @throws Exception
     */
    public ModbusTransport(final String portName, final int baud) throws Exception {
        this.portName = portName;
        this.baud = baud;
        openPort();
    }

    /**
     * Retrieve {@link SerialPort} device name the {@code Modbus} device is connected to.
     * 
     * @return {@code portName}
     */
    public String getPortName() {
        return portName;
    }
    
    /**
     * Close port connected to {@code Modbus} device.
     */
    public void close() {
        port.closePort();
    }

    /**
     * Closes and re-opens the serial port at the same baud rate.
     *
     * <p>
     * Called by {@link com.serial.device.ModbusDevice#reconnect()} after consecutive poll failures,
     * which indicates the USB-serial adapter was unplugged and re-plugged. jSerialComm requires a
     * fresh {@link SerialPort} object after a physical disconnect; the existing object cannot be
     * re-opened.
     * </p>
     *
     * @throws Exception if the port cannot be re-opened
     */
    public void reconnect() throws Exception {
        try {
            port.closePort();
        } catch (Exception ignored) {
            // Already closed or invalid - proceed.
        }
        openPort();
        logger.info("Serial port {} re-opened at {} baud.", portName, baud);
    }

    /**
     * Opens the serial port and obtains the I/O streams.
     *
     * @throws Exception if the port cannot be opened
     */
    private void openPort() throws Exception {
        port = SerialPort.getCommPort(portName);
        port.setComPortParameters(baud, ModbusConstants.DATABITS_8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, ModbusConstants.READ_TIMEOUT_MS,
                ModbusConstants.WRITE_TIMEOUT_MS);
        if (!port.openPort())
            throw new RuntimeException("Cannot open serial port: " + portName);
        in = port.getInputStream();
        out = port.getOutputStream();
    }

    /**
     * Reads a single 16-bit holding register from the Sinilink using Modbus RTU.
     *
     * <p>
     * The method sends a Modbus "Read Holding Registers" request (function code 0x03) and returns the value of the requested
     * register.
     * </p>
     *
     * <p>
     * Frame format transmitted:
     * </p>
     *
     * <pre>
     * [slave][0x03][reg_hi][reg_lo][00][01][crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * The response from the device is expected to be:
     * </p>
     *
     * <pre>
     * [slave][0x03][0x02][value_hi][value_lo][crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * Registers on the XY6008Old typically represent scaled values:
     * </p>
     *
     * <ul>
     * <li>Voltage registers: raw / 100 → volts</li>
     * <li>Current registers: raw / 1000 → amperes</li>
     * </ul>
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>
     * int raw = readRegister(REG_VOUT);
     * double volts = raw / 100.0;
     * </pre>
     *
     * @param slave address of slave
     * @param reg   Register address to read (0x0000 – 0xFFFF).
     * @return The raw 16-bit register value returned by the device.
     * @throws Exception If a serial timeout occurs, the Modbus response is malformed, or the CRC validation fails.
     */
    public int readRegister(final byte slave, final int reg) throws Exception {
        byte[] frame = new byte[8];
        frame[0] = slave;
        frame[1] = ModbusFunctionCodes.READ_HOLDING_REGISTERS;
        frame[2] = (byte) (reg >> 8);
        frame[3] = (byte) reg;
        frame[4] = 0;
        frame[5] = 1;
        int crc = ModbusCRC.calculate(frame, 6);
        frame[6] = (byte) crc;
        frame[7] = (byte) (crc >> 8);
        log("TX", frame, null);
        out.write(frame);
        out.flush();
        byte[] resp = readBytes(7);
        verifyCRC(resp);
        log("RX", resp, null);
        return ((resp[3] & 0xFF) << 8) | (resp[4] & 0xFF);
    }

    /**
     * Reads multiple consecutive 16-bit holding registers in a single Modbus RTU frame.
     *
     * <p>
     * Sends a single Modbus "Read Holding Registers" request (function code 0x03) for {@code count}
     * consecutive registers starting at {@code startReg}, and returns all values in one array.
     * This is more efficient than calling {@link #readRegister} in a loop because it uses only
     * one serial round-trip regardless of how many registers are requested.
     * </p>
     *
     * <p>
     * Frame format transmitted:
     * </p>
     *
     * <pre>
     * [slave][0x03][start_hi][start_lo][count_hi][count_lo][crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * Response received ({@code 3 + count * 2 + 2} bytes):
     * </p>
     *
     * <pre>
     * [slave][0x03][byte_count][val0_hi][val0_lo]...[valN_hi][valN_lo][crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * Example — read 19 registers starting at 0x0000:
     * </p>
     *
     * <pre>
     * int[] regs = readRegisters(slave, 0x0000, 19);
     * double volts = regs[0] / 100.0;  // VSET at offset 0
     * double amps  = regs[1] / 1000.0; // ISET at offset 1
     * </pre>
     *
     * @param slave    Modbus slave address
     * @param startReg starting register address (0x0000–0xFFFF)
     * @param count    number of registers to read (1–32)
     * @return array of {@code count} raw 16-bit register values in address order
     * @throws Exception if a serial timeout occurs, the response is malformed, or CRC validation fails
     */
    public int[] readRegisters(final byte slave, final int startReg, final int count) throws Exception {
        byte[] frame = new byte[8];
        frame[0] = slave;
        frame[1] = ModbusFunctionCodes.READ_HOLDING_REGISTERS;
        frame[2] = (byte) (startReg >> 8);
        frame[3] = (byte) startReg;
        frame[4] = (byte) (count >> 8);
        frame[5] = (byte) count;
        int crc = ModbusCRC.calculate(frame, 6);
        frame[6] = (byte) crc;
        frame[7] = (byte) (crc >> 8);
        log("TX", frame, null);
        out.write(frame);
        out.flush();
        // Response: [slave][fc][byte_count][val_hi][val_lo]... × count [crc_lo][crc_hi]
        byte[] resp = readBytes(3 + count * 2 + 2);
        verifyCRC(resp);
        log("RX", resp, null);
        final int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = ((resp[3 + i * 2] & 0xFF) << 8) | (resp[4 + i * 2] & 0xFF);
        }
        return values;
    }

    /**
     * Writes 16-bit values to multiple consecutive holding registers in a single Modbus RTU frame.
     *
     * <p>
     * Sends a single Modbus "Write Multiple Registers" request (function code 0x10) for
     * {@code values.length} consecutive registers starting at {@code startReg}.
     * This is more efficient than calling {@link #writeRegister} in a loop when two or more
     * adjacent registers must be updated atomically (e.g. VSET and ISET).
     * </p>
     *
     * <p>
     * Frame format transmitted ({@code 7 + values.length * 2 + 2} bytes):
     * </p>
     *
     * <pre>
     * [slave][0x10][start_hi][start_lo][qty_hi][qty_lo][byte_count][val0_hi][val0_lo]...[crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * The device acknowledges with a fixed 8-byte response echoing the start address and quantity:
     * </p>
     *
     * <pre>
     * [slave][0x10][start_hi][start_lo][qty_hi][qty_lo][crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * Example — write VSET and ISET atomically on a Sinilink (addresses 0x0000–0x0001):
     * </p>
     *
     * <pre>
     * writeRegisters(slave, 0x0000, new int[] { 500, 2500 }); // 5.00 V, 2.500 A
     * </pre>
     *
     * @param slave    Modbus slave address
     * @param startReg starting register address (0x0000–0xFFFF)
     * @param values   raw 16-bit values to write, one per register in address order (1–32 elements)
     * @throws Exception if the device does not respond, the response is invalid, or CRC verification fails
     */
    public void writeRegisters(final byte slave, final int startReg, final int[] values) throws Exception {
        final int qty = values.length;
        final int byteCount = qty * 2;
        // Frame: [slave][0x10][start_hi][start_lo][qty_hi][qty_lo][byte_count][data×byteCount][crc_lo][crc_hi]
        byte[] frame = new byte[7 + byteCount + 2];
        frame[0] = slave;
        frame[1] = ModbusFunctionCodes.WRITE_MULTIPLE_REGISTERS;
        frame[2] = (byte) (startReg >> 8);
        frame[3] = (byte) startReg;
        frame[4] = (byte) (qty >> 8);
        frame[5] = (byte) qty;
        frame[6] = (byte) byteCount;
        for (int i = 0; i < qty; i++) {
            frame[7 + i * 2]     = (byte) (values[i] >> 8);
            frame[7 + i * 2 + 1] = (byte) values[i];
        }
        int crc = ModbusCRC.calculate(frame, frame.length - 2);
        frame[frame.length - 2] = (byte) crc;
        frame[frame.length - 1] = (byte) (crc >> 8);
        log("TX", frame, null);
        out.write(frame);
        out.flush();
        // Response: [slave][0x10][start_hi][start_lo][qty_hi][qty_lo][crc_lo][crc_hi]
        byte[] resp = readBytes(8);
        verifyCRC(resp);
        log("RX", resp, null);
    }

    /**
     * Writes a 16-bit value to a holding register using Modbus RTU.
     *
     * <p>
     * This method uses the Modbus "Write Single Register" command (function code 0x06).
     * </p>
     *
     * <p>
     * Frame format transmitted:
     * </p>
     *
     * <pre>
     * [slave][0x06][reg_hi][reg_lo][value_hi][value_lo][crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * The device echoes the same frame back if the write operation was accepted.
     * </p>
     *
     * <p>
     * Many Sinilink parameters require scaled integer values.
     * </p>
     *
     * <p>
     * Examples:
     * </p>
     *
     * <pre>
     * // Set voltage to 5.00 V
     * writeRegister(REG_VSET, 500);
     *
     * // Set current to 2.500 A
     * writeRegister(REG_ISET, 2500);
     *
     * // Enable output
     * writeRegister(REG_OUTPUT_ENABLE, 1);
     * </pre>
     *
     * <p>
     * Voltage scaling used by the device:
     * </p>
     *
     * <ul>
     * <li>Voltage: raw / 100 = volts</li>
     * <li>Current: raw / 1000 = amperes</li>
     * </ul>
     *
     * @param slave address of slave
     * @param reg   Register address to write.
     * @param value Raw 16-bit value to write to the register.
     * @throws Exception If the device does not respond, the response frame is invalid, or the CRC verification fails.
     */
    public void writeRegister(final byte slave, final int reg, final int value) throws Exception {
        byte[] frame = new byte[8];
        frame[0] = slave;
        frame[1] = ModbusFunctionCodes.WRITE_SINGLE_REGISTER;
        frame[2] = (byte) (reg >> 8);
        frame[3] = (byte) reg;
        frame[4] = (byte) (value >> 8);
        frame[5] = (byte) value;
        int crc = ModbusCRC.calculate(frame, 6);
        frame[6] = (byte) crc;
        frame[7] = (byte) (crc >> 8);
        log("TX", frame, null);
        out.write(frame);
        out.flush();
        byte[] resp = readBytes(8);
        verifyCRC(resp);
        log("RX", resp, null);
    }

    @Deprecated
    void log(final String dir, final byte[] data) {
        log(dir, data, null);
    }

    /**
     * Logs a Modbus frame via SLF4J, automatically appending a human-readable annotation
     * decoded from the frame bytes themselves.
     *
     * <p>Log levels:</p>
     * <ul>
     * <li>Raw hex bytes (TX/RX) → {@code DEBUG} - visible in the log file, suppressed on the console.</li>
     * <li>Decoded annotation ({@code -> Value = …}) → {@code TRACE} - log file only, deepest detail.</li>
     * </ul>
     *
     * <p>Example output:</p>
     * <pre>
     * DEBUG TX  01 03 00 17 00 01 34 0E
     * TRACE     -> Read 0x0017
     * DEBUG RX  01 03 02 00 6E B8 C2
     * TRACE     -> Value = 110 (0x006E)
     * </pre>
     *
     * @param dir  direction label, e.g. {@code "TX"} or {@code "RX"}
     * @param data frame bytes to format as hex
     * @param hint optional extra annotation appended after auto-decoded info, or {@code null}
     */
    @Deprecated
    void log(final String dir, final byte[] data, final String hint) {
        // Raw hex bytes at DEBUG - file only, not on console.
        if (logger.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder(dir).append("  ");
            for (byte b : data)
                sb.append(String.format("%02X ", b));
            logger.debug(sb.toString());
        }

        // Decoded annotation at TRACE - file only, deepest detail level.
        if (logger.isTraceEnabled()) {
            String auto = decodeFrame(dir, data);
            if (auto != null || hint != null) {
                StringBuilder detail = new StringBuilder("    -> ");
                if (auto != null) detail.append(auto);
                if (auto != null && hint != null) detail.append(", ");
                if (hint != null) detail.append(hint);
                logger.trace(detail.toString());
            }
        }
    }

    /**
     * Derives a short human-readable annotation from a raw Modbus RTU frame.
     *
     * <p>Handles all three function codes used by the devices in this project:</p>
     * <ul>
     * <li>TX fc=0x03: single-register read → {@code "Read <name>"}; multi-register read →
     *     {@code "Read 0x0000–0x0012 (19 regs)"}.</li>
     * <li>TX fc=0x06: single-register write → {@code "Write <name> = <value>"}.</li>
     * <li>TX fc=0x10: multi-register write → {@code "Write 0x0000–0x0001 (2 regs)"}.</li>
     * <li>RX fc=0x03 (7 bytes, single-register response): {@code "Value = <n> (0x…)"}.</li>
     * <li>RX fc=0x03 (>7 bytes, multi-register response): {@code "Read 19 regs, 38 data bytes"}.</li>
     * <li>RX fc=0x06: echoed register address and value.</li>
     * <li>RX fc=0x10: echoed start address and quantity.</li>
     * </ul>
     * <p>Returns {@code null} for unrecognised frames or frames that are too short to decode.</p>
     *
     * @param dir  direction label ({@code "TX"} or {@code "RX"})
     * @param data raw frame bytes
     * @return annotation string, or {@code null} if the frame cannot be decoded
     */
    private static String decodeFrame(final String dir, final byte[] data) {
        if (data == null || data.length < 4) {
            return null;
        }
        final byte fc = data[1];
        if ("TX".equals(dir)) {
            if (data.length >= 6) {
                final int start = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                if (fc == ModbusFunctionCodes.READ_HOLDING_REGISTERS) {
                    final int count = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
                    if (count == 1) {
                        final String regName = DeviceRegister.REGISTRY.getOrDefault(start, String.format("0x%04X", start));
                        return "Read " + regName;
                    }
                    return String.format("Read 0x%04X–0x%04X (%d regs)", start, start + count - 1, count);
                }
                if (fc == ModbusFunctionCodes.WRITE_SINGLE_REGISTER) {
                    final String regName = DeviceRegister.REGISTRY.getOrDefault(start, String.format("0x%04X", start));
                    final int val = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
                    return String.format("Write %s = %d", regName, val);
                }
                if (fc == ModbusFunctionCodes.WRITE_MULTIPLE_REGISTERS && data.length >= 7) {
                    final int qty = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
                    return String.format("Write 0x%04X–0x%04X (%d regs)", start, start + qty - 1, qty);
                }
            }
        } else {
            if (fc == ModbusFunctionCodes.READ_HOLDING_REGISTERS) {
                if (data.length == 7) {
                    // Single-register response: [slave][0x03][0x02][val_hi][val_lo][crc×2]
                    final int val = ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
                    return String.format("Value = %d (0x%04X)", val, val);
                }
                if (data.length > 7) {
                    // Multi-register response: [slave][0x03][byte_count][data...][crc×2]
                    final int byteCount = data[2] & 0xFF;
                    return String.format("Read %d regs, %d data bytes", byteCount / 2, byteCount);
                }
            }
            // RX: fc=0x06 write echo — [slave][0x06][reg_hi][reg_lo][val_hi][val_lo][crc×2]
            if (fc == ModbusFunctionCodes.WRITE_SINGLE_REGISTER && data.length >= 6) {
                final int reg = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                final String regName = DeviceRegister.REGISTRY.getOrDefault(reg, String.format("0x%04X", reg));
                final int val = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
                return String.format("Write %s = %d", regName, val);
            }
            // RX: fc=0x10 ack — [slave][0x10][start_hi][start_lo][qty_hi][qty_lo][crc×2]
            if (fc == ModbusFunctionCodes.WRITE_MULTIPLE_REGISTERS && data.length == 8) {
                final int start = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                final int qty   = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
                return String.format("Wrote 0x%04X–0x%04X (%d regs)", start, start + qty - 1, qty);
            }
        }
        return null;
    }

    /**
     * Read {@code n} bytes from {@link ModbusTransport#in}.
     * 
     * @param n number of bytes to read
     * @return byte[] of bytes read
     * @throws Exception
     */
    private byte[] readBytes(final int n) throws Exception {
        byte[] buf = new byte[n];
        int pos = 0;
        while (pos < n) {
            int r = in.read(buf, pos, n - pos);
            if (r < 0)
                throw new RuntimeException("Serial timeout");
            pos += r;
        }
        return buf;
    }

    /**
     * Write {@code frame} to {@link ModbusTransport#out}.
     * 
     * @param frame to write
     */
    private void verifyCRC(final byte[] frame) {
        int len = frame.length;
        int calc = ModbusCRC.calculate(frame, len - 2);
        int received = ((frame[len - 1] & 0xFF) << 8) | (frame[len - 2] & 0xFF);
        if (calc != received)
            throw new RuntimeException("CRC mismatch");
    }

}

package com.serial.modbus;

import java.io.InputStream;
import java.io.OutputStream;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Handles low-level Modbus RTU communication over serial.
 */
public class ModbusTransport {

    /** {@link SerialPort} the {@code Modbus} device is connected to. */
    private final SerialPort port;

    /** {@link InputStream} reading from {@link ModbusTransport#port}. */
    private final InputStream in;

    /** {@link InputStream} writing to {@link ModbusTransport#port}. */
    private final OutputStream out;

    /**
     * Constructor.
     * 
     * @param portName of port to open for attached {@code Modbus} device
     * @param baud     to set (typically {@code 115200} baud)
     * @throws Exception
     */
    public ModbusTransport(final String portName, final int baud) throws Exception {
        port = SerialPort.getCommPort(portName);
        port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);
        if (!port.openPort())
            throw new RuntimeException("Cannot open serial port");
        in = port.getInputStream();
        out = port.getOutputStream();
    }

    /**
     * Close port connected to {@code Modbus} device.
     */
    public void close() {
        port.closePort();
    }

    /**
     * Reads a single 16-bit holding register from the XY6008Old using Modbus RTU.
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
        frame[0] = slave; // ModbusConstants.SLAVE_ADDRESS;
        frame[1] = ModbusFunctionCodes.READ_HOLDING_REGISTERS;
        frame[2] = (byte) (reg >> 8);
        frame[3] = (byte) reg;
        frame[4] = 0;
        frame[5] = 1;
        int crc = ModbusCRC.calculate(frame, 6);
        frame[6] = (byte) crc;
        frame[7] = (byte) (crc >> 8);
        log("TX", frame);
        out.write(frame);
        out.flush();
        byte[] resp = readBytes(7);
        log("RX", resp);
        verifyCRC(resp);
        return ((resp[3] & 0xFF) << 8) | (resp[4] & 0xFF);
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
     * Many XY6008Old parameters require scaled integer values.
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
    public void writeRegister(final byte slave, final int reg, int value) throws Exception {
        byte[] frame = new byte[8];
        frame[0] = slave; // ModbusConstants.SLAVE_ADDRESS;
        frame[1] = ModbusFunctionCodes.WRITE_SINGLE_REGISTER;
        frame[2] = (byte) (reg >> 8);
        frame[3] = (byte) reg;
        frame[4] = (byte) (value >> 8);
        frame[5] = (byte) value;
        int crc = ModbusCRC.calculate(frame, 6);
        frame[6] = (byte) crc;
        frame[7] = (byte) (crc >> 8);
        log("TX", frame);
        
        out.write(frame);
        out.flush();
        byte[] resp = readBytes(8);
        log("RX", resp);
        verifyCRC(resp);
    }

    // TODO: Improve logging
    @Deprecated
    void log(String dir, byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data)
            sb.append(String.format("%02X ", b));
        System.out.println(dir + "  " + sb);
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

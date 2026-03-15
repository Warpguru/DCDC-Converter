package com.serial;

import java.io.InputStream;
import java.io.OutputStream;

import com.fazecast.jSerialComm.SerialPort;

/**
 * XY6008Old {@code Modbus} to {@code TTL} 3.3V {@code serial} connection.
 * 
 * <ul>
 * <li>XY6008Old Black: → Gnd
 * <li>XY6008Old Green: → TxD
 * <li>XY6008Old Yellow: → RxD
 * <li>Red: NC (5V)
 * </ul>
 */
public class TestXY6008 {

    static final int BAUD = 115200;
    static final int SLAVE = 1;

    /**
     * Register map for the Sinilink XY6008Old programmable power supply.
     *
     * Communication protocol: Modbus RTU over serial.
     *
     * All registers are 16-bit unsigned integers. Multi-byte values are transmitted big-endian.
     *
     * Voltage scaling: raw_value / 100 = volts
     *
     * Current scaling: raw_value / 1000 = amperes
     *
     * Example:
     *
     * 5.00 V -> write 500 3.30 V -> write 330 2.000 A -> write 2000
     */
    public final class XY6008Registers {

        private XY6008Registers() {
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
         * Firmware version register.
         *
         * Read only.
         *
         * Used for device identification.
         *
         * Typical values: 10 – 100
         *
         * Used by {@link TestXY6008#verifyDevicePresent()} to confirm that the attached serial device is an XY6008Old compatible
         * unit.
         */
        public static final int REG_FIRMWARE = 0x0005;

        /**
         * Hardware version register.
         *
         * Read only.
         *
         * Alternative device identification register. Used by {@link TestXY6008#verifyDevicePresent()} if firmware register
         * cannot be retrieved.
         */
        public static final int REG_HARDWARE = 0x0006;

        /**
         * Input voltage measurement.
         *
         * Read only.
         *
         * Voltage supplied to the converter input.
         *
         * Scaling: raw / 100 = volts
         */
        public static final int REG_VIN = 0x0007;

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
        public static final int REG_OUTPUT_ENABLE = 0x0008;

        /**
         * Device protection state.
         *
         * Read only.
         *
         * Values:
         * <ul>
         * <li>0 -> normal
         * <li>1 -> over-voltage protection
         * <li>2 -> over-current protection
         * <li>3 -> over-power protection
         * <li>4 -> over-temperature protection
         * </ul>
         */
        public static final int REG_PROTECTION_STATE = 0x0009;

        /**
         * Device temperature.
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
        public static final int REG_TEMPERATURE = 0x000A;
    }

    /**
     * Modbus RTU function codes used by the XY6008Old.
     *
     * <p>
     * The device implements a subset of the {@code Modbus} RTU protocol. Only the following commands are required for normal
     * operation.
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

    static final int MAX_RETRY = 3;

    SerialPort port;
    InputStream in;
    OutputStream out;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java XY6008Tester <serialPort>");
            return;
        }
        TestXY6008 testXY6008 = new TestXY6008();
        testXY6008.open(args[0]);
        if (!testXY6008.verifyDevicePresent()) {
            System.out.println("No XY6008Old detected on this port.");
            testXY6008.close();
            return;
        }
        for (int i = 1; i <= 3; i++) {
            System.out.println("\n=== TEST CYCLE " + i + " ===");
            testXY6008.setVoltageVerified(5.0);
            double voltage = testXY6008.getOutputVoltage();
            double current = testXY6008.getOutputCurrent();
            double power = testXY6008.getOutputPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);            
            testXY6008.sleepSeconds(1);
            
            testXY6008.setVoltageVerified(3.3);
            testXY6008.sleepSeconds(1);
            voltage = testXY6008.getOutputVoltage();
            current = testXY6008.getOutputCurrent();
            power = testXY6008.getOutputPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);            
        }
        testXY6008.close();
    }

    void open(String portName) throws Exception {
        port = SerialPort.getCommPort(portName);
        port.setComPortParameters(BAUD, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);
        if (!port.openPort())
            throw new RuntimeException("Cannot open port");
        in = port.getInputStream();
        out = port.getOutputStream();
        System.out.println("Opened " + portName);
    }

    void close() {
        port.closePort();
        System.out.println("Serial closed");
    }

    /**
     * Sets the voltage setpoint of the XY6008Old.
     *
     * <p>
     * The device expects the voltage value as an integer with a scaling factor of 100.
     * </p>
     *
     * <p>
     * Example conversions:
     * </p>
     *
     * <pre>
     * 5.00 V -> 500
     * 3.30 V -> 330
     * 12.00 V -> 1200
     * </pre>
     *
     * @param volts desired output voltage in volts
     * @throws Exception if communication fails
     */
    public void setVoltage(double volts) throws Exception {
        int raw = (int) Math.round(volts * 100);
        writeRegister(XY6008Registers.REG_VSET, raw);
    }

    /**
     * Returns the configured voltage setpoint.
     *
     * @return voltage in volts
     */
    public double getVoltageSetpoint() throws Exception {
        int raw = readRegister(XY6008Registers.REG_VSET);
        return raw / 100.0;
    }

    /**
     * Sets the current limit of the power supply.
     *
     * <p>
     * The XY6008Old expects current values scaled by 1000.
     * </p>
     *
     * <pre>
     * 1.000 A -> 1000
     * 2.500 A -> 2500
     * </pre>
     *
     * @param amps current limit in amperes
     */
    public void setCurrent(double amps) throws Exception {
        int raw = (int) Math.round(amps * 1000);
        writeRegister(XY6008Registers.REG_ISET, raw);
    }

    /**
     * Returns the configured current limit.
     *
     * @return current limit in amperes
     */
    public double getCurrentSetpoint() throws Exception {
        int raw = readRegister(XY6008Registers.REG_ISET);
        return raw / 1000.0;
    }

    /**
     * Reads the actual measured output voltage.
     *
     * @return voltage in volts
     */
    public double getOutputVoltage() throws Exception {
        int raw = readRegister(XY6008Registers.REG_VOUT);
        return raw / 100.0;
    }

    /**
     * Reads the measured output current.
     *
     * @return current in amperes
     */
    public double getOutputCurrent() throws Exception {
        int raw = readRegister(XY6008Registers.REG_IOUT);
        return raw / 1000.0;
    }

    /**
     * Reads the measured output power.
     *
     * @return power in watts
     */
    public double getOutputPower() throws Exception {
        int raw = readRegister(XY6008Registers.REG_POUT);
        return raw / 100.0;
    }
    
    /**
     * Reads the firmware version of the device.
     *
     * @return firmware version number
     */
    public int getFirmwareVersion() throws Exception {
        return readRegister(XY6008Registers.REG_FIRMWARE);
    }

    /**
     * Reads the hardware version of the device.
     *
     * @return hardware version number
     */
    public int getHardwareVersion() throws Exception {
        return readRegister(XY6008Registers.REG_HARDWARE);
    }    

    /**
     * Reads the input voltage supplied to the power module.
     *
     * @return input voltage in volts
     */
    public double getInputVoltage() throws Exception {
        int raw = readRegister(XY6008Registers.REG_VIN);
        return raw / 100.0;
    }

    /**
     * Enables or disables the power supply output.
     *
     * <p>
     * Register values:
     * </p>
     *
     * <ul>
     * <li>0 = output disabled</li>
     * <li>1 = output enabled</li>
     * </ul>
     *
     * @param enabled true to enable output
     */
    public void setOutputEnabled(boolean enabled) throws Exception {
        writeRegister(XY6008Registers.REG_OUTPUT_ENABLE, enabled ? 1 : 0);
    }

    /**
     * Returns the current output enable state.
     *
     * @return true if output is enabled
     */
    public boolean isOutputEnabled() throws Exception {
        int val = readRegister(XY6008Registers.REG_OUTPUT_ENABLE);
        return val != 0;
    }

    /**
     * Returns the protection state of the device.
     *
     * Possible values:
     *
     * <ul>
     * <li>0 = normal</li>
     * <li>1 = over-voltage protection</li>
     * <li>2 = over-current protection</li>
     * <li>3 = over-power protection</li>
     * <li>4 = over-temperature protection</li>
     * </ul>
     *
     * @return protection state code
     */
    public int getProtectionState() throws Exception {
        return readRegister(XY6008Registers.REG_PROTECTION_STATE);
    }
    
    /**
     * Returns the internal device temperature.
     *
     * <p>
     * The value is scaled by 10.
     * </p>
     *
     * <pre>
     * 350 -> 35.0 °C
     * </pre>
     *
     * @return temperature in °C
     */
    public double getTemperature() throws Exception {
        int raw = readRegister(XY6008Registers.REG_TEMPERATURE);
        return raw / 10.0;
    }
    
    boolean verifyDevicePresent() {
        System.out.println("Checking for XY6008Old device...");
        boolean devicePresent = false;
        // Try firmware register
        try {
            int fw = readRegister(XY6008Registers.REG_FIRMWARE);
            System.out.println("Firmware version register read: " + fw);
            if (fw >= 0 && fw < 10000) {
                System.out.println("Device detected via firmware register.");
                devicePresent = true;
            }
        } catch (Exception e) {
            System.out.println("Firmware register read failed: " + e.getMessage());
        }
        // Try hardware register
        try {
            int hw = readRegister(XY6008Registers.REG_HARDWARE);
            System.out.println("Hardware version register read: " + hw);
            if (hw >= 0 && hw < 10000) {
                System.out.println("Device detected via hardware register.");
                devicePresent = true;
            }
        } catch (Exception e) {
            System.out.println("Hardware register read failed: " + e.getMessage());
        }
        if (devicePresent == false) {
            System.out.println("No XY6008Old detected.");
        } else {
            // Device detected
        }
        return devicePresent;
    }

    void setVoltageVerified(double volts) throws Exception {
        int raw = (int) Math.round(volts * 100);
        System.out.println("\nSetting voltage → " + volts + " V");
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            writeRegister(XY6008Registers.REG_VSET, raw);
            Thread.sleep(200);
            int readSet = readRegister(XY6008Registers.REG_VSET);
            int readOut = readRegister(XY6008Registers.REG_VOUT);
            double setV = readSet / 100.0;
            double outV = readOut / 100.0;
            System.out.println("Attempt " + attempt + " SET=" + setV + "V OUT=" + outV + "V");
            if (readSet == raw) {
                System.out.println("Voltage verified");
                return;
            }
        }
        throw new RuntimeException("Failed to set voltage");
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
     * @param reg   Register address to write.
     *
     * @param value Raw 16-bit value to write to the register.
     *
     * @throws Exception If the device does not respond, the response frame is invalid, or the CRC verification fails.
     */
    void writeRegister(final int reg, final int value) throws Exception {
        byte[] frame = new byte[8];
        frame[0] = (byte) SLAVE;
        frame[1] = ModbusFunctionCodes.WRITE_SINGLE_REGISTER;
        frame[2] = (byte) (reg >> 8);
        frame[3] = (byte) reg;
        frame[4] = (byte) (value >> 8);
        frame[5] = (byte) value;
        int crc = crc(frame, 6);
        frame[6] = (byte) crc;
        frame[7] = (byte) (crc >> 8);
        log("TX", frame);

        out.write(frame);
        out.flush();
        byte[] resp = readBytes(8);
        log("RX", resp);
        verifyCRC(resp);
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
     * @param reg Register address to read (0x0000 – 0xFFFF).
     *
     * @return The raw 16-bit register value returned by the device.
     *
     * @throws Exception If a serial timeout occurs, the Modbus response is malformed, or the CRC validation fails.
     */
    int readRegister(final int reg) throws Exception {
        byte[] frame = new byte[8];
        frame[0] = (byte) SLAVE;
        frame[1] = ModbusFunctionCodes.READ_HOLDING_REGISTERS;
        frame[2] = (byte) (reg >> 8);
        frame[3] = (byte) reg;
        frame[4] = 0;
        frame[5] = 1;
        int crc = crc(frame, 6);
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

    byte[] readBytes(int n) throws Exception {
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

    void verifyCRC(byte[] frame) {
        int len = frame.length;
        int crcCalc = crc(frame, len - 2);
        int crcFrame = ((frame[len - 1] & 0xFF) << 8) | (frame[len - 2] & 0xFF);
        if (crcCalc != crcFrame)
            throw new RuntimeException("CRC mismatch");
    }

    /**
     * Calculates the Modbus RTU CRC-16 checksum for a frame.
     *
     * <p>
     * The XY6008Old uses the standard Modbus CRC algorithm with polynomial 0xA001 and an initial value of 0xFFFF.
     * </p>
     *
     * <p>
     * The CRC is calculated over all bytes in the frame except the CRC field itself.
     * </p>
     *
     * <p>
     * Example frame before CRC:
     * </p>
     *
     * <pre>
     * 01 03 00 02 00 01
     * </pre>
     *
     * <p>
     * The resulting CRC is appended in little-endian order:
     * </p>
     *
     * <pre>
     * [crc_lo][crc_hi]
     * </pre>
     *
     * <p>
     * Example full frame:
     * </p>
     *
     * <pre>
     * 01 03 00 02 00 01 25 CA
     * </pre>
     *
     * @param data Byte array containing the Modbus frame.
     *
     * @param len  Number of bytes to include in the CRC calculation (typically the frame length excluding the CRC bytes).
     *
     * @return 16-bit CRC value. The low byte is transmitted first.
     */
    int crc(byte[] data, int len) {
        int crc = 0xFFFF;
        for (int i = 0; i < len; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0)
                    crc = (crc >> 1) ^ 0xA001;
                else
                    crc >>= 1;
            }
        }
        return crc;
    }

    void log(String dir, byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data)
            sb.append(String.format("%02X ", b));
        System.out.println(dir + "  " + sb);
    }

    void sleepSeconds(final int seconds) throws Exception {
        System.out.println("Waiting " + seconds + " seconds...");
        Thread.sleep(seconds * 1000);
    }

}

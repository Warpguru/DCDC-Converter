package com.serial.modbus;

/**
 * Utility class for calculating Modbus RTU CRC16 checksums.
 */
public final class ModbusCRC {

    private ModbusCRC() {
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
    public static int calculate(final byte[] data, final int len) {
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

}

package com.serial;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serial Controller Application - Iteration 1.
 * <p>
 * Proof of concept that prints "Hello World" and enumerates
 * all available serial ports on the system.
 */
public class SerialControllerApp {

    private static final Logger logger = LoggerFactory.getLogger(SerialControllerApp.class);

    public static void main(String[] args) {
        // Hello World
        String helloMessage = "Hello World";
        System.out.println(helloMessage);
        logger.info(helloMessage);

        // Enumerate serial ports
        logger.info("Enumerating serial ports...");
        SerialPort[] ports = SerialPort.getCommPorts();

        if (ports.length == 0) {
            String noPortsMessage = "No serial ports found on this system.";
            System.out.println(noPortsMessage);
            logger.warn(noPortsMessage);
        } else {
            String foundMessage = String.format("Found %d serial port(s):", ports.length);
            System.out.println(foundMessage);
            logger.info(foundMessage);

            for (SerialPort port : ports) {
                printPortDetails(port);
            }
        }

        logger.info("Serial Controller finished.");
    }

    /**
     * Prints detailed information about a serial port to both console and log.
     * Handles gracefully when fields return null or empty (e.g. on Linux or
     * for non-USB serial ports).
     *
     * @param port the serial port to display details for
     */
    private static void printPortDetails(SerialPort port) {
        String name = port.getSystemPortName();
        String description = port.getDescriptivePortName();
        String location = valueOrNA(port.getPortLocation());
        String manufacturer = valueOrNA(port.getManufacturer());
        String serialNumber = valueOrNA(port.getSerialNumber());
        int vid = port.getVendorID();
        int pid = port.getProductID();
        String usbId = (vid != 0 || pid != 0)
                ? String.format("0x%04X:0x%04X", vid, pid)
                : "N/A";

        System.out.println("  -----------------------------------------");
        System.out.printf("  Port:         %s%n", name);
        System.out.printf("  Description:  %s%n", description);
        System.out.printf("  Location:     %s%n", location);
        System.out.printf("  Manufacturer: %s%n", manufacturer);
        System.out.printf("  Serial No:    %s%n", serialNumber);
        System.out.printf("  USB VID:PID:  %s%n", usbId);

        logger.info("Port: {} | Description: {} | Location: {} | Manufacturer: {} | Serial: {} | VID:PID: {}",
                name, description, location, manufacturer, serialNumber, usbId);
    }

    /**
     * Returns the given value if it is non-null and non-empty, otherwise "N/A".
     *
     * @param value the value to check
     * @return the value or "N/A"
     */
    private static String valueOrNA(String value) {
        return (value != null && !value.isEmpty()) ? value : "N/A";
    }
}

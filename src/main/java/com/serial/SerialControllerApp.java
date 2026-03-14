package com.serial;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serial.devices.XY6008;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

/**
 * Serial Controller Application - Iteration 1.
 * <p>
 * Proof of concept that prints "Hello World" and enumerates all available serial ports on the system.
 */
public class SerialControllerApp {

    private static final Logger logger = LoggerFactory.getLogger(SerialControllerApp.class);

    // Keep track of connected clients to broadcast updates
    private static final Set<io.javalin.websocket.WsContext> clients = ConcurrentHashMap.newKeySet();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Current setting in amperes, adjustable via the GUI (0.00 – 2.00 A). */
    private static volatile double currentSetting = 2.00;

    public static void main(String[] args) throws Exception {
        logger.info("Serial Controller started.");
        SerialControllerApp serialControllerApp = new SerialControllerApp();
        serialControllerApp.process(args);
        logger.info("Serial Controller finished.");
    }

    private void process(final String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: XY6008TestTool <port>");
            return;
        }

        Javalin javalin = Javalin.create(config -> {
            config.jetty.port = 8000;
            // Serve ./public/* at /
            config.staticFiles.add("/public", Location.CLASSPATH);
            // Server HTTP GET /init
            config.routes.get("/init", this::init);

            // WebSocket endpoint for live data
            config.routes.ws("/ws/data", ws -> {
                ws.onConnect(ctx -> {
                    clients.add(ctx);
                    logger.info("WebSocket client connected (total: {})", clients.size());
                });
                ws.onMessage(ctx -> {
                    String msg = ctx.message();
                    logger.info("WebSocket message received: {}", msg);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> json = objectMapper.readValue(msg, Map.class);
                        if (json.containsKey("setCurrent")) {
                            double value = ((Number) json.get("setCurrent")).doubleValue();
                            // Clamp to 0.00 – 2.00 and round to 1 decimal
                            value = Math.round(Math.max(0, Math.min(2, value)) * 10.0) / 10.0;
                            currentSetting = value;
                            logger.info("Current setting updated to {} A", currentSetting);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse WebSocket message: {}", e.getMessage());
                    }
                });
                ws.onClose(ctx -> {
                    clients.remove(ctx);
                    logger.info("WebSocket client disconnected (total: {})", clients.size());
                });
                ws.onError(ctx -> {
                    clients.remove(ctx);
                    logger.warn("WebSocket error", ctx.error());
                });
            });

        }).start(8000);

        // Background thread to push Modbus data to all connected clients
        startUpdateThread();

        sleepSeconds(600);
        javalin.stop();

        ModbusTransport transport = null;
        try {
            transport = new ModbusTransport(args[0], ModbusConstants.BAUD);
        } catch (Exception e) {
            System.out.println("Usage: XY6008TestTool <port>");
            System.out.println("       Port: " + args[0] + " invalid!");
            return;
        }
        XY6008 xy6008 = new XY6008(transport, ModbusConstants.SLAVE_ADDRESS);
        if (!xy6008.verifyDevicePresent()) {
            System.out.println("No XY6008 detected on this port.");
            transport.close();
            return;
        }

        for (int i = 1; i <= 3; i++) {
            System.out.println("\n=== TEST CYCLE " + i + " ===");
            xy6008.setVoltage(5.0);
            double voltage = xy6008.getVoltage();
            double current = xy6008.getCurrent();
            double power = xy6008.getPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);
            sleepSeconds(1);

            xy6008.setVoltage(3.3);
            sleepSeconds(1);
            voltage = xy6008.getVoltage();
            current = xy6008.getCurrent();
            power = xy6008.getPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);
        }
        transport.close();
    }

    private static void startUpdateThread() {
        new Thread(() -> {
            while (true) {
                try {
                    // Simulate reading from Modbus
                    // String jsonUpdate = "{\"voltage\": 12.6, \"current\": 2.0}";
                    String jsonUpdate = String.format("{\"voltage\": %.2f, \"current\": %.2f}", Math.random() * 12, currentSetting);

                    // Broadcast to all active WebSocket clients
                    clients.forEach(client -> {
                        try {
                            client.send(jsonUpdate);
                        } catch (Exception e) {
                            logger.warn("Failed to send to client, removing: {}", e.getMessage());
                            clients.remove(client);
                        }
                    });

                    Thread.sleep(1000); // Push every second
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    public record Status(double voltage, double current) {
    }

    private void config(final Context ctx) {

    }

    private void init(final Context ctx) {
        ctx.result("Abracadabra");
    }

    private void log(String dir, byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data)
            sb.append(String.format("%02X ", b));
        System.out.println(dir + "  " + sb);
    }

    private void sleepSeconds(final int seconds) throws Exception {
        System.out.println("Waiting " + seconds + " seconds...");
        Thread.sleep(seconds * 1000);
    }

    /**
     * Prints detailed information about a serial port to both console and log. Handles gracefully when fields return null or
     * empty (e.g. on Linux or for non-USB serial ports).
     *
     * @param port the serial port to display details for
     */
    private void printPortDetails(SerialPort port) {
        String name = port.getSystemPortName();
        String description = port.getDescriptivePortName();
        String location = valueOrNA(port.getPortLocation());
        String manufacturer = valueOrNA(port.getManufacturer());
        String serialNumber = valueOrNA(port.getSerialNumber());
        int vid = port.getVendorID();
        int pid = port.getProductID();
        String usbId = (vid != 0 || pid != 0) ? String.format("0x%04X:0x%04X", vid, pid) : "N/A";

        System.out.println("  -----------------------------------------");
        System.out.printf("  Port:         %s%n", name);
        System.out.printf("  Description:  %s%n", description);
        System.out.printf("  Location:     %s%n", location);
        System.out.printf("  Manufacturer: %s%n", manufacturer);
        System.out.printf("  Serial No:    %s%n", serialNumber);
        System.out.printf("  USB VID:PID:  %s%n", usbId);

        logger.info("Port: {} | Description: {} | Location: {} | Manufacturer: {} | Serial: {} | VID:PID: {}", name,
                description, location, manufacturer, serialNumber, usbId);
    }

    /**
     * Returns the given value if it is non-null and non-empty, otherwise "N/A".
     *
     * @param value the value to check
     * @return the value or "N/A"
     */
    private String valueOrNA(String value) {
        return (value != null && !value.isEmpty()) ? value : "N/A";
    }
}

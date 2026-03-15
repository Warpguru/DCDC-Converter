package com.serial;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;
import com.serial.devices.DC2DCConverter;
import com.serial.devices.Sinilink;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;

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

        // Enumerate serial ports
        logger.info("Enumerating serial ports...");
        SerialPort[] serialPorts = SerialPort.getCommPorts();
        if (serialPorts.length == 0) {
            String noPortsMessage = "No serial ports found on this system.";
            System.out.println(noPortsMessage);
            logger.warn(noPortsMessage);
        } else {
            String foundMessage = String.format("Found %d serial port(s):", serialPorts.length);
            System.out.println(foundMessage);
            logger.info(foundMessage);

            for (SerialPort serialPort : serialPorts) {
                printPortDetails(serialPort);
                demoVoltages(serialPort.getSystemPortName());
            }
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

            // OpenAPI
            config.registerPlugin(new OpenApiPlugin(openApiConfig -> {
                openApiConfig.withDefinitionConfiguration((version, definition) -> {
                    definition.info(info -> info.title("SerialController").version("1.0.0"));
                });
            }));

            // This makes the UI available at /swagger
            config.registerPlugin(new SwaggerPlugin());

        }).start(8000);

        // Background thread to push Modbus data to all connected clients
        startUpdateThread();

        sleepSeconds(600);
        javalin.stop();
    }

    private static void startUpdateThread() {
        new Thread(() -> {
            while (true) {
                try {
                    // Simulate reading from Modbus
                    // String jsonUpdate = "{\"voltage\": 12.6, \"current\": 2.0}";
                    String jsonUpdate = String.format("{\"voltage\": %.2f, \"current\": %.2f}", Math.random() * 12,
                            currentSetting);

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

    public void config(final Context ctx) {

    }

    // @formatter:off
    @OpenApi(
            path = "/init",
            methods = HttpMethod.GET,
            summary = "Get all users",
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = String.class))
            }
        )
    // @formatter:on
    public void init(final Context ctx) {
        ctx.result("Abracadabra");
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
     * Demo setting output voltage alternatively to {@code 5V} and {@code 3.3V}.
     * 
     * @param portName to use
     * @throws Exception
     */
    @Deprecated
    private void demoVoltages(@Deprecated final String portName) throws Exception {
        ModbusTransport transport = null;
        try {
            transport = new ModbusTransport(portName, ModbusConstants.BAUD);
        } catch (Exception e) {
            System.out.println("Usage: XY6008TestTool <port>");
            System.out.println("       Port: " + portName + " invalid!");
            return;
        }
        Sinilink sinilink = new Sinilink(transport, ModbusConstants.SLAVE_ADDRESS);
        if (!sinilink.verifyDevicePresent()) {
            System.out.println("No Sinilink detected on this port.");
            transport.close();
            return;
        }

        DC2DCConverter dc2dcConverter = sinilink;
        for (int i = 1; i <= 3; i++) {
            System.out.println("\n=== TEST CYCLE " + i + " ===");
            dc2dcConverter.setVoltageVerified(5.0);
            double voltage = dc2dcConverter.getVoltage();
            double current = dc2dcConverter.getCurrent();
            double power = dc2dcConverter.getPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);
            sleepSeconds(1);

            dc2dcConverter.setVoltageVerified(3.3);
            sleepSeconds(1);
            voltage = dc2dcConverter.getVoltage();
            current = dc2dcConverter.getCurrent();
            power = dc2dcConverter.getPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);
        }
        transport.close();
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

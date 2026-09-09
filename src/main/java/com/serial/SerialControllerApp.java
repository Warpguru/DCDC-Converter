package com.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.serial.service.ConverterState;
import com.serial.service.DeviceService;
import com.serial.service.RestService;
import com.serial.service.WebSocketService;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serial Controller Application — entry point.
 *
 * <p>
 * This class is responsible only for wiring up the Javalin HTTP/WebSocket server and the service
 * layer. All business logic and state are managed by the {@code com.serial.service} package.
 * </p>
 *
 * <p>
 * Iteration history:
 * </p>
 * <ul>
 * <li>Iteration 1 — proof of concept: serial port enumeration.</li>
 * <li>Iteration 2 — Javalin HTTP server + static index.html.</li>
 * <li>Iteration 3 — WebSocket live data push.</li>
 * <li>Iteration 4 — current control widget in the webpage.</li>
 * <li>Iteration 5 — service layer: {@link DeviceService}, {@link ConverterState}, device properties files.</li>
 * <li>Iteration 6 — {@link RestService}: full CRUD REST API with OpenAPI documentation.</li>
 * <li>Iteration 7 — {@link WebSocketService}: WebSocket handler extracted from app; full state push.</li>
 * </ul>
 */
public class SerialControllerApp {

    private static final Logger logger = LoggerFactory.getLogger(SerialControllerApp.class);

    /** The service layer — owns the converter, polling thread, and ConverterState. */
    private DeviceService deviceService;

    /** WebSocket service — owns connected clients and the broadcast thread. */
    private WebSocketService webSocketService;

    /**
     * Monitor used to let the main thread sleep until the 600-second timeout elapses or until
     * the {@code /api/exit} handler wakes it early by calling {@code notifyAll()}.
     */
    private final Object shutdownLock = new Object();

    public static void main(final String[] args) throws Exception {
        logger.info("Serial Controller started.");
        SerialControllerApp app = new SerialControllerApp();
        app.process(args);
        logger.info("Serial Controller finished.");
    }

    private void process(final String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: SerialController <port>");
            return;
        }

        final String portName = args[0];

        // Enumerate serial ports for diagnostics.
        logger.info("Enumerating serial ports...");
        SerialPort[] serialPorts = SerialPort.getCommPorts();
        if (serialPorts.length == 0) {
            String msg = "No serial ports found on this system.";
            System.out.println(msg);
            logger.warn(msg);
        } else {
            logger.info("Found {} serial port(s):", serialPorts.length);
            for (SerialPort serialPort : serialPorts) {
                printPortDetails(serialPort);
            }
            // NOTE: demoVoltages() is no longer called here — it opened its own ModbusTransport
            // on every discovered port, which conflicts with DeviceService acquiring the transport
            // exclusively. Removed in Iteration 5; method body cleared, signature retained @Deprecated.
        }

        // Initialise the service layer — detects device, loads limits, reads initial setpoints.
        deviceService    = new DeviceService(portName);
        webSocketService = new WebSocketService(deviceService, deviceService.getObjectMapper());

        final RestService restService = new RestService(deviceService);

        Javalin javalin = Javalin.create(config -> {
            config.jetty.port = 8000;

            // Serve ./public/* at /
            config.staticFiles.add("/public", Location.CLASSPATH);

            // REST API routes
            restService.registerRoutes(config.routes);

            // WebSocket endpoint — all handling delegated to WebSocketService
            config.routes.ws("/ws/data", ws -> {
                ws.onConnect(webSocketService::onConnect);
                ws.onMessage(webSocketService::onMessage);
                ws.onClose(webSocketService::onClose);
                ws.onError(webSocketService::onError);
            });

            // OpenAPI JSON endpoint at /openapi
            config.registerPlugin(new OpenApiPlugin(openApiConfig -> {
                openApiConfig.withDocumentationPath("/openapi");
                openApiConfig.withDefinitionConfiguration((version, definition) -> {
                    definition.info(info -> info.title("SerialController").version("1.0.0"));
                    definition.withBasicAuth("BasicAuth");
                });
            }));

            // Swagger UI at /openapi/ui
            config.registerPlugin(new SwaggerPlugin(swaggerConfig -> {
                swaggerConfig.withDocumentationPath("/openapi");
                swaggerConfig.withUiPath("/openapi/ui");
            }));

        }).start(8000);

        // Give the exit handler a reference to shut down the server and wake the main thread.
        restService.setShutdown(javalin, () -> {
            synchronized (shutdownLock) {
                shutdownLock.notifyAll();
            }
        });

        // Start the Modbus polling thread and the WebSocket broadcast thread.
        deviceService.start();
        webSocketService.start();

        // Sleep until the 600-second timeout expires or /api/exit wakes us early.
        synchronized (shutdownLock) {
            shutdownLock.wait(600_000L);
        }

        webSocketService.stop();
        deviceService.stop();
        javalin.stop();
    }

    /**
     * Prints detailed information about a serial port to both console and log.
     *
     * <p>
     * Handles gracefully when fields return null or empty (e.g. on Linux or for non-USB serial ports).
     * </p>
     *
     * @param port the serial port to display details for
     */
    private void printPortDetails(final SerialPort port) {
        String name         = port.getSystemPortName();
        String description  = port.getDescriptivePortName();
        String location     = valueOrNA(port.getPortLocation());
        String manufacturer = valueOrNA(port.getManufacturer());
        String serialNumber = valueOrNA(port.getSerialNumber());
        int    vid          = port.getVendorID();
        int    pid          = port.getProductID();
        String usbId        = (vid != 0 || pid != 0) ? String.format("0x%04X:0x%04X", vid, pid) : "N/A";

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
     * Demo method for setting output voltage — kept for reference only.
     *
     * <p>
     * This method is no longer called. It was removed from {@link #process(String[])} in Iteration 5
     * because it opens its own {@code ModbusTransport}, which conflicts with {@link DeviceService}
     * holding exclusive transport ownership. Retained here with {@code @Deprecated} per project
     * convention. Full resolution in Iteration 9.
     * </p>
     *
     * @param portName port to use (unused — kept for signature compatibility)
     * @throws Exception never in normal operation; inherited from old implementation
     */
    @Deprecated
    @SuppressWarnings("unused")
    private void demoVoltages(@Deprecated final String portName) throws Exception {
        // Removed call site in Iteration 5 — see class Javadoc.
        // This method conflicts with DeviceService transport ownership and must not be called.
    }

    /**
     * Returns the given value if it is non-null and non-empty, otherwise {@code "N/A"}.
     *
     * @param value the value to check
     * @return the value or {@code "N/A"}
     */
    private String valueOrNA(final String value) {
        return (value != null && !value.isEmpty()) ? value : "N/A";
    }
}

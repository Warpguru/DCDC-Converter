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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serial Controller Application - entry point.
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
 * <li>Iteration 1 - proof of concept: serial port enumeration.</li>
 * <li>Iteration 2 - Javalin HTTP server + static index.html.</li>
 * <li>Iteration 3 - WebSocket live data push.</li>
 * <li>Iteration 4 - current control widget in the webpage.</li>
 * <li>Iteration 5 - service layer: {@link DeviceService}, {@link ConverterState}, device properties files.</li>
 * <li>Iteration 6 - {@link RestService}: full CRUD REST API with OpenAPI documentation.</li>
 * <li>Iteration 7 - {@link WebSocketService}: WebSocket handler extracted from app; full state push.</li>
 * </ul>
 */
public class SerialController {

    private static final Logger logger = LoggerFactory.getLogger(SerialController.class);

    /** Serial Controller version (keep in sync with pom.xml). */
    public static final String SERIALCONTROLLER_VERSION = "1.0.0";
    
    /** The service layer - owns the converter, polling thread, and ConverterState. */
    private DeviceService deviceService;

    /** WebSocket service - owns connected clients and the broadcast thread. */
    private WebSocketService webSocketService;

    /**
     * Monitor used to let the main thread sleep until the 600-second timeout elapses or until
     * the {@code /api/exit} handler wakes it early by calling {@code notifyAll()}.
     */
    private final Object shutdownLock = new Object();

    public static void main(final String[] args) throws Exception {
        logger.info("Serial Controller - Control Riden/Ruideng and Sinilink DC/DC converters v{}", SERIALCONTROLLER_VERSION);
        logger.info("");
        logger.info("                  (C) by Roman Stangl 09, 2026 (Roman.Stangl@gmx.net)");
        logger.info("                  http://warpguru.bplaced.net/");
        logger.info("");
        SerialController app = new SerialController();
        app.process(args);
    }

    private void process(final String[] args) throws Exception {
        if (args.length == 0 || args.length > 2) {
            System.out.println("Usage:");
            System.out.println("  java -jar SerialController.jar <port> [config-file]");
            System.out.println("Where:");
            System.out.println("  <port>        Serial port name, e.g. COM3 or /dev/ttyUSB0");
            System.out.println("  [config-file] Optional: fully-qualified path to a properties file.");
            System.out.println("                Overrides credentials.properties defaults and may specify:");
            System.out.println("                  serialcontroller.host           Hostname/IP the server binds to");
            System.out.println("                  serialcontroller.port           TCP port the server listens on");
            System.out.println("                  serialcontroller.log.level      Log level (TRACE/DEBUG/INFO/WARN/ERROR)");
            System.out.println("                  serialcontroller.admin.username Username for GUI administration");
            System.out.println("                  serialcontroller.admin.password Password for GUI administration");
            return;
        }
        logger.info("Serial Controller started.");

        final String portName        = args[0];
        final String externalConfig  = (args.length == 2) ? args[1] : null;

        // Load configuration: classpath defaults overlaid with optional external file.
        final AppConfiguration config = new AppConfiguration(externalConfig);

        // Apply log level override before any further logging.
        applyLogLevel(config.getLogLevel());

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
            // NOTE: demoVoltages() is no longer called here - it opened its own ModbusTransport
            // on every discovered port, which conflicts with DeviceService acquiring the transport
            // exclusively. Removed in Iteration 5; method body cleared, signature retained @Deprecated.
        }

        // Initialise the service layer - detects device, loads limits, reads initial setpoints.
        deviceService    = new DeviceService(portName);
        webSocketService = new WebSocketService(deviceService, deviceService.getObjectMapper());

        final RestService restService = new RestService(deviceService, config);

        final int serverPort = config.getPort();
        Javalin javalin = Javalin.create(cfg -> {
            cfg.jetty.port = serverPort;

            // Serve ./public/* at /
            cfg.staticFiles.add("/public", Location.CLASSPATH);

            // REST API routes
            restService.registerRoutes(cfg.routes);

            // WebSocket endpoint - all handling delegated to WebSocketService
            cfg.routes.ws("/ws/data", ws -> {
                ws.onConnect(webSocketService::onConnect);
                ws.onMessage(webSocketService::onMessage);
                ws.onClose(webSocketService::onClose);
                ws.onError(webSocketService::onError);
            });

            // OpenAPI JSON endpoint at /openapi
            cfg.registerPlugin(new OpenApiPlugin(openApiConfig -> {
                openApiConfig.withDocumentationPath("/openapi");
                openApiConfig.withDefinitionConfiguration((version, definition) -> {
                    definition.info(info -> info.title("SerialController").version("1.0.0"));
                    definition.withBasicAuth("BasicAuth");
                });
            }));

            // Swagger UI at /openapi/ui
            cfg.registerPlugin(new SwaggerPlugin(swaggerConfig -> {
                swaggerConfig.withDocumentationPath("/openapi");
                swaggerConfig.withUiPath("/openapi/ui");
            }));

        }).start(serverPort);

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
        logger.info("Serial Controller finished.");
    }

    /**
     * Applies the {@code serialcontroller.log.level} override to the {@code com.serial} logger.
     *
     * <p>
     * Uses the Log4j2 {@link Configurator} API to adjust the level at runtime without reloading the
     * entire {@code log4j2.xml} configuration. The override affects only the {@code com.serial}
     * package logger so third-party library log levels are unaffected.
     * </p>
     *
     * <p>
     * If the value is blank or not a recognised Log4j2 level name the method logs a warning and
     * leaves the level unchanged.
     * </p>
     *
     * @param levelStr level name from the config file, e.g. {@code "DEBUG"}; blank to skip
     */
    private static void applyLogLevel(final String levelStr) {
        if (levelStr == null || levelStr.isBlank()) {
            return;
        }
        final Level level = Level.getLevel(levelStr.toUpperCase());
        if (level == null) {
            logger.warn("Unrecognised log level '{}' in configuration — keeping log4j2.xml level.", levelStr);
            return;
        }
        Configurator.setLevel("com.serial", level);
        logger.info("Log level for com.serial set to {} (from configuration).", level);
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
     * Returns the given value if it is non-null and non-empty, otherwise {@code "N/A"}.
     *
     * @param value the value to check
     * @return the value or {@code "N/A"}
     */
    private String valueOrNA(final String value) {
        return (value != null && !value.isEmpty()) ? value : "N/A";
    }
    
}

package com.serial;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;
import com.serial.service.ConverterState;
import com.serial.service.DeviceService;
import com.serial.service.RestService;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;

/**
 * Serial Controller Application — entry point.
 *
 * <p>
 * This class is responsible only for wiring up the Javalin HTTP/WebSocket server and the
 * {@link DeviceService}. All business logic and state are managed by the service layer in
 * {@code com.serial.service}.
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
 * </ul>
 */
public class SerialControllerApp {

    private static final Logger logger = LoggerFactory.getLogger(SerialControllerApp.class);

    // -------------------------------------------------------------------------
    // Temporary: WebSocket client set and ObjectMapper remain here until
    // Iteration 7 (WebSocketService refactor). DeviceService owns state from
    // this iteration onward.
    // -------------------------------------------------------------------------

    /** Keep track of connected WebSocket clients to broadcast updates. */
    private static final Set<io.javalin.websocket.WsContext> clients = ConcurrentHashMap.newKeySet();

    /** JSON serialiser — temporary until Iteration 7 moves this to DeviceService. */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** The service layer — owns the converter, polling thread, and ConverterState. */
    private DeviceService deviceService;

    /** WebSocket broadcaster thread — kept so it can be interrupted on shutdown. */
    private Thread broadcasterThread;

    /**
     * Monitor used to let the main thread sleep until the 600-second timeout elapses or until
     * the {@code /api/exit} handler wakes it early by calling {@code notify()}.
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

        // Enumerate serial ports for diagnostics
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
            }
            // NOTE: demoVoltages() is no longer called here. It opened its own ModbusTransport
            // on every discovered port, which conflicts with DeviceService acquiring the transport
            // exclusively. Removed in Iteration 5; method kept @Deprecated for reference.
        }

        // Initialise the service layer — detects device, loads limits, reads initial setpoints.
        deviceService = new DeviceService(portName);

        // REST service — registers @OpenApi-annotated routes so the annotation processor
        // emits a valid OpenAPI spec for Swagger UI.
        final RestService restService = new RestService(deviceService);

        Javalin javalin = Javalin.create(config -> {
            config.jetty.port = 8000;
            // Serve ./public/* at /
            config.staticFiles.add("/public", Location.CLASSPATH);

            // REST API routes — registered here so they are visible to the annotation processor.
            restService.registerRoutes(config.routes);

            // WebSocket endpoint for live data — temporary inline handler until Iteration 7
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
                            try {
                                deviceService.setCurrent(value);
                            } catch (IllegalArgumentException e) {
                                logger.warn("setCurrent rejected: {}", e.getMessage());
                            }
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

            // Swagger UI at /swagger
            config.registerPlugin(new SwaggerPlugin());

        }).start(8000);

        // Give the exit handler a reference to javalin and a shutdown runnable that wakes
        // the main thread (which is sleeping in shutdownLock.wait below).
        restService.setShutdown(javalin, () -> {
            synchronized (shutdownLock) {
                shutdownLock.notifyAll();
            }
        });

        // Start the Modbus polling thread now that Javalin is up.
        deviceService.start();

        // Start WebSocket broadcast thread — reads from ConverterState (no more Math.random).
        startUpdateThread();

        // Sleep until the 600-second timeout expires or /api/exit wakes us early.
        synchronized (shutdownLock) {
            shutdownLock.wait(600_000L);
        }

        deviceService.stop();
        if (broadcasterThread != null) {
            broadcasterThread.interrupt();
        }
        javalin.stop();
    }

    /**
     * Background thread that broadcasts the current {@link ConverterState} to all connected WebSocket clients
     * every second.
     *
     * <p>
     * This is a temporary implementation that will be replaced by {@code WebSocketService} in Iteration 7.
     * It now reads from {@link DeviceService#getState()} instead of a local volatile field.
     * </p>
     */
    private void startUpdateThread() {
        broadcasterThread = new Thread(() -> {
            while (true) {
                try {
                    ConverterState state = deviceService.getState();
                    String jsonUpdate = String.format(
                            "{\"voltage\": %.2f, \"current\": %.2f}",
                            state.getVoltageOut(),
                            state.getCurrentOut());

                    clients.forEach(client -> {
                        try {
                            client.send(jsonUpdate);
                        } catch (Exception e) {
                            logger.warn("Failed to send to WebSocket client, removing: {}", e.getMessage());
                            clients.remove(client);
                        }
                    });

                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ws-broadcaster");
        broadcasterThread.setDaemon(true);
        broadcasterThread.start();
    }

    /**
     * Sleeps the current thread for the specified number of seconds.
     *
     * @param seconds number of seconds to sleep
     * @throws Exception if the thread is interrupted
     */
    private void sleepSeconds(final int seconds) throws Exception {
        System.out.println("Waiting " + seconds + " seconds...");
        Thread.sleep(seconds * 1000L);
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

        logger.info("Port: {} | Description: {} | Location: {} | Manufacturer: {} | Serial: {} | VID:PID: {}",
                name, description, location, manufacturer, serialNumber, usbId);
    }

    /**
     * Demo method for setting output voltage — kept for reference only.
     *
     * <p>
     * This method is no longer called. It was removed from {@link #process(String[])} in Iteration 5
     * because it opens its own {@code ModbusTransport}, which conflicts with {@link DeviceService}
     * holding exclusive transport ownership. Retained here with {@code @Deprecated} per project convention.
     * Full resolution in Iteration 9.
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

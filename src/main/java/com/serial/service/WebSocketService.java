package com.serial.service;

import java.nio.channels.ClosedChannelException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;

/**
 * Manages the WebSocket endpoint for live converter data.
 *
 * <p>
 * This service owns the connected-client set, the broadcast thread, and all WebSocket message
 * handling. It replaces the inline WebSocket code that previously lived in
 * {@code SerialControllerApp}.
 * </p>
 *
 * <p>
 * <strong>Push:</strong> A background thread broadcasts the full {@link ConverterState} as JSON
 * to every connected client every second. The payload is the complete state snapshot so that the
 * webpage always has current data regardless of what changed.
 * </p>
 *
 * <p>
 * <strong>Receive:</strong> Clients may send JSON messages to adjust setpoints. The following
 * keys are recognised (unrecognised keys are silently ignored at DEBUG level):
 * </p>
 * <ul>
 * <li>{@code setCurrent}  — {@code double} — current setpoint in amperes</li>
 * <li>{@code setVoltage}  — {@code double} — voltage setpoint in volts</li>
 * <li>{@code setOutput}   — {@code boolean} — {@code true} to enable output, {@code false} to disable</li>
 * </ul>
 *
 * <p>
 * <strong>Threading:</strong> The broadcast thread is the only application-owned thread in this
 * service. Write operations from incoming messages are delegated to the {@code synchronized}
 * methods on {@link DeviceService}, which serialises them with the Modbus polling thread.
 * This design maps directly to two FreeRTOS tasks on the planned ESP32 C port.
 * </p>
 */
public class WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);

    /** Broadcast interval in milliseconds. */
    private static final int BROADCAST_INTERVAL_MS = 1000;

    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    /** Thread-safe set of all currently connected WebSocket clients. */
    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();

    /** Background broadcast thread. */
    private Thread broadcastThread;

    /** Set to {@code false} to stop the broadcast thread. */
    private volatile boolean running;

    /**
     * Constructs a {@code WebSocketService}.
     *
     * @param deviceService the service providing {@link ConverterState} and write operations
     * @param objectMapper  shared Jackson mapper used to serialise {@link ConverterState} to JSON
     */
    public WebSocketService(final DeviceService deviceService, final ObjectMapper objectMapper) {
        this.deviceService = deviceService;
        this.objectMapper  = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the background broadcast thread.
     *
     * <p>
     * Must be called after the Javalin server has started so the first broadcast fires as soon as
     * the first WebSocket client connects.
     * </p>
     */
    public void start() {
        running = true;
        broadcastThread = new Thread(this::broadcastLoop, "ws-broadcaster");
        broadcastThread.setDaemon(true);
        broadcastThread.start();
        logger.info("WebSocketService broadcast thread started.");
    }

    /**
     * Stops the background broadcast thread.
     */
    public void stop() {
        running = false;
        if (broadcastThread != null) {
            broadcastThread.interrupt();
        }
        logger.info("WebSocketService stopped.");
    }

    // -------------------------------------------------------------------------
    // WebSocket event handlers — wired from SerialControllerApp
    // -------------------------------------------------------------------------

    /**
     * Called when a new WebSocket client connects.
     *
     * @param ctx the WebSocket context of the new client
     */
    public void onConnect(final WsConnectContext ctx) {
        clients.add(ctx);
        logger.info("WebSocket client connected (total: {})", clients.size());
    }

    /**
     * Called when a WebSocket client sends a message.
     *
     * <p>
     * Parses the incoming JSON and dispatches to the appropriate {@link DeviceService} write
     * method. Recognised keys: {@code setCurrent}, {@code setVoltage}, {@code setOutput}.
     * Unrecognised keys are logged at DEBUG level and ignored.
     * </p>
     *
     * @param ctx the WebSocket context carrying the message
     */
    @SuppressWarnings("unchecked")
    public void onMessage(final WsMessageContext ctx) {
        final String msg = ctx.message();
        logger.info("WebSocket message received: {}", msg);
        try {
            java.util.Map<String, Object> json = objectMapper.readValue(msg, java.util.Map.class);

            if (json.containsKey("setCurrent")) {
                double value = ((Number) json.get("setCurrent")).doubleValue();
                try {
                    deviceService.setCurrent(value);
                } catch (IllegalArgumentException e) {
                    logger.warn("setCurrent rejected: {}", e.getMessage());
                } catch (Exception e) {
                    logger.warn("setCurrent failed: {}", e.getMessage());
                }
            }

            if (json.containsKey("setVoltage")) {
                double value = ((Number) json.get("setVoltage")).doubleValue();
                try {
                    deviceService.setVoltage(value);
                } catch (IllegalArgumentException e) {
                    logger.warn("setVoltage rejected: {}", e.getMessage());
                } catch (Exception e) {
                    logger.warn("setVoltage failed: {}", e.getMessage());
                }
            }

            if (json.containsKey("setOutput")) {
                boolean value = (Boolean) json.get("setOutput");
                try {
                    deviceService.setOutput(value);
                } catch (Exception e) {
                    logger.warn("setOutput failed: {}", e.getMessage());
                }
            }

            // Log unrecognised keys at DEBUG so they are visible when debugging but do not clutter INFO logs.
            for (String key : json.keySet()) {
                if (!key.equals("setCurrent") && !key.equals("setVoltage") && !key.equals("setOutput")) {
                    logger.debug("WebSocket message: unrecognised key '{}' — ignored.", key);
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to parse WebSocket message: {}", e.getMessage());
        }
    }

    /**
     * Called when a WebSocket client disconnects.
     *
     * @param ctx the WebSocket context of the disconnecting client
     */
    public void onClose(final WsCloseContext ctx) {
        clients.remove(ctx);
        logger.info("WebSocket client disconnected (total: {})", clients.size());
    }

    /**
     * Called when a WebSocket error occurs.
     *
     * <p>
     * {@link ClosedChannelException} and {@link java.io.EOFException} are logged at DEBUG level
     * (they are normal during server shutdown or client disconnect). All other errors are logged
     * at WARN level.
     * </p>
     *
     * @param ctx the WebSocket context on which the error occurred
     */
    public void onError(final WsErrorContext ctx) {
        clients.remove(ctx);
        Throwable error = ctx.error();
        if (error instanceof ClosedChannelException || error instanceof java.io.EOFException) {
            logger.debug("WebSocket connection closed by peer or server shutdown: {}", error.toString());
        } else {
            logger.warn("WebSocket error", error);
        }
    }

    // -------------------------------------------------------------------------
    // Private — broadcast loop
    // -------------------------------------------------------------------------

    /**
     * Main body of the broadcast thread.
     *
     * <p>
     * Serialises the full {@link ConverterState} snapshot to JSON and sends it to every connected
     * client every {@link #BROADCAST_INTERVAL_MS} milliseconds. Failed sends remove the client
     * from the active set.
     * </p>
     */
    private void broadcastLoop() {
        while (running) {
            try {
                final String json = objectMapper.writeValueAsString(deviceService.getState());
                clients.forEach(client -> {
                    try {
                        client.send(json);
                    } catch (Exception e) {
                        logger.warn("Failed to send to WebSocket client, removing: {}", e.getMessage());
                        clients.remove(client);
                    }
                });
                Thread.sleep(BROADCAST_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("Broadcast cycle failed: {}", e.getMessage());
            }
        }
        logger.info("WebSocket broadcast thread exiting.");
    }
}

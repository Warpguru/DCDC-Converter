package com.serial.service;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import io.javalin.security.BasicAuthCredentials;

/**
 * REST API route definitions for the Serial Controller web interface.
 *
 * <p>
 * All endpoints are registered under the {@code /api} prefix. Each handler method carries an
 * {@code @OpenApi} annotation so the compile-time annotation processor emits a valid OpenAPI 3.x
 * specification, which the Swagger UI at {@code /swagger} renders correctly.
 * </p>
 *
 * <p>Routes provided:</p>
 * <ul>
 * <li>{@code GET  /api/state}           — full converter state snapshot</li>
 * <li>{@code PUT  /api/voltage}          — set output voltage setpoint</li>
 * <li>{@code PUT  /api/current}          — set output current setpoint</li>
 * <li>{@code PUT  /api/output}           — enable or disable the output</li>
 * <li>{@code POST /api/protection/clear} — clear a tripped protection condition</li>
 * <li>{@code POST /api/exit}             — shut down the application (Basic Auth required)</li>
 * </ul>
 *
 * <p>
 * The {@code /api/exit} endpoint is protected by HTTP Basic Authentication. Credentials are read
 * from a {@code serial-controller.properties} file located next to the running JAR, using the keys
 * {@code exit.username} and {@code exit.password}. If the file is absent or the credentials are
 * missing the endpoint returns {@code 503 Service Unavailable}.
 * </p>
 */
public class RestService {

    private static final Logger logger = LoggerFactory.getLogger(RestService.class);

    /** Properties file name expected next to the JAR. */
    private static final String PROPS_FILE = "serial-controller.properties";

    private final DeviceService deviceService;

    /** Javalin instance — used by the exit handler to stop the server. */
    private Javalin javalin;

    /** Shutdown callback — called by the exit handler after sending the response. */
    private Runnable shutdownHook;

    /**
     * Constructs a {@code RestService} backed by the given {@link DeviceService}.
     *
     * @param deviceService the service owning the device connection and state
     */
    public RestService(final DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * Provides the {@link Javalin} instance and a shutdown callback to the exit handler.
     *
     * <p>Must be called before {@link #registerRoutes} so that {@code /api/exit} has a reference
     * to stop the server. The {@code shutdown} runnable is executed on a daemon thread after the
     * HTTP 204 response has been committed, giving the client time to receive it.</p>
     *
     * <pre>
     *     restService.setShutdown(javalin, () -> {
     *         deviceService.stop();
     *         javalin.stop();
     *         System.exit(0);
     *     });
     * </pre>
     *
     * @param javalinInstance the running Javalin server
     * @param shutdown        runnable executed after the exit response is sent
     */
    public void setShutdown(final Javalin javalinInstance, final Runnable shutdown) {
        this.javalin = javalinInstance;
        this.shutdownHook = shutdown;
    }

    /**
     * Registers all REST routes on the provided {@link JavalinDefaultRoutingApi} instance.
     *
     * <p>Call this inside the {@code Javalin.create(config -> ...)} lambda, passing
     * {@code config.routes}:</p>
     * <pre>
     *     restService.registerRoutes(config.routes);
     * </pre>
     *
     * @param router the routing API to register routes on (typically {@code config.routes})
     */
    public void registerRoutes(final JavalinDefaultRoutingApi router) {
        router.get("/api/state",             this::getState);
        router.put("/api/voltage",           this::setVoltage);
        router.put("/api/current",           this::setCurrent);
        router.put("/api/output",            this::setOutput);
        router.post("/api/protection/clear", this::clearProtection);
        router.post("/api/exit",             this::exit);
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    /**
     * Returns the full current converter state as JSON.
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = "/api/state",
        methods     = { HttpMethod.GET },
        summary     = "Get converter state",
        description = "Returns the full current state of the DC/DC converter: measured values, setpoints, and device information.",
        tags        = { "Converter" },
        responses   = {
            @OpenApiResponse(status = "200",
                description = "Current converter state",
                content     = { @OpenApiContent(from = ConverterState.class) })
        }
    )
    // @formatter:on
    public void getState(final Context ctx) {
        ctx.json(deviceService.getState());
    }

    /**
     * Sets the output voltage setpoint.
     *
     * <p>Request body: {@code { "voltage": 5.0 }} — voltage in volts.</p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = "/api/voltage",
        methods     = { HttpMethod.PUT },
        summary     = "Set output voltage",
        description = "Sets the output voltage setpoint on the device. Value must be within the device's configured voltage limits.",
        tags        = { "Converter" },
        requestBody = @OpenApiRequestBody(
            required    = true,
            description = "Voltage setpoint in volts",
            content     = { @OpenApiContent(from = VoltageRequest.class, example = "{\"voltage\": 5.0}") }
        ),
        responses   = {
            @OpenApiResponse(status = "204", description = "Voltage applied successfully"),
            @OpenApiResponse(status = "400", description = "Value out of range"),
            @OpenApiResponse(status = "503", description = "No device connected")
        }
    )
    // @formatter:on
    public void setVoltage(final Context ctx) {
        if (!deviceService.isDeviceDetected()) {
            ctx.status(503).result("No device connected");
            return;
        }
        VoltageRequest req = ctx.bodyAsClass(VoltageRequest.class);
        try {
            deviceService.setVoltage(req.voltage);
            ctx.status(204);
        } catch (IllegalArgumentException e) {
            ctx.status(400).result(e.getMessage());
        } catch (Exception e) {
            ctx.status(500).result("Device write failed");
        }
    }

    /**
     * Sets the output current setpoint.
     *
     * <p>Request body: {@code { "current": 1.0 }} — current in amperes.</p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = "/api/current",
        methods     = { HttpMethod.PUT },
        summary     = "Set output current",
        description = "Sets the output current setpoint on the device. Value must be within the device's configured current limits.",
        tags        = { "Converter" },
        requestBody = @OpenApiRequestBody(
            required    = true,
            description = "Current setpoint in amperes",
            content     = { @OpenApiContent(from = CurrentRequest.class, example = "{\"current\": 1.0}") }
        ),
        responses   = {
            @OpenApiResponse(status = "204", description = "Current applied successfully"),
            @OpenApiResponse(status = "400", description = "Value out of range"),
            @OpenApiResponse(status = "503", description = "No device connected")
        }
    )
    // @formatter:on
    public void setCurrent(final Context ctx) {
        if (!deviceService.isDeviceDetected()) {
            ctx.status(503).result("No device connected");
            return;
        }
        CurrentRequest req = ctx.bodyAsClass(CurrentRequest.class);
        try {
            deviceService.setCurrent(req.current);
            ctx.status(204);
        } catch (IllegalArgumentException e) {
            ctx.status(400).result(e.getMessage());
        } catch (Exception e) {
            ctx.status(500).result("Device write failed");
        }
    }

    /**
     * Enables or disables the converter output.
     *
     * <p>Request body: {@code { "outputEnable": true }} to enable, {@code { "outputEnable": false }} to disable.</p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = "/api/output",
        methods     = { HttpMethod.PUT },
        summary     = "Enable or disable output",
        description = "Enables or disables the converter output.",
        tags        = { "Converter" },
        requestBody = @OpenApiRequestBody(
            required    = true,
            description = "true to enable the output, false to disable it",
            content     = { @OpenApiContent(from = OutputRequest.class, example = "{\"outputEnable\": true}") }
        ),
        responses   = {
            @OpenApiResponse(status = "204", description = "Output state applied successfully"),
            @OpenApiResponse(status = "503", description = "No device connected")
        }
    )
    // @formatter:on
    public void setOutput(final Context ctx) {
        if (!deviceService.isDeviceDetected()) {
            ctx.status(503).result("No device connected");
            return;
        }
        OutputRequest req = ctx.bodyAsClass(OutputRequest.class);
        try {
            deviceService.setOutput(req.outputEnable);
            ctx.status(204);
        } catch (Exception e) {
            ctx.status(500).result("Device write failed");
        }
    }

    /**
     * Clears a tripped protection condition on the device.
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = "/api/protection/clear",
        methods     = { HttpMethod.POST },
        summary     = "Clear protection",
        description = "Clears a tripped protection condition (OVP, OCP, OPP, etc.) and allows the device to resume normal operation.",
        tags        = { "Converter" },
        responses   = {
            @OpenApiResponse(status = "204", description = "Protection cleared successfully"),
            @OpenApiResponse(status = "503", description = "No device connected")
        }
    )
    // @formatter:on
    public void clearProtection(final Context ctx) {
        if (!deviceService.isDeviceDetected()) {
            ctx.status(503).result("No device connected");
            return;
        }
        try {
            deviceService.clearProtection();
            ctx.status(204);
        } catch (Exception e) {
            ctx.status(500).result("Device write failed");
        }
    }

    /**
     * Shuts down the application after verifying HTTP Basic Auth credentials.
     *
     * <p>
     * Credentials are read from {@code serial-controller.properties} next to the JAR. The shutdown
     * itself runs on a short-lived daemon thread so the HTTP 204 response is committed before the
     * server stops.
     * </p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = "/api/exit",
        methods     = { HttpMethod.POST },
        summary     = "Shut down the application",
        description = "Performs a clean shutdown of the Serial Controller application. Requires HTTP Basic Authentication (credentials configured in serial-controller.properties next to the JAR).",
        tags        = { "Admin" },
        responses   = {
            @OpenApiResponse(status = "204", description = "Shutdown initiated"),
            @OpenApiResponse(status = "401", description = "Missing or invalid credentials"),
            @OpenApiResponse(status = "503", description = "Exit credentials not configured")
        }
    )
    // @formatter:on
    public void exit(final Context ctx) {
        // Load credentials from properties file next to the JAR
        Properties props = loadExitCredentials();
        if (props == null) {
            ctx.status(503).result("Exit credentials not configured (serial-controller.properties not found)");
            return;
        }

        String expectedUsername = props.getProperty("exit.username");
        String expectedPassword = props.getProperty("exit.password");
        if (expectedUsername == null || expectedPassword == null) {
            ctx.status(503).result("Exit credentials not configured (exit.username / exit.password missing)");
            return;
        }

        // Validate Basic Auth
        BasicAuthCredentials creds = ctx.basicAuthCredentials();
        if (creds == null
                || !expectedUsername.equals(creds.getUsername())
                || !expectedPassword.equals(creds.getPassword())) {
            ctx.header("WWW-Authenticate", "Basic realm=\"SerialController\"");
            ctx.status(401).result("Unauthorized");
            return;
        }

        logger.info("Shutdown requested via /api/exit by user '{}'", creds.getUsername());
        ctx.status(204);

        // Shut down on a daemon thread so the 204 response is committed first.
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (shutdownHook != null) {
                shutdownHook.run();
            }
        }, "exit-handler");
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads the properties file {@code serial-controller.properties} from the working directory
     * (i.e. next to the JAR).
     *
     * @return loaded {@link Properties}, or {@code null} if the file does not exist or cannot be read
     */
    private Properties loadExitCredentials() {
        File file = new File(PROPS_FILE);
        if (!file.exists()) {
            logger.warn("Properties file '{}' not found — /api/exit is disabled.", PROPS_FILE);
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            return props;
        } catch (Exception e) {
            logger.error("Failed to read '{}': {}", PROPS_FILE, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Request body DTOs
    // -------------------------------------------------------------------------

    /**
     * Request body for {@code PUT /api/voltage}.
     *
     * <pre>{ "voltage": 5.0 }</pre>
     */
    public static class VoltageRequest {
        /** Output voltage setpoint in volts (V). */
        public double voltage;
    }

    /**
     * Request body for {@code PUT /api/current}.
     *
     * <pre>{ "current": 1.0 }</pre>
     */
    public static class CurrentRequest {
        /** Output current setpoint in amperes (A). */
        public double current;
    }

    /**
     * Request body for {@code PUT /api/output}.
     *
     * <pre>{ "outputEnable": true }</pre>
     */
    public static class OutputRequest {
        /** {@code true} to enable the output, {@code false} to disable it. */
        public boolean outputEnable;
    }
}

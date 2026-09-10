package com.serial.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serial.AppConfiguration;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApiSecurity;
import io.javalin.router.JavalinDefaultRoutingApi;
import io.javalin.security.BasicAuthCredentials;

/**
 * REST API route definitions for the Serial Controller web interface.
 *
 * <p>
 * All endpoints are registered under the {@code /api} prefix. Each handler method carries an {@code @OpenApi} annotation so the
 * compile-time annotation processor emits a valid OpenAPI 3.x specification, which the Swagger UI at {@code /openapi/ui}
 * renders correctly.
 * </p>
 *
 * <p>
 * Routes provided:
 * </p>
 * <ul>
 * <li>{@code GET  /api/state} - full converter state snapshot</li>
 * <li>{@code PUT  /api/voltage} - set output voltage setpoint</li>
 * <li>{@code PUT  /api/current} - set output current setpoint</li>
 * <li>{@code PUT  /api/output} - enable or disable the output</li>
 * <li>{@code PUT  /api/keypad} - lock or unlock the keypad (child lock)</li>
 * <li>{@code POST /api/protection/clear} - clear a tripped protection condition</li>
 * <li>{@code POST /api/exit} - shut down the application (Basic Auth required)</li>
 * </ul>
 *
 * <p>
 * The {@code /api/exit} endpoint is protected by HTTP Basic Authentication. Credentials are read from a
 * {@link com.serial.AppConfiguration} (sourced from the bundled {@code credentials.properties} and optionally overridden by an
 * external file), using the keys {@code serialcontroller.admin.username} and {@code serialcontroller.admin.password}. If the
 * credentials are absent the endpoint returns {@code 503 Service Unavailable}.
 * </p>
 */
public class RestService {

    private static final Logger logger = LoggerFactory.getLogger(RestService.class);

    /** REST API context root. */
    public static final String API_CONTEXT_ROOT = "/api";

    /** Endpoint path for full converter state. */
    public static final String PATH_STATE = "/state";

    /** Endpoint path for device capability limits. */
    public static final String PATH_LIMITS = "/limits";

    /** Endpoint path for output voltage setpoint. */
    public static final String PATH_VOLTAGE = "/voltage";

    /** Endpoint path for output current setpoint. */
    public static final String PATH_CURRENT = "/current";

    /** Endpoint path for output enable/disable. */
    public static final String PATH_OUTPUT = "/output";

    /** Endpoint path for keypad lock (child lock). */
    public static final String PATH_KEYPAD = "/keypad";

    /** Endpoint path for clearing tripped protection. */
    public static final String PATH_PROTECTION_CLEAR = "/protection/clear";

    /** Endpoint path for administrative application shutdown. */
    public static final String PATH_EXIT = "/exit";

    /** Full URI for full converter state. */
    public static final String URI_STATE = API_CONTEXT_ROOT + PATH_STATE;

    /** Full URI for device capability limits. */
    public static final String URI_LIMITS = API_CONTEXT_ROOT + PATH_LIMITS;

    /** Full URI for output voltage setpoint. */
    public static final String URI_VOLTAGE = API_CONTEXT_ROOT + PATH_VOLTAGE;

    /** Full URI for output current setpoint. */
    public static final String URI_CURRENT = API_CONTEXT_ROOT + PATH_CURRENT;

    /** Full URI for output enable/disable. */
    public static final String URI_OUTPUT = API_CONTEXT_ROOT + PATH_OUTPUT;

    /** Full URI for keypad lock (child lock). */
    public static final String URI_KEYPAD = API_CONTEXT_ROOT + PATH_KEYPAD;

    /** Full URI for clearing tripped protection. */
    public static final String URI_PROTECTION_CLEAR = API_CONTEXT_ROOT + PATH_PROTECTION_CLEAR;

    /** Full URI for administrative application shutdown. */
    public static final String URI_EXIT = API_CONTEXT_ROOT + PATH_EXIT;

    private final DeviceService deviceService;

    private final AppConfiguration appConfig;

    /** Javalin instance - used by the exit handler to stop the server. */
    @SuppressWarnings("unused")
    private Javalin javalin;

    /** Shutdown callback - called by the exit handler after sending the response. */
    private Runnable shutdownHook;

    /**
     * Constructs a {@code RestService} backed by the given {@link DeviceService} and {@link AppConfiguration}.
     *
     * @param deviceService the service owning the device connection and state
     * @param appConfig     application configuration providing exit credentials and server settings
     */
    public RestService(final DeviceService deviceService, final AppConfiguration appConfig) {
        this.deviceService = deviceService;
        this.appConfig = appConfig;
    }

    /**
     * Provides the {@link Javalin} instance and a shutdown callback to the exit handler.
     *
     * <p>
     * Must be called before {@link #registerRoutes} so that {@code /api/exit} has a reference to stop the server. The
     * {@code shutdown} runnable is executed on a daemon thread after the HTTP 204 response has been committed, giving the
     * client time to receive it.
     * </p>
     *
     * <pre>
     * restService.setShutdown(javalin, () -> {
     *     deviceService.stop();
     *     javalin.stop();
     *     System.exit(0);
     * });
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
     * <p>
     * Call this inside the {@code Javalin.create(config -> ...)} lambda, passing {@code config.routes}:
     * </p>
     * 
     * <pre>
     * restService.registerRoutes(config.routes);
     * </pre>
     *
     * @param router the routing API to register routes on (typically {@code config.routes})
     */
    public void registerRoutes(final JavalinDefaultRoutingApi router) {
        router.get(URI_STATE, this::getState);
        router.get(URI_LIMITS, this::getLimits);
        router.put(URI_VOLTAGE, this::setVoltage);
        router.put(URI_CURRENT, this::setCurrent);
        router.put(URI_OUTPUT, this::setOutput);
        router.put(URI_KEYPAD, this::setKeypad);
        router.post(URI_PROTECTION_CLEAR, this::clearProtection);
        router.post(URI_EXIT, this::exit);
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
        path        = URI_STATE,
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
        logger.debug("REST GET /api/state");
        ctx.json(deviceService.getState());
    }

    /**
     * Returns the device capability limits from the current converter state.
     *
     * <p>
     * Useful for clients that need to know the valid voltage/current range before sending setpoint commands, without fetching
     * the full state snapshot.
     * </p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = URI_LIMITS,
        methods     = { HttpMethod.GET },
        summary     = "Get device limits",
        description = "Returns the device capability limits: manufacturer, device name, and the min/max voltage, current, and power values loaded from the device properties file.",
        tags        = { "Converter" },
        responses   = {
            @OpenApiResponse(status = "200",
                description = "Device capability limits",
                content     = { @OpenApiContent(from = LimitsResponse.class) })
        }
    )
    // @formatter:on
    public void getLimits(final Context ctx) {
        logger.debug("REST GET /api/limits");
        ConverterState s = deviceService.getState();
        ctx.json(new LimitsResponse(s.getManufacturer(), s.getDeviceName(), s.getMinVoltage(), s.getMaxVoltage(),
                s.getMinCurrent(), s.getMaxCurrent(), s.getMaxPower()));
    }

    /**
     * Sets the output voltage setpoint.
     *
     * <p>
     * Request body: {@code { "voltage": 5.0 }} - voltage in volts.
     * </p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = URI_VOLTAGE,
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
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).result("No device connected");
            return;
        }
        VoltageRequest req = ctx.bodyAsClass(VoltageRequest.class);
        logger.debug("REST PUT /api/voltage: {}", compactBody(ctx.body()));
        try {
            deviceService.setVoltage(req.voltage);
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result(e.getMessage());
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Device write failed");
        }
    }

    /**
     * Sets the output current setpoint.
     *
     * <p>
     * Request body: {@code { "current": 1.0 }} - current in amperes.
     * </p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = URI_CURRENT,
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
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).result("No device connected");
            return;
        }
        CurrentRequest req = ctx.bodyAsClass(CurrentRequest.class);
        logger.debug("REST PUT /api/current: {}", compactBody(ctx.body()));
        try {
            deviceService.setCurrent(req.current);
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result(e.getMessage());
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Device write failed");
        }
    }

    /**
     * Enables or disables the converter output.
     *
     * <p>
     * Request body: {@code { "outputEnable": true }} to enable, {@code { "outputEnable": false }} to disable.
     * </p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = URI_OUTPUT,
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
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).result("No device connected");
            return;
        }
        OutputRequest req = ctx.bodyAsClass(OutputRequest.class);
        logger.debug("REST PUT /api/output: {}", compactBody(ctx.body()));
        try {
            deviceService.setOutput(req.outputEnable);
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Device write failed");
        }
    }

    /**
     * Locks or unlocks the converter keypad (child lock).
     *
     * <p>
     * Request body: {@code { "keypadLock": true }} to lock, {@code { "keypadLock": false }} to unlock.
     * </p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = URI_KEYPAD,
        methods     = { HttpMethod.PUT },
        summary     = "Lock or unlock keypad",
        description = "Locks or unlocks the converter keypad (child lock).",
        tags        = { "Converter" },
        requestBody = @OpenApiRequestBody(
            required    = true,
            description = "true to lock the keypad, false to unlock it",
            content     = { @OpenApiContent(from = KeypadRequest.class, example = "{\"keypadLock\": true}") }
        ),
        responses   = {
            @OpenApiResponse(status = "204", description = "Keypad lock state applied successfully"),
            @OpenApiResponse(status = "503", description = "No device connected")
        }
    )
    // @formatter:on
    public void setKeypad(final Context ctx) {
        if (!deviceService.isDeviceDetected()) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).result("No device connected");
            return;
        }
        KeypadRequest req = ctx.bodyAsClass(KeypadRequest.class);
        logger.debug("REST PUT /api/keypad: {}", compactBody(ctx.body()));
        try {
            deviceService.setKeypad(req.keypadLock);
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Device write failed");
        }
    }

    /**
     * Clears a tripped protection condition on the device.
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = URI_PROTECTION_CLEAR,
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
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).result("No device connected");
            return;
        }
        logger.debug("REST POST /api/protection/clear");
        try {
            deviceService.clearProtection();
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Device write failed");
        }
    }

    /**
     * Shuts down the application after verifying HTTP Basic Auth credentials.
     *
     * <p>
     * Credentials are read from {@link com.serial.AppConfiguration} (credentials.properties). The shutdown itself runs on a
     * short-lived daemon thread so the HTTP 204 response is committed before the server stops.
     * </p>
     *
     * @param ctx the Javalin request context
     */
    // @formatter:off
    @OpenApi(
        path        = URI_EXIT,
        methods     = { HttpMethod.POST },
        summary     = "Shut down the application",
        description = "Performs a clean shutdown of the Serial Controller application. Requires HTTP Basic Authentication (credentials configured in credentials.properties).",
        tags        = { "Admin" },
        security    = {
            @OpenApiSecurity(name = "BasicAuth")
        },
        responses   = {
            @OpenApiResponse(status = "204", description = "Shutdown initiated"),
            @OpenApiResponse(status = "401", description = "Missing or invalid credentials"),
            @OpenApiResponse(status = "503", description = "Exit credentials not configured")
        }
    )
    // @formatter:on
    public void exit(final Context ctx) {
        logger.debug("REST POST /api/exit");
        final String expectedUsername = appConfig.getAdminUsername();
        final String expectedPassword = appConfig.getAdminPassword();
        if (expectedUsername == null || expectedPassword == null) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).result(
                    "Exit credentials not configured (exit.username / exit.password missing in credentials.properties)");
            return;
        }

        // Validate Basic Auth
        BasicAuthCredentials creds = ctx.basicAuthCredentials();
        if (creds == null || !expectedUsername.equals(creds.getUsername()) || !expectedPassword.equals(creds.getPassword())) {
            ctx.header("WWW-Authenticate", "Basic realm=\"SerialController\"");
            ctx.status(HttpStatus.UNAUTHORIZED).result("Unauthorized");
            return;
        }

        logger.info("Shutdown requested via /api/exit by user '{}'", creds.getUsername());
        ctx.status(HttpStatus.NO_CONTENT);

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
     * Returns the request body as a single-line compact JSON string, suitable for logging.
     *
     * <p>
     * Parses the raw body string with Jackson and re-serialises it without pretty-printing. If parsing fails (malformed JSON),
     * the raw body is returned as-is so that the log entry is still present and useful for debugging.
     * </p>
     *
     * @param body raw HTTP request body
     * @return compact single-line JSON, or the original body if it cannot be parsed
     */
    private String compactBody(final String body) {
        try {
            return deviceService.getObjectMapper().writeValueAsString(deviceService.getObjectMapper().readTree(body));
        } catch (Exception e) {
            return body;
        }
    }

    // -------------------------------------------------------------------------
    // Request body DTOs
    // -------------------------------------------------------------------------

    /**
     * Request body for {@code PUT /api/voltage}.
     *
     * <pre>
     * { "voltage": 5.0 }
     * </pre>
     */
    public static class VoltageRequest {
        /** Output voltage setpoint in volts (V). */
        public double voltage;
    }

    /**
     * Request body for {@code PUT /api/current}.
     *
     * <pre>
     * { "current": 1.0 }
     * </pre>
     */
    public static class CurrentRequest {
        /** Output current setpoint in amperes (A). */
        public double current;
    }

    /**
     * Request body for {@code PUT /api/output}.
     *
     * <pre>
     * { "outputEnable": true }
     * </pre>
     */
    public static class OutputRequest {
        /** {@code true} to enable the output, {@code false} to disable it. */
        public boolean outputEnable;
    }

    /**
     * Request body for {@code PUT /api/keypad}.
     *
     * <pre>
     * { "keypadLock": true }
     * </pre>
     */
    public static class KeypadRequest {
        /** {@code true} to lock the keypad, {@code false} to unlock it. */
        public boolean keypadLock;
    }

    /**
     * Response body for {@code GET /api/limits}.
     *
     * <p>
     * Contains only the device capability limits - a subset of {@link ConverterState} useful for clients that need to know
     * valid ranges without fetching the full state.
     * </p>
     */
    public static class LimitsResponse {
        /** Device manufacturer name, e.g. {@code "Sinilink"}. */
        public final String manufacturer;
        /** Device model name, e.g. {@code "XY6008"}. */
        public final String deviceName;
        /** Minimum output voltage in volts (V). */
        public final double minVoltage;
        /** Maximum output voltage in volts (V). */
        public final double maxVoltage;
        /** Minimum output current in amperes (A). */
        public final double minCurrent;
        /** Maximum output current in amperes (A). */
        public final double maxCurrent;
        /** Maximum output power in watts (W). */
        public final double maxPower;

        /**
         * Constructs a {@code LimitsResponse} from the given device capability values.
         *
         * @param manufacturer device manufacturer name
         * @param deviceName   device model name
         * @param minVoltage   minimum output voltage (V)
         * @param maxVoltage   maximum output voltage (V)
         * @param minCurrent   minimum output current (A)
         * @param maxCurrent   maximum output current (A)
         * @param maxPower     maximum output power (W)
         */
        public LimitsResponse(final String manufacturer, final String deviceName, final double minVoltage,
                final double maxVoltage, final double minCurrent, final double maxCurrent, final double maxPower) {
            this.manufacturer = manufacturer;
            this.deviceName = deviceName;
            this.minVoltage = minVoltage;
            this.maxVoltage = maxVoltage;
            this.minCurrent = minCurrent;
            this.maxCurrent = maxCurrent;
            this.maxPower = maxPower;
        }
    }

}

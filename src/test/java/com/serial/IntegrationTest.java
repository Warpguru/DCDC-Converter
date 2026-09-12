package com.serial;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serial.service.RestService;

/**
 * Integration test for the Serial Controller REST API.
 *
 * <p>
 * Executes a fixed sequence of REST calls against a running Serial Controller instance and reports PASS / FAIL for each step to
 * standard output. The test requires a physical DC/DC converter to be attached; measured output values (voltageOut, currentOut)
 * will only read non-zero when a load is connected.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * java -cp SerialController-1.0.0.jar com.serial.IntegrationTest [host [port]]
 * </pre>
 *
 * <p>
 * Arguments are positional and optional:
 * </p>
 * <ul>
 * <li>{@code host} – hostname or IP of the running Serial Controller (default {@code localhost})</li>
 * <li>{@code port} – TCP port (default {@code 8000})</li>
 * </ul>
 *
 * <p>
 * All verifications use {@link #waitForNearValue} which polls {@code GET /api/state} until the expected value is seen (within
 * tolerance) or a timeout expires. This eliminates fixed sleeps and makes the test as fast as the device allows.
 * </p>
 *
 * <p>
 * APIs covered (all except {@code POST /api/exit}):
 * </p>
 * <ul>
 * <li>{@code GET  /api/state}</li>
 * <li>{@code GET  /api/limits}</li>
 * <li>{@code GET  /api/measurements}</li>
 * <li>{@code GET  /api/voltage}</li>
 * <li>{@code GET  /api/current}</li>
 * <li>{@code GET  /api/power}</li>
 * <li>{@code PUT  /api/measurements}</li>
 * <li>{@code PUT  /api/voltage}</li>
 * <li>{@code PUT  /api/current}</li>
 * <li>{@code PUT  /api/output}</li>
 * <li>{@code PUT  /api/keypad}</li>
 * <li>{@code POST /api/protection/clear}</li>
 * </ul>
 */
public class IntegrationTest {

    /** Default hostname when no argument is supplied. */
    private static final String DEFAULT_HOST = "localhost";

    /** Default TCP port when no argument is supplied. */
    private static final int DEFAULT_PORT = 8000;

    /**
     * Tolerance applied when comparing numeric values read back from the device (±0.05 V / ±0.05 A). The device stores
     * setpoints in fixed-point registers with a resolution of 0.01, so a 0.05 margin comfortably covers rounding.
     */
    private static final double SETPOINT_TOLERANCE = 0.05;

    /**
     * Tolerance for verifying output-on measured voltage: 5 % of the expected setpoint. Real-world Modbus converters may show
     * ±1-2 % regulation error plus ADC noise.
     */
    private static final double OUTPUT_VOLTAGE_TOLERANCE_PERCENT = 0.05;

    /**
     * Maximum milliseconds {@link #waitForNearValue} will poll before declaring a failure. 10 s is generous enough to cover
     * buck-boost capacitor bleed-down (voltageOut after output-disable) and one full Modbus poll cycle (1 s) for setpoint
     * confirmation.
     */
    private static final long WAIT_TIMEOUT_MS = 10_000;

    /**
     * Polling interval used by {@link #waitForNearValue} between successive state reads.
     */
    private static final long WAIT_POLL_MS = 500;

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    private int passed;
    private int failed;

    /**
     * Constructs an {@code IntegrationTest} targeting the given base URL.
     *
     * @param baseUrl base URL of the running Serial Controller, e.g. {@code http://localhost:8000}
     */
    public IntegrationTest(final String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Entry point.
     *
     * @param args optional: {@code [host [port]]}
     * @throws Exception on unrecoverable I/O or JSON error
     */
    public static void main(final String[] args) throws Exception {
        final String host = (args.length >= 1) ? args[0] : DEFAULT_HOST;
        final int port = (args.length >= 2) ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        final String baseUrl = "http://" + host + ":" + port;

        System.out.println("=============================================================");
        System.out.println("  Serial Controller Integration Test");
        System.out.println("  Target: " + baseUrl);
        System.out.println("=============================================================");
        System.out.println();

        final IntegrationTest test = new IntegrationTest(baseUrl);
        test.run();
    }

    /**
     * Runs the full test sequence.
     *
     * @throws Exception on unrecoverable I/O or JSON error
     */
    public void run() throws Exception {

        // ------------------------------------------------------------------
        // Step 1: GET /api/state - device online and test voltages/currents within limits
        // ------------------------------------------------------------------
        step("Step 1: GET /api/state - device online and test voltages/currents within limits");
        final JsonNode state = getState();
        checkTrue("deviceOnline is true", state.path("deviceOnline").asBoolean(false));
        final double minV = state.path("minVoltage").asDouble();
        final double maxV = state.path("maxVoltage").asDouble();
        final double minA = state.path("minCurrent").asDouble();
        final double maxA = state.path("maxCurrent").asDouble();
        System.out.printf("  Device: %s %s  |  V: [%.2f, %.2f]  |  A: [%.3f, %.3f]%n", state.path("manufacturer").asText("?"),
                state.path("deviceName").asText("?"), minV, maxV, minA, maxA);
        checkTrue("3.3 V within device voltage range", minV <= 3.3 && 3.3 <= maxV);
        checkTrue("5.0 V within device voltage range", minV <= 5.0 && 5.0 <= maxV);
        checkTrue("0.5 A within device current range", minA <= 0.5 && 0.5 <= maxA);
        checkTrue("1.0 A within device current range", minA <= 1.0 && 1.0 <= maxA);

        // ------------------------------------------------------------------
        // Step 2: GET /api/limits - limits match state and are sensible
        // ------------------------------------------------------------------
        step("Step 2: GET /api/limits - limits consistent with /api/state");
        final JsonNode limits = getLimits();
        checkTrue("limits.manufacturer matches state.manufacturer",
                limits.path("manufacturer").asText("").equals(state.path("manufacturer").asText()));
        checkTrue("limits.deviceName matches state.deviceName",
                limits.path("deviceName").asText("").equals(state.path("deviceName").asText()));
        checkTrue("limits.minVoltage matches state.minVoltage", limits.path("minVoltage").asDouble() == minV);
        checkTrue("limits.maxVoltage matches state.maxVoltage", limits.path("maxVoltage").asDouble() == maxV);
        checkTrue("limits.minCurrent matches state.minCurrent", limits.path("minCurrent").asDouble() == minA);
        checkTrue("limits.maxCurrent matches state.maxCurrent", limits.path("maxCurrent").asDouble() == maxA);
        checkTrue("limits.maxPower > 0", limits.path("maxPower").asDouble() > 0.0);

        // ------------------------------------------------------------------
        // Step 3: PUT /api/output {false} - disable output
        // ------------------------------------------------------------------
        step("Step 3: PUT /api/output {false} - disable output");
        putOutput(false);
        waitForBooleanValue("output disabled", "outputEnabled", false);

        // ------------------------------------------------------------------
        // Step 4: PUT /api/output {true} - enable output
        // ------------------------------------------------------------------
        step("Step 4: PUT /api/output {true} - enable output");
        putOutput(true);
        waitForBooleanValue("output enabled", "outputEnabled", true);

        // ------------------------------------------------------------------
        // Step 5: PUT /api/voltage {5.0} - set voltage, verify via state
        // ------------------------------------------------------------------
        step("Step 5: PUT /api/voltage {5.0} - set voltage to 5.0 V");
        putVoltage(5.0);
        waitForNearValue("voltageSet ≈ 5.0 V", "voltageSet", 5.0, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 6: PUT /api/current {1.0} - set current, verify via state
        // ------------------------------------------------------------------
        step("Step 6: PUT /api/current {1.0} - set current limit to 1.0 A");
        putCurrent(1.0);
        waitForNearValue("currentSet ≈ 1.0 A", "currentSet", 1.0, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 7: PUT /api/voltage/verified {4.8} - verified write, check response body
        // ------------------------------------------------------------------
        step("Step 7: PUT /api/voltage/verified {4.8} - verified voltage write");
        final JsonNode verifiedVoltageResp = putVerified(RestService.URI_VOLTAGE_VERIFIED, "{\"voltage\":4.8}");
        final double verifiedVoltage = verifiedVoltageResp.path("voltageSet").asDouble(Double.NaN);
        checkTrue(String.format("PUT /api/voltage/verified response.voltageSet ≈ 4.8 V (actual=%.3f)", verifiedVoltage),
                Math.abs(verifiedVoltage - 4.8) <= SETPOINT_TOLERANCE);
        waitForNearValue("voltageSet ≈ 4.8 V in state after /verified", "voltageSet", 4.8, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 8: PUT /api/current/verified {0.9} - verified write, check response body
        // ------------------------------------------------------------------
        step("Step 8: PUT /api/current/verified {0.9} - verified current write");
        final JsonNode verifiedCurrentResp = putVerified(RestService.URI_CURRENT_VERIFIED, "{\"current\":0.9}");
        final double verifiedCurrent = verifiedCurrentResp.path("currentSet").asDouble(Double.NaN);
        checkTrue(String.format("PUT /api/current/verified response.currentSet ≈ 0.9 A (actual=%.3f)", verifiedCurrent),
                Math.abs(verifiedCurrent - 0.9) <= SETPOINT_TOLERANCE);
        waitForNearValue("currentSet ≈ 0.9 A in state after /verified", "currentSet", 0.9, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 9: GET /api/voltage - dedicated voltage endpoint matches state
        // ------------------------------------------------------------------
        step("Step 9: GET /api/voltage - dedicated endpoint reflects state voltageOut");
        final JsonNode voltageReading = getJson(RestService.URI_VOLTAGE);
        final double voltageReadingVal = voltageReading.path("voltage").asDouble(Double.NaN);
        final double voltageOutFromState = getState().path("voltageOut").asDouble(Double.NaN);
        checkTrue(String.format("GET /api/voltage matches GET /api/state voltageOut (%.3f vs %.3f)", voltageReadingVal,
                voltageOutFromState), Math.abs(voltageReadingVal - voltageOutFromState) <= SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 10: GET /api/current - dedicated current endpoint matches state
        // ------------------------------------------------------------------
        step("Step 10: GET /api/current - dedicated endpoint reflects state currentOut");
        final JsonNode currentReading = getJson(RestService.URI_CURRENT);
        final double currentReadingVal = currentReading.path("current").asDouble(Double.NaN);
        final double currentOutFromState = getState().path("currentOut").asDouble(Double.NaN);
        checkTrue(String.format("GET /api/current matches GET /api/state currentOut (%.3f vs %.3f)", currentReadingVal,
                currentOutFromState), Math.abs(currentReadingVal - currentOutFromState) <= SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 11: GET /api/power - dedicated power endpoint matches state
        // ------------------------------------------------------------------
        step("Step 11: GET /api/power - dedicated endpoint reflects state powerOut");
        final JsonNode powerReading = getJson(RestService.URI_POWER);
        final double powerReadingVal = powerReading.path("power").asDouble(Double.NaN);
        final double powerOutFromState = getState().path("powerOut").asDouble(Double.NaN);
        checkTrue(String.format("GET /api/power matches GET /api/state powerOut (%.3f vs %.3f)", powerReadingVal,
                powerOutFromState), Math.abs(powerReadingVal - powerOutFromState) <= SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 12: GET /api/measurements - voltage, current, power all match state
        // ------------------------------------------------------------------
        step("Step 12: GET /api/measurements - all three fields match state");
        final JsonNode measurements = getJson(RestService.URI_MEASUREMENTS);
        final JsonNode stateForMeasurements = getState();
        checkTrue(
                String.format("measurements.voltage matches state.voltageOut (%.3f vs %.3f)",
                        measurements.path("voltage").asDouble(), stateForMeasurements.path("voltageOut").asDouble()),
                Math.abs(measurements.path("voltage").asDouble(Double.NaN)
                        - stateForMeasurements.path("voltageOut").asDouble(Double.NaN)) <= SETPOINT_TOLERANCE);
        checkTrue(
                String.format("measurements.current matches state.currentOut (%.3f vs %.3f)",
                        measurements.path("current").asDouble(), stateForMeasurements.path("currentOut").asDouble()),
                Math.abs(measurements.path("current").asDouble(Double.NaN)
                        - stateForMeasurements.path("currentOut").asDouble(Double.NaN)) <= SETPOINT_TOLERANCE);
        checkTrue(
                String.format("measurements.power matches state.powerOut (%.3f vs %.3f)", measurements.path("power").asDouble(),
                        stateForMeasurements.path("powerOut").asDouble()),
                Math.abs(measurements.path("power").asDouble(Double.NaN)
                        - stateForMeasurements.path("powerOut").asDouble(Double.NaN)) <= SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 13: PUT /api/measurements {3.3 V, 0.5 A} - set both setpoints in one call,
        // verify via GET /api/state
        // ------------------------------------------------------------------
        step("Step 13: PUT /api/measurements {3.3 V, 0.5 A} - set both setpoints in one call");
        putMeasurements(3.3, 0.5);
        waitForNearValue("voltageSet ≈ 3.3 V after PUT /api/measurements", "voltageSet", 3.3, SETPOINT_TOLERANCE);
        waitForNearValue("currentSet ≈ 0.5 A after PUT /api/measurements", "currentSet", 0.5, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 14: PUT /api/keypad {true} - lock keypad, verify via state
        // ------------------------------------------------------------------
        step("Step 14: PUT /api/keypad {true} - lock keypad");
        putKeypad(true);
        waitForBooleanValue("keypad locked", "keypadLocked", true);

        // ------------------------------------------------------------------
        // Step 15: PUT /api/keypad {false} - unlock keypad, verify via state
        // ------------------------------------------------------------------
        step("Step 15: PUT /api/keypad {false} - unlock keypad");
        putKeypad(false);
        waitForBooleanValue("keypad unlocked", "keypadLocked", false);

        // ------------------------------------------------------------------
        // Step 16: POST /api/protection/clear - clear protection (HTTP 204 expected;
        // protectionState should be 0 after the call)
        // ------------------------------------------------------------------
        step("Step 16: POST /api/protection/clear - clear protection");
        postProtectionClear();
        waitForNearValue("protectionState = 0 after clear", "protectionState", 0.0, 0.5);

        // ------------------------------------------------------------------
        // Step 17: PUT /api/output {false} - disable output, verify voltageOut ≈ 0 and currentOut ≈ 0
        // ------------------------------------------------------------------
        step("Step 17: PUT /api/output {false} - disable output, verify output readings are ~0");
        putOutput(false);
        waitForBooleanValue("output disabled", "outputEnabled", false);
        waitForNearValue("voltageOut ≈ 0 (output off)", "voltageOut", 0.0, SETPOINT_TOLERANCE);
        waitForNearValue("currentOut ≈ 0 (output off)", "currentOut", 0.0, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 18: Enable output, verify voltageOut ≈ 3.3 V (last setpoint set in step 13)
        // ------------------------------------------------------------------
        step("Step 18: PUT /api/output {true} - enable output, verify voltageOut ≈ 3.3 V");
        putOutput(true);
        waitForBooleanValue("output enabled", "outputEnabled", true);
        waitForNearValue("voltageOut ≈ 3.3 V", "voltageOut", 3.3, 3.3 * OUTPUT_VOLTAGE_TOLERANCE_PERCENT);

        // ------------------------------------------------------------------
        // Step 19: PUT /api/voltage {5.0} - restore 5 V setpoint
        // ------------------------------------------------------------------
        step("Step 19: PUT /api/voltage {5.0} - set voltage to 5.0 V");
        putVoltage(5.0);
        waitForNearValue("voltageSet ≈ 5.0 V", "voltageSet", 5.0, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 20: PUT /api/current {1.0} - restore 1 A setpoint
        // ------------------------------------------------------------------
        step("Step 20: PUT /api/current {1.0} - set current limit to 1.0 A");
        putCurrent(1.0);
        waitForNearValue("currentSet ≈ 1.0 A", "currentSet", 1.0, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 21: PUT /api/output {false} - disable output, verify voltageOut ≈ 0 and currentOut ≈ 0
        // ------------------------------------------------------------------
        step("Step 21: PUT /api/output {false} - disable output, verify output readings are ~0");
        putOutput(false);
        waitForBooleanValue("output disabled", "outputEnabled", false);
        waitForNearValue("voltageOut ≈ 0 (output off)", "voltageOut", 0.0, SETPOINT_TOLERANCE);
        waitForNearValue("currentOut ≈ 0 (output off)", "currentOut", 0.0, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 22: Enable output, verify voltageOut ≈ 5 V
        // ------------------------------------------------------------------
        step("Step 22: PUT /api/output {true} - enable output, verify voltageOut ≈ 5.0 V");
        putOutput(true);
        waitForBooleanValue("output enabled", "outputEnabled", true);
        waitForNearValue("voltageOut ≈ 5.0 V", "voltageOut", 5.0, 5.0 * OUTPUT_VOLTAGE_TOLERANCE_PERCENT);

        // ------------------------------------------------------------------
        // Step 23: PUT /api/voltage {3.3} - set voltage to 3.3 V
        // ------------------------------------------------------------------
        step("Step 23: PUT /api/voltage {3.3} - set voltage to 3.3 V");
        putVoltage(3.3);
        waitForNearValue("voltageSet ≈ 3.3 V", "voltageSet", 3.3, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 24: PUT /api/current {0.5} - set current limit to 0.5 A
        // ------------------------------------------------------------------
        step("Step 24: PUT /api/current {0.5} - set current limit to 0.5 A");
        putCurrent(0.5);
        waitForNearValue("currentSet ≈ 0.5 A", "currentSet", 0.5, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 25: PUT /api/output {false} - disable output, verify voltageOut ≈ 0 and currentOut ≈ 0
        // ------------------------------------------------------------------
        step("Step 25: PUT /api/output {false} - disable output, verify output readings are ~0");
        putOutput(false);
        waitForBooleanValue("output disabled", "outputEnabled", false);
        waitForNearValue("voltageOut ≈ 0 (output off)", "voltageOut", 0.0, SETPOINT_TOLERANCE);
        waitForNearValue("currentOut ≈ 0 (output off)", "currentOut", 0.0, SETPOINT_TOLERANCE);

        // ------------------------------------------------------------------
        // Step 26: Enable output, verify voltageOut ≈ 3.3 V
        // ------------------------------------------------------------------
        step("Step 26: PUT /api/output {true} - enable output, verify voltageOut ≈ 3.3 V");
        putOutput(true);
        waitForBooleanValue("output enabled", "outputEnabled", true);
        waitForNearValue("voltageOut ≈ 3.3 V", "voltageOut", 3.3, 3.3 * OUTPUT_VOLTAGE_TOLERANCE_PERCENT);

        // ------------------------------------------------------------------
        // Summary
        // ------------------------------------------------------------------
        System.out.println();
        System.out.println("=============================================================");
        System.out.printf("  Results:  %d PASSED  |  %d FAILED%n", passed, failed);
        System.out.println("=============================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    /**
     * Issues {@code GET /api/state} and returns the parsed JSON body.
     *
     * @return parsed {@link JsonNode} of the full converter state
     * @throws Exception on HTTP or JSON error
     */
    private JsonNode getState() throws Exception {
        return getJson(RestService.URI_STATE);
    }

    /**
     * Issues {@code GET /api/limits} and returns the parsed JSON body.
     *
     * @return parsed {@link JsonNode} of the limits response
     * @throws Exception on HTTP or JSON error
     */
    private JsonNode getLimits() throws Exception {
        return getJson(RestService.URI_LIMITS);
    }

    /**
     * Issues a {@code GET} request to the given URI path and returns the parsed JSON body. Asserts {@code 200 OK}.
     *
     * @param path URI path from a {@link RestService} {@code URI_*} constant
     * @return parsed {@link JsonNode} of the response body
     * @throws Exception on HTTP or JSON error
     */
    private JsonNode getJson(final String path) throws Exception {
        final HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5)).GET()
                .build();
        final HttpResponse<String> resp = http.send(req, BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("GET " + path + " returned HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return mapper.readTree(resp.body());
    }

    /**
     * Issues {@code PUT /api/output} with the given enable flag.
     *
     * @param enable {@code true} to enable the output, {@code false} to disable it
     * @throws Exception on HTTP or I/O error
     */
    private void putOutput(final boolean enable) throws Exception {
        put(RestService.URI_OUTPUT, "{\"outputEnable\":" + enable + "}");
    }

    /**
     * Issues {@code PUT /api/voltage} with the given voltage setpoint.
     *
     * @param voltage voltage setpoint in volts (V)
     * @throws Exception on HTTP or I/O error
     */
    private void putVoltage(final double voltage) throws Exception {
        put(RestService.URI_VOLTAGE, "{\"voltage\":" + voltage + "}");
    }

    /**
     * Issues {@code PUT /api/current} with the given current setpoint.
     *
     * @param current current setpoint in amperes (A)
     * @throws Exception on HTTP or I/O error
     */
    private void putCurrent(final double current) throws Exception {
        put(RestService.URI_CURRENT, "{\"current\":" + current + "}");
    }

    /**
     * Issues {@code PUT /api/measurements} with both voltage and current setpoints in one call.
     *
     * @param voltage voltage setpoint in volts (V)
     * @param current current setpoint in amperes (A)
     * @throws Exception on HTTP or I/O error
     */
    private void putMeasurements(final double voltage, final double current) throws Exception {
        put(RestService.URI_MEASUREMENTS, "{\"voltage\":" + voltage + ",\"current\":" + current + ",\"power\":0.0}");
    }

    /**
     * Issues {@code PUT /api/keypad} with the given lock state.
     *
     * @param lock {@code true} to lock the keypad, {@code false} to unlock it
     * @throws Exception on HTTP or I/O error
     */
    private void putKeypad(final boolean lock) throws Exception {
        put(RestService.URI_KEYPAD, "{\"keypadLock\":" + lock + "}");
    }

    /**
     * Issues {@code POST /api/protection/clear} with no body, asserting {@code 204 No Content}.
     *
     * @throws Exception on HTTP or I/O error
     */
    private void postProtectionClear() throws Exception {
        final HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + RestService.URI_PROTECTION_CLEAR))
                .timeout(Duration.ofSeconds(5)).POST(BodyPublishers.noBody()).build();
        final HttpResponse<String> resp = http.send(req, BodyHandlers.ofString());
        if (resp.statusCode() != 204) {
            throw new RuntimeException(
                    "POST " + RestService.URI_PROTECTION_CLEAR + " returned HTTP " + resp.statusCode() + ": " + resp.body());
        }
    }

    /**
     * Issues an HTTP PUT to the given path with a JSON body, asserting {@code 204 No Content}.
     *
     * @param path URI path from a {@link RestService} {@code URI_*} constant
     * @param json request body JSON string
     * @throws Exception on HTTP or I/O error
     */
    private void put(final String path, final String json) throws Exception {
        final HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").PUT(BodyPublishers.ofString(json)).build();
        final HttpResponse<String> resp = http.send(req, BodyHandlers.ofString());
        if (resp.statusCode() != 204) {
            throw new RuntimeException("PUT " + path + " returned HTTP " + resp.statusCode() + ": " + resp.body());
        }
    }

    /**
     * Issues an HTTP PUT to the given path with a JSON body, asserting {@code 200 OK}, and returns the parsed response body.
     *
     * <p>
     * Used for the verified endpoints ({@code PUT /api/voltage/verified} and {@code PUT /api/current/verified}) which return
     * {@code 200 OK} with a JSON body containing the confirmed setpoint value, unlike the fire-and-forget endpoints which
     * return {@code 204 No Content}.
     * </p>
     *
     * @param path URI path from a {@link RestService} {@code URI_*} constant
     * @param json request body JSON string
     * @return parsed {@link JsonNode} of the response body
     * @throws Exception on HTTP or JSON error
     */
    private JsonNode putVerified(final String path, final String json) throws Exception {
        final HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").PUT(BodyPublishers.ofString(json)).build();
        final HttpResponse<String> resp = http.send(req, BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("PUT " + path + " returned HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return mapper.readTree(resp.body());
    }

    // -------------------------------------------------------------------------
    // Assertion helpers
    // -------------------------------------------------------------------------

    /**
     * Checks that a boolean condition holds and records PASS / FAIL.
     *
     * @param description human-readable description of the assertion
     * @param condition   the condition to verify
     */
    private void checkTrue(final String description, final boolean condition) {
        if (condition) {
            System.out.println("  PASS: " + description);
            passed++;
        } else {
            System.out.println("  FAIL: " + description);
            failed++;
        }
    }

    /**
     * Polls {@code GET /api/state} repeatedly until the named boolean field equals the expected value or
     * {@link #WAIT_TIMEOUT_MS} elapses, then records PASS / FAIL.
     *
     * @param description   human-readable description
     * @param field         JSON boolean field name in the state object, e.g. {@code "outputEnabled"}
     * @param expectedValue expected boolean value
     * @throws Exception on HTTP or JSON error
     */
    private void waitForBooleanValue(final String description, final String field, final boolean expectedValue)
            throws Exception {
        final long start = System.currentTimeMillis();
        final long deadline = start + WAIT_TIMEOUT_MS;
        boolean actual = !expectedValue;
        while (System.currentTimeMillis() < deadline) {
            actual = getState().path(field).asBoolean(!expectedValue);
            if (actual == expectedValue) {
                break;
            }
            Thread.sleep(WAIT_POLL_MS);
        }
        final long elapsed = System.currentTimeMillis() - start;
        checkTrue(String.format("%s (%s=%b, waited %d ms)", description, field, actual, elapsed), actual == expectedValue);
    }

    /**
     * Polls {@code GET /api/state} repeatedly until the named numeric field is within {@code tolerance} of {@code expected}, or
     * {@link #WAIT_TIMEOUT_MS} elapses, then records PASS / FAIL.
     *
     * <p>
     * Use this for all numeric verifications: setpoints (confirmed via the Modbus poll cycle), measured output values (affected
     * by physical capacitor charge/discharge), and any other state field that may not be instantly visible.
     * </p>
     *
     * @param description human-readable description
     * @param field       JSON numeric field name in the state object, e.g. {@code "voltageSet"}, {@code "voltageOut"}
     * @param expected    expected value
     * @param tolerance   allowed absolute deviation
     * @throws Exception on HTTP or JSON error
     */
    private void waitForNearValue(final String description, final String field, final double expected, final double tolerance)
            throws Exception {
        final long start = System.currentTimeMillis();
        final long deadline = start + WAIT_TIMEOUT_MS;
        double actual = Double.NaN;
        while (System.currentTimeMillis() < deadline) {
            actual = getState().path(field).asDouble(Double.NaN);
            if (Math.abs(actual - expected) <= tolerance) {
                break;
            }
            Thread.sleep(WAIT_POLL_MS);
        }
        final long elapsed = System.currentTimeMillis() - start;
        final boolean ok = Math.abs(actual - expected) <= tolerance;
        checkTrue(String.format("%s (actual=%.3f, expected=%.3f ±%.3f, waited %d ms)", description, actual, expected, tolerance,
                elapsed), ok);
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    /**
     * Prints a step header to standard output.
     *
     * @param title step description
     */
    private void step(final String title) {
        System.out.println();
        System.out.println("--- " + title);
    }

}

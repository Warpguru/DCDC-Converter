package com.serial;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application configuration loader.
 *
 * <p>
 * Configuration is resolved in two layers:
 * </p>
 * <ol>
 * <li><strong>Classpath defaults</strong> — {@code credentials.properties} bundled inside the JAR. Contains the built-in exit
 * credentials ({@code serialcontroller.admin.username}, {@code serialcontroller.admin.password}).</li>
 * <li><strong>External override file</strong> (optional) — a fully-qualified path supplied as the second command-line argument.
 * Every property present in that file overwrites the classpath default. In addition to overriding credentials, the external
 * file may specify:
 * <ul>
 * <li>{@code serialcontroller.host} — hostname or IP address the server binds to (default: {@value #DEFAULT_HOST})</li>
 * <li>{@code serialcontroller.port} — TCP port the HTTP/WebSocket server listens on (default: {@value #DEFAULT_PORT})</li>
 * <li>{@code serialcontroller.log.level} — Log4j2 log level applied to the {@code com.serial} logger at startup, overriding
 * {@code log4j2.xml}. Valid values (case-insensitive): {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR},
 * {@code FATAL}, {@code OFF}. Absent or blank means "keep the level from {@code log4j2.xml}".</li>
 * </ul>
 * </li>
 * </ol>
 */
public class AppConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AppConfiguration.class);

    /** Classpath resource name for the bundled default credentials. */
    private static final String CLASSPATH_DEFAULTS = "/credentials.properties";

    /** Property key for the server host. */
    private static final String KEY_HOST = "serialcontroller.host";

    /** Property key for the server port. */
    private static final String KEY_PORT = "serialcontroller.port";

    /** Property key for the log level override. */
    private static final String KEY_LOG_LEVEL = "serialcontroller.log.level";

    /** Property key for the exit endpoint username. */
    private static final String KEY_ADMIN_USERNAME = "serialcontroller.admin.username";

    /** Property key for the exit endpoint password. */
    private static final String KEY_ADMIN_PASSWORD = "serialcontroller.admin.password";

    /** Default server host when not specified in any config file. */
    public static final String DEFAULT_HOST = "localhost";

    /** Default server port when not specified in any config file. */
    public static final int DEFAULT_PORT = 8000;

    private final Properties props = new Properties();

    /**
     * Loads configuration from the classpath defaults, then overlays the external file if provided.
     *
     * @param externalFilePath fully-qualified path to an external properties file, or {@code null} if no external file was
     *                         supplied
     */
    public AppConfiguration(final String externalFilePath) {
        loadClasspathDefaults();
        if (externalFilePath != null) {
            loadExternalFile(externalFilePath);
        }
    }

    /**
     * Returns the server host.
     *
     * @return host string, e.g. {@code "localhost"} or {@code "0.0.0.0"}
     */
    public String getHost() {
        return props.getProperty(KEY_HOST, DEFAULT_HOST).trim();
    }

    /**
     * Returns the server port.
     *
     * @return TCP port number, e.g. {@code 8000}
     */
    public int getPort() {
        final String raw = props.getProperty(KEY_PORT, String.valueOf(DEFAULT_PORT)).trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            logger.warn("Invalid value for '{}': '{}' — using default {}", KEY_PORT, raw, DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    /**
     * Returns the log level override string for the {@code com.serial} logger.
     *
     * @return log level string (e.g. {@code "DEBUG"}), or an empty string if not set
     */
    public String getLogLevel() {
        final String raw = props.getProperty(KEY_LOG_LEVEL, "").trim();
        return raw;
    }

    /**
     * Returns the GUI administrator username.
     *
     * @return username, or {@code null} if not configured
     */
    public String getAdminUsername() {
        return props.getProperty(KEY_ADMIN_USERNAME);
    }

    /**
     * Returns the GUI administrator password.
     *
     * @return password, or {@code null} if not configured
     */
    public String getAdminPassword() {
        return props.getProperty(KEY_ADMIN_PASSWORD);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads the bundled {@code credentials.properties} from the classpath.
     */
    private void loadClasspathDefaults() {
        try (InputStream in = AppConfiguration.class.getResourceAsStream(CLASSPATH_DEFAULTS)) {
            if (in == null) {
                logger.warn("Classpath resource '{}' not found — no default credentials available.", CLASSPATH_DEFAULTS);
                return;
            }
            props.load(in);
            logger.info("Loaded default configuration from classpath: {}", CLASSPATH_DEFAULTS);
        } catch (Exception e) {
            logger.error("Failed to load classpath defaults from '{}': {}", CLASSPATH_DEFAULTS, e.getMessage());
        }
    }

    /**
     * Loads the external properties file and overlays its values on top of the classpath defaults.
     *
     * @param path fully-qualified file path
     */
    private void loadExternalFile(final String path) {
        try (FileInputStream fis = new FileInputStream(path)) {
            final Properties override = new Properties();
            override.load(fis);
            override.forEach((k, v) -> props.setProperty((String) k, (String) v));
            logger.info("Loaded external configuration from: {}", path);
        } catch (Exception e) {
            logger.error("Failed to load external configuration from '{}': {}", path, e.getMessage());
        }
    }
    
}

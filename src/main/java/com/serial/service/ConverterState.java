package com.serial.service;

/**
 * Thread-safe holder of all DC/DC converter state.
 *
 * <p>
 * This class is the single source of truth for all converter data. It is shared between the Modbus polling thread
 * (which updates measured values and setpoints every second) and the web-layer threads (REST handlers and WebSocket
 * message handlers, which write setpoints on user request).
 * </p>
 *
 * <p>
 * <strong>Thread-safety contract:</strong> All mutable fields are declared {@code volatile}, which guarantees
 * visibility across threads for individual field reads and writes. Compound read-modify-write operations (e.g.
 * validate-then-write) are coordinated externally by {@code DeviceService} using {@code synchronized} methods,
 * which maps directly to a FreeRTOS mutex in the planned ESP32 C port.
 * </p>
 *
 * <p>
 * Fields are grouped into three logical categories:
 * </p>
 * <ol>
 * <li><strong>Measured values</strong> — updated by the polling thread from device registers every second.</li>
 * <li><strong>Setpoints</strong> — the voltage and current targets. Also polled every second so that changes
 * made on the device's physical front panel (buttons/wheel) are picked up automatically.</li>
 * <li><strong>Device limits</strong> — populated once after device detection from a per-device properties file.
 * Setters for limit fields are package-private; only {@link DeviceService} may set them.</li>
 * </ol>
 */
public class ConverterState {

    // -------------------------------------------------------------------------
    // Measured values (updated by polling thread)
    // -------------------------------------------------------------------------

    /**
     * Measured output voltage in volts (V).
     */
    private volatile double voltageOut;

    /**
     * Measured output current in amperes (A).
     */
    private volatile double currentOut;

    /**
     * Measured output power in watts (W).
     */
    private volatile double powerOut;

    /**
     * Measured input voltage in volts (V).
     */
    private volatile double voltageIn;

    /**
     * Internal temperature in degrees Celsius (°C).
     */
    private volatile double temperatureCelsius;

    /**
     * Output enable state.
     *
     * <ul>
     * <li>{@code true} — output is ON</li>
     * <li>{@code false} — output is OFF</li>
     * </ul>
     */
    private volatile boolean outputEnabled;

    /**
     * Protection state code.
     *
     * <ul>
     * <li>0 — normal (no protection tripped)</li>
     * <li>1 — OVP (over-voltage protection)</li>
     * <li>2 — OCP (over-current protection)</li>
     * <li>3 — OPP (over-power protection)</li>
     * <li>4 — LVP (low-voltage protection)</li>
     * <li>5 — OAH (over ampere-hour)</li>
     * <li>6 — OHP (over-time protection)</li>
     * <li>7 — OTP (over-temperature protection)</li>
     * <li>8 — OEP</li>
     * <li>9 — OWH</li>
     * <li>10 — ICP</li>
     * </ul>
     */
    private volatile int protectionState;

    /**
     * Regulation mode.
     *
     * <ul>
     * <li>{@code true} — CV (constant voltage)</li>
     * <li>{@code false} — CC (constant current)</li>
     * </ul>
     */
    private volatile boolean cvMode;

    // -------------------------------------------------------------------------
    // Setpoints (written by user via REST / WebSocket; also polled from device
    // to pick up front-panel changes)
    // -------------------------------------------------------------------------

    /**
     * Voltage setpoint in volts (V).
     *
     * <p>
     * This value is both written by the user (via REST or WebSocket) and polled from the device register every
     * second. Polling ensures that changes made on the device's physical front panel are reflected here.
     * </p>
     */
    private volatile double voltageSet;

    /**
     * Current setpoint in amperes (A).
     *
     * <p>
     * This value is both written by the user (via REST or WebSocket) and polled from the device register every
     * second. Polling ensures that changes made on the device's physical front panel are reflected here.
     * </p>
     */
    private volatile double currentSet;

    // -------------------------------------------------------------------------
    // Device limits (set once after detection from properties file)
    // -------------------------------------------------------------------------

    /**
     * Device model name, e.g. {@code "XY6008"}, {@code "RD5020"}.
     *
     * <p>
     * Matches the file name of the corresponding {@code .properties} file under
     * {@code src/main/resources/devices/}.
     * </p>
     */
    private volatile String deviceName = "Unknown";

    /**
     * Device manufacturer name, e.g. {@code "Sinilink"}, {@code "Riden"}.
     */
    private volatile String manufacturer = "Unknown";

    /**
     * Maximum output voltage in volts (V), from the device datasheet.
     */
    private volatile double maxVoltage;

    /**
     * Minimum output voltage in volts (V), from the device datasheet.
     */
    private volatile double minVoltage;

    /**
     * Maximum output current in amperes (A), from the device datasheet.
     */
    private volatile double maxCurrent;

    /**
     * Minimum output current in amperes (A), from the device datasheet.
     */
    private volatile double minCurrent;

    /**
     * Maximum output power in watts (W), from the device datasheet.
     */
    private volatile double maxPower;

    // -------------------------------------------------------------------------
    // Getters — measured values
    // -------------------------------------------------------------------------

    /**
     * Returns the measured output voltage.
     *
     * @return output voltage in volts (V)
     */
    public double getVoltageOut() {
        return voltageOut;
    }

    /**
     * Sets the measured output voltage. Called by the polling thread.
     *
     * @param voltageOut output voltage in volts (V)
     */
    public void setVoltageOut(final double voltageOut) {
        this.voltageOut = voltageOut;
    }

    /**
     * Returns the measured output current.
     *
     * @return output current in amperes (A)
     */
    public double getCurrentOut() {
        return currentOut;
    }

    /**
     * Sets the measured output current. Called by the polling thread.
     *
     * @param currentOut output current in amperes (A)
     */
    public void setCurrentOut(final double currentOut) {
        this.currentOut = currentOut;
    }

    /**
     * Returns the measured output power.
     *
     * @return output power in watts (W)
     */
    public double getPowerOut() {
        return powerOut;
    }

    /**
     * Sets the measured output power. Called by the polling thread.
     *
     * @param powerOut output power in watts (W)
     */
    public void setPowerOut(final double powerOut) {
        this.powerOut = powerOut;
    }

    /**
     * Returns the measured input voltage.
     *
     * @return input voltage in volts (V)
     */
    public double getVoltageIn() {
        return voltageIn;
    }

    /**
     * Sets the measured input voltage. Called by the polling thread.
     *
     * @param voltageIn input voltage in volts (V)
     */
    public void setVoltageIn(final double voltageIn) {
        this.voltageIn = voltageIn;
    }

    /**
     * Returns the internal temperature.
     *
     * @return temperature in degrees Celsius (°C)
     */
    public double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    /**
     * Sets the internal temperature. Called by the polling thread.
     *
     * @param temperatureCelsius temperature in degrees Celsius (°C)
     */
    public void setTemperatureCelsius(final double temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    /**
     * Returns whether the output is enabled.
     *
     * @return {@code true} if output is ON, {@code false} if output is OFF
     */
    public boolean isOutputEnabled() {
        return outputEnabled;
    }

    /**
     * Sets the output enable state. Called by the polling thread and by write operations.
     *
     * @param outputEnabled {@code true} to indicate output is ON
     */
    public void setOutputEnabled(final boolean outputEnabled) {
        this.outputEnabled = outputEnabled;
    }

    /**
     * Returns the protection state code.
     *
     * @return protection state (0 = normal; see class Javadoc for codes)
     */
    public int getProtectionState() {
        return protectionState;
    }

    /**
     * Sets the protection state code. Called by the polling thread and by {@code clearProtection()}.
     *
     * @param protectionState protection state code
     */
    public void setProtectionState(final int protectionState) {
        this.protectionState = protectionState;
    }

    /**
     * Returns the regulation mode.
     *
     * @return {@code true} = CV (constant voltage), {@code false} = CC (constant current)
     */
    public boolean isCvMode() {
        return cvMode;
    }

    /**
     * Sets the regulation mode. Called by the polling thread.
     *
     * @param cvMode {@code true} for CV mode, {@code false} for CC mode
     */
    public void setCvMode(final boolean cvMode) {
        this.cvMode = cvMode;
    }

    // -------------------------------------------------------------------------
    // Getters/setters — setpoints
    // -------------------------------------------------------------------------

    /**
     * Returns the voltage setpoint.
     *
     * @return voltage setpoint in volts (V)
     */
    public double getVoltageSet() {
        return voltageSet;
    }

    /**
     * Sets the voltage setpoint. Called by the polling thread (to reflect front-panel changes)
     * and by {@code DeviceService.setVoltage()}.
     *
     * @param voltageSet voltage setpoint in volts (V)
     */
    public void setVoltageSet(final double voltageSet) {
        this.voltageSet = voltageSet;
    }

    /**
     * Returns the current setpoint.
     *
     * @return current setpoint in amperes (A)
     */
    public double getCurrentSet() {
        return currentSet;
    }

    /**
     * Sets the current setpoint. Called by the polling thread (to reflect front-panel changes)
     * and by {@code DeviceService.setCurrent()}.
     *
     * @param currentSet current setpoint in amperes (A)
     */
    public void setCurrentSet(final double currentSet) {
        this.currentSet = currentSet;
    }

    // -------------------------------------------------------------------------
    // Getters/setters — device limits (setters package-private)
    // -------------------------------------------------------------------------

    /**
     * Returns the device model name.
     *
     * @return device name, e.g. {@code "XY6008"}
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Sets the device model name. Package-private — only {@link DeviceService} may call this.
     *
     * @param deviceName device model name
     */
    void setDeviceName(final String deviceName) {
        this.deviceName = deviceName;
    }

    /**
     * Returns the device manufacturer name.
     *
     * @return manufacturer, e.g. {@code "Sinilink"}
     */
    public String getManufacturer() {
        return manufacturer;
    }

    /**
     * Sets the device manufacturer name. Package-private — only {@link DeviceService} may call this.
     *
     * @param manufacturer manufacturer name
     */
    void setManufacturer(final String manufacturer) {
        this.manufacturer = manufacturer;
    }

    /**
     * Returns the maximum output voltage.
     *
     * @return maximum voltage in volts (V)
     */
    public double getMaxVoltage() {
        return maxVoltage;
    }

    /**
     * Sets the maximum output voltage. Package-private — only {@link DeviceService} may call this.
     *
     * @param maxVoltage maximum voltage in volts (V)
     */
    void setMaxVoltage(final double maxVoltage) {
        this.maxVoltage = maxVoltage;
    }

    /**
     * Returns the minimum output voltage.
     *
     * @return minimum voltage in volts (V)
     */
    public double getMinVoltage() {
        return minVoltage;
    }

    /**
     * Sets the minimum output voltage. Package-private — only {@link DeviceService} may call this.
     *
     * @param minVoltage minimum voltage in volts (V)
     */
    void setMinVoltage(final double minVoltage) {
        this.minVoltage = minVoltage;
    }

    /**
     * Returns the maximum output current.
     *
     * @return maximum current in amperes (A)
     */
    public double getMaxCurrent() {
        return maxCurrent;
    }

    /**
     * Sets the maximum output current. Package-private — only {@link DeviceService} may call this.
     *
     * @param maxCurrent maximum current in amperes (A)
     */
    void setMaxCurrent(final double maxCurrent) {
        this.maxCurrent = maxCurrent;
    }

    /**
     * Returns the minimum output current.
     *
     * @return minimum current in amperes (A)
     */
    public double getMinCurrent() {
        return minCurrent;
    }

    /**
     * Sets the minimum output current. Package-private — only {@link DeviceService} may call this.
     *
     * @param minCurrent minimum current in amperes (A)
     */
    void setMinCurrent(final double minCurrent) {
        this.minCurrent = minCurrent;
    }

    /**
     * Returns the maximum output power.
     *
     * @return maximum power in watts (W)
     */
    public double getMaxPower() {
        return maxPower;
    }

    /**
     * Sets the maximum output power. Package-private — only {@link DeviceService} may call this.
     *
     * @param maxPower maximum power in watts (W)
     */
    void setMaxPower(final double maxPower) {
        this.maxPower = maxPower;
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    /**
     * Returns a human-readable summary of the current converter state, suitable for logging.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return String.format(
                "ConverterState{device=%s %s, vOut=%.2fV, iOut=%.3fA, pOut=%.2fW, vIn=%.2fV, temp=%.1f°C, " +
                "output=%s, protection=%d, mode=%s, vSet=%.2fV, iSet=%.3fA}",
                manufacturer, deviceName,
                voltageOut, currentOut, powerOut, voltageIn, temperatureCelsius,
                outputEnabled ? "ON" : "OFF",
                protectionState,
                cvMode ? "CV" : "CC",
                voltageSet, currentSet);
    }
}

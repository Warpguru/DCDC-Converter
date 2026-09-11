package com.serial.devices.ifc;

import com.serial.service.DeviceService;

/**
 * Common interfaces for {@code DC/DC Converter}s.
 */
public interface DC2DCConverter {

    /**
     * Retrieve {@code DC/DC Converter} device as {@link String}.
     * 
     * @return device
     */
    public abstract String getDevice();

    /**
     * Set output voltage verified.
     * 
     * @param volts
     * @throws Exception
     */
    public void setVoltageVerified(final double volts) throws Exception;

    /**
     * Set output voltage.
     * 
     * @param volts
     * @throws Exception
     */
    public void setVoltage(final double volts) throws Exception;

    /**
     * Get output voltage.
     * 
     * @return voltage
     * @throws Exception
     */
    public double getVoltage() throws Exception;

    /**
     * Set output current verified.
     * 
     * @param amperes
     * @throws Exception
     */
    public void setCurrentVerified(final double amperes) throws Exception;

    /**
     * Set output current.
     * 
     * @param amperes
     * @throws Exception
     */
    public void setCurrent(final double amperes) throws Exception;

    /**
     * Get output current.
     * 
     * @return amperes
     * @throws Exception
     */
    public double getCurrent() throws Exception;

    /**
     * Get output power.
     * 
     * @return watts
     * @throws Exception
     */
    public double getPower() throws Exception;

    /**
     * Get input voltage.
     * 
     * @return volts
     * @throws Exception
     */
    public double getInputVoltage() throws Exception;

    /**
     * Set output state.
     * 
     * @param on {@code true} or {@code false}
     * @throws Exception
     */
    public void setOutput(final boolean on) throws Exception;

    /**
     * Get output state.
     * 
     * @return {@code true} or {@code false}
     * @throws Exception
     */
    public boolean getOutput() throws Exception;

    /**
     * Get temperature.
     * 
     * @return temperature
     * @throws Exception
     */
    public double getTemperatureCelsius() throws Exception;

    /**
     * Get firmware version.
     * 
     * @return firmwareVersion
     * @throws Exception
     */
    public int getFirmwareVersion() throws Exception;

    /**
     * Set the protection state.
     * 
     * @param on {@code true} or {@code false}
     * @throws Exception
     */
    public void setProtectionState(final boolean on) throws Exception;

    /**
     * Get the protection state.
     *
     * @return {@code true} or {@code false}
     * @throws Exception
     */
    public boolean getProtectionState() throws Exception;

    /**
     * Set the keypad (child lock) state.
     *
     * @param locked {@code true} to lock the keypad, {@code false} to unlock it
     * @throws Exception if writing to the device fails
     */
    public void setKeypad(final boolean locked) throws Exception;

    /**
     * Get the keypad (child lock) state.
     *
     * @return {@code true} if locked, {@code false} if unlocked
     * @throws Exception if reading from the device fails
     */
    public boolean getKeypad() throws Exception;

    /**
     * Returns the regulation mode.
     *
     * @return {@code true} for CV (constant voltage), {@code false} for CC (constant current)
     * @throws Exception if reading from the device fails
     */
    public boolean isCvMode() throws Exception;

    /**
     * Reads all registers needed for a full poll cycle in a single bulk Modbus frame per device, and stores the decoded values
     * in internal cache fields.
     *
     * <p>
     * After this method returns, all getter methods ({@link #getVoltage()}, {@link #getCurrent()}, {@link #getVoltageSet()},
     * etc.) return the freshly cached values without issuing any additional Modbus frames. This reduces the per-cycle serial
     * round-trips from 11 individual reads to a single {@code 0x03} multi-register request per device.
     * </p>
     *
     * <p>
     * <strong>Cache contract:</strong> all cache fields are zero-initialised ({@code 0} / {@code 0.0} / {@code false}) until
     * the first successful call to this method. Getters invoked before the first successful {@code pollAll()} silently return
     * these zero defaults - no exception is thrown. Under normal operation {@link DeviceService} calls {@code pollAll()} via
     * {@code readInitialSetpoints()} during construction before the polling thread starts, so the cache is populated before any
     * getter is used externally. If that initial call fails the exception is caught and logged; all state fields remain at
     * their zero defaults until the first successful poll cycle.
     * </p>
     *
     * <p>
     * A failed bulk read throws before any cache field is written, so on failure the cache retains the values from the previous
     * successful call - there is no partial update.
     * </p>
     *
     * @throws Exception if the bulk Modbus read fails
     */
    public void pollAll() throws Exception;

    /**
     * Returns the cached voltage setpoint (VSET) populated by the last {@link #pollAll()} call.
     *
     * <p>
     * Unlike {@link #getVoltage()}, which returns the measured output voltage (VOUT), this method returns the programmed
     * setpoint register value.
     * </p>
     *
     * <p>
     * Returns {@code 0.0} if {@link #pollAll()} has not yet been called successfully.
     * </p>
     *
     * @return voltage setpoint in volts, or {@code 0.0} if the cache has not been populated
     * @throws Exception if the underlying transport throws during the call
     */
    public double getVoltageSet() throws Exception;

    /**
     * Returns the cached current setpoint (ISET) populated by the last {@link #pollAll()} call.
     *
     * <p>
     * Returns {@code 0.0} if {@link #pollAll()} has not yet been called successfully.
     * </p>
     *
     * @return current setpoint in amperes, or {@code 0.0} if the cache has not been populated
     * @throws Exception if the underlying transport throws during the call
     */
    public double getCurrentSet() throws Exception;

    /**
     * Closes and re-opens the serial transport at the same port and baud rate.
     *
     * <p>
     * Called after consecutive poll failures to recover from a USB-serial adapter being physically disconnected and
     * reconnected.
     * </p>
     *
     * @throws Exception if the transport cannot be re-opened
     */
    public void reconnect() throws Exception;

}

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
     * Sets the output voltage via the slow {@link com.serial.device.base.ModbusDevice#writeVerified} loop
     * (write + 200 ms sleep + read VSET + read VOUT, up to 3 attempts).
     *
     * @deprecated Not called by {@link com.serial.service.DeviceService}. The verified write path in
     *             {@link com.serial.service.DeviceService#setVoltageVerified} uses
     *             {@link #getVoltageSetVerified()} directly for a single fast read-back at 50 ms, making
     *             this method dead code. Retained for potential direct driver use only.
     * @param volts voltage setpoint in volts (V)
     * @throws Exception if the Modbus write or read-back fails
     */
    @Deprecated
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
     * Sets the output current via the slow {@link com.serial.device.base.ModbusDevice#writeVerified} loop
     * (write + 200 ms sleep + read ISET + read IOUT, up to 3 attempts).
     *
     * @deprecated Not called by {@link com.serial.service.DeviceService}. The verified write path in
     *             {@link com.serial.service.DeviceService#setCurrentVerified} uses
     *             {@link #getCurrentSetVerified()} directly for a single fast read-back at 50 ms, making
     *             this method dead code. Retained for potential direct driver use only.
     * @param amperes current setpoint in amperes (A)
     * @throws Exception if the Modbus write or read-back fails
     */
    @Deprecated
    public void setCurrentVerified(final double amperes) throws Exception;

    /**
     * Sets the output voltage and current setpoints atomically in a single Modbus {@code 0x10} Write Multiple Registers frame.
     *
     * <p>
     * Because VSET and ISET are at consecutive register addresses on every supported device, both values can be written in one
     * serial round-trip. This prevents the inter-frame gap between two separate {@code 0x06} frames from being misinterpreted
     * by the Sinilink firmware, which does not tolerate back-to-back single-register writes without a sufficient idle time.
     * </p>
     *
     * @param volts   voltage setpoint in volts (V)
     * @param amperes current setpoint in amperes (A)
     * @throws Exception if the Modbus write fails
     */
    public void setVoltageCurrent(final double volts, final double amperes) throws Exception;

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
     * Reads the voltage setpoint (VSET) register directly from the device via a fresh Modbus frame, bypassing the poll cache.
     *
     * <p>
     * Used by {@link DeviceService#setVoltageVerified} to confirm that the register value has settled after a write, without
     * waiting for the next background poll cycle.
     * </p>
     *
     * @return voltage setpoint in volts, read directly from the device register
     * @throws Exception if the Modbus read fails
     */
    public double getVoltageSetVerified() throws Exception;

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
     * Reads the current setpoint (ISET) register directly from the device via a fresh Modbus frame, bypassing the poll cache.
     *
     * <p>
     * Used by {@link DeviceService#setCurrentVerified} to confirm that the register value has settled after a write, without
     * waiting for the next background poll cycle.
     * </p>
     *
     * @return current setpoint in amperes, read directly from the device register
     * @throws Exception if the Modbus read fails
     */
    public double getCurrentSetVerified() throws Exception;

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

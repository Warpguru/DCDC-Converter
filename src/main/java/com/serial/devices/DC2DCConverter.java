package com.serial.devices;

/**
 * Common interfaces for {@code DC/DC Converter}s.
 */
public interface DC2DCConverter {

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
    
}

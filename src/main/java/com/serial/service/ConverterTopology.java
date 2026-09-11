package com.serial.service;

/**
 * Power-converter topology - describes whether the output voltage is constrained by the input voltage.
 *
 * <p>
 * Used by {@link ConverterState} and {@link DeviceService} to decide whether to apply a dynamic voltage ceiling of
 * {@code voltageIn − 1 V} when the user sets a voltage setpoint.
 * </p>
 *
 * <p>
 * The enum name (e.g. {@code "BUCK"}) is what appears in the device {@code .properties} file under the key
 * {@code device.topology}, and is also what Jackson serialises into the JSON state broadcast to the webpage.
 * </p>
 */
public enum ConverterTopology {

    /**
     * Pure step-down (buck) converter.
     *
     * <p>
     * The output voltage cannot exceed the input voltage minus the dropout voltage (≈ 1 V). Any setpoint above
     * {@code voltageIn − 1 V} will be silently ignored by the device. {@link DeviceService} enforces this ceiling before
     * writing to the device.
     * </p>
     */
    BUCK,

    /**
     * Pure step-up (boost) converter.
     *
     * <p>
     * The output voltage is always higher than the input voltage. No Vin-derived ceiling applies.
     * </p>
     */
    BOOST,

    /**
     * Combined buck/boost converter.
     *
     * <p>
     * The output voltage may be above or below the input voltage. No Vin-derived ceiling applies.
     * </p>
     */
    BUCK_BOOST

}

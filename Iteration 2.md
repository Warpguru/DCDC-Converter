# Iteration 2

The goal of this iteration is to design an architecture to allow this application to interact with various embedded controllers.

## Context

You will resarch how to control these devices with embedded controllers:
- RD6030 (typically manufactured by `Riden`, but possibly also under different brand names)
- XY6008 (typically manufactured by `Sinilink`)

## Task

Your task is to research, analyze and understand the protocols used to communicate with those embedded devices over the serial port.
The interface should be generic, and there should be an implementations for each device.

1. Find the protocol used to control those devices.
Likely this is `Modbus` over serial.
2. Design a generic interface that is common to these devices such as e.g.:
    * read and write device capabilities such as firmware level, display brightness, baud rate
    * read supplied current and voltage
    * write current and voltage limits (for constant current and constant voltage)
    * read and write power off and on status
    * caclulate checksums required for device commands
3. Design a device specific implementation of those interface.
The implementation for now should only write to console and log a textual representation of what the implemented interface does, e.g. `Reading voltage: 12V`.

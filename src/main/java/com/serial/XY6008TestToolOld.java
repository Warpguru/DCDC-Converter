package com.serial;

import com.serial.util.ModbusConstants;
import com.serial.util.ModbusTransport;

@Deprecated
public class XY6008TestToolOld {

    public static void main(String[] args) throws Exception {
        XY6008TestToolOld xy6008TestTool = new XY6008TestToolOld();
        if (args.length == 0) {
            System.out.println("Usage: XY6008TestToolOld <port>");
            return;
        }

        ModbusTransport transport = null;
        try {
            transport = new ModbusTransport(args[0], ModbusConstants.BAUD);
        } catch (Exception e) {
            System.out.println("Usage: XY6008TestToolOld <port>");
            System.out.println("       Port: " + args[0] + " invalid!");
            return;
        }
        XY6008Old xy6008 = new XY6008Old(transport);
        if (!xy6008.verifyDevicePresent()) {
            System.out.println("No XY6008Old detected on this port.");
            transport.close();
            return;
        }

        for (int i = 1; i <= 3; i++) {
            System.out.println("\n=== TEST CYCLE " + i + " ===");
            xy6008.setOutputVoltageVerified(5.0);
            double voltage = xy6008.getOutputVoltage();
            double current = xy6008.getOutputCurrent();
            double power = xy6008.getOutputPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);
            xy6008TestTool.sleepSeconds(1);

            xy6008.setOutputVoltageVerified(3.3);
            xy6008TestTool.sleepSeconds(1);
            voltage = xy6008.getOutputVoltage();
            current = xy6008.getOutputCurrent();
            power = xy6008.getOutputPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);
        }

        xy6008.setOutputVoltage(5.0);
        xy6008.setOutputCurrent(2.0);
        xy6008.setOutputEnabled(true);
        System.out.println("Voltage: " + xy6008.getOutputVoltage());
        System.out.println("Current: " + xy6008.getOutputCurrent());
        System.out.println("Power:   " + xy6008.getOutputPower());
        transport.close();
    }

    void log(String dir, byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data)
            sb.append(String.format("%02X ", b));
        System.out.println(dir + "  " + sb);
    }

    void sleepSeconds(final int seconds) throws Exception {
        System.out.println("Waiting " + seconds + " seconds...");
        Thread.sleep(seconds * 1000);
    }

}

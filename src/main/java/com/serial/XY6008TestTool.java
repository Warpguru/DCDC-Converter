package com.serial;

import com.serial.devices.XY6008;
import com.serial.modbus.ModbusConstants;
import com.serial.modbus.ModbusTransport;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

public class XY6008TestTool {

    public static void main(final String[] args) throws Exception {
        XY6008TestTool xy6008TestTool = new XY6008TestTool();
        xy6008TestTool.process(args);
    }

    private void process(final String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: XY6008TestTool <port>");
            return;
        }
        
        Javalin javalin = Javalin.create(config -> {
            config.jetty.port = 8000;
            // Serve ./public/* at /
            config.staticFiles.add("/public", Location.CLASSPATH);
            // Server HTTP GET /init
            config.routes.get("/init", this::init);
        }).start(8000);

        sleepSeconds(600);
        javalin.stop();
        
        ModbusTransport transport = null;
        try {
            transport = new ModbusTransport(args[0], ModbusConstants.BAUD);
        } catch (Exception e) {
            System.out.println("Usage: XY6008TestTool <port>");
            System.out.println("       Port: " + args[0] + " invalid!");
            return;
        }
        XY6008 xy6008 = new XY6008(transport, ModbusConstants.SLAVE_ADDRESS);
        if (!xy6008.verifyDevicePresent()) {
            System.out.println("No XY6008 detected on this port.");
            transport.close();
            return;
        }

        for (int i = 1; i <= 3; i++) {
            System.out.println("\n=== TEST CYCLE " + i + " ===");
            xy6008.setVoltage(5.0);
            double voltage = xy6008.getVoltage();
            double current = xy6008.getCurrent();
            double power = xy6008.getPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);
            sleepSeconds(1);

            xy6008.setVoltage(3.3);
            sleepSeconds(1);
            voltage = xy6008.getVoltage();
            current = xy6008.getCurrent();
            power = xy6008.getPower();
            System.out.println("V=" + voltage + " I=" + current + " P=" + power);
        }
        transport.close();
    }

    private void config(final Context ctx) {

    }

    private void init(final Context ctx) {
        ctx.result("Abracadabra");
    }

    private void log(String dir, byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data)
            sb.append(String.format("%02X ", b));
        System.out.println(dir + "  " + sb);
    }

    private void sleepSeconds(final int seconds) throws Exception {
        System.out.println("Waiting " + seconds + " seconds...");
        Thread.sleep(seconds * 1000);
    }

}

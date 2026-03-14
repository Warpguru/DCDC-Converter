# Iteration 3

The goal of this iteration is to design an architecture to allow this application to provide a WebSocket server and client.

## Context

You will resarch how to add to `SerialControllerApp.java`:
- A `Javalin` 7.1.0 compatible WebSocket implementation (in addition to the WebServices served already)

## Task

Your task is to research, analyze and understand how to add a WebSocket server and client to `SerialControllerApp.java`.

1. Add a WebSocket client to `SerialControllerApp.java` that connects to the WebSocket server and displays the data provided by the WebSocket server and client.
The method `startUpdateThread` should be used to test the WebSocket server and client.
2. Modify the HTML page `./public/index.html` to display the data provided by the WebSocket server and client.

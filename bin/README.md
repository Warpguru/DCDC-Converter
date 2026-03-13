# Serial Controller

An application for serial communication with embedded controllers, built with Java 21 and [jSerialComm](https://fazecast.github.io/jSerialComm/).

## Prerequisites

- **JDK 21** or later
- **Apache Maven 3.9+**

## Build

```bash
mvn clean install
```

This produces a fat JAR at `target/SerialController.jar` containing all dependencies.

## Run

```bash
java -jar target/SerialController.jar
```

### Expected Output

```
Hello World
Found 2 serial port(s):
  Port: COM3             Description: USB Serial Port
  Port: COM4             Description: Arduino Uno
```

If no serial devices are connected:

```
Hello World
No serial ports found on this system.
```

## Project Structure

```
Serial/
├── pom.xml
├── README.md
├── .gitignore
└── src/
    ├── main/
    │   ├── java/com/serial/
    │   │   └── SerialControllerApp.java
    │   └── resources/
    │       └── simplelogger.properties
    └── test/
        └── java/
```

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| [jSerialComm](https://fazecast.github.io/jSerialComm/) | 2.11.4 | Cross-platform serial port access |
| [SLF4J](https://www.slf4j.org/) | 2.0.17 | Logging facade + simple backend |

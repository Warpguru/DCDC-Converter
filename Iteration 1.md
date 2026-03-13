# Iteration 1

The goal of this iteration is to build an empty application as a proof of concept that the development pipeline is setup correctly.

## Context

You will research which Java library is the state of the art for serial communication.
You will explain the pros and cons and submit that for a review and instructions how to continue.

## Task

Your task is to create the following:
1. A Maven project that can be build with `mvn clean install` and has the following structure:
- src/main/java
- src/test/java
- pom.xml
2. A simple Java class that: 
    * prints `Hello World` to the console and into the log to proof the application can be built.
    * that enumerates all serial ports and prints the port name and description to the console and into the log.
3. A simple configuration for `Slf4J` in `*.properties` format,.
4. A README.md file that explains how to build and run the application.
5. A .gitignore file that ignores the target directory.

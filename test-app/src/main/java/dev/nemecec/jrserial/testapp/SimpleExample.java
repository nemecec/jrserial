/*
 * Copyright 2025 Neeme Praks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.nemecec.jrserial.testapp;

import dev.nemecec.jrserial.SerialPort;
import dev.nemecec.jrserial.SerialPortInfo;

import java.util.List;

/**
 * Simple example demonstrating basic serial port usage.
 */
@SuppressWarnings("java:S106") // System.out is intentional for this CLI application
public class SimpleExample {

  public static void main(String[] args) {
    // List available ports
    System.out.println("Available serial ports:");
    List<SerialPortInfo> ports = SerialPort.listPorts();
    for (SerialPortInfo port : ports) {
      System.out.println("  - " + port);
    }

    // If no ports available, exit
    if (ports.isEmpty()) {
      System.err.println("No serial ports found!");
      return;
    }

    // Use the first available port
    String portName = ports.get(0).getPortName();
    System.out.println("Using port: " + portName);

    // Create and configure serial port
    try (SerialPort serialPort = SerialPort.builder()
        .portName(portName)
        .baudRate(9600)
        .build()) {

      // Open the port
      System.out.println("Opening port...");
      serialPort.open();
      System.out.println("Port opened successfully!");

      // Write some data
      String message = "Hello, Serial World!\n";
      System.out.println("Writing: " + message.trim());
      serialPort.write(message.getBytes());
      serialPort.flush();

      // Wait a bit for potential response
      Thread.sleep(100);

      // Check if data is available
      int available = serialPort.available();
      System.out.println("Bytes available to read: " + available);

      // Read data if available
      if (available > 0) {
        byte[] buffer = new byte[available];
        int bytesRead = serialPort.read(buffer);
        if (bytesRead > 0) {
          String response = new String(buffer, 0, bytesRead);
          System.out.println("Received: " + response);
        }
      }

      System.out.println("Example completed successfully!");

    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
    }
  }

}

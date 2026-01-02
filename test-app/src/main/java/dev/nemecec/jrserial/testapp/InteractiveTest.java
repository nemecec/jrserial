/*
 * Copyright (C) 2026 Neeme Praks
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Interactive serial port terminal for manual testing.
 * <p>
 * This allows you to: - Connect to a serial port - Send commands interactively - See responses in real-time
 * <p>
 * Useful for testing with devices like Arduino, modems, GPS modules, etc.
 */
@SuppressWarnings("java:S106") // System.out is intentional for this interactive CLI application
public class InteractiveTest {

  private static volatile boolean running = true;

  public static void main(String[] args) {
    System.out.println("=== JR-Serial Interactive Terminal ===");

    // List available ports
    List<SerialPortInfo> ports = SerialPort.listPorts();
    if (ports.isEmpty()) {
      System.err.println("No serial ports found!");
      return;
    }

    System.out.println("Available serial ports:");
    for (int i = 0; i < ports.size(); i++) {
      SerialPortInfo port = ports.get(i);
      System.out.println("  [" + i + "] " + port);
    }

    // Get port selection (not using try-with-resources as closing reader would close System.in)
    @SuppressWarnings("java:S2093")
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    try {
      System.out.print("\nSelect port number: ");
      int portIndex = Integer.parseInt(reader.readLine());

      if (portIndex < 0 || portIndex >= ports.size()) {
        System.err.println("Invalid port number!");
        return;
      }

      String portName = ports.get(portIndex).getPortName();

      System.out.print("Baud rate (default 9600): ");
      String baudStr = reader.readLine().trim();
      int baudRate = baudStr.isEmpty() ? 9600 : Integer.parseInt(baudStr);

      System.out.println("Connecting to " + portName + " at " + baudRate + " baud...");

      SerialPort port = SerialPort.builder()
          .portName(portName)
          .baudRate(baudRate)
          .timeout(100)
          .build();

      port.open();
      System.out.println("Connected!");
      System.out.println("Commands:");
      System.out.println("  Type your message and press Enter to send");
      System.out.println("  Type 'exit' to quit");
      System.out.println("  Type 'clear' to clear input buffer");
      System.out.println("  Type 'info' to show port info");
      System.out.println("  Type 'hex:' followed by hex bytes to send binary (e.g., 'hex:01 02 FF')");

      // Start reader thread
      Thread readerThread = new Thread(() -> readLoop(port));
      readerThread.setDaemon(true);
      readerThread.start();

      // Main input loop
      while (running) {
        String line = reader.readLine();
        if (line == null || line.trim().equalsIgnoreCase("exit")) {
          running = false;
        }
        else {
          processCommand(port, line.trim());
        }
      }

      port.close();
      System.out.println("Disconnected.");

    }
    catch (IOException | NumberFormatException e) {
      System.err.println("Error: " + e.getMessage());
    }
  }

  private static void readLoop(SerialPort port) {
    byte[] buffer = new byte[1024];
    StringBuilder lineBuffer = new StringBuilder();

    while (running) {
      try {
        processAvailableData(port, buffer, lineBuffer);
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        running = false;
      }
      catch (IOException e) {
        if (running) {
          System.err.println("Read error: " + e.getMessage());
        }
        running = false;
      }
    }
  }

  private static void processCommand(SerialPort port, String line) throws IOException {
    if (line.equalsIgnoreCase("clear")) {
      port.clearInput();
      System.out.println("[Input buffer cleared]");
    }
    else if (line.equalsIgnoreCase("info")) {
      showPortInfo(port);
    }
    else if (line.toLowerCase().startsWith("hex:")) {
      sendHex(port, line.substring(4).trim());
    }
    else if (!line.isEmpty()) {
      port.write(line.getBytes());
      port.write(new byte[] { '\r', '\n' }); // Add CR LF
      port.flush();
      System.out.println("-> Sent: " + line);
    }
  }

  private static void processAvailableData(SerialPort port, byte[] buffer, StringBuilder lineBuffer)
      throws IOException {
    int available = port.available();
    if (available <= 0) {
      return;
    }

    int bytesRead = port.read(buffer, 0, Math.min(available, buffer.length));
    if (bytesRead <= 0) {
      return;
    }

    for (int i = 0; i < bytesRead; i++) {
      processCharacter((char) (buffer[i] & 0xFF), lineBuffer);
    }

    // Flush if buffer is getting long
    if (lineBuffer.length() > 100) {
      flushLineBuffer(lineBuffer);
    }
  }

  private static void processCharacter(char c, StringBuilder lineBuffer) {
    if (c == '\r') {
      // Ignore CR
      return;
    }
    if (c == '\n') {
      flushLineBuffer(lineBuffer);
      return;
    }
    if (c >= 32 && c < 127) {
      // Printable ASCII
      lineBuffer.append(c);
    }
    else {
      // Non-printable character
      lineBuffer.append(String.format("[0x%02X]", (int) c));
    }
  }

  private static void flushLineBuffer(StringBuilder lineBuffer) {
    if (lineBuffer.length() > 0) {
      System.out.println("<- Received: " + lineBuffer);
      lineBuffer.setLength(0);
    }
  }

  private static void showPortInfo(SerialPort port) {
    System.out.println("\n=== Port Information ===");
    System.out.println("Port: " + port.getPortName());
    System.out.println("Baud rate: " + port.getBaudRate());
    System.out.println("Data bits: " + port.getDataBits());
    System.out.println("Stop bits: " + port.getStopBits());
    System.out.println("Parity: " + port.getParity());
    System.out.println("Status: " + (port.isOpen() ? "OPEN" : "CLOSED"));
    try {
      System.out.println("Bytes available: " + port.available());
    }
    catch (IOException e) {
      System.out.println("Bytes available: Error - " + e.getMessage());
    }
    System.out.println();
  }

  private static void sendHex(SerialPort port, String hexString) {
    try {
      String[] hexBytes = hexString.split("\\s+");
      byte[] data = new byte[hexBytes.length];

      for (int i = 0; i < hexBytes.length; i++) {
        data[i] = (byte) Integer.parseInt(hexBytes[i], 16);
      }

      port.write(data);
      port.flush();

      StringBuilder sb = new StringBuilder();
      for (byte b : data) {
        sb.append(String.format("%02X ", b & 0xFF));
      }
      System.out.println("-> Sent (hex): " + sb.toString().trim());

    }
    catch (NumberFormatException | IOException e) {
      System.err.println("Error sending hex data: " + e.getMessage());
    }
  }

}

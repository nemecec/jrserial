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

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Loopback test for two serial ports.
 * <p>
 * This test requires: - Two serial ports physically connected (TX of port1 to RX of port2, and vice versa) - Or a
 * loopback adapter on a single port (TX connected to RX)
 * <p>
 * Usage: java -cp build/libs/jr-serial-0.1.0-SNAPSHOT.jar examples.LoopbackTest
 */
@SuppressWarnings("java:S106") // System.out is intentional for this interactive CLI application
public class LoopbackTest {

  public static final String PASSED = "PASSED";
  public static final String FAILED = "FAILED";

  public static void main(String[] args) {
    System.out.println("=== JR-Serial Loopback Test ===\n");

    // List available ports
    List<SerialPortInfo> ports = SerialPort.listPorts();
    if (ports.isEmpty()) {
      System.err.println("No serial ports found!");
      return;
    }

    System.out.println("Available serial ports:");
    for (int i = 0; i < ports.size(); i++) {
      System.out.println("  [" + i + "] " + ports.get(i));
    }

    // Get user input for port selection (not using try-with-resources as closing Scanner would close System.in)
    @SuppressWarnings("java:S2093")
    Scanner scanner = new Scanner(System.in);

    System.out.println("Select test mode:");
    System.out.println("  [1] Single port loopback (requires TX-RX jumper)");
    System.out.println("  [2] Two port test (requires two ports connected)");
    System.out.print("Choice: ");
    int mode = scanner.nextInt();

    if (mode == 1) {
      singlePortLoopback(scanner, ports);
    }
    else if (mode == 2) {
      twoPortTest(scanner, ports);
    }
    else {
      System.err.println("Invalid choice!");
    }

    scanner.close();
  }

  private static void singlePortLoopback(Scanner scanner, List<SerialPortInfo> ports) {
    System.out.print("\nSelect port number for loopback test: ");
    int portIndex = scanner.nextInt();

    if (portIndex < 0 || portIndex >= ports.size()) {
      System.err.println("Invalid port number!");
      return;
    }

    String portName = ports.get(portIndex).getPortName();
    System.out.println("Using port: " + portName);
    System.out.println("Make sure TX and RX are connected (loopback/jumper wire)!\n");

    SerialPort port = SerialPort.builder()
        .portName(portName)
        .baudRate(9600)
        .timeout(1000)
        .build();

    try {
      port.open();
      System.out.println("Port opened successfully!");

      // Test data
      String[] testMessages = {
          "Hello, Serial!",
          "Testing 123",
          "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
          "0123456789",
          "Special chars: !@#$%^&*()"
      };

      int passed = 0;
      int failed = 0;

      for (String testMsg : testMessages) {
        System.out.println("\nTest: Sending '" + testMsg + "'");

        // Clear buffers
        port.clearAll();

        // Write
        byte[] sendData = testMsg.getBytes();
        port.write(sendData);
        port.flush();

        // Wait a bit for data to loop back
        Thread.sleep(100);

        // Read
        int available = port.available();
        System.out.println("  Bytes available: " + available);

        if (available > 0) {
          byte[] receiveData = new byte[available];
          int bytesRead = port.read(receiveData);
          String received = new String(receiveData, 0, bytesRead);

          System.out.println("  Received: '" + received + "'");

          if (testMsg.equals(received)) {
            System.out.println("  PASSED");
            passed++;
          }
          else {
            System.out.println("  FAILED - Data mismatch!");
            failed++;
          }
        }
        else {
          System.out.println("  FAILED - No data received!");
          failed++;
        }
      }

      System.out.println("\n=== Test Results ===");
      System.out.println("Passed: " + passed + "/" + testMessages.length);
      System.out.println("Failed: " + failed + "/" + testMessages.length);

    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
    }
    finally {
      port.close();
      System.out.println("\nPort closed.");
    }
  }

  private static void twoPortTest(Scanner scanner, List<SerialPortInfo> ports) {
    if (ports.size() < 2) {
      System.err.println("Need at least 2 serial ports for this test!");
      return;
    }

    System.out.print("\nSelect port 1 (sender): ");
    int port1Index = scanner.nextInt();
    System.out.print("Select port 2 (receiver): ");
    int port2Index = scanner.nextInt();

    if (port1Index < 0 || port1Index >= ports.size() ||
        port2Index < 0 || port2Index >= ports.size() ||
        port1Index == port2Index) {
      System.err.println("Invalid port selection!");
      return;
    }

    String port1Name = ports.get(port1Index).getPortName();
    String port2Name = ports.get(port2Index).getPortName();

    System.out.println("\nPort 1 (sender): " + port1Name);
    System.out.println("Port 2 (receiver): " + port2Name);
    System.out.println("Make sure ports are connected: TX1->RX2, RX1->TX2\n");

    SerialPort port1 = SerialPort.builder()
        .portName(port1Name)
        .baudRate(9600)
        .timeout(2000)
        .build();

    SerialPort port2 = SerialPort.builder()
        .portName(port2Name)
        .baudRate(9600)
        .timeout(2000)
        .build();

    try {
      port1.open();
      System.out.println("Port 1 opened successfully!");

      port2.open();
      System.out.println("Port 2 opened successfully!");

      // Clear buffers
      port1.clearAll();
      port2.clearAll();

      // Test 1: Port 1 -> Port 2
      System.out.println("\n=== Test 1: Port 1 -> Port 2 ===");
      testCommunication(port1, port2, "Hello from Port 1!");

      Thread.sleep(500);

      // Test 2: Port 2 -> Port 1
      System.out.println("\n=== Test 2: Port 2 -> Port 1 ===");
      testCommunication(port2, port1, "Hello from Port 2!");

      Thread.sleep(500);

      // Test 3: Bidirectional
      System.out.println("\n=== Test 3: Bidirectional Communication ===");
      bidirectionalTest(port1, port2);

      // Test 4: Binary data
      System.out.println("\n=== Test 4: Binary Data Transfer ===");
      binaryDataTest(port1, port2);

      System.out.println("\n=== All Tests Completed ===");

    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
    }
    finally {
      port1.close();
      port2.close();
      System.out.println("\nPorts closed.");
    }
  }

  private static void testCommunication(SerialPort sender, SerialPort receiver, String message)
      throws IOException, InterruptedException {
    System.out.println("Sending: '" + message + "'");

    receiver.clearAll();

    sender.write(message.getBytes());
    sender.flush();

    Thread.sleep(200);

    int available = receiver.available();
    System.out.println("Bytes available: " + available);

    if (available > 0) {
      byte[] buffer = new byte[available];
      int bytesRead = receiver.read(buffer);
      String received = new String(buffer, 0, bytesRead);

      System.out.println("Received: '" + received + "'");

      if (message.equals(received)) {
        System.out.println(PASSED);
      }
      else {
        System.out.println("FAILED - Data mismatch!");
      }
    }
    else {
      System.out.println("FAILED - No data received!");
    }
  }

  private static void bidirectionalTest(SerialPort port1, SerialPort port2)
      throws IOException, InterruptedException {
    port1.clearAll();
    port2.clearAll();

    // Send from both ports simultaneously
    String msg1 = "Message from Port 1";
    String msg2 = "Message from Port 2";

    System.out.println("Port 1 sending: '" + msg1 + "'");
    System.out.println("Port 2 sending: '" + msg2 + "'");

    port1.write(msg1.getBytes());
    port2.write(msg2.getBytes());
    port1.flush();
    port2.flush();

    Thread.sleep(300);

    // Read on both ports
    int avail1 = port1.available();
    int avail2 = port2.available();

    System.out.println("Port 1 has " + avail1 + " bytes available");
    System.out.println("Port 2 has " + avail2 + " bytes available");

    if (avail1 > 0) {
      byte[] buffer = new byte[avail1];
      port1.read(buffer);
      String received = new String(buffer);
      System.out.println("Port 1 received: '" + received + "'");
      System.out.println(msg2.equals(received) ? PASSED : FAILED);
    }

    if (avail2 > 0) {
      byte[] buffer = new byte[avail2];
      port2.read(buffer);
      String received = new String(buffer);
      System.out.println("Port 2 received: '" + received + "'");
      System.out.println(msg1.equals(received) ? PASSED : FAILED);
    }
  }

  private static void binaryDataTest(SerialPort sender, SerialPort receiver)
      throws IOException, InterruptedException {
    receiver.clearAll();

    // Create binary test data
    byte[] testData = new byte[256];
    for (int i = 0; i < 256; i++) {
      testData[i] = (byte) i;
    }

    System.out.println("Sending 256 bytes of binary data (0x00 to 0xFF)");
    sender.write(testData);
    sender.flush();

    Thread.sleep(300);

    int available = receiver.available();
    System.out.println("Bytes available: " + available);

    if (available == 256) {
      byte[] received = new byte[256];
      int bytesRead = receiver.read(received);

      boolean passed = true;
      for (int i = 0; i < bytesRead; i++) {
        if (testData[i] != received[i]) {
          passed = false;
          System.out.println("Mismatch at byte " + i + ": sent=0x" +
              String.format("%02X", testData[i] & 0xFF) +
              ", received=0x" + String.format("%02X", received[i] & 0xFF));
          break;
        }
      }

      if (passed) {
        System.out.println("PASSED - All 256 bytes matched!");
      }
      else {
        System.out.println("FAILED - Binary data mismatch!");
      }
    }
    else {
      System.out.println("FAILED - Expected 256 bytes, got " + available);
    }
  }

}

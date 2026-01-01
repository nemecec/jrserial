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
package dev.nemecec.jrserial.hwtest;

/**
 * Test application for RS-485 hardware testing.
 * This application is deployed to remote machines and runs as a TCP server
 * that accepts test commands from the test controller.
 * <p>
 * Usage:
 * <pre>
 *   java -jar rs485-test-app-all.jar server [tcpPort] [serialPort]
 * </pre>
 * <p>
 * Arguments:
 * <ul>
 *   <li>tcpPort: TCP port for control connections (default: 9485)</li>
 *   <li>serialPort: Optional serial port to use (can be configured later via commands)</li>
 * </ul>
 */
public class Rs485TestApp {

  public static void main(String[] args) {
    System.out.flush();
    System.err.flush();

    if (args.length < 1 || !"server".equalsIgnoreCase(args[0])) {
      printUsage();
      System.exit(1);
      return;
    }

    int tcpPort = 9485;
    String serialPort = null;

    if (args.length > 1) {
      tcpPort = Integer.parseInt(args[1]);
    }
    if (args.length > 2) {
      serialPort = args[2];
    }

    debugLog("Starting in server mode on TCP port " + tcpPort);

    Rs485TestServer server = new Rs485TestServer(tcpPort, serialPort);
    try {
      server.start();
    } catch (Exception e) {
      System.err.println("Failed to start server: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void printUsage() {
    System.err.println("Usage: java -jar rs485-test-app-all.jar server [tcpPort] [serialPort]");
    System.err.println();
    System.err.println("Arguments:");
    System.err.println("  tcpPort     TCP port for server mode (default: 9485)");
    System.err.println("  serialPort  Optional serial port path (e.g., /dev/ttyUSB0)");
  }

  private static void debugLog(String message) {
    System.err.println("[RS485_DEBUG] " + message);
    System.err.flush();
  }
}

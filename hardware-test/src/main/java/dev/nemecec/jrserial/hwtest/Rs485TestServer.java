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
package dev.nemecec.jrserial.hwtest;

import dev.nemecec.jrserial.Rs485Config;
import dev.nemecec.jrserial.Rs485ControlPin;
import dev.nemecec.jrserial.SerialPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long-running test server for RS-485 hardware testing.
 * <p>
 * Listens on a TCP port and accepts commands to control serial port operations.
 * This avoids JVM startup overhead and allows proper synchronization between
 * sender and receiver without arbitrary delays.
 * <p>
 * Commands (line-based, responses are JSON):
 * <ul>
 *   <li>PING - Health check, returns {"status":"ok"}</li>
 *   <li>CONFIGURE &lt;port&gt; &lt;baud&gt; &lt;mode&gt; &lt;pin&gt; - Open serial port</li>
 *   <li>CLOSE - Close serial port (keep server running)</li>
 *   <li>SEND &lt;base64data&gt; - Send data, returns timing</li>
 *   <li>RECEIVE &lt;timeoutMs&gt; &lt;expectedBytes&gt; - Receive data</li>
 *   <li>READY_TO_RECEIVE - Signal ready to receive (for synchronization)</li>
 *   <li>WARMUP &lt;iterations&gt; - Run warmup operations</li>
 *   <li>SHUTDOWN - Stop the server</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 *   java -jar rs485-test-app-all.jar server &lt;port&gt; [serialPort]
 * </pre>
 */
public class Rs485TestServer {

  private static final int DEFAULT_TCP_PORT = 9485;
  private static final int READ_POLL_INTERVAL_MS = 10;

  private final int tcpPort;
  private final String defaultSerialPort;
  private final AtomicBoolean running = new AtomicBoolean(true);

  private SerialPort serialPort;
  private InputStream serialIn;
  private OutputStream serialOut;
  private boolean kernelRs485Active;

  public Rs485TestServer(int tcpPort, String defaultSerialPort) {
    this.tcpPort = tcpPort;
    this.defaultSerialPort = defaultSerialPort;
  }

  public void start() throws IOException {
    log("Starting RS-485 Test Server on TCP port " + tcpPort);

    try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
      serverSocket.setReuseAddress(true);
      // Output SERVER_READY to both stdout and stderr, and flush immediately
      String readyMsg = "SERVER_READY port=" + tcpPort;
      System.out.println(readyMsg);
      System.out.flush();
      log(readyMsg);
      System.err.flush();

      while (running.get()) {
        try {
          Socket clientSocket = serverSocket.accept();
          log("Client connected from " + clientSocket.getRemoteSocketAddress());
          handleClient(clientSocket);
        } catch (IOException e) {
          if (running.get()) {
            log("Error accepting client: " + e.getMessage());
          }
        }
      }
    } finally {
      closeSerialPort();
    }

    log("Server stopped");
  }

  private void handleClient(Socket clientSocket) {
    try (
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
    ) {
      clientSocket.setSoTimeout(60000); // 60 second timeout for commands

      String line;
      while (running.get() && (line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;

        log("Command: " + (line.length() > 100 ? line.substring(0, 100) + "..." : line));
        String response = handleCommand(line);
        out.println(response);
        log("Response: " + (response.length() > 200 ? response.substring(0, 200) + "..." : response));
      }
    } catch (IOException e) {
      log("Client error: " + e.getMessage());
    }
    log("Client disconnected");
  }

  private String handleCommand(String commandLine) {
    String[] parts = commandLine.split("\\s+", 2);
    String command = parts[0].toUpperCase();
    String args = parts.length > 1 ? parts[1] : "";

    try {
      switch (command) {
        case "PING":
          return jsonOk("pong");
        case "CONFIGURE":
          return handleConfigure(args);
        case "CLOSE":
          return handleClose();
        case "SEND":
          return handleSend(args);
        case "SEND_RECEIVE":
          return handleSendReceive(args);
        case "RECEIVE":
          return handleReceive(args);
        case "READY_TO_RECEIVE":
          return handleReadyToReceive();
        case "ECHO":
          return handleEcho(args);
        case "WARMUP":
          return handleWarmup(args);
        case "SHUTDOWN":
          return handleShutdown();
        default:
          return jsonError("Unknown command: " + command);
      }
    } catch (Exception e) {
      log("Error handling command: " + e.getMessage());
      return jsonError(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private String handleConfigure(String args) throws IOException {
    String[] parts = args.split("\\s+");
    if (parts.length < 4) {
      return jsonError("Usage: CONFIGURE <port> <baud> <mode> <pin>");
    }

    String portName = parts[0];
    int baudRate = Integer.parseInt(parts[1]);
    String modeStr = parts[2].toUpperCase();
    Rs485ControlPin pin = Rs485ControlPin.valueOf(parts[3].toUpperCase());

    // Close existing port if open
    closeSerialPort();

    SerialPort.Builder builder = SerialPort.builder()
        .portName(portName)
        .baudRate(baudRate)
        .timeout(1000);

    // Configure RS-485 if mode is not NONE
    if (!"NONE".equals(modeStr)) {
      Rs485Config rs485Config = Rs485Config.builder()
          .enabled(true)
          .controlPin(pin)
          .build();
      builder.rs485Config(rs485Config);
    }

    serialPort = builder.build();
    serialPort.open();
    serialIn = serialPort.getInputStream();
    serialOut = serialPort.getOutputStream();
    kernelRs485Active = serialPort.isKernelRs485Active();

    log("Serial port configured: " + portName + " @ " + baudRate + " baud, mode=" + modeStr + ", kernelRs485=" + kernelRs485Active);

    return String.format("{\"status\":\"ok\",\"port\":\"%s\",\"baudRate\":%d,\"mode\":\"%s\",\"kernelRs485\":%s}",
        portName, baudRate, modeStr, kernelRs485Active);
  }

  private String handleClose() {
    closeSerialPort();
    return jsonOk("closed");
  }

  private String handleSend(String args) throws IOException {
    if (serialOut == null) {
      return jsonError("Serial port not configured");
    }

    byte[] data = Base64.getDecoder().decode(args.trim());

    long startTime = System.nanoTime();
    serialOut.write(data);
    serialOut.flush();
    long endTime = System.nanoTime();

    long durationNanos = endTime - startTime;
    double durationMs = durationNanos / 1_000_000.0;
    double throughput = data.length / (durationMs / 1000.0);

    log("Sent " + data.length + " bytes in " + String.format("%.2f", durationMs) + " ms");

    return String.format(
        "{\"status\":\"ok\",\"bytesSent\":%d,\"durationMs\":%.3f,\"throughputBytesPerSec\":%.1f,\"kernelRs485\":%s}",
        data.length, durationMs, throughput, kernelRs485Active);
  }

  /**
   * Send data and wait for echoed response. Measures round-trip time.
   * Args: <timeoutMs> <base64data>
   * All timing is done on this machine for accurate measurement.
   */
  private String handleSendReceive(String args) throws IOException {
    if (serialIn == null || serialOut == null) {
      return jsonError("Serial port not configured");
    }

    String[] parts = args.split("\\s+", 2);
    if (parts.length < 2) {
      return jsonError("Usage: SEND_RECEIVE <timeoutMs> <base64data>");
    }
    int timeoutMs = Integer.parseInt(parts[0]);
    byte[] data = Base64.getDecoder().decode(parts[1].trim());

    // Clear input buffer before starting
    drainInputStream();

    // Start timing
    long startTime = System.nanoTime();

    // Send data
    serialOut.write(data);
    serialOut.flush();
    long afterSend = System.nanoTime();

    // Receive echoed data
    byte[] buffer = new byte[data.length];
    int totalRead = 0;
    long deadline = System.currentTimeMillis() + timeoutMs;
    long firstByteTime = 0;

    while (System.currentTimeMillis() < deadline && totalRead < data.length) {
      int available = serialIn.available();
      if (available > 0) {
        if (firstByteTime == 0) {
          firstByteTime = System.nanoTime();
        }
        int bytesToRead = Math.min(available, data.length - totalRead);
        int read = serialIn.read(buffer, totalRead, bytesToRead);
        if (read > 0) {
          totalRead += read;
        }
      } else {
        try {
          Thread.sleep(READ_POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    long endTime = System.nanoTime();

    // Calculate metrics
    double roundTripMs = (endTime - startTime) / 1_000_000.0;
    double sendDurationMs = (afterSend - startTime) / 1_000_000.0;
    double oneWayMs = roundTripMs / 2.0;
    double throughputBytesPerSec = data.length / (oneWayMs / 1000.0);

    // Verify data integrity
    boolean dataMatch = totalRead == data.length && Arrays.equals(data, buffer);
    String receivedBase64 = Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, totalRead));

    log(String.format("Round-trip: sent %d bytes, received %d bytes in %.2f ms (%.1f bytes/sec one-way)",
        data.length, totalRead, roundTripMs, throughputBytesPerSec));

    return String.format(
        "{\"status\":\"ok\",\"bytesSent\":%d,\"bytesReceived\":%d,\"roundTripMs\":%.3f,\"sendDurationMs\":%.3f,\"oneWayMs\":%.3f,\"throughputBytesPerSec\":%.1f,\"dataMatch\":%s,\"data\":\"%s\",\"kernelRs485\":%s}",
        data.length, totalRead, roundTripMs, sendDurationMs, oneWayMs, throughputBytesPerSec, dataMatch, receivedBase64, kernelRs485Active);
  }

  private String handleReceive(String args) throws IOException {
    if (serialIn == null) {
      return jsonError("Serial port not configured");
    }

    String[] parts = args.split("\\s+");
    int timeoutMs = Integer.parseInt(parts[0]);
    int expectedBytes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

    // Note: Do NOT clear input here - READY_TO_RECEIVE already clears the buffer
    // before the sender starts, and data may have already arrived by the time
    // this method is called.

    byte[] buffer = new byte[Math.max(expectedBytes, 65536)];
    int totalRead = 0;
    long startTime = System.nanoTime();
    long firstByteTime = 0;
    long deadline = System.currentTimeMillis() + timeoutMs;

    while (System.currentTimeMillis() < deadline) {
      int available = serialIn.available();
      if (available > 0) {
        if (firstByteTime == 0) {
          firstByteTime = System.nanoTime();
        }
        int bytesToRead = Math.min(available, buffer.length - totalRead);
        int read = serialIn.read(buffer, totalRead, bytesToRead);
        if (read > 0) {
          totalRead += read;
          // If we have expected bytes and received them all, we're done
          if (expectedBytes > 0 && totalRead >= expectedBytes) {
            break;
          }
        }
      } else {
        try {
          Thread.sleep(READ_POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    long endTime = System.nanoTime();
    double totalDurationMs = (endTime - startTime) / 1_000_000.0;
    double transferDurationMs = firstByteTime > 0 ? (endTime - firstByteTime) / 1_000_000.0 : 0;
    double latencyMs = firstByteTime > 0 ? (firstByteTime - startTime) / 1_000_000.0 : totalDurationMs;

    String receivedData = Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, totalRead));
    double throughput = transferDurationMs > 0 ? totalRead / (transferDurationMs / 1000.0) : 0;

    log("Received " + totalRead + " bytes in " + String.format("%.2f", totalDurationMs) + " ms (latency: " + String.format("%.2f", latencyMs) + " ms)");

    return String.format(
        "{\"status\":\"ok\",\"bytesReceived\":%d,\"expectedBytes\":%d,\"totalDurationMs\":%.3f,\"latencyMs\":%.3f,\"transferDurationMs\":%.3f,\"throughputBytesPerSec\":%.1f,\"data\":\"%s\",\"kernelRs485\":%s}",
        totalRead, expectedBytes, totalDurationMs, latencyMs, transferDurationMs, throughput, receivedData, kernelRs485Active);
  }

  private String handleReadyToReceive() throws IOException {
    if (serialIn == null) {
      return jsonError("Serial port not configured");
    }
    // Clear input buffer and signal ready
    drainInputStream();
    return jsonOk("ready");
  }

  /**
   * Echo mode: receive data and immediately send it back.
   * Used for round-trip throughput measurement from a single machine.
   * Args: <timeoutMs> <expectedBytes>
   */
  private String handleEcho(String args) throws IOException {
    if (serialIn == null || serialOut == null) {
      return jsonError("Serial port not configured");
    }

    String[] parts = args.split("\\s+");
    if (parts.length < 2) {
      return jsonError("Usage: ECHO <timeoutMs> <expectedBytes>");
    }
    int timeoutMs = Integer.parseInt(parts[0]);
    int expectedBytes = Integer.parseInt(parts[1]);

    // Clear any stale data
    drainInputStream();

    byte[] buffer = new byte[expectedBytes];
    int totalRead = 0;
    long deadline = System.currentTimeMillis() + timeoutMs;

    // Receive data
    while (System.currentTimeMillis() < deadline && totalRead < expectedBytes) {
      int available = serialIn.available();
      if (available > 0) {
        int bytesToRead = Math.min(available, expectedBytes - totalRead);
        int read = serialIn.read(buffer, totalRead, bytesToRead);
        if (read > 0) {
          totalRead += read;
        }
      } else {
        try {
          Thread.sleep(READ_POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    if (totalRead < expectedBytes) {
      return jsonError("Echo timeout: received only " + totalRead + " of " + expectedBytes + " bytes");
    }

    // Echo back immediately
    serialOut.write(buffer, 0, totalRead);
    serialOut.flush();

    log("Echo: received and sent back " + totalRead + " bytes");

    return String.format(
        "{\"status\":\"ok\",\"bytesEchoed\":%d,\"kernelRs485\":%s}",
        totalRead, kernelRs485Active);
  }

  private String handleWarmup(String args) throws IOException {
    if (serialIn == null) {
      return jsonError("Serial port not configured");
    }

    int iterations = args.isEmpty() ? 10 : Integer.parseInt(args.trim());

    log("Running warmup with " + iterations + " iterations");

    // Warmup: do some read/write operations to trigger JIT compilation
    for (int i = 0; i < iterations; i++) {
      // Just trigger the code paths, don't care about actual serial communication
      serialIn.available();
      // Small sleep to avoid busy loop
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    log("Warmup complete");
    return jsonOk("warmup_complete");
  }

  private String handleShutdown() {
    log("Shutdown requested");
    running.set(false);
    closeSerialPort();
    return jsonOk("shutting_down");
  }

  private void closeSerialPort() {
    serialIn = null;
    serialOut = null;
    if (serialPort != null) {
      try {
        serialPort.close();
        log("Serial port closed");
      } catch (Exception e) {
        log("Error closing serial port: " + e.getMessage());
      }
      serialPort = null;
    }
  }

  /**
   * Drain any available data from the input stream.
   */
  private void drainInputStream() throws IOException {
    if (serialIn != null) {
      int available;
      while ((available = serialIn.available()) > 0) {
        serialIn.skip(available);
      }
    }
  }

  private String jsonOk(String message) {
    return "{\"status\":\"ok\",\"message\":\"" + message + "\"}";
  }

  private String jsonError(String message) {
    return "{\"status\":\"error\",\"message\":\"" + escapeJson(message) + "\"}";
  }

  private String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  private void log(String message) {
    System.err.println("[RS485_SERVER] " + message);
    System.err.flush();
  }

  public static void main(String[] args) {
    int tcpPort = DEFAULT_TCP_PORT;
    String serialPort = null;

    if (args.length > 0) {
      tcpPort = Integer.parseInt(args[0]);
    }
    if (args.length > 1) {
      serialPort = args[1];
    }

    Rs485TestServer server = new Rs485TestServer(tcpPort, serialPort);
    try {
      server.start();
    } catch (IOException e) {
      System.err.println("Failed to start server: " + e.getMessage());
      System.exit(1);
    }
  }
}

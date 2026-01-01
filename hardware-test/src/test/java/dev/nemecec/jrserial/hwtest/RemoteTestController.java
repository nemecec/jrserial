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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Controller for coordinating RS-485 tests between remote machines.
 * <p>
 * Manages long-running test servers on remote machines via SSH tunnels,
 * enabling efficient test execution without JVM startup overhead.
 */
public class RemoteTestController implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(RemoteTestController.class);
  private static final int DEFAULT_SERVER_PORT = 9485;
  private static final int STARTUP_TIMEOUT_MS = 30000;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SshExecutor senderSsh;
  private final SshExecutor receiverSsh;
  private final TestConfig testConfig;
  private final String remoteJarPath;

  private int senderLocalPort;
  private int receiverLocalPort;
  private Socket senderSocket;
  private Socket receiverSocket;
  private PrintWriter senderOut;
  private BufferedReader senderIn;
  private PrintWriter receiverOut;
  private BufferedReader receiverIn;

  private boolean serversStarted = false;
  private String senderPid;
  private String receiverPid;

  public RemoteTestController(SshExecutor senderSsh, SshExecutor receiverSsh,
                              TestConfig testConfig, String remoteJarPath) {
    this.senderSsh = senderSsh;
    this.receiverSsh = receiverSsh;
    this.testConfig = testConfig;
    this.remoteJarPath = remoteJarPath;
  }

  /**
   * Start the test servers on both remote machines.
   * Sets up SSH port forwarding for TCP control connections.
   */
  public void startServers() throws JSchException, IOException {
    LOG.info("Starting test servers on remote machines...");

    // Kill any existing server processes
    cleanup();

    // Start servers on remote machines (background processes)
    senderPid = startRemoteServer(senderSsh, "sender");
    receiverPid = startRemoteServer(receiverSsh, "receiver");

    // Set up SSH port forwarding
    senderLocalPort = setupPortForwarding(senderSsh, DEFAULT_SERVER_PORT);
    receiverLocalPort = setupPortForwarding(receiverSsh, DEFAULT_SERVER_PORT);

    LOG.info("Port forwarding: sender=localhost:{}, receiver=localhost:{}",
        senderLocalPort, receiverLocalPort);

    // Connect to the servers via forwarded ports
    connectToServer("sender", senderLocalPort);
    connectToServer("receiver", receiverLocalPort);

    // Verify connections with ping
    pingServer("sender");
    pingServer("receiver");

    serversStarted = true;
    LOG.info("Both test servers started and connected");
  }

  private String startRemoteServer(SshExecutor ssh, String name) throws JSchException, IOException {
    String javaExec = testConfig.getJavaExecutable();
    String logFile = testConfig.getRemoteWorkDir() + "/server-" + name + ".log";

    // Start server as background process (BusyBox-compatible, no nohup)
    // The process will continue after SSH disconnects thanks to redirected I/O
    String cmd = String.format(
        "cd %s && %s -jar %s server %d </dev/null >%s 2>&1 & echo $!",
        testConfig.getRemoteWorkDir(), javaExec, remoteJarPath, DEFAULT_SERVER_PORT, logFile);

    LOG.info("Starting {} server: {}", name, cmd);

    SshExecutor.CommandResult result = ssh.executeCommand(cmd, 10);
    if (result.exitCode != 0) {
      throw new IOException("Failed to start " + name + " server: " + result.stderr);
    }

    String pid = result.stdout.trim();
    LOG.info("{} server started with PID: {}", name, pid);

    // Wait for server to be ready
    waitForServerReady(ssh, name);

    return pid;
  }

  private void waitForServerReady(SshExecutor ssh, String name) throws JSchException, IOException {
    String logFile = testConfig.getRemoteWorkDir() + "/server-" + name + ".log";
    long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;

    while (System.currentTimeMillis() < deadline) {
      // BusyBox-compatible: use grep without -q, check for output containing SERVER_READY
      SshExecutor.CommandResult result = ssh.executeCommand("grep SERVER_READY " + logFile + " 2>/dev/null", 5);
      if (result.stdout.contains("SERVER_READY")) {
        LOG.info("{} server is ready", name);
        return;
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while waiting for " + name + " server");
      }
    }

    // Get log content for debugging
    SshExecutor.CommandResult logResult = ssh.executeCommand("cat " + logFile + " 2>/dev/null || echo 'Log file not found'", 5);
    throw new IOException("Timeout waiting for " + name + " server to start. Log:\n" + logResult.stdout + logResult.stderr);
  }

  private int setupPortForwarding(SshExecutor ssh, int remotePort) throws JSchException {
    Session session = ssh.getTargetSession();
    if (session == null) {
      throw new IllegalStateException("SSH session not connected");
    }
    // Set up local port forwarding: localhost:random -> remote:serverPort
    return session.setPortForwardingL(0, "127.0.0.1", remotePort);
  }

  private void connectToServer(String name, int localPort) throws IOException {
    LOG.info("Connecting to {} server at localhost:{}", name, localPort);

    int retries = 10;
    IOException lastError = null;

    for (int i = 0; i < retries; i++) {
      try {
        Socket socket = new Socket("127.0.0.1", localPort);
        socket.setSoTimeout(30000);

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        if ("sender".equals(name)) {
          senderSocket = socket;
          senderOut = out;
          senderIn = in;
        } else {
          receiverSocket = socket;
          receiverOut = out;
          receiverIn = in;
        }
        return;
      } catch (IOException e) {
        lastError = e;
        LOG.debug("Connection attempt {} failed: {}", i + 1, e.getMessage());
        try {
          Thread.sleep(500);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted while connecting to " + name);
        }
      }
    }
    throw new IOException("Failed to connect to " + name + " server after " + retries + " attempts", lastError);
  }

  private void pingServer(String name) throws IOException {
    JsonNode response = sendCommand(name, "PING");
    if (!"ok".equals(response.path("status").asText())) {
      throw new IOException("Ping failed for " + name + ": " + response);
    }
    LOG.debug("{} server ping successful", name);
  }

  /**
   * Configure serial ports on both machines.
   */
  public ConfigureResult configure(String senderPort, String receiverPort, int baudRate,
                                   String rs485Mode, String rs485Pin) throws IOException {
    LOG.info("Configuring serial ports: baud={}, mode={}", baudRate, rs485Mode);

    JsonNode senderResp = sendCommand("sender",
        String.format("CONFIGURE %s %d %s %s", senderPort, baudRate, rs485Mode, rs485Pin));
    JsonNode receiverResp = sendCommand("receiver",
        String.format("CONFIGURE %s %d %s %s", receiverPort, baudRate, rs485Mode, rs485Pin));

    return new ConfigureResult(
        "ok".equals(senderResp.path("status").asText()),
        "ok".equals(receiverResp.path("status").asText()),
        senderResp.path("kernelRs485").asBoolean(),
        receiverResp.path("kernelRs485").asBoolean()
    );
  }

  /**
   * Run warmup on both machines.
   */
  public void warmup(int iterations) throws IOException {
    LOG.info("Running warmup with {} iterations", iterations);
    sendCommand("sender", "WARMUP " + iterations);
    sendCommand("receiver", "WARMUP " + iterations);
  }

  /**
   * Execute a synchronized send/receive test.
   * Uses TCP control plane to coordinate without delays.
   */
  public TransferResult transfer(byte[] data, int receiveTimeoutMs) throws IOException {
    String base64Data = Base64.getEncoder().encodeToString(data);

    // Signal receiver to be ready
    JsonNode readyResp = sendCommand("receiver", "READY_TO_RECEIVE");
    if (!"ok".equals(readyResp.path("status").asText())) {
      throw new IOException("Receiver not ready: " + readyResp);
    }

    // Start receive (async - receiver will wait for data)
    long receiveStartTime = System.nanoTime();

    // Send the RECEIVE command (it will block until data or timeout)
    // We need to do this in a separate thread or use async IO
    // For simplicity, we'll send RECEIVE, then SEND, then read RECEIVE response

    // Actually, let's use a simpler approach:
    // 1. Tell receiver to start receiving
    // 2. Tell sender to send
    // 3. Get both results

    // Send data
    JsonNode sendResp = sendCommand("sender", "SEND " + base64Data);
    if (!"ok".equals(sendResp.path("status").asText())) {
      throw new IOException("Send failed: " + sendResp);
    }

    // Now get receive result
    JsonNode recvResp = sendCommand("receiver", "RECEIVE " + receiveTimeoutMs + " " + data.length);

    long totalTime = System.nanoTime() - receiveStartTime;

    return new TransferResult(
        sendResp.path("bytesSent").asInt(),
        recvResp.path("bytesReceived").asInt(),
        sendResp.path("durationMs").asDouble(),
        recvResp.path("latencyMs").asDouble(),
        recvResp.path("transferDurationMs").asDouble(),
        recvResp.path("throughputBytesPerSec").asDouble(),
        totalTime / 1_000_000.0,
        decodeData(recvResp.path("data").asText("")),
        sendResp.path("kernelRs485").asBoolean()
    );
  }

  private byte[] decodeData(String base64) {
    if (base64 == null || base64.isEmpty()) {
      return new byte[0];
    }
    return Base64.getDecoder().decode(base64);
  }

  /**
   * Close serial ports on both machines (but keep servers running).
   */
  public void closePorts() throws IOException {
    sendCommand("sender", "CLOSE");
    sendCommand("receiver", "CLOSE");
  }

  /**
   * Execute a round-trip echo test for accurate throughput measurement.
   * Sender sends data to receiver (in ECHO mode), receiver echoes back,
   * sender receives echo. All timing is done on the sender machine.
   *
   * @param data the test data to send
   * @param timeoutMs timeout for the round-trip operation
   * @return EchoResult with round-trip timing and throughput
   */
  public EchoResult echoTransfer(byte[] data, int timeoutMs) throws IOException {
    String base64Data = Base64.getEncoder().encodeToString(data);

    // Start receiver in ECHO mode in a background thread (it blocks waiting for serial data)
    final IOException[] echoError = {null};
    Thread echoThread = new Thread(() -> {
      try {
        sendCommand("receiver", "ECHO " + timeoutMs + " " + data.length);
      } catch (IOException e) {
        echoError[0] = e;
      }
    });
    echoThread.start();

    // Give receiver a moment to start listening on serial port
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Sender: send data and receive echo (measures round-trip on sender's clock)
    JsonNode sendRecvResp = sendCommand("sender", "SEND_RECEIVE " + timeoutMs + " " + base64Data);

    // Wait for echo thread to complete
    try {
      echoThread.join(timeoutMs + 1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    if (echoError[0] != null) {
      throw new IOException("Echo failed: " + echoError[0].getMessage(), echoError[0]);
    }

    if (!"ok".equals(sendRecvResp.path("status").asText())) {
      throw new IOException("Send/receive failed: " + sendRecvResp);
    }

    return new EchoResult(
        sendRecvResp.path("bytesSent").asInt(),
        sendRecvResp.path("bytesReceived").asInt(),
        sendRecvResp.path("roundTripMs").asDouble(),
        sendRecvResp.path("oneWayMs").asDouble(),
        sendRecvResp.path("throughputBytesPerSec").asDouble(),
        sendRecvResp.path("dataMatch").asBoolean(),
        sendRecvResp.path("kernelRs485").asBoolean()
    );
  }

  /**
   * Calculate data size for a given baud rate and target transfer duration.
   * At 8N1, each byte takes 10 bits (start + 8 data + stop).
   */
  public static int calculateDataSize(int baudRate, double targetSeconds) {
    int bytesPerSecond = baudRate / 10;
    return (int) (bytesPerSecond * targetSeconds);
  }

  /**
   * Generate test data of specified size.
   */
  public static byte[] generateTestData(int size) {
    byte[] data = new byte[size];
    for (int i = 0; i < size; i++) {
      data[i] = (byte) (32 + (i % 95)); // Printable ASCII
    }
    return data;
  }

  private JsonNode sendCommand(String target, String command) throws IOException {
    PrintWriter out;
    BufferedReader in;
    SshExecutor ssh;

    if ("sender".equals(target)) {
      out = senderOut;
      in = senderIn;
      ssh = senderSsh;
    } else {
      out = receiverOut;
      in = receiverIn;
      ssh = receiverSsh;
    }

    if (out == null || in == null) {
      throw new IOException("Not connected to " + target);
    }

    out.println(command);
    String response = in.readLine();
    if (response == null) {
      // Fetch server log to help diagnose the issue
      String machineName = "sender".equals(target)
          ? testConfig.getSender().getName() + " @ " + testConfig.getSender().getHost()
          : testConfig.getReceiver().getName() + " @ " + testConfig.getReceiver().getHost();
      String serverLog = fetchServerLog(ssh, target);
      throw new IOException("No response from " + target + " [" + machineName + "] for command: " + command +
          "\n--- Server log (" + target + " [" + machineName + "]) ---\n" + serverLog);
    }

    return MAPPER.readTree(response);
  }

  private String fetchServerLog(SshExecutor ssh, String name) {
    if (ssh == null) {
      return "(SSH not connected)";
    }
    try {
      String logFile = testConfig.getRemoteWorkDir() + "/server-" + name + ".log";
      SshExecutor.CommandResult result = ssh.executeCommand("tail -100 " + logFile + " 2>&1", 5);
      return result.stdout + result.stderr;
    } catch (Exception e) {
      return "(Failed to fetch log: " + e.getMessage() + ")";
    }
  }

  /**
   * Get the current line count for server logs (used as a marker for test boundaries).
   */
  public LogMarker markLogs() {
    int senderLines = getLogLineCount(senderSsh, "sender");
    int receiverLines = getLogLineCount(receiverSsh, "receiver");
    return new LogMarker(senderLines, receiverLines);
  }

  private int getLogLineCount(SshExecutor ssh, String name) {
    if (ssh == null) return 0;
    try {
      String logFile = testConfig.getRemoteWorkDir() + "/server-" + name + ".log";
      SshExecutor.CommandResult result = ssh.executeCommand("wc -l < " + logFile + " 2>/dev/null || echo 0", 5);
      return Integer.parseInt(result.stdout.trim());
    } catch (Exception e) {
      return 0;
    }
  }

  /**
   * Get logs since a marker and check for errors.
   * Returns null if no errors, or error details if errors found.
   * Expected errors (as specified in the LogMarker) are filtered out.
   */
  public String checkLogsForErrors(LogMarker marker) {
    StringBuilder errors = new StringBuilder();

    String senderName = testConfig.getSender().getName();
    String senderHost = testConfig.getSender().getHost();
    String senderErrors = getLogErrorsSince(senderSsh, "sender", marker.senderLines, marker);
    if (senderErrors != null && !senderErrors.isEmpty()) {
      errors.append(String.format("=== SENDER SERVER ERRORS [%s @ %s] ===\n", senderName, senderHost))
            .append(senderErrors).append("\n");
    }

    String receiverName = testConfig.getReceiver().getName();
    String receiverHost = testConfig.getReceiver().getHost();
    String receiverErrors = getLogErrorsSince(receiverSsh, "receiver", marker.receiverLines, marker);
    if (receiverErrors != null && !receiverErrors.isEmpty()) {
      errors.append(String.format("=== RECEIVER SERVER ERRORS [%s @ %s] ===\n", receiverName, receiverHost))
            .append(receiverErrors).append("\n");
    }

    return errors.length() > 0 ? errors.toString() : null;
  }

  private String getLogErrorsSince(SshExecutor ssh, String name, int startLine, LogMarker marker) {
    if (ssh == null) return null;
    try {
      String logFile = testConfig.getRemoteWorkDir() + "/server-" + name + ".log";
      // Get lines since marker, then grep for errors/exceptions
      String cmd = String.format(
          "tail -n +%d %s 2>/dev/null | grep -iE '(exception|error|fatal|caused by|at [a-z].*\\(.*\\.java:[0-9]+\\))' || true",
          startLine + 1, logFile);
      SshExecutor.CommandResult result = ssh.executeCommand(cmd, 10);
      String output = result.stdout.trim();

      if (output.isEmpty()) {
        return null;
      }

      // Filter out expected errors
      StringBuilder unexpectedErrors = new StringBuilder();
      for (String line : output.split("\n")) {
        if (!marker.isExpectedError(line)) {
          if (unexpectedErrors.length() > 0) {
            unexpectedErrors.append("\n");
          }
          unexpectedErrors.append(line);
        }
      }

      return unexpectedErrors.length() > 0 ? unexpectedErrors.toString() : null;
    } catch (Exception e) {
      return "(Failed to check logs: " + e.getMessage() + ")";
    }
  }

  /**
   * Get full logs since a marker (for debugging).
   */
  public String getLogsSince(LogMarker marker) {
    StringBuilder logs = new StringBuilder();

    String senderName = testConfig.getSender().getName();
    String senderHost = testConfig.getSender().getHost();
    String senderLogs = getLogContentSince(senderSsh, "sender", marker.senderLines);
    if (senderLogs != null && !senderLogs.isEmpty()) {
      logs.append(String.format("=== SENDER SERVER LOG [%s @ %s] ===\n", senderName, senderHost))
          .append(senderLogs).append("\n");
    }

    String receiverName = testConfig.getReceiver().getName();
    String receiverHost = testConfig.getReceiver().getHost();
    String receiverLogs = getLogContentSince(receiverSsh, "receiver", marker.receiverLines);
    if (receiverLogs != null && !receiverLogs.isEmpty()) {
      logs.append(String.format("=== RECEIVER SERVER LOG [%s @ %s] ===\n", receiverName, receiverHost))
          .append(receiverLogs).append("\n");
    }

    return logs.toString();
  }

  private String getLogContentSince(SshExecutor ssh, String name, int startLine) {
    if (ssh == null) return null;
    try {
      String logFile = testConfig.getRemoteWorkDir() + "/server-" + name + ".log";
      String cmd = String.format("tail -n +%d %s 2>/dev/null || true", startLine + 1, logFile);
      SshExecutor.CommandResult result = ssh.executeCommand(cmd, 10);
      return result.stdout;
    } catch (Exception e) {
      return "(Failed to fetch logs: " + e.getMessage() + ")";
    }
  }

  /**
   * Marker for log position at start of a test, including expected error patterns.
   */
  public static class LogMarker {
    final int senderLines;
    final int receiverLines;
    final java.util.List<String> expectedErrorPatterns;

    LogMarker(int senderLines, int receiverLines) {
      this.senderLines = senderLines;
      this.receiverLines = receiverLines;
      this.expectedErrorPatterns = new java.util.ArrayList<>();
    }

    /**
     * Add patterns for errors that are expected during this test.
     * Lines matching any of these patterns (case-insensitive) will not cause test failure.
     */
    public void expectErrors(String... patterns) {
      for (String pattern : patterns) {
        expectedErrorPatterns.add(pattern.toLowerCase());
      }
    }

    /**
     * Check if an error line is expected (should not cause test failure).
     */
    boolean isExpectedError(String line) {
      if (line == null) return false;
      String lowerLine = line.toLowerCase();
      for (String pattern : expectedErrorPatterns) {
        if (lowerLine.contains(pattern)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Cleanup: kill server processes and remove files.
   */
  public void cleanup() {
    LOG.info("Cleaning up remote servers...");

    // Close TCP connections
    closeQuietly(senderSocket);
    closeQuietly(receiverSocket);
    senderSocket = null;
    receiverSocket = null;
    senderOut = null;
    senderIn = null;
    receiverOut = null;
    receiverIn = null;

    // Kill server processes by PID (if known) or by finding the process
    killServerProcess(senderSsh, senderPid, "sender");
    killServerProcess(receiverSsh, receiverPid, "receiver");
    senderPid = null;
    receiverPid = null;

    // Clean up log files
    try {
      senderSsh.executeCommand("rm -f " + testConfig.getRemoteWorkDir() + "/server-*.log 2>/dev/null || true", 5);
    } catch (Exception e) {
      LOG.debug("Failed to clean sender logs: {}", e.getMessage());
    }

    try {
      receiverSsh.executeCommand("rm -f " + testConfig.getRemoteWorkDir() + "/server-*.log 2>/dev/null || true", 5);
    } catch (Exception e) {
      LOG.debug("Failed to clean receiver logs: {}", e.getMessage());
    }

    serversStarted = false;
    LOG.info("Cleanup complete");
  }

  private void killServerProcess(SshExecutor ssh, String pid, String name) {
    try {
      if (pid != null && !pid.isEmpty()) {
        // Kill by specific PID
        LOG.debug("Killing {} server with PID {}", name, pid);
        ssh.executeCommand("kill " + pid + " 2>/dev/null; sleep 1; kill -9 " + pid + " 2>/dev/null || true", 5);
      } else {
        // Fallback: find and kill any rs485 test server process
        // BusyBox-compatible: use ps w | grep pattern
        LOG.debug("Finding and killing {} server by process name", name);
        String findAndKill = "PID=$(ps w | grep 'rs485-test-app.*server' | grep -v grep | awk '{print $1}'); " +
            "if [ -n \"$PID\" ]; then kill $PID 2>/dev/null; sleep 1; kill -9 $PID 2>/dev/null; fi || true";
        ssh.executeCommand(findAndKill, 5);
      }
    } catch (Exception e) {
      LOG.warn("Failed to kill {} server: {}", name, e.getMessage());
    }
  }

  private void closeQuietly(Closeable c) {
    if (c != null) {
      try {
        c.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  @Override
  public void close() {
    if (serversStarted) {
      // Shutdown servers gracefully
      try {
        if (senderOut != null) {
          senderOut.println("SHUTDOWN");
        }
      } catch (Exception e) {
        // ignore
      }
      try {
        if (receiverOut != null) {
          receiverOut.println("SHUTDOWN");
        }
      } catch (Exception e) {
        // ignore
      }
    }
    cleanup();
  }

  public boolean isServersStarted() {
    return serversStarted;
  }

  /**
   * Result of port configuration.
   */
  public static class ConfigureResult {
    public final boolean senderOk;
    public final boolean receiverOk;
    public final boolean senderKernelRs485;
    public final boolean receiverKernelRs485;

    public ConfigureResult(boolean senderOk, boolean receiverOk,
                           boolean senderKernelRs485, boolean receiverKernelRs485) {
      this.senderOk = senderOk;
      this.receiverOk = receiverOk;
      this.senderKernelRs485 = senderKernelRs485;
      this.receiverKernelRs485 = receiverKernelRs485;
    }

    public boolean isOk() {
      return senderOk && receiverOk;
    }
  }

  /**
   * Result of a data transfer.
   */
  public static class TransferResult {
    public final int bytesSent;
    public final int bytesReceived;
    public final double sendDurationMs;
    public final double receiveLatencyMs;
    public final double transferDurationMs;
    public final double throughputBytesPerSec;
    public final double totalDurationMs;
    public final byte[] receivedData;
    public final boolean kernelRs485;

    public TransferResult(int bytesSent, int bytesReceived, double sendDurationMs,
                          double receiveLatencyMs, double transferDurationMs,
                          double throughputBytesPerSec, double totalDurationMs,
                          byte[] receivedData, boolean kernelRs485) {
      this.bytesSent = bytesSent;
      this.bytesReceived = bytesReceived;
      this.sendDurationMs = sendDurationMs;
      this.receiveLatencyMs = receiveLatencyMs;
      this.transferDurationMs = transferDurationMs;
      this.throughputBytesPerSec = throughputBytesPerSec;
      this.totalDurationMs = totalDurationMs;
      this.receivedData = receivedData;
      this.kernelRs485 = kernelRs485;
    }

    public boolean isDataMatch(byte[] expected) {
      return java.util.Arrays.equals(expected, receivedData);
    }
  }

  /**
   * Result of a round-trip echo transfer with accurate timing.
   */
  public static class EchoResult {
    public final int bytesSent;
    public final int bytesReceived;
    public final double roundTripMs;
    public final double oneWayMs;
    public final double throughputBytesPerSec;
    public final boolean dataMatch;
    public final boolean kernelRs485;

    public EchoResult(int bytesSent, int bytesReceived, double roundTripMs,
                      double oneWayMs, double throughputBytesPerSec,
                      boolean dataMatch, boolean kernelRs485) {
      this.bytesSent = bytesSent;
      this.bytesReceived = bytesReceived;
      this.roundTripMs = roundTripMs;
      this.oneWayMs = oneWayMs;
      this.throughputBytesPerSec = throughputBytesPerSec;
      this.dataMatch = dataMatch;
      this.kernelRs485 = kernelRs485;
    }

    /**
     * Calculate efficiency compared to theoretical maximum throughput.
     * @param baudRate the baud rate used for the transfer
     * @return efficiency as a percentage (0-100+)
     */
    public double getEfficiency(int baudRate) {
      double theoreticalMax = baudRate / 10.0; // bytes per second at 8N1
      return (throughputBytesPerSec / theoreticalMax) * 100.0;
    }
  }
}

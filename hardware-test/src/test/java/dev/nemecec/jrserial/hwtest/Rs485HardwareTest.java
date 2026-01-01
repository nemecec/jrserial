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

import com.jcraft.jsch.JSchException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RS-485 hardware tests using server-based architecture.
 * <p>
 * Uses long-running test servers on remote machines with TCP control,
 * eliminating JVM startup overhead and enabling precise synchronization.
 * <p>
 * Run with: ./gradlew :hardware-test:test -DhardwareTest=true
 */
@EnabledIfSystemProperty(named = "hardwareTest", matches = "true")
@DisplayName("RS-485 Hardware Tests")
public class Rs485HardwareTest {

  private static final Logger LOG = LoggerFactory.getLogger(Rs485HardwareTest.class);
  private static final String CONFIG_RESOURCE = "rs485-test-config.yaml";

  // Target 2 seconds of transfer time at each baud rate
  private static final double TARGET_TRANSFER_SECONDS = 2.0;
  // Warmup: 10 seconds of data transfer to fully warm up JVM
  private static final double WARMUP_SECONDS = 10.0;

  private static TestConfig testConfig;
  private static SshExecutor senderSsh;
  private static SshExecutor receiverSsh;
  private static String remoteJarPath;
  private static RemoteTestController controller;

  // Per-test log marker to track which log entries belong to which test
  private RemoteTestController.LogMarker logMarker;

  @BeforeAll
  static void setupClass() throws Exception {
    LOG.info("Loading configuration...");
    testConfig = loadConfig();

    LOG.info("Connecting to remote machines via SSH...");
    TestConfig.MachineConfig senderConfig = testConfig.getSender();
    TestConfig.MachineConfig receiverConfig = testConfig.getReceiver();

    senderSsh = new SshExecutor(senderConfig, testConfig);
    receiverSsh = new SshExecutor(receiverConfig, testConfig);

    senderSsh.connect();
    receiverSsh.connect();

    LOG.info("Deploying test JAR to remote machines...");
    remoteJarPath = deployTestJar();

    LOG.info("Starting test servers (once for all tests)...");
    controller = new RemoteTestController(senderSsh, receiverSsh, testConfig, remoteJarPath);
    controller.startServers();

    // Run extended warmup with actual data transfer
    runExtendedWarmup();

    LOG.info("Setup complete. Servers started and warmed up.");
  }

  private static void runExtendedWarmup() throws Exception {
    LOG.info("Running extended warmup ({} seconds of data transfer)...", WARMUP_SECONDS);

    String senderPort = testConfig.getSender().getSerialPort();
    String receiverPort = testConfig.getReceiver().getSerialPort();

    // Configure at high baud rate for faster warmup
    controller.configure(senderPort, receiverPort, 115200, "AUTO", "RTS");

    // Calculate warmup data size: 10 seconds at 115200 baud
    int warmupDataSize = RemoteTestController.calculateDataSize(115200, WARMUP_SECONDS);
    byte[] warmupData = RemoteTestController.generateTestData(warmupDataSize);
    int timeoutMs = (int) (WARMUP_SECONDS * 3000);

    LOG.info("Warmup: transferring {} bytes via echo...", warmupDataSize);
    RemoteTestController.EchoResult warmupResult = controller.echoTransfer(warmupData, timeoutMs);

    LOG.info("Warmup complete: {} bytes round-trip in {} ms ({} bytes/sec)",
        warmupResult.bytesReceived,
        String.format("%.1f", warmupResult.roundTripMs),
        String.format("%.1f", warmupResult.throughputBytesPerSec));

    controller.closePorts();
  }

  @AfterAll
  static void tearDownClass() {
    LOG.info("Shutting down...");
    if (controller != null) {
      controller.close();
    }

    // Clean up remote test files
    cleanupRemoteFiles();

    if (senderSsh != null) senderSsh.close();
    if (receiverSsh != null) receiverSsh.close();
  }

  @BeforeEach
  void setUp() throws Exception {
    // Mark log position before test starts
    if (controller != null && controller.isServersStarted()) {
      logMarker = controller.markLogs();
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    // Close ports after each test but keep servers running
    if (controller != null && controller.isServersStarted()) {
      controller.closePorts();

      // Check server logs for errors that occurred during this test
      if (logMarker != null) {
        String errors = controller.checkLogsForErrors(logMarker);
        if (errors != null) {
          // Get full logs for context
          String fullLogs = controller.getLogsSince(logMarker);
          fail("Server errors detected during test:\n" + errors + "\n\nFull logs:\n" + fullLogs);
        }
      }
    }
  }

  @Test
  @DisplayName("Comprehensive AUTO vs MANUAL benchmark with echo timing")
  void testAutoVsManualBenchmark() throws Exception {
    String senderPort = testConfig.getSender().getSerialPort();
    String receiverPort = testConfig.getReceiver().getSerialPort();

    int[] baudRates = {9600, 19200, 38400, 57600, 115200};
    int rounds = 3;
    double minRequiredEfficiency = 99.0;

    LOG.info("================================================================================");
    LOG.info("AUTO vs MANUAL BENCHMARK (Round-Trip Echo Measurement)");
    LOG.info("================================================================================");
    LOG.info("Data size: {} seconds of transfer time at each baud rate", TARGET_TRANSFER_SECONDS);
    LOG.info("Rounds per configuration: {}", rounds);
    LOG.info("Throughput = bytes / (round_trip_time / 2) - accurate one-way measurement");
    LOG.info("Minimum required efficiency: {}%", minRequiredEfficiency);
    LOG.info("");

    StringBuilder summary = new StringBuilder();
    summary.append(String.format("%-10s %-8s %-14s %-12s %-10s %-10s%n",
        "Baud", "Mode", "Throughput", "RoundTrip", "Efficiency", "Success"));
    summary.append(String.format("%-10s %-8s %-14s %-12s %-10s %-10s%n",
        "", "", "(bytes/sec)", "(ms)", "(%)", ""));
    summary.append("─".repeat(70)).append("\n");

    // Track results for assertions
    java.util.List<String> belowTargetConfigs = new java.util.ArrayList<>();
    int totalConfigurations = 0;
    int failedConfigurations = 0;

    for (int baudRate : baudRates) {
      int dataSize = RemoteTestController.calculateDataSize(baudRate, TARGET_TRANSFER_SECONDS);
      byte[] testData = RemoteTestController.generateTestData(dataSize);
      // Round-trip timeout: 2x one-way time plus margin
      int timeoutMs = (int) (TARGET_TRANSFER_SECONDS * 2 * 1500);

      double theoreticalMax = baudRate / 10.0;

      for (String mode : new String[]{"AUTO", "MANUAL"}) {
        totalConfigurations++;
        double totalThroughput = 0;
        double totalRoundTrip = 0;
        int successCount = 0;

        for (int round = 0; round < rounds; round++) {
          try {
            controller.configure(senderPort, receiverPort, baudRate, mode, "RTS");
            RemoteTestController.EchoResult result = controller.echoTransfer(testData, timeoutMs);

            if (result.dataMatch && result.bytesReceived == dataSize) {
              totalThroughput += result.throughputBytesPerSec;
              totalRoundTrip += result.roundTripMs;
              successCount++;
            }
          } catch (Exception e) {
            LOG.warn("Round {} failed for {} at {} baud: {}", round, mode, baudRate, e.getMessage());
          }
        }

        if (successCount > 0) {
          double avgThroughput = totalThroughput / successCount;
          double avgRoundTrip = totalRoundTrip / successCount;
          double efficiency = (avgThroughput / theoreticalMax) * 100.0;
          summary.append(String.format("%-10d %-8s %-14.1f %-12.1f %-10.1f %d/%d%n",
              baudRate, mode, avgThroughput, avgRoundTrip, efficiency, successCount, rounds));

          // Track configurations below target efficiency
          if (efficiency < minRequiredEfficiency) {
            belowTargetConfigs.add(String.format("%d baud %s: %.1f%%", baudRate, mode, efficiency));
          }
        } else {
          summary.append(String.format("%-10d %-8s %-14s %-12s %-10s %d/%d%n",
              baudRate, mode, "FAILED", "-", "-", successCount, rounds));
          failedConfigurations++;
        }
      }
    }

    LOG.info("\n{}", summary);
    LOG.info("Theoretical max throughput: 9600=960, 19200=1920, 38400=3840, 57600=5760, 115200=11520 bytes/sec");
    LOG.info("================================================================================");

    // Assertions
    assertEquals(0, failedConfigurations,
        "All configurations should complete successfully");
    assertTrue(belowTargetConfigs.isEmpty(),
        String.format("Efficiency should be at least %.1f%% but these configurations failed:\n  %s",
            minRequiredEfficiency, String.join("\n  ", belowTargetConfigs)));
  }

  @Test
  @DisplayName("Multiple consecutive transfers")
  void testConsecutiveTransfers() throws Exception {
    String senderPort = testConfig.getSender().getSerialPort();
    String receiverPort = testConfig.getReceiver().getSerialPort();

    int baudRate = 38400;
    int dataSize = RemoteTestController.calculateDataSize(baudRate, 0.5); // 0.5 seconds per transfer
    int transfers = 10;

    LOG.info("Testing {} consecutive transfers of {} bytes at {} baud", transfers, dataSize, baudRate);

    controller.configure(senderPort, receiverPort, baudRate, "AUTO", "RTS");

    int successCount = 0;
    double totalThroughput = 0;

    for (int i = 0; i < transfers; i++) {
      byte[] testData = RemoteTestController.generateTestData(dataSize);
      // Add transfer number to make each unique
      testData[0] = (byte) i;

      RemoteTestController.TransferResult result = controller.transfer(testData, 3000);

      if (result.bytesReceived == dataSize && result.isDataMatch(testData)) {
        successCount++;
        totalThroughput += result.throughputBytesPerSec;
        LOG.debug("Transfer {}: {} bytes/sec", i, String.format("%.1f", result.throughputBytesPerSec));
      } else {
        LOG.warn("Transfer {} failed: sent={}, received={}", i, result.bytesSent, result.bytesReceived);
      }
    }

    LOG.info("Consecutive transfers: {}/{} successful, avg throughput={} bytes/sec",
        successCount, transfers, String.format("%.1f", totalThroughput / Math.max(1, successCount)));

    assertTrue(successCount >= transfers * 0.9,
        "At least 90% of transfers should succeed");
  }

  // ========================================================================
  // Error Scenario Tests
  // ========================================================================

  @Test
  @DisplayName("Echo timeout handling - very short timeout should result in no data received")
  void testEchoTimeout() throws Exception {
    // This test intentionally causes timeout - expect timeout-related error messages in logs
    logMarker.expectErrors("timeout", "timed out", "read error");

    String senderPort = testConfig.getSender().getSerialPort();
    String receiverPort = testConfig.getReceiver().getSerialPort();

    int baudRate = 9600;
    // Calculate data size for 5 seconds of transfer at 9600 baud (4800 bytes)
    int dataSize = RemoteTestController.calculateDataSize(baudRate, 5.0);
    byte[] testData = RemoteTestController.generateTestData(dataSize);

    // Timeout of 100ms is way too short for 5 seconds of data at 9600 baud
    // Serial transfer takes 5 seconds, but timeout is 100ms, so receiver should get 0 bytes
    int impossibleTimeoutMs = 100;

    LOG.info("Testing timeout: {} bytes at {} baud with {}ms timeout (expected: 0 bytes received)",
        dataSize, baudRate, impossibleTimeoutMs);

    controller.configure(senderPort, receiverPort, baudRate, "AUTO", "RTS");

    // The transfer should either throw IOException OR return with 0 bytes received
    try {
      RemoteTestController.EchoResult result = controller.echoTransfer(testData, impossibleTimeoutMs);

      LOG.info("Timeout result: sent={}, received={}, match={}", dataSize, result.bytesReceived, result.dataMatch);

      // With 100ms timeout for 5 seconds of data, receiver should get 0 bytes
      assertEquals(0, result.bytesReceived,
          "With timeout shorter than transfer time, receiver should get 0 bytes");
      assertFalse(result.dataMatch, "Data should not match when receiver got 0 bytes");

      LOG.info("Timeout test passed - receiver got 0 bytes as expected");

    } catch (IOException e) {
      // IOException is also acceptable - means timeout was enforced at protocol level
      LOG.info("Timeout test passed - got IOException: {}", e.getMessage());
    }
  }

  @Test
  @DisplayName("Recovery after timeout - system should recover and work normally")
  void testRecoveryAfterTimeout() throws Exception {
    // This test intentionally causes timeout first, then recovers - expect timeout-related error messages
    logMarker.expectErrors("timeout", "timed out", "read error");

    String senderPort = testConfig.getSender().getSerialPort();
    String receiverPort = testConfig.getReceiver().getSerialPort();

    int baudRate = 9600;
    // Use large data with short timeout to reliably cause timeout
    int largeDataSize = RemoteTestController.calculateDataSize(baudRate, 5.0);
    byte[] largeData = RemoteTestController.generateTestData(largeDataSize);

    // Smaller data for recovery test
    int smallDataSize = RemoteTestController.calculateDataSize(baudRate, 0.5);
    byte[] smallData = RemoteTestController.generateTestData(smallDataSize);

    LOG.info("Testing recovery after timeout...");

    // First, cause a timeout with very short timeout and large data
    controller.configure(senderPort, receiverPort, baudRate, "AUTO", "RTS");

    int impossibleTimeoutMs = 100;
    try {
      RemoteTestController.EchoResult failResult = controller.echoTransfer(largeData, impossibleTimeoutMs);
      LOG.info("First transfer (expected timeout): received={} bytes", failResult.bytesReceived);
      assertEquals(0, failResult.bytesReceived, "Should receive 0 bytes with short timeout");
    } catch (IOException e) {
      LOG.info("First transfer threw exception as expected: {}", e.getMessage());
    }

    // Close and reconfigure ports to reset state
    controller.closePorts();
    Thread.sleep(100); // Brief pause to let hardware reset

    // Now try a normal transfer with adequate timeout - it should succeed
    controller.configure(senderPort, receiverPort, baudRate, "AUTO", "RTS");
    int normalTimeoutMs = 5000;

    RemoteTestController.EchoResult result = controller.echoTransfer(smallData, normalTimeoutMs);

    assertTrue(result.dataMatch, "Data should match after recovery");
    assertEquals(smallDataSize, result.bytesReceived, "Should receive all bytes after recovery");

    LOG.info("Recovery test passed - transferred {} bytes successfully after previous timeout", result.bytesReceived);
  }

  @Test
  @DisplayName("Short timeout results in zero bytes received")
  void testShortTimeoutZeroBytes() throws Exception {
    // This test intentionally causes timeout - expect timeout-related error messages in logs
    logMarker.expectErrors("timeout", "timed out", "read error");

    String senderPort = testConfig.getSender().getSerialPort();
    String receiverPort = testConfig.getReceiver().getSerialPort();

    int baudRate = 9600; // Slow baud rate
    // Large data size that takes ~3 seconds to transfer
    int dataSize = RemoteTestController.calculateDataSize(baudRate, 3.0);
    byte[] testData = RemoteTestController.generateTestData(dataSize);

    // Timeout of 100ms - receiver won't have time to echo anything back
    int shortTimeoutMs = 100;

    LOG.info("Testing short timeout: {} bytes at {} baud with {}ms timeout", dataSize, baudRate, shortTimeoutMs);

    controller.configure(senderPort, receiverPort, baudRate, "AUTO", "RTS");

    try {
      RemoteTestController.EchoResult result = controller.echoTransfer(testData, shortTimeoutMs);

      LOG.info("Short timeout result: sent={}, received={}, match={}",
          result.bytesSent, result.bytesReceived, result.dataMatch);

      // With timeout much shorter than transfer time, should receive 0 bytes
      assertEquals(0, result.bytesReceived, "Should receive 0 bytes with short timeout");
      assertFalse(result.dataMatch, "Data should not match with 0 bytes received");

    } catch (IOException e) {
      // IOException is also acceptable
      LOG.info("Short timeout resulted in IOException: {}", e.getMessage());
    }

    // Clean up - close ports to reset state
    controller.closePorts();
  }

  @Test
  @DisplayName("Multiple rapid transfers - stress test port handling")
  void testRapidTransfers() throws Exception {
    String senderPort = testConfig.getSender().getSerialPort();
    String receiverPort = testConfig.getReceiver().getSerialPort();

    int baudRate = 115200; // Fast baud rate for quick transfers
    int dataSize = RemoteTestController.calculateDataSize(baudRate, 0.1); // 100ms of data
    int transfers = 20;

    LOG.info("Testing {} rapid transfers of {} bytes at {} baud", transfers, dataSize, baudRate);

    controller.configure(senderPort, receiverPort, baudRate, "AUTO", "RTS");

    int successCount = 0;

    for (int i = 0; i < transfers; i++) {
      byte[] testData = RemoteTestController.generateTestData(dataSize);
      testData[0] = (byte) i; // Make each transfer unique

      RemoteTestController.EchoResult result = controller.echoTransfer(testData, 2000);

      if (result.dataMatch && result.bytesReceived == dataSize) {
        successCount++;
      } else {
        LOG.warn("Transfer {} incomplete: received={}, match={}", i, result.bytesReceived, result.dataMatch);
      }
    }

    LOG.info("Rapid transfers: {}/{} successful", successCount, transfers);

    assertEquals(transfers, successCount,
        "All rapid transfers should succeed");
  }

  private static TestConfig loadConfig() throws IOException {
    InputStream is = Rs485HardwareTest.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE);
    if (is == null) {
      throw new IllegalStateException("Config not found: " + CONFIG_RESOURCE);
    }
    LOG.info("Loading config from classpath: {}", CONFIG_RESOURCE);
    return TestConfig.load(is);
  }

  private static String deployTestJar() throws Exception {
    String jarDir = testConfig.getTestJarPath();
    File[] jars = new File(jarDir).listFiles((dir, name) ->
        name.startsWith("rs485-test-app") && name.endsWith("-all.jar"));

    if (jars == null || jars.length == 0) {
      throw new IllegalStateException("Test JAR not found in: " + jarDir +
          ". Run ./gradlew :hardware-test:testAppJar first.");
    }

    File jarFile = jars[0];
    String remoteDir = testConfig.getRemoteWorkDir();
    String remotePath = remoteDir + "/" + jarFile.getName();

    LOG.info("Checking {} on remote machines...", jarFile.getName());

    // Use MD5 check to avoid unnecessary uploads
    boolean senderUploaded = senderSsh.uploadFileIfChanged(jarFile, remoteDir);
    boolean receiverUploaded = receiverSsh.uploadFileIfChanged(jarFile, remoteDir);

    if (senderUploaded || receiverUploaded) {
      LOG.info("JAR deployed to {} machine(s)",
          (senderUploaded ? 1 : 0) + (receiverUploaded ? 1 : 0));
    } else {
      LOG.info("JAR already up-to-date on both machines");
    }

    return remotePath;
  }

  /**
   * Clean up remote test files after all tests complete.
   */
  private static void cleanupRemoteFiles() {
    String remoteDir = testConfig.getRemoteWorkDir();
    LOG.info("Cleaning up remote test files in {}...", remoteDir);

    try {
      if (senderSsh != null && senderSsh.isConnected()) {
        senderSsh.deleteDirectory(remoteDir);
      }
    } catch (Exception e) {
      LOG.warn("Failed to cleanup sender: {}", e.getMessage());
    }

    try {
      if (receiverSsh != null && receiverSsh.isConnected()) {
        receiverSsh.deleteDirectory(remoteDir);
      }
    } catch (Exception e) {
      LOG.warn("Failed to cleanup receiver: {}", e.getMessage());
    }
  }
}

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
package dev.nemecec.jrserial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for serial port communication using virtual serial ports created by socat.
 * <p>
 * These tests require socat to be installed:
 * - macOS: brew install socat
 * - Linux: apt install socat
 * <p>
 * Tests are skipped if:
 * - socat is not available
 * - Native library is not available
 * - PTY devices are not supported by the serial port library (common on macOS)
 * <p>
 * Note: The Rust serialport library may not support PTY devices on all platforms,
 * as PTYs are pseudo-terminals, not real serial ports. This test works best on
 * Linux systems or when using actual serial hardware.
 */
@EnabledOnOs({OS.MAC, OS.LINUX})
class VirtualSerialPortTest {

  private static final Logger LOG = LoggerFactory.getLogger(VirtualSerialPortTest.class);

  private final VirtualSerialPortSupport support = new VirtualSerialPortSupport();

  @BeforeEach
  void setUp() throws IOException, InterruptedException {
    assumeTrue(VirtualSerialPortSupport.isSocatAvailable(), "socat is not installed, skipping test");
    assumeTrue(VirtualSerialPortSupport.isNativeLibraryAvailable(), "Native library not available for current platform");

    support.start();
  }

  @AfterEach
  void tearDown() {
    support.stop();
  }

  @Test
  void testBasicWriteRead() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      String message = "Hello, Virtual Serial!";
      sender.write(message.getBytes(StandardCharsets.UTF_8));
      sender.flush();

      // Wait for data to be available
      Thread.sleep(100);

      int available = receiver.available();
      assertThat(available).isGreaterThan(0);

      byte[] buffer = new byte[available];
      int bytesRead = receiver.read(buffer);

      String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
      assertThat(received).isEqualTo(message);

      LOG.info("Basic write/read test passed: sent='{}', received='{}'", message, received);
    }
  }

  @Test
  void testBidirectionalCommunication() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort portA = support.createPort(support.getPort1());
         SerialPort portB = support.createPort(support.getPort2())) {

      portA.open();
      portB.open();

      // Send from A to B
      String messageAtoB = "Message from A to B";
      portA.write(messageAtoB.getBytes(StandardCharsets.UTF_8));
      portA.flush();

      Thread.sleep(100);

      byte[] bufferB = new byte[messageAtoB.length()];
      int bytesReadB = portB.read(bufferB);
      String receivedAtB = new String(bufferB, 0, bytesReadB, StandardCharsets.UTF_8);

      assertThat(receivedAtB).isEqualTo(messageAtoB);

      // Send from B to A
      String messageBtoA = "Message from B to A";
      portB.write(messageBtoA.getBytes(StandardCharsets.UTF_8));
      portB.flush();

      Thread.sleep(100);

      byte[] bufferA = new byte[messageBtoA.length()];
      int bytesReadA = portA.read(bufferA);
      String receivedAtA = new String(bufferA, 0, bytesReadA, StandardCharsets.UTF_8);

      assertThat(receivedAtA).isEqualTo(messageBtoA);

      LOG.info("Bidirectional communication test passed");
    }
  }

  @Test
  void testBinaryData() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Create binary test data with all byte values 0-255
      byte[] testData = new byte[256];
      for (int i = 0; i < 256; i++) {
        testData[i] = (byte) i;
      }

      sender.write(testData);
      sender.flush();

      Thread.sleep(200);

      int available = receiver.available();
      assertThat(available).isEqualTo(256);

      byte[] received = new byte[256];
      int bytesRead = receiver.read(received);

      assertThat(bytesRead).isEqualTo(256);
      assertThat(received).isEqualTo(testData);

      LOG.info("Binary data test passed: all 256 byte values transferred correctly");
    }
  }

  @Test
  void testLargeDataTransfer() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Send 4KB of data
      int dataSize = 4096;
      byte[] testData = new byte[dataSize];
      for (int i = 0; i < dataSize; i++) {
        testData[i] = (byte) (i % 256);
      }

      sender.write(testData);
      sender.flush();

      // Wait longer for larger data
      Thread.sleep(500);

      // Read all available data
      byte[] received = new byte[dataSize];
      int totalRead = 0;
      int attempts = 0;
      while (totalRead < dataSize && attempts < 50) {
        int available = receiver.available();
        if (available > 0) {
          int bytesRead = receiver.read(received, totalRead, Math.min(available, dataSize - totalRead));
          totalRead += bytesRead;
        } else {
          Thread.sleep(50);
          attempts++;
        }
      }

      assertThat(totalRead).isEqualTo(dataSize);
      assertThat(received).isEqualTo(testData);

      LOG.info("Large data transfer test passed: {} bytes transferred correctly", dataSize);
    }
  }

  @Test
  void testClearBuffers() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Send some data
      sender.write("Data to be cleared".getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      // Verify data is available
      assertThat(receiver.available()).isGreaterThan(0);

      // Clear input buffer
      receiver.clearInput();

      // Verify buffer is cleared
      assertThat(receiver.available()).isZero();

      LOG.info("Clear buffers test passed");
    }
  }

  @Test
  void testRs485AutoModeFallback() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");
    assumeTrue(support.isRtsControlSupported(), "RTS/DTR control not supported on PTY devices");

    // Create port with RS-485 AUTO mode - should fall back to manual on PTY
    try (SerialPort sender = SerialPort.builder()
            .portName(support.getPort1())
            .baudRate(115200)
            .timeout(1000)
            .rs485()  // Enable RS-485 AUTO mode
            .build();
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Kernel RS-485 should NOT be active on PTY devices
      boolean kernelActive = sender.isKernelRs485Active();
      LOG.info("Kernel RS-485 active on PTY: {} (expected: false)", kernelActive);
      assertThat(kernelActive).isFalse();

      // But communication should still work (manual mode fallback)
      String message = "RS-485 fallback test";
      sender.write(message.getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      byte[] buffer = new byte[message.length()];
      int bytesRead = receiver.read(buffer);

      String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
      assertThat(received).isEqualTo(message);

      LOG.info("RS-485 AUTO mode fallback test passed");
    }
  }

  @Test
  void testRs485ManualMode() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");
    assumeTrue(support.isRtsControlSupported(), "RTS/DTR control not supported on PTY devices");

    // Create port with RS-485 mode
    Rs485Config rs485Config = Rs485Config.builder()
        .enabled(true)
        .controlPin(Rs485ControlPin.RTS)
        .build();

    try (SerialPort sender = SerialPort.builder()
            .portName(support.getPort1())
            .baudRate(115200)
            .timeout(1000)
            .rs485Config(rs485Config)
            .build();
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Verify RS-485 configuration
      assertThat(sender.getRs485Config()).isNotNull();
      assertThat(sender.getRs485Config().isEnabled()).isTrue();
      assertThat(sender.getRs485Config().getControlPin()).isEqualTo(Rs485ControlPin.RTS);

      // Communication should work with RTS control
      String message = "RS-485 mode test";
      sender.write(message.getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      byte[] buffer = new byte[message.length()];
      int bytesRead = receiver.read(buffer);

      String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
      assertThat(received).isEqualTo(message);

      LOG.info("RS-485 mode test passed");
    }
  }

  @Test
  void testRs485WithDtrPin() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");
    assumeTrue(support.isRtsControlSupported(), "RTS/DTR control not supported on PTY devices");

    // Create port with RS-485 using DTR pin instead of RTS
    Rs485Config rs485Config = Rs485Config.builder()
        .enabled(true)
        .controlPin(Rs485ControlPin.DTR)
        .build();

    try (SerialPort sender = SerialPort.builder()
            .portName(support.getPort1())
            .baudRate(115200)
            .timeout(1000)
            .rs485Config(rs485Config)
            .build();
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Kernel RS-485 should NOT be active (DTR not supported by kernel)
      assertThat(sender.isKernelRs485Active()).isFalse();

      // Verify configuration
      assertThat(sender.getRs485Config()).isNotNull();
      assertThat(sender.getRs485Config().getControlPin()).isEqualTo(Rs485ControlPin.DTR);

      // Communication should work with manual DTR control
      String message = "RS-485 DTR test";
      sender.write(message.getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      byte[] buffer = new byte[message.length()];
      int bytesRead = receiver.read(buffer);

      String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
      assertThat(received).isEqualTo(message);

      LOG.info("RS-485 with DTR pin test passed");
    }
  }

  @Test
  void testManualRtsDtrControl() throws IOException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");
    assumeTrue(support.isRtsControlSupported(), "RTS/DTR control not supported on PTY devices");

    try (SerialPort port = support.createPort(support.getPort1())) {
      port.open();

      // Test manual RTS control
      port.setRTS(true);
      port.setRTS(false);

      // Test manual DTR control
      port.setDTR(true);
      port.setDTR(false);

      LOG.info("Manual RTS/DTR control test passed");
    }
  }

  @Test
  void testSetRs485ConfigAtRuntime() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");
    assumeTrue(support.isRtsControlSupported(), "RTS/DTR control not supported on PTY devices");

    // Create port WITHOUT RS-485 config initially
    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Verify no RS-485 config initially
      assertThat(sender.getRs485Config()).isNull();

      // Enable RS-485 at runtime
      Rs485Config config = Rs485Config.builder()
          .enabled(true)
          .controlPin(Rs485ControlPin.RTS)
          .rtsActiveHigh(true)
          .delayBeforeSendMicros(50)
          .delayAfterSendMicros(50)
          .build();
      sender.setRs485Config(config);

      // Communication should work after enabling RS-485
      String message = "RS-485 runtime config test";
      sender.write(message.getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      byte[] buffer = new byte[message.length()];
      int bytesRead = receiver.read(buffer);

      String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
      assertThat(received).isEqualTo(message);

      // Change RS-485 config at runtime (switch to DTR)
      Rs485Config newConfig = Rs485Config.builder()
          .enabled(true)
          .controlPin(Rs485ControlPin.DTR)
          .rtsActiveHigh(false)
          .build();
      sender.setRs485Config(newConfig);

      // Communication should still work after config change
      String message2 = "RS-485 config changed";
      sender.write(message2.getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      byte[] buffer2 = new byte[message2.length()];
      int bytesRead2 = receiver.read(buffer2);

      String received2 = new String(buffer2, 0, bytesRead2, StandardCharsets.UTF_8);
      assertThat(received2).isEqualTo(message2);

      LOG.info("setRs485Config() runtime configuration test passed");
    }
  }

  // ============================================================================
  // Convenience Read Methods Tests
  // ============================================================================

  @Test
  void testReadExactly() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Send exactly 10 bytes
      String message = "0123456789";
      sender.write(message.getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      // Read exactly 10 bytes
      byte[] buffer = new byte[10];
      int bytesRead = receiver.readExactly(buffer, 0, 10);

      assertThat(bytesRead).isEqualTo(10);
      assertThat(new String(buffer, StandardCharsets.UTF_8)).isEqualTo(message);

      LOG.info("readExactly() test passed");
    }
  }

  @Test
  void testReadExactlyConvenience() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Send exactly 5 bytes
      String message = "hello";
      sender.write(message.getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      // Use convenience method that returns a new buffer
      byte[] result = receiver.readExactly(5);

      assertThat(result).hasSize(5);
      assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo(message);

      LOG.info("readExactly(length) convenience test passed");
    }
  }

  @Test
  void testReadExactlyTimeout() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2(), 200)) {

      sender.open();
      receiver.open();

      // Send only 5 bytes
      sender.write("hello".getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      // Try to read 10 bytes - should timeout after receiving only 5
      byte[] buffer = new byte[10];
      try {
        receiver.readExactly(buffer, 0, 10);
        // Should not reach here
        throw new AssertionError("Expected IOException for timeout");
      } catch (IOException e) {
        assertThat(e.getMessage()).contains("Timeout");
        assertThat(e.getMessage()).contains("5 of 10");  // Should mention partial read
        LOG.info("readExactly() timeout test passed: {}", e.getMessage());
      }
    }
  }

  @Test
  void testReadExactlyWithCustomTimeout() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2(), 5000)) {

      sender.open();
      receiver.open();

      // Send only 3 bytes
      sender.write("abc".getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      // Try to read 10 bytes with short custom timeout - should timeout quickly
      byte[] buffer = new byte[10];
      long startTime = System.currentTimeMillis();
      try {
        receiver.readExactly(buffer, 0, 10, 300);  // 300ms custom timeout
        throw new AssertionError("Expected IOException for timeout");
      } catch (IOException e) {
        long elapsed = System.currentTimeMillis() - startTime;
        assertThat(e.getMessage()).contains("Timeout");
        // Should timeout around 300ms, not 5000ms
        assertThat(elapsed).isLessThan(2000);
        LOG.info("readExactly() with custom timeout test passed in {}ms: {}", elapsed, e.getMessage());
      }
    }
  }

  @Test
  void testReadAvailable() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Send some data
      sender.write("hello".getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      // readAvailable should return immediately with available data
      byte[] buffer = new byte[100];
      int bytesRead = receiver.readAvailable(buffer);

      assertThat(bytesRead).isEqualTo(5);
      assertThat(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8)).isEqualTo("hello");

      LOG.info("readAvailable() test passed");
    }
  }

  @Test
  void testReadAvailableNoData() throws IOException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort receiver = support.createPort(support.getPort2())) {
      receiver.open();

      // readAvailable should return 0 immediately if no data
      byte[] buffer = new byte[100];
      int bytesRead = receiver.readAvailable(buffer);

      assertThat(bytesRead).isZero();

      LOG.info("readAvailable() no data test passed");
    }
  }

  @Test
  void testReadLine() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Send lines with different line endings
      sender.write("hello\n".getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      String line1 = receiver.readLine();
      assertThat(line1).isEqualTo("hello");

      // Send with CRLF
      sender.write("world\r\n".getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      String line2 = receiver.readLine();
      assertThat(line2).isEqualTo("world");

      LOG.info("readLine() test passed");
    }
  }

  @Test
  void testReadLineMultiple() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Send multiple lines at once
      sender.write("line1\nline2\nline3\n".getBytes(StandardCharsets.UTF_8));
      sender.flush();

      Thread.sleep(100);

      assertThat(receiver.readLine()).isEqualTo("line1");
      assertThat(receiver.readLine()).isEqualTo("line2");
      assertThat(receiver.readLine()).isEqualTo("line3");

      LOG.info("readLine() multiple lines test passed");
    }
  }

  @Test
  void testReadLineTimeout() throws IOException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort receiver = support.createPort(support.getPort2(), 200)) {
      receiver.open();

      // Try to read line with no data - should timeout
      try {
        receiver.readLine();
        throw new AssertionError("Expected IOException for timeout");
      } catch (IOException e) {
        assertThat(e.getMessage()).contains("Timeout");
        LOG.info("readLine() timeout test passed: {}", e.getMessage());
      }
    }
  }

  @Test
  void testWriteString() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Write string without newline
      sender.writeString("Hello, World!");
      sender.flush();

      Thread.sleep(100);

      byte[] buffer = new byte[20];
      int bytesRead = receiver.read(buffer);

      assertThat(bytesRead).isEqualTo(13);
      assertThat(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8)).isEqualTo("Hello, World!");

      LOG.info("writeString() test passed");
    }
  }

  @Test
  void testWriteLine() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Write line (adds newline)
      sender.writeLine("Hello");
      sender.flush();

      Thread.sleep(100);

      // Should be able to read it with readLine
      String line = receiver.readLine();
      assertThat(line).isEqualTo("Hello");

      LOG.info("writeLine() test passed");
    }
  }

  @Test
  void testWriteLineAndReadLine() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Write multiple lines
      sender.writeLine("line1");
      sender.writeLine("line2");
      sender.writeLine("line3");
      sender.flush();

      Thread.sleep(100);

      // Read them back
      assertThat(receiver.readLine()).isEqualTo("line1");
      assertThat(receiver.readLine()).isEqualTo("line2");
      assertThat(receiver.readLine()).isEqualTo("line3");

      LOG.info("writeLine/readLine integration test passed");
    }
  }

  @Test
  void testWriteStringWithCharset() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Write with ISO-8859-1 (Latin-1)
      sender.writeString("Héllo", java.nio.charset.StandardCharsets.ISO_8859_1);
      sender.flush();

      Thread.sleep(100);

      byte[] buffer = new byte[20];
      int bytesRead = receiver.read(buffer);

      // In ISO-8859-1, 'é' is a single byte (0xE9)
      assertThat(bytesRead).isEqualTo(5);
      assertThat(new String(buffer, 0, bytesRead, java.nio.charset.StandardCharsets.ISO_8859_1))
          .isEqualTo("Héllo");

      LOG.info("writeString(charset) test passed");
    }
  }

  @Test
  void testWriteLineWithCharset() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Write line with US-ASCII
      sender.writeLine("Hello", java.nio.charset.StandardCharsets.US_ASCII);
      sender.flush();

      Thread.sleep(100);

      String line = receiver.readLine(java.nio.charset.StandardCharsets.US_ASCII);
      assertThat(line).isEqualTo("Hello");

      LOG.info("writeLine(charset) test passed");
    }
  }

  @Test
  void testReadLineWithCharset() throws IOException, InterruptedException {
    assumeTrue(support.isPtySupported(), "PTY devices not supported by serial library");

    try (SerialPort sender = support.createPort(support.getPort1());
         SerialPort receiver = support.createPort(support.getPort2())) {

      sender.open();
      receiver.open();

      // Send ISO-8859-1 encoded text
      sender.writeString("Bönjour\n", java.nio.charset.StandardCharsets.ISO_8859_1);
      sender.flush();

      Thread.sleep(100);

      // Read with matching charset
      String line = receiver.readLine(java.nio.charset.StandardCharsets.ISO_8859_1);
      assertThat(line).isEqualTo("Bönjour");

      LOG.info("readLine(charset) test passed");
    }
  }
}

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
package dev.nemecec.jrserial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Support class for creating virtual serial ports using socat.
 * <p>
 * This class manages the lifecycle of a socat process that creates a pair of
 * connected pseudo-terminal (PTY) devices for testing serial communication.
 * <p>
 * Usage:
 * <pre>{@code
 * VirtualSerialPortSupport support = new VirtualSerialPortSupport();
 * support.start();
 * try {
 *     if (support.isPtySupported()) {
 *         // Use support.getPort1() and support.getPort2()
 *     }
 * } finally {
 *     support.stop();
 * }
 * }</pre>
 */
public class VirtualSerialPortSupport {

  private static final Logger LOG = LoggerFactory.getLogger(VirtualSerialPortSupport.class);
  private static final Pattern PTY_PATTERN = Pattern.compile("PTY is (.+)$");

  private Process socatProcess;
  private String port1;
  private String port2;
  private boolean ptySupported = false;
  private boolean rtsControlSupported = false;

  /**
   * Returns the first virtual serial port path.
   */
  public String getPort1() {
    return port1;
  }

  /**
   * Returns the second virtual serial port path.
   */
  public String getPort2() {
    return port2;
  }

  /**
   * Returns true if PTY devices are supported by the serial library.
   */
  public boolean isPtySupported() {
    return ptySupported;
  }

  /**
   * Returns true if RTS/DTR control is supported on PTY devices.
   */
  public boolean isRtsControlSupported() {
    return rtsControlSupported;
  }

  /**
   * Checks if socat is available on the system.
   */
  public static boolean isSocatAvailable() {
    try {
      Process process = new ProcessBuilder("which", "socat").start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  /**
   * Checks if the native library is available.
   */
  public static boolean isNativeLibraryAvailable() {
    try {
      SerialPort.listPorts();
      return true;
    } catch (UnsatisfiedLinkError e) {
      LOG.warn("Native library not available: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Starts socat and creates virtual serial ports.
   *
   * @throws IOException if socat cannot be started or ports cannot be created
   * @throws InterruptedException if interrupted while waiting for socat
   */
  public void start() throws IOException, InterruptedException {
    if (!isSocatAvailable()) {
      LOG.info("socat not available, PTY tests will be skipped");
      return;
    }

    if (!isNativeLibraryAvailable()) {
      LOG.info("Native library not available, PTY tests will be skipped");
      return;
    }

    startSocat();
    checkPtySupport();
    if (ptySupported) {
      checkRtsSupport();
    }
  }

  /**
   * Stops the socat process and cleans up resources.
   */
  public void stop() {
    stopSocat();
  }

  private void startSocat() throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(
        "socat",
        "-d", "-d",
        "pty,raw,echo=0",
        "pty,raw,echo=0"
    );
    pb.redirectErrorStream(true);

    socatProcess = pb.start();

    // Read socat output to get PTY paths
    BufferedReader reader = new BufferedReader(new InputStreamReader(socatProcess.getInputStream()));

    String line;
    int portsFound = 0;
    long startTime = System.currentTimeMillis();

    while (portsFound < 2 && (System.currentTimeMillis() - startTime) < 5000) {
      if (reader.ready()) {
        line = reader.readLine();
        if (line != null) {
          LOG.debug("socat: {}", line);
          Matcher matcher = PTY_PATTERN.matcher(line);
          if (matcher.find()) {
            if (port1 == null) {
              port1 = matcher.group(1);
              LOG.info("Virtual port 1: {}", port1);
              portsFound++;
            } else if (port2 == null) {
              port2 = matcher.group(1);
              LOG.info("Virtual port 2: {}", port2);
              portsFound++;
            }
          }
        }
      } else {
        Thread.sleep(50);
      }
    }

    if (port1 == null || port2 == null) {
      stopSocat();
      throw new IOException("Failed to get virtual serial port paths from socat");
    }

    // Give socat a moment to fully initialize the PTYs
    Thread.sleep(200);
  }

  private void checkPtySupport() {
    // Try to open one of the PTY ports to check if the library supports them
    try (SerialPort testPort = createPort(port1)) {
      testPort.open();
      testPort.close();
      ptySupported = true;
    } catch (IOException e) {
      LOG.warn("PTY devices not supported by serial library: {}", e.getMessage());
      ptySupported = false;
    }
  }

  private void checkRtsSupport() {
    // Try to set RTS on a PTY port to check if RTS/DTR control is supported
    try (SerialPort testPort = createPort(port1)) {
      testPort.open();
      testPort.setRTS(true);
      testPort.setRTS(false);
      rtsControlSupported = true;
    } catch (IOException e) {
      LOG.warn("RTS/DTR control not supported on PTY devices: {}", e.getMessage());
      rtsControlSupported = false;
    }
  }

  private void stopSocat() {
    if (socatProcess != null && socatProcess.isAlive()) {
      socatProcess.destroy();
      try {
        socatProcess.waitFor(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        socatProcess.destroyForcibly();
      }
      LOG.info("socat process stopped");
    }
    port1 = null;
    port2 = null;
    ptySupported = false;
    rtsControlSupported = false;
  }

  /**
   * Creates a SerialPort for the given port name with default test settings.
   */
  public SerialPort createPort(String portName) {
    return SerialPort.builder()
        .portName(portName)
        .baudRate(115200)
        .timeout(1000)
        .build();
  }

  /**
   * Creates a SerialPort for the given port name with custom timeout.
   */
  public SerialPort createPort(String portName, int timeout) {
    return SerialPort.builder()
        .portName(portName)
        .baudRate(115200)
        .timeout(timeout)
        .build();
  }
}

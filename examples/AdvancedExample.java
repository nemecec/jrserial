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
package examples;

import dev.nemecec.jrserial.DataBits;
import dev.nemecec.jrserial.Parity;
import dev.nemecec.jrserial.SerialPort;
import dev.nemecec.jrserial.SerialPortInfo;
import dev.nemecec.jrserial.StopBits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Advanced example demonstrating various serial port configurations and operations.
 */
public class AdvancedExample {

  private static final Logger LOG = LoggerFactory.getLogger(AdvancedExample.class);

  public static void main(String[] args) {
    List<SerialPortInfo> ports = SerialPort.listPorts();
    if (ports.isEmpty()) {
      LOG.warn("No serial ports found!");
      return;
    }

    String portName = ports.get(0).getPortName();
    LOG.info("Using port: {}", portName);

    // Create port with custom configuration
    SerialPort serialPort = SerialPort.builder()
        .portName(portName)
        .baudRate(115200)           // High speed
        .dataBits(DataBits.EIGHT)   // 8 data bits
        .stopBits(StopBits.ONE)     // 1 stop bit
        .parity(Parity.NONE)        // No parity
        .timeout(2000)              // 2 second timeout
        .build();

    try {
      // Open port
      serialPort.open();
      LOG.info("Port configuration:");
      LOG.info("  Baud rate: {}", serialPort.getBaudRate());
      LOG.info("  Data bits: {}", serialPort.getDataBits());
      LOG.info("  Stop bits: {}", serialPort.getStopBits());
      LOG.info("  Parity: {}", serialPort.getParity());

      // Clear any existing data in buffers
      LOG.info("Clearing buffers...");
      serialPort.clearAll();

      // Write binary data
      byte[] binaryData = new byte[] {
          0x01, 0x02, 0x03, 0x04, 0x05,
          (byte) 0xFF, (byte) 0xFE, (byte) 0xFD
      };
      LOG.info("Writing {} bytes of binary data...", binaryData.length);
      int written = serialPort.write(binaryData);
      LOG.info("Wrote {} bytes", written);
      serialPort.flush();

      // Wait for response
      Thread.sleep(200);

      // Check available data
      int available = serialPort.available();
      LOG.info("Bytes available: {}", available);

      if (available > 0) {
        // Read with offset and length
        byte[] buffer = new byte[256];
        int bytesRead = serialPort.read(buffer, 0, Math.min(available, buffer.length));

        LOG.info("Read {} bytes:", bytesRead);
        logHexDump(buffer, bytesRead);
      }

      // Change timeout dynamically
      LOG.info("Changing timeout to 5 seconds...");
      serialPort.setTimeout(5000);

      // Demonstrate partial writes
      byte[] largeData = new byte[1024];
      for (int i = 0; i < largeData.length; i++) {
        largeData[i] = (byte) (i % 256);
      }

      LOG.info("Writing large data in chunks...");
      int chunkSize = 128;
      for (int offset = 0; offset < largeData.length; offset += chunkSize) {
        int length = Math.min(chunkSize, largeData.length - offset);
        int chunkWritten = serialPort.write(largeData, offset, length);
        LOG.info("  Chunk at offset {}: {} bytes", offset, chunkWritten);
      }

      serialPort.flush();
      LOG.info("All data flushed to port");

      // Clear output buffer
      serialPort.clearOutput();
      LOG.info("Output buffer cleared");

      LOG.info("Advanced example completed!");

    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOG.error("Error: {}", e.getMessage(), e);
    }
    finally {
      serialPort.close();
      LOG.info("Port closed.");
    }
  }

  /**
   * Log a hex dump of binary data.
   */
  private static void logHexDump(byte[] data, int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      if (i % 16 == 0 && i > 0) {
        LOG.info("  {}", sb.toString());
        sb.setLength(0);
      }
      if (i % 16 == 0) {
        sb.append(String.format("%04X: ", i));
      }
      sb.append(String.format("%02X ", data[i] & 0xFF));
    }
    if (sb.length() > 0) {
      LOG.info("  {}", sb.toString());
    }
  }

}

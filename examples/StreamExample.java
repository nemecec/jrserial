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
package examples;

import dev.nemecec.jrserial.SerialPort;
import dev.nemecec.jrserial.SerialPortInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

/**
 * Example demonstrating stream-based I/O with serial ports.
 */
public class StreamExample {

  private static final Logger LOG = LoggerFactory.getLogger(StreamExample.class);

  public static void main(String[] args) {
    List<SerialPortInfo> ports = SerialPort.listPorts();
    if (ports.isEmpty()) {
      LOG.warn("No serial ports found!");
      return;
    }

    String portName = ports.get(0).getPortName();
    LOG.info("Using port: {}", portName);

    try (SerialPort serialPort = SerialPort.builder()
        .portName(portName)
        .baudRate(9600)
        .timeout(5000)  // 5 second timeout
        .build()) {

      serialPort.open();
      LOG.info("Port opened successfully!");

      // Get streams
      OutputStream out = serialPort.getOutputStream();
      BufferedReader in = new BufferedReader(
          new InputStreamReader(serialPort.getInputStream())
      );

      // Send a command
      String command = "AT\r\n";  // Example AT command
      LOG.info("Sending: {}", command.trim());
      out.write(command.getBytes());
      out.flush();

      // Read response line by line
      LOG.info("Waiting for response...");
      String line;
      int lineCount = 0;
      while (lineCount < 10) {  // Read up to 10 lines
        // Check if data is available (non-blocking check)
        if (serialPort.available() > 0) {
          line = in.readLine();
          if (line != null) {
            LOG.info("< {}", line);
            lineCount++;
          }
        }
        else {
          // No data available, wait a bit
          Thread.sleep(100);
        }

        // Break if we've waited too long with no data
        if (lineCount == 0) {
          break;
        }
      }

      LOG.info("Stream example completed!");
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOG.error("Error: {}", e.getMessage(), e);
    }
  }

}

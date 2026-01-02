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

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for SerialPort.
 */
class SerialPortTest {

  private static final Logger LOG = LoggerFactory.getLogger(SerialPortTest.class);

  @Test
  void testListPorts() {
    try {
      // This should not throw an exception
      List<SerialPortInfo> ports = SerialPort.listPorts();
      assertThat(ports).isNotNull();
      // We can't assert on the content as it depends on the system
    }
    catch (UnsatisfiedLinkError e) {
      // Skip test if native library can't be loaded (architecture mismatch)
      LOG.warn("Skipping test - native library not available for current architecture: {}", e.getMessage());
      org.junit.jupiter.api.Assumptions.assumeTrue(false, "Native library not available for current architecture");
    }
  }

  @Test
  void testBuilderRequiresPortName() {
    SerialPort.Builder builder = SerialPort.builder();
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Port name must be set");
  }

  @Test
  void testBuilderDefaults() {
    SerialPort port = SerialPort.builder()
        .portName("COM1")
        .build();

    assertThat(port.getPortName()).isEqualTo("COM1");
    assertThat(port.getBaudRate()).isEqualTo(9600);
    assertThat(port.getDataBits()).isEqualTo(DataBits.EIGHT);
    assertThat(port.getStopBits()).isEqualTo(StopBits.ONE);
    assertThat(port.getParity()).isEqualTo(Parity.NONE);
    assertThat(port.isOpen()).isFalse();
  }

  @Test
  void testBuilderCustomValues() {
    SerialPort port = SerialPort.builder()
        .portName("/dev/ttyUSB0")
        .baudRate(115200)
        .dataBits(DataBits.SEVEN)
        .stopBits(StopBits.TWO)
        .parity(Parity.EVEN)
        .timeout(2000)
        .build();

    assertThat(port.getPortName()).isEqualTo("/dev/ttyUSB0");
    assertThat(port.getBaudRate()).isEqualTo(115200);
    assertThat(port.getDataBits()).isEqualTo(DataBits.SEVEN);
    assertThat(port.getStopBits()).isEqualTo(StopBits.TWO);
    assertThat(port.getParity()).isEqualTo(Parity.EVEN);
  }

  @Test
  void testOperationsOnClosedPortThrowException() {
    SerialPort port = SerialPort.builder()
        .portName("COM1")
        .build();

    byte[] buffer = new byte[10];

    assertThatThrownBy(() -> port.read(buffer))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("not open");

    assertThatThrownBy(() -> port.write(buffer))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("not open");

    assertThatThrownBy(port::available)
        .isInstanceOf(Exception.class)
        .hasMessageContaining("not open");

    assertThatThrownBy(port::flush)
        .isInstanceOf(Exception.class)
        .hasMessageContaining("not open");
  }

}

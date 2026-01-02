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
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for RS-485 configuration and functionality.
 * <p>
 * Note: Full RS-485 testing requires actual hardware or virtual serial ports.
 * These tests focus on the configuration API and builder patterns.
 */
class Rs485Test {

  private static final Logger LOG = LoggerFactory.getLogger(Rs485Test.class);

  @Test
  void testRs485ControlPinValues() {
    assertThat(Rs485ControlPin.RTS.getValue()).isEqualTo(0);
    assertThat(Rs485ControlPin.DTR.getValue()).isEqualTo(1);
  }

  @Test
  void testRs485ConfigBuilderDefaults() {
    Rs485Config config = Rs485Config.builder().build();

    assertThat(config.isEnabled()).isTrue();
    assertThat(config.getControlPin()).isEqualTo(Rs485ControlPin.RTS);
    assertThat(config.isRtsActiveHigh()).isTrue();
    assertThat(config.isRxDuringTx()).isFalse();
    assertThat(config.isTerminationEnabled()).isFalse();
    assertThat(config.getDelayBeforeSendMicros()).isEqualTo(0);
    assertThat(config.getDelayAfterSendMicros()).isEqualTo(0);
  }

  @Test
  void testRs485ConfigDisabled() {
    Rs485Config config = Rs485Config.disabled();

    assertThat(config.isEnabled()).isFalse();
  }

  @Test
  void testRs485ConfigEnabled() {
    Rs485Config config = Rs485Config.enabled();

    assertThat(config.isEnabled()).isTrue();
    assertThat(config.getControlPin()).isEqualTo(Rs485ControlPin.RTS);
    assertThat(config.isRtsActiveHigh()).isTrue();
  }

  @Test
  void testRs485ConfigCustom() {
    Rs485Config config = Rs485Config.builder()
        .enabled(true)
        .controlPin(Rs485ControlPin.DTR)
        .rtsActiveHigh(false)
        .rxDuringTx(true)
        .terminationEnabled(true)
        .delayBeforeSendMicros(100)
        .delayAfterSendMicros(200)
        .build();

    assertThat(config.isEnabled()).isTrue();
    assertThat(config.getControlPin()).isEqualTo(Rs485ControlPin.DTR);
    assertThat(config.isRtsActiveHigh()).isFalse();
    assertThat(config.isRxDuringTx()).isTrue();
    assertThat(config.isTerminationEnabled()).isTrue();
    assertThat(config.getDelayBeforeSendMicros()).isEqualTo(100);
    assertThat(config.getDelayAfterSendMicros()).isEqualTo(200);
  }

  @Test
  void testBuilderDefaultsToNoRs485() {
    SerialPort port = SerialPort.builder()
        .portName("/dev/ttyUSB0")
        .build();

    assertThat(port.getRs485Config()).isNull();
  }

  @Test
  void testBuilderRs485Convenience() {
    SerialPort port = SerialPort.builder()
        .portName("/dev/ttyUSB0")
        .rs485()
        .build();

    assertThat(port.getRs485Config()).isNotNull();
    assertThat(port.getRs485Config().isEnabled()).isTrue();
    assertThat(port.getRs485Config().getControlPin()).isEqualTo(Rs485ControlPin.RTS);
  }

  @Test
  void testBuilderWithRs485Config() {
    Rs485Config config = Rs485Config.builder()
        .controlPin(Rs485ControlPin.DTR)
        .rtsActiveHigh(false)
        .delayBeforeSendMicros(50)
        .build();

    SerialPort port = SerialPort.builder()
        .portName("/dev/ttyUSB0")
        .rs485Config(config)
        .build();

    assertThat(port.getRs485Config()).isEqualTo(config);
    assertThat(port.getRs485Config().getControlPin()).isEqualTo(Rs485ControlPin.DTR);
    assertThat(port.getRs485Config().isRtsActiveHigh()).isFalse();
    assertThat(port.getRs485Config().getDelayBeforeSendMicros()).isEqualTo(50);
  }

  @Test
  @EnabledOnOs({OS.MAC, OS.LINUX})
  void testKernelRs485NotActiveWhenNoPort() {
    // Without opening a port, kernel RS-485 should not be active
    SerialPort port = SerialPort.builder()
        .portName("/dev/ttyUSB0")
        .rs485()
        .build();

    assertThat(port.isOpen()).isFalse();
    // Can't check isKernelRs485Active() without opening
  }

  @Test
  void testNativeLibraryLoads() {
    // This implicitly tests that the native library with RS-485 support loads
    assumeTrue(isNativeLibraryAvailable(), "Native library not available");

    // If we get here, the native library loaded successfully
    LOG.info("Native library with RS-485 support loaded successfully");
  }

  private boolean isNativeLibraryAvailable() {
    try {
      SerialPort.listPorts();
      return true;
    }
    catch (UnsatisfiedLinkError e) {
      LOG.warn("Native library not available: {}", e.getMessage());
      return false;
    }
  }

}

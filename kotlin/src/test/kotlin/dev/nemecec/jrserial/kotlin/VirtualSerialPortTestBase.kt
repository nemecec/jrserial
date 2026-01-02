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

package dev.nemecec.jrserial.kotlin

import dev.nemecec.jrserial.SerialPort
import dev.nemecec.jrserial.VirtualSerialPortSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

/**
 * Base class for tests that require virtual serial ports via socat.
 * Uses the shared [VirtualSerialPortSupport] from test fixtures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class VirtualSerialPortTestBase {

  protected val support = VirtualSerialPortSupport()

  protected val port1: String
    get() = support.port1 ?: ""

  protected val port2: String
    get() = support.port2 ?: ""

  protected val ptySupported: Boolean
    get() = support.isPtySupported

  @BeforeAll
  fun setupVirtualPorts() {
    if (!VirtualSerialPortSupport.isSocatAvailable()) {
      println("socat not available, skipping PTY tests")
      return
    }

    if (!VirtualSerialPortSupport.isNativeLibraryAvailable()) {
      println("Native library not available, skipping PTY tests")
      return
    }

    support.start()

    if (ptySupported) {
      println("Virtual serial ports created: $port1 <-> $port2")
    }
  }

  @AfterAll
  fun teardownVirtualPorts() {
    support.stop()
  }

  /**
   * Create a SerialPort with the Kotlin DSL.
   */
  protected fun serialPort(portName: String, block: SerialPortBuilder.() -> Unit = {}): SerialPort {
    return dev.nemecec.jrserial.kotlin.serialPort(portName, block)
  }
}

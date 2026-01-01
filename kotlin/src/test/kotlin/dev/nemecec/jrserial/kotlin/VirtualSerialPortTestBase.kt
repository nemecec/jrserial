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

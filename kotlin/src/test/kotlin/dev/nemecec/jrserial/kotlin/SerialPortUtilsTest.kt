package dev.nemecec.jrserial.kotlin

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class SerialPortUtilsTest : VirtualSerialPortTestBase() {

  @Test
  fun `withOpen opens and closes port automatically`() {
    assumeTrue(ptySupported, "PTY devices not supported")

    val port = serialPort(port1) { baudRate = 115200 }

    assertThat(port.isOpen).isFalse()

    val result = port.withOpen {
      assertThat(isOpen).isTrue()
      "result from block"
    }

    assertThat(result).isEqualTo("result from block")
    assertThat(port.isOpen).isFalse()
  }

  @Test
  fun `withOpen closes port even on exception`() {
    assumeTrue(ptySupported, "PTY devices not supported")

    val port = serialPort(port1) { baudRate = 115200 }

    try {
      port.withOpen {
        assertThat(isOpen).isTrue()
        throw RuntimeException("Test exception")
      }
    } catch (e: RuntimeException) {
      assertThat(e.message).isEqualTo("Test exception")
    }

    assertThat(port.isOpen).isFalse()
  }

  @Test
  fun `withOpen allows read and write operations`() {
    assumeTrue(ptySupported, "PTY devices not supported")

    val sender = serialPort(port1) { baudRate = 115200; timeout = 1000 }
    val receiver = serialPort(port2) { baudRate = 115200; timeout = 1000 }

    sender.withOpen {
      receiver.withOpen {
        writeLine("Hello withOpen")
        flush()

        Thread.sleep(100)

        val line = receiver.readLine()
        assertThat(line).isEqualTo("Hello withOpen")
      }
    }
  }

  @Test
  fun `withOpenAsync opens and closes port automatically`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val port = serialPort(port1) { baudRate = 115200 }

    assertThat(port.isOpen).isFalse()

    val result = port.withOpenAsync {
      assertThat(isOpen).isTrue()
      "async result"
    }

    assertThat(result).isEqualTo("async result")
    assertThat(port.isOpen).isFalse()
  }

  @Test
  fun `withOpenAsync closes port even on exception`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val port = serialPort(port1) { baudRate = 115200 }

    try {
      port.withOpenAsync {
        assertThat(isOpen).isTrue()
        throw RuntimeException("Async test exception")
      }
    } catch (e: RuntimeException) {
      assertThat(e.message).isEqualTo("Async test exception")
    }

    assertThat(port.isOpen).isFalse()
  }

  @Test
  fun `withOpenAsync allows async operations`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val sender = serialPort(port1) { baudRate = 115200; timeout = 1000 }
    val receiver = serialPort(port2) { baudRate = 115200; timeout = 1000 }

    sender.withOpenAsync {
      receiver.withOpenAsync {
        writeLineAsync("Hello withOpenAsync")
        flush()

        kotlinx.coroutines.delay(100)

        val line = receiver.readLineAsync()
        assertThat(line).isEqualTo("Hello withOpenAsync")
      }
    }
  }
}

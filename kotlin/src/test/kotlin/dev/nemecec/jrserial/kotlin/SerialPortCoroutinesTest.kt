package dev.nemecec.jrserial.kotlin

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class SerialPortCoroutinesTest : VirtualSerialPortTestBase() {

  @Test
  fun `writeAsync and readAsync work`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val sender = serialPort(port1) { baudRate = 115200; timeout = 1000 }
    val receiver = serialPort(port2) { baudRate = 115200; timeout = 1000 }

    sender.withOpenAsync {
      receiver.withOpenAsync {
        val message = "Hello Coroutines!".toByteArray()
        writeAsync(message)
        flush()

        kotlinx.coroutines.delay(100)

        val buffer = ByteArray(50)
        val bytesRead = receiver.readAsync(buffer)

        assertThat(bytesRead).isEqualTo(message.size)
        assertThat(String(buffer, 0, bytesRead)).isEqualTo("Hello Coroutines!")
      }
    }
  }

  @Test
  fun `writeStringAsync and readLineAsync work`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val sender = serialPort(port1) { baudRate = 115200; timeout = 1000 }
    val receiver = serialPort(port2) { baudRate = 115200; timeout = 1000 }

    sender.withOpenAsync {
      receiver.withOpenAsync {
        writeLineAsync("Hello from coroutine")
        flush()

        kotlinx.coroutines.delay(100)

        val line = receiver.readLineAsync()
        assertThat(line).isEqualTo("Hello from coroutine")
      }
    }
  }

  @Test
  fun `readExactlyAsync blocks until all bytes received`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val sender = serialPort(port1) { baudRate = 115200; timeout = 2000 }
    val receiver = serialPort(port2) { baudRate = 115200; timeout = 2000 }

    sender.withOpenAsync {
      receiver.withOpenAsync {
        val message = "0123456789".toByteArray()
        writeAsync(message)
        flush()

        kotlinx.coroutines.delay(100)

        val result = receiver.readExactlyAsync(10)
        assertThat(result).hasSize(10)
        assertThat(String(result)).isEqualTo("0123456789")
      }
    }
  }

  @Test
  fun `multiple writeLineAsync and readLineAsync`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val sender = serialPort(port1) { baudRate = 115200; timeout = 1000 }
    val receiver = serialPort(port2) { baudRate = 115200; timeout = 1000 }

    sender.withOpenAsync {
      receiver.withOpenAsync {
        writeLineAsync("line1")
        writeLineAsync("line2")
        writeLineAsync("line3")
        flush()

        kotlinx.coroutines.delay(100)

        assertThat(receiver.readLineAsync()).isEqualTo("line1")
        assertThat(receiver.readLineAsync()).isEqualTo("line2")
        assertThat(receiver.readLineAsync()).isEqualTo("line3")
      }
    }
  }
}

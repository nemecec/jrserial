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

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class SerialPortFlowTest : VirtualSerialPortTestBase() {

  @Test
  fun `bytes flow emits individual bytes`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val sender = serialPort(port1) { baudRate = 115200; timeout = 500 }
    val receiver = serialPort(port2) { baudRate = 115200; timeout = 500 }

    sender.open()
    receiver.open()

    try {
      // Send some data
      sender.write("Hello".toByteArray())
      sender.flush()

      delay(100)

      // Collect 5 bytes using the flow
      val bytes = receiver.bytes().take(5).toList()

      assertThat(bytes).hasSize(5)
      assertThat(String(bytes.toByteArray())).isEqualTo("Hello")
    } finally {
      sender.close()
      receiver.close()
    }
  }

  @Test
  fun `chunks flow emits byte arrays`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val sender = serialPort(port1) { baudRate = 115200; timeout = 500 }
    val receiver = serialPort(port2) { baudRate = 115200; timeout = 500 }

    sender.open()
    receiver.open()

    try {
      sender.write("Hello World!".toByteArray())
      sender.flush()

      delay(100)

      // Collect one chunk
      val chunks = receiver.chunks(256).take(1).toList()

      assertThat(chunks).hasSize(1)
      assertThat(String(chunks[0])).isEqualTo("Hello World!")
    } finally {
      sender.close()
      receiver.close()
    }
  }

  @Test
  fun `lines flow emits complete lines`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val sender = serialPort(port1) { baudRate = 115200; timeout = 1000 }
    val receiver = serialPort(port2) { baudRate = 115200; timeout = 1000 }

    sender.open()
    receiver.open()

    try {
      sender.writeLine("line1")
      sender.writeLine("line2")
      sender.writeLine("line3")
      sender.flush()

      delay(100)

      // Collect 3 lines
      val lines = receiver.lines().take(3).toList()

      assertThat(lines).containsExactly("line1", "line2", "line3")
    } finally {
      sender.close()
      receiver.close()
    }
  }

  @Test
  fun `flow can be cancelled`() = runBlocking {
    assumeTrue(ptySupported, "PTY devices not supported")

    val receiver = serialPort(port2) { baudRate = 115200; timeout = 200 }

    receiver.open()

    try {
      // Start collecting, then cancel after a short delay
      val job = launch {
        receiver.bytes().collect { /* just consume */ }
      }

      delay(300)
      job.cancel()

      // Should complete without exception
      job.join()
      assertThat(job.isCancelled).isTrue()
    } finally {
      receiver.close()
    }
  }
}

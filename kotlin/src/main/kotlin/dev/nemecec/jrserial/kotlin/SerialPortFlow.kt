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

@file:JvmName("SerialPortFlow")

package dev.nemecec.jrserial.kotlin

import dev.nemecec.jrserial.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.nio.charset.Charset

/**
 * Returns a [Flow] that emits bytes read from the serial port.
 *
 * The flow will continue emitting bytes until the coroutine is cancelled
 * or an error occurs. Timeouts (read returning 0) are skipped.
 *
 * Example:
 * ```kotlin
 * port.bytes().collect { byte ->
 *     println("Received: ${byte.toInt().toChar()}")
 * }
 * ```
 */
fun SerialPort.bytes(): Flow<Byte> = flow {
  val buffer = ByteArray(1)
  while (currentCoroutineContext().isActive) {
    val bytesRead = read(buffer)
    if (bytesRead > 0) {
      emit(buffer[0])
    }
  }
}.flowOn(Dispatchers.IO)

/**
 * Returns a [Flow] that emits chunks of bytes read from the serial port.
 *
 * @param bufferSize the size of the read buffer (default: 256)
 *
 * Example:
 * ```kotlin
 * port.chunks(1024).collect { chunk ->
 *     println("Received ${chunk.size} bytes")
 * }
 * ```
 */
fun SerialPort.chunks(bufferSize: Int = 256): Flow<ByteArray> = flow {
  val buffer = ByteArray(bufferSize)
  while (currentCoroutineContext().isActive) {
    val bytesRead = read(buffer)
    if (bytesRead > 0) {
      emit(buffer.copyOf(bytesRead))
    }
  }
}.flowOn(Dispatchers.IO)

/**
 * Returns a [Flow] that emits lines read from the serial port.
 *
 * Each line is emitted when a newline character is received.
 * The newline character is not included in the emitted string.
 *
 * Example:
 * ```kotlin
 * port.lines().collect { line ->
 *     println("Received: $line")
 * }
 * ```
 */
fun SerialPort.lines(): Flow<String> = flow {
  while (currentCoroutineContext().isActive) {
    try {
      val line = readLine()
      emit(line)
    } catch (e: IOException) {
      if (e.message?.contains("Timeout") == true) {
        // Skip timeouts, continue waiting for data
        continue
      }
      throw e
    }
  }
}.flowOn(Dispatchers.IO)

/**
 * Returns a [Flow] that emits lines read from the serial port using the specified charset.
 */
fun SerialPort.lines(charset: Charset): Flow<String> = flow {
  while (currentCoroutineContext().isActive) {
    try {
      val line = readLine(charset)
      emit(line)
    } catch (e: IOException) {
      if (e.message?.contains("Timeout") == true) {
        continue
      }
      throw e
    }
  }
}.flowOn(Dispatchers.IO)

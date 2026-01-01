@file:JvmName("SerialPortCoroutines")

package dev.nemecec.jrserial.kotlin

import dev.nemecec.jrserial.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/**
 * Suspending version of [SerialPort.read] that runs on [Dispatchers.IO].
 */
suspend fun SerialPort.readAsync(buffer: ByteArray): Int =
  withContext(Dispatchers.IO) { read(buffer) }

/**
 * Suspending version of [SerialPort.read] with offset and length.
 */
suspend fun SerialPort.readAsync(buffer: ByteArray, offset: Int, length: Int): Int =
  withContext(Dispatchers.IO) { read(buffer, offset, length) }

/**
 * Suspending version of [SerialPort.readExactly].
 */
suspend fun SerialPort.readExactlyAsync(buffer: ByteArray, offset: Int, length: Int): Int =
  withContext(Dispatchers.IO) { readExactly(buffer, offset, length) }

/**
 * Suspending version of [SerialPort.readExactly] that returns a new buffer.
 */
suspend fun SerialPort.readExactlyAsync(length: Int): ByteArray =
  withContext(Dispatchers.IO) { readExactly(length) }

/**
 * Suspending version of [SerialPort.readLine].
 */
suspend fun SerialPort.readLineAsync(): String =
  withContext(Dispatchers.IO) { readLine() }

/**
 * Suspending version of [SerialPort.readLine] with charset.
 */
suspend fun SerialPort.readLineAsync(charset: Charset): String =
  withContext(Dispatchers.IO) { readLine(charset) }

/**
 * Suspending version of [SerialPort.write].
 */
suspend fun SerialPort.writeAsync(data: ByteArray): Int =
  withContext(Dispatchers.IO) { write(data) }

/**
 * Suspending version of [SerialPort.writeString].
 */
suspend fun SerialPort.writeStringAsync(text: String): Int =
  withContext(Dispatchers.IO) { writeString(text) }

/**
 * Suspending version of [SerialPort.writeString] with charset.
 */
suspend fun SerialPort.writeStringAsync(text: String, charset: Charset): Int =
  withContext(Dispatchers.IO) { writeString(text, charset) }

/**
 * Suspending version of [SerialPort.writeLine].
 */
suspend fun SerialPort.writeLineAsync(text: String): Int =
  withContext(Dispatchers.IO) { writeLine(text) }

/**
 * Suspending version of [SerialPort.writeLine] with charset.
 */
suspend fun SerialPort.writeLineAsync(text: String, charset: Charset): Int =
  withContext(Dispatchers.IO) { writeLine(text, charset) }

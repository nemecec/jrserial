@file:JvmName("SerialPortUtils")

package dev.nemecec.jrserial.kotlin

import dev.nemecec.jrserial.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the port, executes the block, and closes the port.
 *
 * Example:
 * ```kotlin
 * serialPort("/dev/ttyUSB0") { baudRate = 9600 }.withOpen {
 *     writeLine("Hello")
 *     val response = readLine()
 * }
 * ```
 */
inline fun <R> SerialPort.withOpen(block: SerialPort.() -> R): R {
  open()
  try {
    return block()
  } finally {
    close()
  }
}

/**
 * Suspending version of [withOpen] for use in coroutines.
 *
 * Example:
 * ```kotlin
 * serialPort("/dev/ttyUSB0") { baudRate = 9600 }.withOpenAsync {
 *     writeLineAsync("Hello")
 *     val response = readLineAsync()
 * }
 * ```
 */
suspend inline fun <R> SerialPort.withOpenAsync(crossinline block: suspend SerialPort.() -> R): R {
  withContext(Dispatchers.IO) { open() }
  try {
    return block()
  } finally {
    withContext(Dispatchers.IO) { close() }
  }
}
